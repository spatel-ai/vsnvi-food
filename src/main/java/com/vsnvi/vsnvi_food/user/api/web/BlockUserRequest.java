package com.vsnvi.vsnvi_food.user.api.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record BlockUserRequest(@NotBlank @Size(max = 500) String reason) {}
