package com.vsnvi.vsnvi_food.user.api.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update: omitted fields stay unchanged. */
record UpdateProfileRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String fullName,

        @Pattern(regexp = UserValidation.PHONE, message = "must be an E.164 number, e.g. +919876543210")
        @Schema(example = "+919876543210")
        String phone) {}
