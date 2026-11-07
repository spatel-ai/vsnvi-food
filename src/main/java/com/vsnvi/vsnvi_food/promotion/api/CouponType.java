package com.vsnvi.vsnvi_food.promotion.api;

/** Discount types (ARCHITECTURE §8). */
public enum CouponType {
    /** A fixed amount off the item subtotal, never more than the subtotal. */
    FLAT,
    /** A percentage of the item subtotal, capped by {@code maxDiscount} and by the subtotal. */
    PERCENT,
    /** Waives the delivery fee. */
    FREE_DELIVERY
}
