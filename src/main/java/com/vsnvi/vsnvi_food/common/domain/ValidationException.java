package com.vsnvi.vsnvi_food.common.domain;

/** Request input that Bean Validation can't express, e.g. an undocumented sort field. Returns 400. */
public class ValidationException extends DomainException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
