package com.vsnvi.vsnvi_food.auth.domain;

import java.time.Instant;
import java.util.UUID;

/** Issues short-lived access tokens. Implemented with jjwt in {@code infrastructure}. */
public interface AccessTokenIssuer {

    IssuedAccessToken issue(UUID userId, String role);

    record IssuedAccessToken(String token, Instant expiresAt) {}
}
