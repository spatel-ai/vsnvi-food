package com.vsnvi.vsnvi_food.promotion.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CouponRepository {

    Coupon save(Coupon coupon);

    /** Flushes too, so a duplicate code fails here and not at commit. */
    Coupon saveAndFlush(Coupon coupon);

    Optional<Coupon> findById(UUID id);

    Optional<Coupon> findByCode(String code);

    /** {@code SELECT ... FOR UPDATE} on the coupon row, held until the caller's transaction ends. */
    Optional<Coupon> findByCodeForUpdate(String code);

    boolean existsByCode(String code);

    Page<Coupon> findAll(Pageable pageable);

    Page<Coupon> findAllByRestaurantId(UUID restaurantId, Pageable pageable);

    /** Active, inside their window at {@code now}, and either platform coupons or the given restaurant's. */
    List<Coupon> findUsableAt(UUID restaurantId, Instant now);
}
