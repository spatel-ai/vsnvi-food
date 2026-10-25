package com.vsnvi.vsnvi_food.restaurant.api.web;

final class RestaurantValidation {

    /** Lower-case kebab-case, e.g. {@code north-indian}. */
    static final String CUISINE = "^[a-z]+(-[a-z]+)*$";

    static final String DEFAULT_TIME_ZONE = "Asia/Kolkata";

    private RestaurantValidation() {}
}
