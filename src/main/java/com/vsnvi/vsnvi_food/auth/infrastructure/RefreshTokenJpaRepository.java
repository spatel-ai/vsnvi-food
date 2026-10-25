package com.vsnvi.vsnvi_food.auth.infrastructure;

import com.vsnvi.vsnvi_food.auth.domain.RefreshToken;
import com.vsnvi.vsnvi_food.auth.domain.RefreshTokenRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, UUID>, RefreshTokenRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findForUpdateByTokenHash(String tokenHash);

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now
            where t.familyId = :familyId and t.revokedAt is null
            """)
    int revokeFamily(UUID familyId, Instant now);

    @Override
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now
            where t.userId = :userId and t.revokedAt is null
            """)
    int revokeAllOfUser(UUID userId, Instant now);
}
