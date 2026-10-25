package com.vsnvi.vsnvi_food.auth.domain;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;

public class AccountBlockedException extends DomainException {

    public AccountBlockedException() {
        super(ErrorCode.ACCOUNT_BLOCKED, "This account is blocked");
    }
}
