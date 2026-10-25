package com.vsnvi.vsnvi_food.common.domain;

/**
 * Base class for business-rule failures. The message becomes the ProblemDetail {@code detail}, so it must not contain
 * secrets or personal data.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    protected DomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
