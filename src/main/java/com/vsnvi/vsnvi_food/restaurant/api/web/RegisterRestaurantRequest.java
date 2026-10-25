package com.vsnvi.vsnvi_food.restaurant.api.web;

import com.vsnvi.vsnvi_food.common.web.MoneyInput;
import com.vsnvi.vsnvi_food.restaurant.domain.Restaurant;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

record RegisterRestaurantRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotEmpty @Size(max = 10) List<@NotBlank @Pattern(regexp = RestaurantValidation.CUISINE) String> cuisines,
        @NotBlank @Size(max = 300) String addressLine,
        @NotBlank @Size(max = 100) String city,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,

        @NotNull @DecimalMin("0.5") @DecimalMax("30.0") @Schema(example = "6.0")
        Double serviceableRadiusKm,

        @Size(max = 40) @Schema(description = "IANA zone; defaults to Asia/Kolkata", example = "Asia/Kolkata")
        String timeZone,

        @NotNull @Valid MoneyInput deliveryFee,
        @NotNull @Valid MoneyInput packagingCharge) {

    Restaurant.Profile toProfile() {
        return new Restaurant.Profile(
                name,
                description,
                cuisines,
                addressLine,
                city,
                latitude,
                longitude,
                serviceableRadiusKm,
                timeZone == null ? RestaurantValidation.DEFAULT_TIME_ZONE : timeZone);
    }
}
