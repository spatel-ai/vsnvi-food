package com.vsnvi.vsnvi_food.promotion.domain;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;

/** A restaurant already has {@link Coupon#MAX_ACTIVE_PER_RESTAURANT} active coupons. */
public class CouponQuotaReachedException extends DomainException {

    public CouponQuotaReachedException() {
        super(
                ErrorCode.COUPON_QUOTA_REACHED,
                "A restaurant can have at most " + Coupon.MAX_ACTIVE_PER_RESTAURANT
                        + " active coupons; deactivate one first");
    }
}
