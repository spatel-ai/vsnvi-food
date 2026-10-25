package com.vsnvi.vsnvi_food.restaurant.api;

import java.util.Optional;
import java.util.UUID;

/** The {@code restaurant} module's facade for modules above it in the graph. */
public interface RestaurantApi {

    /** Any status; callers decide what a pending or suspended restaurant means for them. */
    Optional<RestaurantSummary> findById(UUID restaurantId);

    /** Ownership check for owner-only actions in other modules (menu, coupons, orders). */
    boolean isOwnedBy(UUID restaurantId, UUID userId);
}
