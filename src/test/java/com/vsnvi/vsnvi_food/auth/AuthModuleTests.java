package com.vsnvi.vsnvi_food.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vsnvi.vsnvi_food.TestcontainersConfiguration;
import com.vsnvi.vsnvi_food.auth.api.UserRegistered;
import com.vsnvi.vsnvi_food.auth.application.AuthService;
import com.vsnvi.vsnvi_food.auth.domain.CredentialRepository;
import com.vsnvi.vsnvi_food.auth.domain.InvalidCredentialsException;
import com.vsnvi.vsnvi_food.auth.domain.InvalidRefreshTokenException;
import com.vsnvi.vsnvi_food.auth.domain.OpaqueTokens;
import com.vsnvi.vsnvi_food.auth.domain.RefreshTokenRepository;
import com.vsnvi.vsnvi_food.common.ratelimit.RateLimitedException;
import com.vsnvi.vsnvi_food.common.security.AuthenticatedUser;
import com.vsnvi.vsnvi_food.common.security.TokenAuthenticator;
import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserBlocked;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.Scenario;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES, extraIncludes = "common")
@Import(TestcontainersConfiguration.class)
class AuthModuleTests {

    private static final String PASSWORD = "s3cret-pass";

    private final AuthService authService;
    private final CredentialRepository credentials;
    private final RefreshTokenRepository refreshTokens;
    private final TokenAuthenticator tokenAuthenticator;
    private final TransactionTemplate tx;

    AuthModuleTests(
            @Autowired AuthService authService,
            @Autowired CredentialRepository credentials,
            @Autowired RefreshTokenRepository refreshTokens,
            @Autowired TokenAuthenticator tokenAuthenticator,
            @Autowired TransactionTemplate tx) {
        this.authService = authService;
        this.credentials = credentials;
        this.refreshTokens = refreshTokens;
        this.tokenAuthenticator = tokenAuthenticator;
        this.tx = tx;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_publish_UserRegistered_when_rider_registers(Scenario scenario) {
        scenario.stimulate(() -> authService.register(email(), PASSWORD, "Ravi", null, Role.RIDER))
                .andWaitForEventOfType(UserRegistered.class)
                .toArriveAndVerify(event -> assertThat(event.role()).isEqualTo(Role.RIDER));
    }

    @Test
    void should_store_bcrypt_hash_not_password_when_registering() {
        var user = authService.register(email(), PASSWORD, "Asha", null, Role.CUSTOMER);

        var hash = credentials.findByUserId(user.id()).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("{bcrypt}$2a$12$").doesNotContain(PASSWORD);
    }

    @Test
    void should_issue_access_token_that_authenticates_when_login_succeeds() {
        var email = email();
        var user = authService.register(email, PASSWORD, "Asha", null, Role.CUSTOMER);

        var tokens = authService.login(email.toUpperCase(), PASSWORD, "127.0.0.1");

        var principal = tokenAuthenticator.authenticate(tokens.accessToken());
        assertThat(principal.id()).isEqualTo(user.id());
        assertThat(principal.role()).isEqualTo("CUSTOMER");
        assertThat(storedHashOf(tokens.refreshToken())).isTrue();
    }

    @Test
    void should_fail_identically_when_email_unknown_or_password_wrong() {
        var email = email();
        authService.register(email, PASSWORD, "Asha", null, Role.CUSTOMER);

        assertThatThrownBy(() -> authService.login(email, "wrong-password", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        assertThatThrownBy(() -> authService.login("nobody@example.com", PASSWORD, "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void should_rotate_and_revoke_family_when_old_refresh_token_is_reused() {
        var email = email();
        authService.register(email, PASSWORD, "Asha", null, Role.CUSTOMER);
        var first = authService.login(email, PASSWORD, "127.0.0.1");

        var second = authService.refresh(first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

        assertThatThrownBy(() -> authService.refresh(first.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        // Reuse revoked the whole family, including the legitimately rotated token.
        assertThatThrownBy(() -> authService.refresh(second.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void should_revoke_family_when_owner_logs_out() {
        var email = email();
        var user = authService.register(email, PASSWORD, "Asha", null, Role.CUSTOMER);
        var tokens = authService.login(email, PASSWORD, "127.0.0.1");

        signInAs(user.id());
        authService.logout(tokens.refreshToken());

        assertThatThrownBy(() -> authService.refresh(tokens.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void should_ignore_logout_when_refresh_token_belongs_to_someone_else() {
        var email = email();
        authService.register(email, PASSWORD, "Asha", null, Role.CUSTOMER);
        var tokens = authService.login(email, PASSWORD, "127.0.0.1");

        signInAs(UUID.randomUUID());
        authService.logout(tokens.refreshToken());

        assertThat(authService.refresh(tokens.refreshToken()).accessToken()).isNotBlank();
    }

    @Test
    void should_revoke_tokens_idempotently_when_UserBlocked_arrives_twice(Scenario scenario) {
        var email = email();
        var user = authService.register(email, PASSWORD, "Asha", null, Role.CUSTOMER);
        var tokens = authService.login(email, PASSWORD, "127.0.0.1");
        var event = new UserBlocked(user.id(), "fraud", Instant.now());

        scenario.publish(event).andWaitForStateChange(() -> isRevoked(tokens.refreshToken()));
        scenario.publish(event).andWaitForStateChange(() -> isRevoked(tokens.refreshToken()));

        assertThat(authService.revokeAllTokensOf(user.id())).isZero();
    }

    @Test
    void should_rotate_once_when_same_refresh_token_is_used_concurrently() throws Exception {
        var email = email();
        authService.register(email, PASSWORD, "Asha", null, Role.CUSTOMER);
        var token = authService.login(email, PASSWORD, "127.0.0.1").refreshToken();
        var start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var attempts = List.of(pool.submit(() -> refreshAfter(start, token)), pool.submit(() -> refreshAfter(start, token)));
            start.countDown();
            var outcomes = new ArrayList<String>();
            for (var attempt : attempts) {
                outcomes.add(attempt.get(30, TimeUnit.SECONDS));
            }
            // The row lock serialises them: one rotates, the other sees a used token and revokes the family.
            assertThat(outcomes).containsExactlyInAnyOrder("rotated", "rejected");
        }
    }

    private String refreshAfter(CountDownLatch start, String token) throws InterruptedException {
        start.await();
        try {
            authService.refresh(token);
            return "rotated";
        } catch (InvalidRefreshTokenException ex) {
            return "rejected";
        }
    }

    @Test
    void should_rate_limit_account_after_ten_failures_even_with_right_password() {
        var email = email();
        authService.register(email, PASSWORD, "Asha", null, Role.CUSTOMER);
        var ip = randomIp();
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> authService.login(email, "wrong-password", ip))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> authService.login(email, PASSWORD, randomIp())).isInstanceOf(RateLimitedException.class);
    }

    @Test
    void should_rate_limit_unknown_email_like_a_real_account() {
        var unknown = email();
        var ip = randomIp();
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> authService.login(unknown, PASSWORD, ip)).isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> authService.login(unknown, PASSWORD, ip)).isInstanceOf(RateLimitedException.class);
    }

    @Test
    void should_reset_failures_when_login_succeeds() {
        var email = email();
        authService.register(email, PASSWORD, "Asha", null, Role.CUSTOMER);
        var ip = randomIp();
        for (int i = 0; i < 9; i++) {
            assertThatThrownBy(() -> authService.login(email, "wrong-password", ip))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        authService.login(email, PASSWORD, ip);

        for (int i = 0; i < 9; i++) {
            assertThatThrownBy(() -> authService.login(email, "wrong-password", randomIp()))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        assertThat(authService.login(email, PASSWORD, randomIp()).accessToken()).isNotBlank();
    }

    @Test
    void should_rate_limit_client_ip_after_thirty_attempts_across_accounts() {
        var ip = randomIp();
        for (int i = 0; i < 30; i++) {
            var attempt = email();
            assertThatThrownBy(() -> authService.login(attempt, PASSWORD, ip)).isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> authService.login(email(), PASSWORD, ip)).isInstanceOf(RateLimitedException.class);
    }

    private static String randomIp() {
        var r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    private Boolean isRevoked(String rawRefreshToken) {
        return tx.execute(status -> refreshTokens
                .findForUpdateByTokenHash(OpaqueTokens.sha256Hex(rawRefreshToken))
                .map(t -> t.getRevokedAt() != null)
                .orElse(false));
    }

    private boolean storedHashOf(String rawRefreshToken) {
        return Boolean.TRUE.equals(tx.execute(status -> refreshTokens
                .findForUpdateByTokenHash(OpaqueTokens.sha256Hex(rawRefreshToken))
                .isPresent()));
    }

    private static String email() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static void signInAs(UUID userId) {
        var principal = new AuthenticatedUser(userId, "CUSTOMER", Instant.now().plusSeconds(900));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }
}
