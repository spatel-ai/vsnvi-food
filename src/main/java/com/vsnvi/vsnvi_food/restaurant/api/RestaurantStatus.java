package com.vsnvi.vsnvi_food.restaurant.api;

/** Only {@code APPROVED} restaurants appear in search and can receive orders. */
public enum RestaurantStatus {
    PENDING_APPROVAL,
    APPROVED,
    SUSPENDED
}
