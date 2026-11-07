package com.vsnvi.vsnvi_food.promotion.infrastructure;

import com.vsnvi.vsnvi_food.promotion.domain.Coupon;
import java.util.Optional;
import java.util.UUID;

/** Spring Data fragment behind the {@code CouponRepository} methods that need the {@code EntityManager}. */
interface CouponStore {

    Coupon insert(Coupon coupon);

    Optional<Coupon> findByIdForUpdate(UUID id);

    Optional<Coupon> findByCodeForUpdate(String code);
}
