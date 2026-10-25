package com.vsnvi.vsnvi_food.restaurant.api.web;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.restaurant.application.RestaurantService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/** A search result. {@code distanceKm} is present only when the search had a location. */
record RestaurantCardResponse(
        UUID id,
        String name,
        List<String> cuisines,
        String city,
        BigDecimal rating,
        BigDecimal distanceKm,
        boolean openNow,
        Money deliveryFee) {

    static RestaurantCardResponse from(RestaurantService.SearchResult result) {
        var r = result.restaurant();
        return new RestaurantCardResponse(
                r.getId(),
                r.getName(),
                r.getCuisines(),
                r.getCity(),
                r.getRating(),
                result.distanceKm() == null
                        ? null
                        : BigDecimal.valueOf(result.distanceKm()).setScale(1, RoundingMode.HALF_UP),
                result.openNow(),
                r.getDeliveryFee());
    }
}
