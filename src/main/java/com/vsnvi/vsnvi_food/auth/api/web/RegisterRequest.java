package com.vsnvi.vsnvi_food.auth.api.web;

import com.vsnvi.vsnvi_food.auth.domain.PasswordPolicy;
import com.vsnvi.vsnvi_food.common.web.validation.MaxUtf8Bytes;
import com.vsnvi.vsnvi_food.user.api.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record RegisterRequest(
        @NotBlank @Email @Size(max = 254) @Schema(example = "asha@example.com")
        String email,
        // BCrypt rejects more than 72 bytes, which multi-byte characters reach before 72 characters.
        @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH) @MaxUtf8Bytes(PasswordPolicy.MAX_BYTES)
        String password,

        @NotBlank @Size(max = 100) String fullName,

        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must be an E.164 number, e.g. +919876543210")
        @Schema(example = "+919876543210")
        String phone,

        @NotNull RegistrableRole role) {

    /** The roles a client may choose. {@code ADMIN} isn't one, so it fails validation. */
    enum RegistrableRole {
        CUSTOMER,
        RESTAURANT_OWNER,
        RIDER;

        Role toRole() {
            return Role.valueOf(name());
        }
    }
}
