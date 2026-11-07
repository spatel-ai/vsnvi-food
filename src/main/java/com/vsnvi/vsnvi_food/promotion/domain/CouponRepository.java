package com.vsnvi.vsnvi_food.promotion.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CouponRepository {

    /**
     * Persists a new coupon and flushes, so a duplicate code fails here.
     *
     * @throws CouponCodeTakenException when the code's unique constraint is violated (e.g. a concurrent create)
     */
    Coupon insert(Coupon coupon);

    Optional<Coupon> findById(UUID id);

    /**
     * {@code SELECT ... FOR UPDATE} on the coupon row, held until the caller's transaction ends. The state is reloaded
     * even if the coupon was read earlier in the same transaction.
     */
    Optional<Coupon> findByIdForUpdate(UUID id);

    Optional<Coupon> findByCode(String code);

    /** As {@link #findByIdForUpdate}, by code. */
    Optional<Coupon> findByCodeForUpdate(String code);

    boolean existsByCode(String code);

    Page<Coupon> findAll(Pageable pageable);

    Page<Coupon> findAllByRestaurantId(UUID restaurantId, Pageable pageable);

    /** Active, inside their window at {@code now}, and either platform coupons or the given restaurant's. */
    List<Coupon> findUsableAt(UUID restaurantId, Instant now);

    /** The restaurant's coupons that are switched on and not yet expired at {@code now}, for the per-restaurant cap. */
    long countActiveByRestaurantId(UUID restaurantId, Instant now);
}
