package com.vsnvi.vsnvi_food.common.ratelimit;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;
import java.time.Duration;

/** 429 {@code RATE_LIMITED}; the error handler adds {@code Retry-After} in whole seconds. */
public class RateLimitedException extends DomainException {

    private final Duration retryAfter;

    public RateLimitedException(Duration retryAfter) {
        super(ErrorCode.RATE_LIMITED, "Too many requests; try again later");
        this.retryAfter = retryAfter;
    }

    public long retryAfterSeconds() {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}
