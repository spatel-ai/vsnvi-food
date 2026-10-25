package com.vsnvi.vsnvi_food.menu.api.web;

import jakarta.validation.constraints.NotNull;

record SetAvailabilityRequest(@NotNull Boolean available) {}
