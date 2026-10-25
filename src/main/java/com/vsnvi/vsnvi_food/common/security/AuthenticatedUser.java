package com.vsnvi.vsnvi_food.common.security;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The principal put into the {@code SecurityContext} for a valid access token. {@code role} is the role name without the
 * {@code ROLE_} prefix, e.g. {@code CUSTOMER}. {@code expiresAt} is the token's {@code exp}, used to close STOMP
 * sessions when the token expires.
 */
public record AuthenticatedUser(UUID id, String role, Instant expiresAt) {

    public AuthenticatedUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
