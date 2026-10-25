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
import com.vsnvi.vsnvi_food.common.security.AuthenticatedUser;
import com.vsnvi.vsnvi_food.common.security.TokenAuthenticator;
import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserBlocked;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

        var tokens = authService.login(email.toUpperCase(), PASSWORD);

        var principal = tokenAuthenticator.authenticate(tokens.accessToken());
        assertThat(principal.id()).isEqualTo(user.id());
        assertThat(principal.role()).isEqualTo("CUSTOMER");
        assertThat(storedHashOf(tokens.refreshToken())).isTrue();
    }

    @Test
    void should_fail_identically_when_email_unknown_or_password_wrong() {
        var email = email();
        authService.register(email, PASSWORD, "Asha", null, Role.CUSTOMER);

        assertThatThrownBy(() -> authService.login(email, "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        assertThatThrownBy(() -> authService.login("nobody@example.com", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void should_rotate_and_revoke_family_when_old_refresh_token_is_reused() {
        var email = email();
        authService.register(email, PASSWORD, "Asha", null, Role.CUSTOMER);
        var first = authService.login(email, PASSWORD);

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
        var tokens = authService.login(email, PASSWORD);

        signInAs(user.id());
        authService.logout(tokens.refreshToken());

        assertThatThrownBy(() -> authService.refresh(tokens.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void should_ignore_logout_when_refresh_token_belongs_to_someone_else() {
        var email = email();
        authService.register(email, PASSWORD, "Asha", null, Role.CUSTOMER);
        var tokens = authService.login(email, PASSWORD);

        signInAs(UUID.randomUUID());
        authService.logout(tokens.refreshToken());

        assertThat(authService.refresh(tokens.refreshToken()).accessToken()).isNotBlank();
    }

    @Test
    void should_revoke_tokens_idempotently_when_UserBlocked_arrives_twice(Scenario scenario) {
        var email = email();
        var user = authService.register(email, PASSWORD, "Asha", null, Role.CUSTOMER);
        var tokens = authService.login(email, PASSWORD);
        var event = new UserBlocked(user.id(), "fraud", Instant.now());

        scenario.publish(event).andWaitForStateChange(() -> isRevoked(tokens.refreshToken()));
        scenario.publish(event).andWaitForStateChange(() -> isRevoked(tokens.refreshToken()));

        assertThat(authService.revokeAllTokensOf(user.id())).isZero();
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
