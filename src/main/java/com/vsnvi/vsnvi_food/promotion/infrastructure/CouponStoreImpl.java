package com.vsnvi.vsnvi_food.promotion.infrastructure;

import com.vsnvi.vsnvi_food.promotion.domain.Coupon;
import com.vsnvi.vsnvi_food.promotion.domain.CouponCodeTakenException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;

/**
 * Locks coupon rows with {@code refresh(..., PESSIMISTIC_WRITE)} instead of a locking query. A locking query returns the
 * instance already in the persistence context unchanged, so a coupon read earlier in the same transaction (e.g. by
 * {@code evaluate}) would keep its stale state; {@code refresh} issues {@code SELECT ... FOR UPDATE} and reloads it.
 */
class CouponStoreImpl implements CouponStore {

    /** The unique constraint on {@code coupons.code} in V8. */
    static final String UNIQUE_CODE_CONSTRAINT = "uq_coupons_code";

    private final EntityManager entityManager;

    CouponStoreImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Coupon insert(Coupon coupon) {
        try {
            entityManager.persist(coupon);
            entityManager.flush();
            return coupon;
        } catch (PersistenceException ex) {
            if (violates(ex, UNIQUE_CODE_CONSTRAINT)) {
                throw new CouponCodeTakenException();
            }
            throw ex; // any other violation is a bug and stays a 500
        }
    }

    /** Hibernate may wrap its {@link ConstraintViolationException}, so look through the cause chain. */
    private static boolean violates(Throwable ex, String constraint) {
        for (var cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<Coupon> findByIdForUpdate(UUID id) {
        var coupon = Optional.ofNullable(entityManager.find(Coupon.class, id));
        coupon.ifPresent(c -> entityManager.refresh(c, LockModeType.PESSIMISTIC_WRITE));
        return coupon;
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
