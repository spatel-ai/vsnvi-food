package com.vsnvi.vsnvi_food.restaurant.api.web;

final class RestaurantValidation {

    /** Lower-case kebab-case, e.g. {@code north-indian}. */
    static final String CUISINE = "^[a-z]+(-[a-z]+)*$";

    /** Keeps each element far below Postgres's GIN index entry limit; V7 enforces the same bound. */
    static final int CUISINE_MAX_LENGTH = 40;

    static final String DEFAULT_TIME_ZONE = "Asia/Kolkata";

    private RestaurantValidation() {}
}
