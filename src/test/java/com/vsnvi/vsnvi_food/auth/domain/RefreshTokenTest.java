package com.vsnvi.vsnvi_food.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @Test
    void should_not_be_fresh_when_used() {
        var token = newToken();

        token.markUsed(NOW);

        assertThat(token.isFresh()).isFalse();
        assertThatThrownBy(() -> token.markUsed(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_keep_first_revocation_time_when_revoked_twice() {
        var token = newToken();

        token.revoke(NOW);
        token.revoke(NOW.plusSeconds(60));

        assertThat(token.getRevokedAt()).isEqualTo(NOW);
        assertThat(token.isFresh()).isFalse();
    }

    @Test
    void should_be_expired_from_expiry_instant_onwards() {
        var token = newToken();

        assertThat(token.isExpired(NOW.plusSeconds(59))).isFalse();
        assertThat(token.isExpired(NOW.plusSeconds(60))).isTrue();
    }

    @Test
    void should_store_hash_not_raw_value_when_hashing() {
        var raw = OpaqueTokens.generate();

        assertThat(OpaqueTokens.sha256Hex(raw)).hasSize(64).isNotEqualTo(raw);
        assertThat(OpaqueTokens.generate()).isNotEqualTo(raw);
    }

    private static RefreshToken newToken() {
        return RefreshToken.issue(UUID.randomUUID(), "a".repeat(64), UUID.randomUUID(), NOW.plusSeconds(60));
    }
}
