package com.vsnvi.vsnvi_food.auth.domain;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;

/** Unknown, expired, revoked or reused refresh token. The client must log in again. */
public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super(ErrorCode.UNAUTHORIZED, "Invalid refresh token; log in again");
    }
}
