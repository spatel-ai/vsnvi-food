package com.vsnvi.vsnvi_food.auth.infrastructure;

import com.vsnvi.vsnvi_food.auth.domain.AccessTokenIssuer;
import com.vsnvi.vsnvi_food.common.security.AuthenticatedUser;
import com.vsnvi.vsnvi_food.common.security.InvalidTokenException;
import com.vsnvi.vsnvi_food.common.security.TokenAuthenticator;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Issues and validates HS256 access tokens with jjwt (ADR-002). Validation checks the signature, {@code exp} and
 * {@code iss}; it is also the {@link TokenAuthenticator} for HTTP and STOMP (ADR-006).
 */
class JwtService implements AccessTokenIssuer, TokenAuthenticator {

    static final String KEY_ID = "primary";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;
    private final JwtParser parser;

    JwtService(JwtProperties properties, Clock clock) {
        // hmacShaKeyFor rejects keys shorter than 256 bits (WeakKeyException), failing startup.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.signingKey()));
        this.properties = properties;
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    @Override
    public IssuedAccessToken issue(UUID userId, String role) {
        var now = clock.instant();
        var expiresAt = now.plus(properties.accessTokenTtl());
        var token = Jwts.builder()
                .header()
                .keyId(KEY_ID)
                .and()
                .subject(userId.toString())
                .claim(ROLE_CLAIM, role)
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedAccessToken(token, expiresAt);
    }

    @Override
    public AuthenticatedUser authenticate(String token) {
        try {
            var jws = parser.parseSignedClaims(token);
            if (!Jwts.SIG.HS256.getId().equals(jws.getHeader().getAlgorithm())) {
                // Pinned: a longer key must not make HS384/HS512 tokens acceptable (ADR-002).
                throw InvalidTokenException.invalid();
            }
            var claims = jws.getPayload();
            var role = claims.get(ROLE_CLAIM, String.class);
            if (role == null || claims.getSubject() == null || claims.getExpiration() == null) {
                throw InvalidTokenException.invalid();
            }
            return new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    role,
                    claims.getExpiration().toInstant());
        } catch (ExpiredJwtException ex) {
            throw InvalidTokenException.expired();
        } catch (JwtException | IllegalArgumentException ex) {
            // Never log the token itself.
            throw InvalidTokenException.invalid();
        }
    }
}
