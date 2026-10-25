package com.vsnvi.vsnvi_food.menu.api;

import com.vsnvi.vsnvi_food.common.domain.Money;
import java.util.List;
import java.util.UUID;

/**
 * Current price data of one menu item, for {@code cart} and {@code order}. Pricing rule (ARCHITECTURE §8): a chosen
 * variant's price replaces {@code basePrice}, add-on prices are added, and an item with variants needs one chosen.
 * {@code CartPricing} applies the rule; this record only carries the data.
 */
public record PricedItem(
        UUID itemId,
        UUID restaurantId,
        String name,
        Money basePrice,
        boolean available,
        List<Option> variants,
        List<Option> addOns) {

    public boolean requiresVariant() {
        return !variants.isEmpty();
    }

    public record Option(UUID id, String name, Money price) {}
}
