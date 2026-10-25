package com.vsnvi.vsnvi_food.common.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler that requires the {@code Idempotency-Key} header. The OpenAPI customizer documents it as a required
 * UUID header parameter; the handler reads it with {@code @RequestHeader(IdempotencyService.HEADER)} and passes it to
 * {@link IdempotencyService}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdempotencyKeyHeader {}
