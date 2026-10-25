package com.vsnvi.vsnvi_food.auth.application;

import com.vsnvi.vsnvi_food.auth.domain.OpaqueTokens;
import com.vsnvi.vsnvi_food.common.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Login limits (docs/SECURITY.md §1): every attempt counts against the client IP; failed attempts count against the
 * account, and a successful login resets that count. Unknown emails are limited exactly like real ones, so the limit
 * doesn't reveal which accounts exist. Account keys hold a SHA-256 of the email, never the email itself.
 */
@Component
class LoginRateLimits {

    private final RateLimiter limiter;
    private final int perIp;
    private final Duration ipWindow;
    private final int failuresPerAccount;
    private final Duration accountWindow;

    LoginRateLimits(
            RateLimiter limiter,
            @Value("${auth.login-rate-limit.per-ip:30}") int perIp,
            @Value("${auth.login-rate-limit.ip-window:5m}") Duration ipWindow,
            @Value("${auth.login-rate-limit.failures-per-account:10}") int failuresPerAccount,
            @Value("${auth.login-rate-limit.account-window:15m}") Duration accountWindow) {
        this.limiter = limiter;
        this.perIp = perIp;
        this.ipWindow = ipWindow;
        this.failuresPerAccount = failuresPerAccount;
        this.accountWindow = accountWindow;
    }

    /** Counts this attempt for the IP and checks the account; throws {@code RATE_LIMITED} when over a limit. */
    void checkAttempt(String clientIp, String email) {
        limiter.hit("login:ip:" + clientIp, perIp, ipWindow).orThrow();
        limiter.peek(accountKey(email), failuresPerAccount, accountWindow).orThrow();
    }

    void recordFailure(String email) {
        limiter.hit(accountKey(email), failuresPerAccount, accountWindow);
    }

    void recordSuccess(String email) {
        limiter.reset(accountKey(email));
    }

    private static String accountKey(String email) {
        var normalized = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        return "login:account:" + OpaqueTokens.sha256Hex(normalized);
    }
}
