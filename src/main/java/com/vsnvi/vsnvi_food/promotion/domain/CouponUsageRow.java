package com.vsnvi.vsnvi_food.promotion.domain;

import java.util.UUID;

/** {@code REDEEMED} counts of one coupon: overall, and by the customer the query was made for. */
public record CouponUsageRow(UUID couponId, Long totalUses, Long customerUses) {

    public Coupon.Usage toUsage() {
        return new Coupon.Usage(totalUses, customerUses);
    }
}
