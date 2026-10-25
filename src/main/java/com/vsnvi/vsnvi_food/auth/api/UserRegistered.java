package com.vsnvi.vsnvi_food.auth.api;

import com.vsnvi.vsnvi_food.user.api.Role;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when someone registers. {@code delivery} creates a rider profile for {@link Role#RIDER}; {@code
 * notification} sends the welcome email.
 */
public record UserRegistered(UUID userId, String email, Role role, Instant occurredAt) {}
