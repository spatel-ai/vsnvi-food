package com.vsnvi.vsnvi_food.restaurant.domain;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantStatus;

public class InvalidRestaurantStateException extends DomainException {

    public InvalidRestaurantStateException(RestaurantStatus from, String action) {
        super(ErrorCode.INVALID_RESTAURANT_STATE, "Can't " + action + " a restaurant that is " + from);
    }
}
