package com.vsnvi.vsnvi_food.auth.api.web;

import com.vsnvi.vsnvi_food.auth.application.AuthService;
import java.time.Instant;

record TokenResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {

    static TokenResponse from(AuthService.Tokens tokens) {
        return new TokenResponse(
                "Bearer",
                tokens.accessToken(),
                tokens.accessTokenExpiresAt(),
                tokens.refreshToken(),
                tokens.refreshTokenExpiresAt());
    }
}
