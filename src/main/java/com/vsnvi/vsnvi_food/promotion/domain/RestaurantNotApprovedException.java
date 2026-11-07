package com.vsnvi.vsnvi_food.promotion.domain;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;

/**
 * Only an approved restaurant may create coupons (SECURITY §2: unapproved restaurants can't act). Otherwise an owner
 * could reserve platform-wide codes before approval.
 */
public class RestaurantNotApprovedException extends DomainException {

    public RestaurantNotApprovedException() {
        super(ErrorCode.INVALID_RESTAURANT_STATE, "The restaurant must be approved before it can create coupons");
    }
}
