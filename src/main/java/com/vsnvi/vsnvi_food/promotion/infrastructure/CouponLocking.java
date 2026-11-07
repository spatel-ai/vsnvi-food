package com.vsnvi.vsnvi_food.promotion.infrastructure;

import com.vsnvi.vsnvi_food.promotion.domain.Coupon;
import java.util.Optional;

/** Spring Data fragment behind {@code CouponRepository.findByCodeForUpdate}. */
interface CouponLocking {

    Optional<Coupon> findByCodeForUpdate(String code);
}
