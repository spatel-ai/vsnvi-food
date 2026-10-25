package com.vsnvi.vsnvi_food.user.api;

import java.time.Instant;
import java.util.UUID;

/** Published when an admin blocks a user. {@code auth} revokes the user's refresh tokens. */
public record UserBlocked(UUID userId, String reason, Instant occurredAt) {}
