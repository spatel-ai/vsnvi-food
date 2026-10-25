package com.vsnvi.vsnvi_food.common.domain;

import java.net.URI;
import java.util.Locale;

/** The error code catalog in {@code docs/API-DESIGN.md} §4. Keep both in sync. */
public enum ErrorCode {
    VALIDATION_FAILED(400, "Validation failed"),
    MALFORMED_REQUEST(400, "Malformed request"),
    INVALID_WEBHOOK_SIGNATURE(400, "Invalid webhook signature"),
    UNAUTHORIZED(401, "Unauthorized"),
    TOKEN_EXPIRED(401, "Token expired"),
    INVALID_CREDENTIALS(401, "Invalid credentials"),
    FORBIDDEN(403, "Forbidden"),
    ACCOUNT_BLOCKED(403, "Account blocked"),
    RESOURCE_NOT_FOUND(404, "Resource not found"),
    METHOD_NOT_ALLOWED(405, "Method not allowed"),
    NOT_ACCEPTABLE(406, "Not acceptable"),
    EMAIL_ALREADY_REGISTERED(409, "Email already registered"),
    INVALID_ORDER_STATE(409, "Invalid order state"),
    INVALID_RESTAURANT_STATE(409, "Invalid restaurant state"),
    CONCURRENT_MODIFICATION(409, "Concurrent modification"),
    IDEMPOTENCY_KEY_CONFLICT(409, "Idempotency key conflict"),
    PAYLOAD_TOO_LARGE(413, "Payload too large"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),
    CANNOT_BLOCK_ADMIN(422, "Cannot block an admin"),
    ADDRESS_LIMIT_REACHED(422, "Address limit reached"),
    CART_RESTAURANT_MISMATCH(422, "Cart restaurant mismatch"),
    COUPON_INVALID(422, "Coupon invalid"),
    COUPON_NOT_APPLICABLE(422, "Coupon not applicable"),
    COUPON_LIMIT_REACHED(422, "Coupon limit reached"),
    PRICE_CHANGED(422, "Price changed"),
    ITEM_UNAVAILABLE(422, "Item unavailable"),
    RESTAURANT_CLOSED(422, "Restaurant closed"),
    OUT_OF_DELIVERY_AREA(422, "Out of delivery area"),
    PAYMENT_FAILED(422, "Payment failed"),
    INVALID_DELIVERY_OTP(422, "Invalid delivery OTP"),
    PARTNER_NOT_APPROVED(422, "Partner not approved"),
    RATE_LIMITED(429, "Too many requests"),
    INTERNAL_ERROR(500, "Internal error");

    private static final String TYPE_BASE = "https://api.vsnvi.com/errors/";

    private final int status;
    private final String title;

    ErrorCode(int status, String title) {
        this.status = status;
        this.title = title;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    public URI type() {
        return URI.create(TYPE_BASE + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
