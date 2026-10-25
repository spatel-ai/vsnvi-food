package com.vsnvi.vsnvi_food.auth.domain;

import com.vsnvi.vsnvi_food.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One issued refresh token, stored only as its SHA-256 hash. Each use rotates it: this token is marked used and a new
 * one is issued in the same family. Presenting a used or revoked token is reuse, which revokes the whole family.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    private UUID userId;
    private String tokenHash;
    private UUID familyId;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant revokedAt;

    public static RefreshToken issue(UUID userId, String tokenHash, UUID familyId, Instant expiresAt) {
        var token = new RefreshToken();
        token.userId = Objects.requireNonNull(userId, "userId");
        token.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        token.familyId = Objects.requireNonNull(familyId, "familyId");
        token.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        return token;
    }

    /** Neither used nor revoked: presenting it again would be reuse. */
    public boolean isFresh() {
        return usedAt == null && revokedAt == null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        if (!isFresh()) {
            throw new IllegalStateException("Refresh token already used or revoked");
        }
        usedAt = now;
    }

    /** Idempotent. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
