package com.vsnvi.vsnvi_food.restaurant.api;

import com.vsnvi.vsnvi_food.common.domain.Money;
import java.util.UUID;

/**
 * Facade view of a restaurant for pricing (cart, order), ownership (menu, promotion) and assignment (delivery).
 * {@code openNow} is evaluated at the time of the call.
 */
public record RestaurantSummary(
        UUID id,
        UUID ownerId,
        String name,
        RestaurantStatus status,
        double latitude,
        double longitude,
        double serviceableRadiusKm,
        Money deliveryFee,
        Money packagingCharge,
        boolean openNow) {

    public boolean canReceiveOrders() {
        return status == RestaurantStatus.APPROVED && openNow;
    }
}
