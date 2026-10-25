package com.vsnvi.vsnvi_food.user.api.web;

import com.vsnvi.vsnvi_food.user.domain.Address;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Used for both create ({@code POST}) and full replace ({@code PUT}). */
record SaveAddressRequest(
        @Size(max = 30) @Schema(example = "Home") String label,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,

        @NotBlank
        @Pattern(regexp = UserValidation.POSTAL_CODE, message = "must be a 6-digit PIN code")
        @Schema(example = "560001")
        String postalCode,

        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @Schema(example = "12.9716")
        Double latitude,

        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @Schema(example = "77.5946")
        Double longitude) {

    Address.Details toDetails() {
        return new Address.Details(label, line1, line2, city, state, postalCode, latitude, longitude);
    }
}
