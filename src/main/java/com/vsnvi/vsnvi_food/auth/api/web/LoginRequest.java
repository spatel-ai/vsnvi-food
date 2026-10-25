package com.vsnvi.vsnvi_food.auth.api.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record LoginRequest(
        @NotBlank @Size(max = 254) String email,
        // Over-long passwords get INVALID_CREDENTIALS from the service, like any wrong password.
        @NotBlank @Size(max = 500) String password) {}
