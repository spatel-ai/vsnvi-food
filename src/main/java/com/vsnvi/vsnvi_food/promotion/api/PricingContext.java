package com.vsnvi.vsnvi_food.promotion.api;

import com.vsnvi.vsnvi_food.common.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * What a coupon is evaluated against, as priced by {@code CartPricing}: the cart's restaurant, its item subtotal and
 * the restaurant's delivery fee.
 */
public record PricingContext(UUID restaurantId, Money itemSubtotal, Money deliveryFee) {

    public PricingContext {
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(itemSubtotal, "itemSubtotal");
        Objects.requireNonNull(deliveryFee, "deliveryFee");
    }
}
