package com.vsnvi.vsnvi_food.restaurant.api.web;

import com.vsnvi.vsnvi_food.common.web.MoneyInput;
import com.vsnvi.vsnvi_food.restaurant.domain.Restaurant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Partial update: omitted fields stay unchanged. */
record UpdateRestaurantRequest(
        @Size(min = 1, max = 120) @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String name,

        @Size(max = 1000) String description,
        @Size(min = 1, max = 10) List<@NotBlank @Pattern(regexp = RestaurantValidation.CUISINE) String> cuisines,
        @Size(min = 1, max = 300) String addressLine,
        @Size(min = 1, max = 100) String city,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @DecimalMin("0.5") @DecimalMax("30.0") Double serviceableRadiusKm,
        @Size(min = 1, max = 40) String timeZone,
        @Valid MoneyInput deliveryFee,
        @Valid MoneyInput packagingCharge) {

    Restaurant.ProfilePatch toPatch() {
        return new Restaurant.ProfilePatch(
                name, description, cuisines, addressLine, city, latitude, longitude, serviceableRadiusKm, timeZone);
    }
}
