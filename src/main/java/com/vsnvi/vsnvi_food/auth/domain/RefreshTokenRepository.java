package com.vsnvi.vsnvi_food.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    /** Locks the row, so two concurrent refreshes with the same token can't both rotate it. */
    Optional<RefreshToken> findForUpdateByTokenHash(String tokenHash);

    /** @return the number of tokens revoked; already revoked tokens are left alone. */
    int revokeFamily(UUID familyId, Instant now);

    /** @return the number of tokens revoked; already revoked tokens are left alone. */
    int revokeAllOfUser(UUID userId, Instant now);
}
