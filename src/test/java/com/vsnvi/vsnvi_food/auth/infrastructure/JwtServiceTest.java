package com.vsnvi.vsnvi_food.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vsnvi.vsnvi_food.common.security.InvalidTokenException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY =
            Base64.getEncoder().encodeToString("another-256-bit-signing-key-0123".getBytes());
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void should_return_user_and_role_when_token_is_valid() {
        var jwt = service(KEY, "vsnvi-food", NOW);
        var issued = jwt.issue(USER_ID, "CUSTOMER");

        var user = jwt.authenticate(issued.token());

        assertThat(user.id()).isEqualTo(USER_ID);
        assertThat(user.role()).isEqualTo("CUSTOMER");
        assertThat(user.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(issued.expiresAt()).isEqualTo(user.expiresAt());
    }

    @Test
    void should_reject_as_expired_when_token_is_past_exp() {
        var issued = service(KEY, "vsnvi-food", NOW).issue(USER_ID, "CUSTOMER");
        var later = service(KEY, "vsnvi-food", NOW.plus(Duration.ofMinutes(16)));

        assertThatThrownBy(() -> later.authenticate(issued.token()))
                .isInstanceOfSatisfying(
                        InvalidTokenException.class,
                        ex -> assertThat(ex.isExpired()).isTrue());
    }

    @Test
    void should_reject_as_invalid_when_signed_with_another_key() {
        var issued = service(OTHER_KEY, "vsnvi-food", NOW).issue(USER_ID, "ADMIN");

        assertThatThrownBy(() -> service(KEY, "vsnvi-food", NOW).authenticate(issued.token()))
                .isInstanceOfSatisfying(
                        InvalidTokenException.class,
                        ex -> assertThat(ex.isExpired()).isFalse());
    }

    @Test
    void should_reject_as_invalid_when_issuer_differs() {
        var issued = service(KEY, "someone-else", NOW).issue(USER_ID, "CUSTOMER");

        assertThatThrownBy(() -> service(KEY, "vsnvi-food", NOW).authenticate(issued.token()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_reject_as_invalid_when_payload_is_tampered() {
        var jwt = service(KEY, "vsnvi-food", NOW);
        var parts = jwt.issue(USER_ID, "CUSTOMER").token().split("\\.");
        var adminPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(new String(Base64.getUrlDecoder().decode(parts[1]))
                        .replace("CUSTOMER", "ADMIN")
                        .getBytes());

        assertThatThrownBy(() -> jwt.authenticate(parts[0] + "." + adminPayload + "." + parts[2]))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_reject_as_invalid_when_token_is_garbage() {
        assertThatThrownBy(() -> service(KEY, "vsnvi-food", NOW).authenticate("not.a.jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_reject_as_invalid_when_token_is_unsigned_alg_none() {
        var unsigned = Jwts.builder()
                .subject(USER_ID.toString())
                .claim("role", "ADMIN")
                .issuer("vsnvi-food")
                .expiration(Date.from(NOW.plusSeconds(600)))
                .compact();

        assertThatThrownBy(() -> service(KEY, "vsnvi-food", NOW).authenticate(unsigned))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void should_reject_as_invalid_when_signed_with_hs512_even_if_key_is_long_enough() {
        var longKey = Base64.getEncoder().encodeToString(new byte[64]);
        var hs512 = Jwts.builder()
                .subject(USER_ID.toString())
                .claim("role", "ADMIN")
                .issuer("vsnvi-food")
                .expiration(Date.from(NOW.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(longKey)), Jwts.SIG.HS512)
                .compact();

        assertThatThrownBy(() -> service(longKey, "vsnvi-food", NOW).authenticate(hs512))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(service(longKey, "vsnvi-food", NOW)
                        .authenticate(service(longKey, "vsnvi-food", NOW)
                                .issue(USER_ID, "ADMIN")
                                .token())
                        .role())
                .isEqualTo("ADMIN");
    }

    @Test
    void should_fail_startup_when_signing_key_is_shorter_than_256_bits() {
        var shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> service(shortKey, "vsnvi-food", NOW)).isInstanceOf(WeakKeyException.class);
    }

    private static JwtService service(String key, String issuer, Instant now) {
        var properties = new JwtProperties(key, issuer, Duration.ofMinutes(15), Duration.ofDays(30));
        return new JwtService(properties, Clock.fixed(now, ZoneOffset.UTC));
    }
}
