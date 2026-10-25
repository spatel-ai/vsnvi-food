package com.vsnvi.vsnvi_food.user.domain;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;

public class AddressLimitReachedException extends DomainException {

    public AddressLimitReachedException(int max) {
        super(ErrorCode.ADDRESS_LIMIT_REACHED, "You can keep at most " + max + " addresses; delete one first");
    }
}
