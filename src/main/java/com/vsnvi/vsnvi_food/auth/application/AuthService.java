package com.vsnvi.vsnvi_food.auth.application;

import com.vsnvi.vsnvi_food.auth.api.UserRegistered;
import com.vsnvi.vsnvi_food.auth.domain.AccessTokenIssuer;
import com.vsnvi.vsnvi_food.auth.domain.AccountBlockedException;
import com.vsnvi.vsnvi_food.auth.domain.Credential;
import com.vsnvi.vsnvi_food.auth.domain.CredentialRepository;
import com.vsnvi.vsnvi_food.auth.domain.InvalidCredentialsException;
import com.vsnvi.vsnvi_food.auth.domain.InvalidRefreshTokenException;
import com.vsnvi.vsnvi_food.auth.domain.OpaqueTokens;
import com.vsnvi.vsnvi_food.auth.domain.PasswordPolicy;
import com.vsnvi.vsnvi_food.auth.domain.RefreshToken;
import com.vsnvi.vsnvi_food.auth.domain.RefreshTokenRepository;
import com.vsnvi.vsnvi_food.common.security.CurrentUser;
import com.vsnvi.vsnvi_food.user.api.NewUser;
import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserApi;
import com.vsnvi.vsnvi_food.user.api.UserSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, login, refresh-token rotation and logout (docs/SECURITY.md §1). Never logs passwords or tokens. */
@Slf4j
@Service
public class AuthService {

    private final UserApi userApi;
    private final CredentialRepository credentials;
    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenIssuer accessTokens;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration refreshTokenTtl;
    private final AuthRateLimits rateLimits;

    /** Compared against when the email is unknown, so a missing account takes as long as a wrong password. */
    private final String dummyHash;

    public AuthService(
            UserApi userApi,
            CredentialRepository credentials,
            RefreshTokenRepository refreshTokens,
            AccessTokenIssuer accessTokens,
            PasswordEncoder passwordEncoder,
            CurrentUser currentUser,
            ApplicationEventPublisher events,
            Clock clock,
            AuthRateLimits rateLimits,
            @Value("${auth.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.userApi = userApi;
        this.credentials = credentials;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.events = events;
        this.clock = clock;
        this.refreshTokenTtl = refreshTokenTtl;
        this.rateLimits = rateLimits;
        this.dummyHash = passwordEncoder.encode(OpaqueTokens.generate());
    }

    /**
     * Rate-limited per client IP. Registration can never create an {@code ADMIN}: the web layer only accepts the three
     * public roles.
     */
    @Transactional
    public UserSummary register(
            String email, String password, String fullName, String phone, Role role, String clientIp) {
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("Registration can't create an ADMIN");
        }
        rateLimits.checkRegistration(clientIp);
        var user = createAccount(email, password, fullName, phone, role);
        events.publishEvent(new UserRegistered(user.id(), user.email(), user.role(), Instant.now(clock)));
        return user;
    }

    /** Creates the user and their credential, without publishing {@link UserRegistered}. Used by the bootstrap. */
    @Transactional
    public UserSummary createAccount(String email, String password, String fullName, String phone, Role role) {
        PasswordPolicy.check(password);
        var user = userApi.createUser(new NewUser(email, fullName, phone, role));
        credentials.save(Credential.create(user.id(), passwordEncoder.encode(password)));
        log.info("Registered user {} with role {}", user.id(), user.role());
        return user;
    }

    /** Rate-limited per client IP and per account (failed attempts); over a limit returns {@code RATE_LIMITED}. */
    @Transactional
    public Tokens login(String email, String password, String clientIp) {
        rateLimits.checkLoginAttempt(clientIp, email);
        if (!PasswordPolicy.fitsBcrypt(password)) {
            // No stored password can be this long, and BCrypt would throw on it.
            rateLimits.recordLoginFailure(email);
            throw new InvalidCredentialsException();
        }
        var user = userApi.findByEmail(email).orElse(null);
        var credential =
                user == null ? null : credentials.findByUserId(user.id()).orElse(null);
        var hash = credential == null ? dummyHash : credential.getPasswordHash();
        var matches = passwordEncoder.matches(password, hash);
        if (credential == null || !matches) {
            rateLimits.recordLoginFailure(email);
            throw new InvalidCredentialsException();
        }
        rateLimits.recordLoginSuccess(email);
        // Checked only after the password, so a blocked status never reveals that an account exists.
        if (user.isBlocked()) {
            throw new AccountBlockedException();
        }
        return issueTokens(user, UUID.randomUUID());
    }

    /**
     * Rotates a refresh token. A token that was already used or revoked is reuse: the whole family is revoked, so a
     * stolen token stops working for both the thief and the owner. Rate-limited per client IP.
     */
    // The family revoke must commit even though the request fails.
    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class, AccountBlockedException.class})
    public Tokens refresh(String rawRefreshToken, String clientIp) {
        rateLimits.checkRefresh(clientIp);
        var now = clock.instant();
        var token = refreshTokens
                .findForUpdateByTokenHash(OpaqueTokens.sha256Hex(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!token.isFresh()) {
            int revoked = refreshTokens.revokeFamily(token.getFamilyId(), now);
            log.warn("Refresh token reuse for user {}: revoked {} token(s) in its family", token.getUserId(), revoked);
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }
        var user = userApi.findById(token.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
        if (user.isBlocked()) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            throw new AccountBlockedException();
        }
        token.markUsed(now);
        return issueTokens(user, token.getFamilyId());
    }

    /**
     * Revokes the token's family if it belongs to the caller. Unknown or foreign tokens are ignored, so the response
     * doesn't reveal anything about them.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        var callerId = currentUser.id();
        refreshTokens
                .findForUpdateByTokenHash(OpaqueTokens.sha256Hex(rawRefreshToken))
                .filter(token -> token.getUserId().equals(callerId))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    /** Idempotent: revoking a user's tokens twice revokes nothing the second time. */
    @Transactional
    public int revokeAllTokensOf(UUID userId) {
        return refreshTokens.revokeAllOfUser(userId, clock.instant());
    }

    private Tokens issueTokens(UserSummary user, UUID familyId) {
        var access = accessTokens.issue(user.id(), user.role().name());
        var rawRefresh = OpaqueTokens.generate();
        var refreshExpiresAt = clock.instant().plus(refreshTokenTtl);
        refreshTokens.save(
                RefreshToken.issue(user.id(), OpaqueTokens.sha256Hex(rawRefresh), familyId, refreshExpiresAt));
        return new Tokens(access.token(), access.expiresAt(), rawRefresh, refreshExpiresAt);
    }

    public record Tokens(
            String accessToken, Instant accessTokenExpiresAt, String refreshToken, Instant refreshTokenExpiresAt) {}
}
