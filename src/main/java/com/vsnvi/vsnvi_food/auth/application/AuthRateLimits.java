package com.vsnvi.vsnvi_food.auth.application;

import com.vsnvi.vsnvi_food.auth.domain.OpaqueTokens;
import com.vsnvi.vsnvi_food.common.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits on the public auth endpoints (docs/SECURITY.md §1).
 *
 * <p>Login: every attempt counts against the client IP; failed attempts count against the account, and a successful
 * login resets that count. Unknown emails are limited exactly like real ones, so the limit doesn't reveal which
 * accounts exist. Account keys hold a SHA-256 of the email, never the email itself.
 *
 * <p>Register and refresh: every attempt counts against the client IP. This bounds email probing through
 * {@code EMAIL_ALREADY_REGISTERED} and the BCrypt work each registration costs.
 */
@Component
class AuthRateLimits {

    record Limit(int max, Duration window) {}

    private final RateLimiter limiter;
    private final Limit loginPerIp;
    private final Limit loginFailuresPerAccount;
    private final Limit registerPerIp;
    private final Limit refreshPerIp;

    @Autowired
    AuthRateLimits(
            RateLimiter limiter,
            @Value("${auth.login-rate-limit.per-ip:30}") int loginPerIp,
            @Value("${auth.login-rate-limit.ip-window:5m}") Duration loginIpWindow,
            @Value("${auth.login-rate-limit.failures-per-account:10}") int failuresPerAccount,
            @Value("${auth.login-rate-limit.account-window:15m}") Duration accountWindow,
            @Value("${auth.register-rate-limit.per-ip:20}") int registerPerIp,
            @Value("${auth.register-rate-limit.ip-window:1h}") Duration registerIpWindow,
            @Value("${auth.refresh-rate-limit.per-ip:120}") int refreshPerIp,
            @Value("${auth.refresh-rate-limit.ip-window:5m}") Duration refreshIpWindow) {
        this(
                limiter,
                new Limit(loginPerIp, loginIpWindow),
                new Limit(failuresPerAccount, accountWindow),
                new Limit(registerPerIp, registerIpWindow),
                new Limit(refreshPerIp, refreshIpWindow));
    }

    AuthRateLimits(
            RateLimiter limiter,
            Limit loginPerIp,
            Limit loginFailuresPerAccount,
            Limit registerPerIp,
            Limit refreshPerIp) {
        this.limiter = limiter;
        this.loginPerIp = loginPerIp;
        this.loginFailuresPerAccount = loginFailuresPerAccount;
        this.registerPerIp = registerPerIp;
        this.refreshPerIp = refreshPerIp;
    }

    /** Counts this attempt for the IP and checks the account; throws {@code RATE_LIMITED} when over a limit. */
    void checkLoginAttempt(String clientIp, String email) {
        hit("login:ip:" + clientIp, loginPerIp);
        limiter.peek(accountKey(email), loginFailuresPerAccount.max(), loginFailuresPerAccount.window())
                .orThrow();
    }

    void recordLoginFailure(String email) {
        limiter.hit(accountKey(email), loginFailuresPerAccount.max(), loginFailuresPerAccount.window());
    }

    void recordLoginSuccess(String email) {
        limiter.reset(accountKey(email));
    }

    void checkRegistration(String clientIp) {
        hit("register:ip:" + clientIp, registerPerIp);
    }

    void checkRefresh(String clientIp) {
        hit("refresh:ip:" + clientIp, refreshPerIp);
    }

    private void hit(String key, Limit limit) {
        limiter.hit(key, limit.max(), limit.window()).orThrow();
    }

    private static String accountKey(String email) {
        var normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        return "login:account:" + OpaqueTokens.sha256Hex(normalized);
    }
}
