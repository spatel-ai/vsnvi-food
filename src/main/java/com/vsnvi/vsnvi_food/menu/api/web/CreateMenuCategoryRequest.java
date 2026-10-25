package com.vsnvi.vsnvi_food.menu.api.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

record CreateMenuCategoryRequest(
        @NotBlank @Size(max = 80) @Schema(example = "Biryanis")
        String name,

        @PositiveOrZero @Schema(description = "Display order; defaults to the end")
        Integer position) {}
