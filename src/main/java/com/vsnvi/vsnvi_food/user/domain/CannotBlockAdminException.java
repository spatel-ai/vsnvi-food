package com.vsnvi.vsnvi_food.user.domain;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;

public class CannotBlockAdminException extends DomainException {

    public CannotBlockAdminException() {
        super(ErrorCode.CANNOT_BLOCK_ADMIN, "Admins can't be blocked");
    }
}
