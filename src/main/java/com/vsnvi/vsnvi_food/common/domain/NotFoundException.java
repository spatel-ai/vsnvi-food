package com.vsnvi.vsnvi_food.common.domain;

/** Not found, or owned by someone else: both return 404 so the API doesn't reveal that the resource exists. */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
