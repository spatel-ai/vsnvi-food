package com.vsnvi.vsnvi_food.promotion.domain;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;

/** Codes are unique platform-wide, because customers apply a coupon by its code alone. */
public class CouponCodeTakenException extends DomainException {

    public CouponCodeTakenException() {
        super(ErrorCode.COUPON_CODE_TAKEN, "This coupon code is already in use; choose another");
    }
}
