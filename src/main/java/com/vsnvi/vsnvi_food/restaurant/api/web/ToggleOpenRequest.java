package com.vsnvi.vsnvi_food.restaurant.api.web;

import jakarta.validation.constraints.NotNull;

/** {@code open=false} closes the restaurant for orders until switched back on. */
record ToggleOpenRequest(@NotNull Boolean open) {}
