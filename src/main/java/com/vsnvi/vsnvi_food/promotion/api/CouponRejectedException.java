package com.vsnvi.vsnvi_food.promotion.api;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;

/**
 * A coupon can't be used. Public so {@code cart} can catch it and show the reason without failing the whole cart read.
 * The message becomes the ProblemDetail {@code detail}.
 */
public class CouponRejectedException extends DomainException {

    private CouponRejectedException(ErrorCode code, String message) {
        super(code, message);
    }

    /** Unknown, inactive, not yet valid or expired. */
    public static CouponRejectedException invalid(String message) {
        return new CouponRejectedException(ErrorCode.COUPON_INVALID, message);
    }

    /** Minimum subtotal, restaurant, first-order or nothing-to-waive rule not met. */
    public static CouponRejectedException notApplicable(String message) {
        return new CouponRejectedException(ErrorCode.COUPON_NOT_APPLICABLE, message);
    }

    /** Per-customer or total usage limit reached. */
    public static CouponRejectedException limitReached(String message) {
        return new CouponRejectedException(ErrorCode.COUPON_LIMIT_REACHED, message);
    }
}
