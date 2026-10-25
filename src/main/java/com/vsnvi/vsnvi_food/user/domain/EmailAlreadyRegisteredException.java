package com.vsnvi.vsnvi_food.user.domain;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;

public class EmailAlreadyRegisteredException extends DomainException {

    public EmailAlreadyRegisteredException() {
        // No email in the message: it would reach the response body and the logs.
        super(ErrorCode.EMAIL_ALREADY_REGISTERED, "This email is already registered");
    }
}
