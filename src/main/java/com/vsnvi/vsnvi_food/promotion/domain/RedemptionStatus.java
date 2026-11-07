package com.vsnvi.vsnvi_food.promotion.domain;

/** Only {@code REDEEMED} redemptions count towards a coupon's limits. */
public enum RedemptionStatus {
    REDEEMED,
    RELEASED
}
