package com.vsnvi.vsnvi_food.common.security;

import com.vsnvi.vsnvi_food.common.domain.ErrorCode;
import org.springframework.security.core.AuthenticationException;

/** A rejected access token. {@link #expired()} selects {@code TOKEN_EXPIRED} over {@code UNAUTHORIZED}. */
public class InvalidTokenException extends AuthenticationException {

    private final boolean expired;

    private InvalidTokenException(String message, boolean expired) {
        super(message);
        this.expired = expired;
    }

    public static InvalidTokenException invalid() {
        return new InvalidTokenException("Invalid access token", false);
    }

    public static InvalidTokenException expired() {
        return new InvalidTokenException("Access token expired", true);
    }

    public boolean isExpired() {
        return expired;
    }

    public ErrorCode errorCode() {
        return expired ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHORIZED;
    }
}
