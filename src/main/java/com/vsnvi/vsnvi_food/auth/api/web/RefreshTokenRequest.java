package com.vsnvi.vsnvi_food.auth.api.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RefreshTokenRequest(@NotBlank @Size(max = 100) String refreshToken) {}
