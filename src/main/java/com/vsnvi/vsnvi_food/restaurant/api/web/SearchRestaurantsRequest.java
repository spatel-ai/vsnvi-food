package com.vsnvi.vsnvi_food.restaurant.api.web;

import com.vsnvi.vsnvi_food.restaurant.domain.RestaurantSearch;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Query parameters of {@code GET /restaurants}. All optional; {@code lat} and {@code lng} go together. */
record SearchRestaurantsRequest(
        @DecimalMin("-90.0") @DecimalMax("90.0") @Schema(example = "12.9716")
        Double lat,

        @DecimalMin("-180.0") @DecimalMax("180.0") @Schema(example = "77.5946")
        Double lng,

        @Size(max = RestaurantValidation.CUISINE_MAX_LENGTH)
        @Pattern(regexp = RestaurantValidation.CUISINE)
        @Schema(example = "north-indian")
        String cuisine,

        @DecimalMin("0.0") @DecimalMax("5.0") BigDecimal minRating,
        Boolean isOpen,
        @Size(min = 1, max = 100) String q) {

    @AssertTrue(message = "lat and lng must be given together")
    @Schema(hidden = true)
    boolean isLocationComplete() {
        return (lat == null) == (lng == null);
    }

    RestaurantSearch.Criteria toCriteria() {
        return new RestaurantSearch.Criteria(lat, lng, cuisine, minRating, isOpen, q == null ? null : q.strip());
    }
}
