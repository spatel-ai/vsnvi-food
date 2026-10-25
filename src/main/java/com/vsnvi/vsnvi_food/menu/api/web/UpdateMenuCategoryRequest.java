package com.vsnvi.vsnvi_food.menu.api.web;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Partial update: omitted fields stay unchanged. */
record UpdateMenuCategoryRequest(
        @Size(min = 1, max = 80) @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String name,

        @PositiveOrZero Integer position) {}
