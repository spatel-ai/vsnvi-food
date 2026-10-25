package com.vsnvi.vsnvi_food.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.vsnvi.vsnvi_food.auth.domain.AccessTokenIssuer;
import com.vsnvi.vsnvi_food.auth.domain.AccountBlockedException;
import com.vsnvi.vsnvi_food.auth.domain.Credential;
import com.vsnvi.vsnvi_food.auth.domain.CredentialRepository;
import com.vsnvi.vsnvi_food.auth.domain.InvalidCredentialsException;
import com.vsnvi.vsnvi_food.auth.domain.OpaqueTokens;
import com.vsnvi.vsnvi_food.auth.domain.RefreshToken;
import com.vsnvi.vsnvi_food.auth.domain.RefreshTokenRepository;
import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;
import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import com.vsnvi.vsnvi_food.common.ratelimit.RateLimiter;
import com.vsnvi.vsnvi_food.common.security.CurrentUser;
import com.vsnvi.vsnvi_food.user.api.NewUser;
import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserApi;
import com.vsnvi.vsnvi_food.user.api.UserStatus;
import com.vsnvi.vsnvi_food.user.api.UserSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

/** Rules that need a user state other modules own (blocked), tested with {@link UserApi} stubbed. */
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UserSummary BLOCKED =
            new UserSummary(USER_ID, "a@example.com", "Asha", null, Role.CUSTOMER, UserStatus.BLOCKED);

    private final UserApi userApi = mock(UserApi.class);
    private final CredentialRepository credentials = mock(CredentialRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final AccessTokenIssuer accessTokens = mock(AccessTokenIssuer.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    @SuppressWarnings("deprecation") // plain-text encoder keeps the test fast; production uses BCrypt(12)
    private final AuthService authService = new AuthService(
            userApi,
            credentials,
            refreshTokens,
            accessTokens,
            NoOpPasswordEncoder.getInstance(),
            new CurrentUser(),
            events,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new LoginRateLimits(allowingLimiter(), 30, Duration.ofMinutes(5), 10, Duration.ofMinutes(15)),
            Duration.ofDays(30));

    private static RateLimiter allowingLimiter() {
        var limiter = mock(RateLimiter.class);
        var allowed = new RateLimiter.Decision(true, Duration.ZERO);
        given(limiter.hit(anyString(), anyInt(), any())).willReturn(allowed);
        given(limiter.peek(anyString(), anyInt(), any())).willReturn(allowed);
        return limiter;
    }

    @Test
    void should_hide_blocked_status_when_password_is_wrong() {
        given(userApi.findByEmail("a@example.com")).willReturn(Optional.of(BLOCKED));
        given(credentials.findByUserId(USER_ID)).willReturn(Optional.of(Credential.create(USER_ID, "right")));

        assertThatThrownBy(() -> authService.login("a@example.com", "wrong", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void should_return_account_blocked_when_password_is_right_but_user_blocked() {
        given(userApi.findByEmail("a@example.com")).willReturn(Optional.of(BLOCKED));
        given(credentials.findByUserId(USER_ID)).willReturn(Optional.of(Credential.create(USER_ID, "right")));

        assertThatThrownBy(() -> authService.login("a@example.com", "right", "127.0.0.1"))
                .isInstanceOf(AccountBlockedException.class);
        then(accessTokens).shouldHaveNoInteractions();
    }

    @Test
    void should_revoke_family_and_refuse_when_refreshing_for_blocked_user() {
        var familyId = UUID.randomUUID();
        var token = RefreshToken.issue(USER_ID, OpaqueTokens.sha256Hex("raw"), familyId, NOW.plusSeconds(60));
        given(refreshTokens.findForUpdateByTokenHash(OpaqueTokens.sha256Hex("raw")))
                .willReturn(Optional.of(token));
        given(userApi.findById(USER_ID)).willReturn(Optional.of(BLOCKED));

        assertThatThrownBy(() -> authService.refresh("raw")).isInstanceOf(AccountBlockedException.class);
        then(refreshTokens).should().revokeFamily(familyId, NOW);
        then(accessTokens).shouldHaveNoInteractions();
    }

    @Test
    void should_refuse_to_register_admin() {
        assertThatThrownBy(() -> authService.register("a@example.com", "pw-123456", "A", null, Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
        then(userApi).shouldHaveNoInteractions();
    }

    @Test
    void should_create_first_admin_when_bootstrap_configured_and_no_admin_exists() {
        given(userApi.existsWithRole(Role.ADMIN)).willReturn(false);
        given(userApi.createUser(any()))
                .willReturn(new UserSummary(
                        USER_ID, "admin@example.com", "Administrator", null, Role.ADMIN, UserStatus.ACTIVE));

        new AdminBootstrap(userApi, authService, "admin@example.com", "admin-pass")
                .run(mock(ApplicationArguments.class));

        then(userApi).should().createUser(new NewUser("admin@example.com", "Administrator", null, Role.ADMIN));
        then(events).shouldHaveNoInteractions();
    }

    @Test
    void should_skip_bootstrap_when_admin_exists_or_not_configured() {
        given(userApi.existsWithRole(Role.ADMIN)).willReturn(true);

        new AdminBootstrap(userApi, authService, "admin@example.com", "admin-pass")
                .run(mock(ApplicationArguments.class));
        new AdminBootstrap(userApi, authService, "", "").run(mock(ApplicationArguments.class));

        then(userApi).should(never()).createUser(any());
    }

    @Test
    void should_return_invalid_credentials_not_crash_when_password_exceeds_72_bytes() {
        var emoji = "\uD83D\uDE00".repeat(30); // 60 chars, 120 bytes

        assertThatThrownBy(() -> authService.login("a@example.com", emoji, "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
        then(userApi).shouldHaveNoInteractions();
    }

    @Test
    void should_reject_weak_password_for_any_account_when_creating() {
        assertThatThrownBy(() -> authService.createAccount("a@example.com", "short", "A", null, Role.ADMIN))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> authService.createAccount("a@example.com", "\uD83D\uDE00".repeat(19), "A", null, Role.ADMIN))
                .isInstanceOf(ValidationException.class);
        then(userApi).shouldHaveNoInteractions();
    }

    @Test
    void should_skip_bootstrap_without_failing_startup_when_email_is_taken() {
        given(userApi.existsWithRole(Role.ADMIN)).willReturn(false);
        given(userApi.createUser(any())).willThrow(new DomainException(ErrorCode.EMAIL_ALREADY_REGISTERED, "taken") {});

        new AdminBootstrap(userApi, authService, "admin@example.com", "admin-pass")
                .run(mock(ApplicationArguments.class));

        then(credentials).shouldHaveNoInteractions();
    }
}
