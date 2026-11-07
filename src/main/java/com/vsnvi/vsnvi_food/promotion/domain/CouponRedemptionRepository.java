package com.vsnvi.vsnvi_food.promotion.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRedemptionRepository {

    CouponRedemption save(CouponRedemption redemption);

    Optional<CouponRedemption> findByOrderId(UUID orderId);

    long countByCouponIdAndStatus(UUID couponId, RedemptionStatus status);

    long countByCouponIdAndCustomerIdAndStatus(UUID couponId, UUID customerId, RedemptionStatus status);

    /** {@code REDEEMED} counts for several coupons in one query; coupons with no uses are absent. */
    List<CouponUsageRow> countUsage(Collection<UUID> couponIds, UUID customerId);
}
