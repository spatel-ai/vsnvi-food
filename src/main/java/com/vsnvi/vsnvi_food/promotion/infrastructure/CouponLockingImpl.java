package com.vsnvi.vsnvi_food.promotion.infrastructure;

import com.vsnvi.vsnvi_food.promotion.domain.Coupon;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * Locks the coupon row with {@code refresh(..., PESSIMISTIC_WRITE)} instead of a locking query. A locking query returns
 * the instance already in the persistence context unchanged, so a coupon read earlier in the same transaction (e.g. by
 * {@code evaluate}) would keep its stale state; {@code refresh} issues {@code SELECT ... FOR UPDATE} and reloads it.
 */
class CouponLockingImpl implements CouponLocking {

    private final EntityManager entityManager;

    CouponLockingImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Coupon> findByCodeForUpdate(String code) {
        var coupon = entityManager
                .createQuery("select c from Coupon c where c.code = :code", Coupon.class)
                .setParameter("code", code)
                .getResultStream()
                .findFirst();
        coupon.ifPresent(c -> entityManager.refresh(c, LockModeType.PESSIMISTIC_WRITE));
        return coupon;
    }
}
