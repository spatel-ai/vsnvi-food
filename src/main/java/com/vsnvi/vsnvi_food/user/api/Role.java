package com.vsnvi.vsnvi_food.user.api;

/** A user has exactly one role (docs/SECURITY.md §2). */
public enum Role {
    CUSTOMER,
    RESTAURANT_OWNER,
    RIDER,
    ADMIN
}
