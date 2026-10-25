package com.vsnvi.vsnvi_food.menu.domain;

import com.vsnvi.vsnvi_food.common.domain.BaseEntity;
import com.vsnvi.vsnvi_food.common.domain.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A dish with its variants and add-ons. Pricing rule (ARCHITECTURE §8): a chosen variant's price replaces the base
 * price, add-ons are added, and an item with variants must be ordered with one. The calculation itself lives in
 * {@code cart}'s {@code CartPricing}; this aggregate only holds the prices.
 */
@Entity
@Table(name = "menu_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuItem extends BaseEntity {

    public static final int MAX_VARIANTS = 10;
    public static final int MAX_ADD_ONS = 20;

    private UUID restaurantId;
    private UUID categoryId;
    private String name;
    private String description;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "price_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "price_currency"))
    })
    private Money price;

    private boolean vegetarian;
    private boolean available;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    @OrderBy("createdAt, id")
    private List<Variant> variants = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    @OrderBy("createdAt, id")
    private List<AddOn> addOns = new ArrayList<>();

    public static MenuItem create(
            UUID restaurantId,
            UUID categoryId,
            String name,
            String description,
            Money price,
            boolean vegetarian,
            Collection<Variant> variants,
            Collection<AddOn> addOns) {
        var item = new MenuItem();
        item.restaurantId = Objects.requireNonNull(restaurantId, "restaurantId");
        item.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        item.name = requireName(name);
        item.description = description;
        item.price = requireNonNegative(price);
        item.vegetarian = vegetarian;
        item.available = true;
        item.replaceVariants(variants);
        item.replaceAddOns(addOns);
        return item;
    }

    public void rename(String name, String description) {
        if (name != null) {
            this.name = requireName(name);
        }
        if (description != null) {
            this.description = description;
        }
    }

    public void reprice(Money price) {
        this.price = requireNonNegative(price);
    }

    public void markVegetarian(boolean vegetarian) {
        this.vegetarian = vegetarian;
    }

    public void moveToCategory(UUID categoryId) {
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
    }

    /** Owner's in-stock switch. Idempotent. */
    public void setAvailability(boolean available) {
        this.available = available;
    }

    public void replaceVariants(Collection<Variant> replacement) {
        if (replacement.size() > MAX_VARIANTS) {
            throw new IllegalArgumentException("at most " + MAX_VARIANTS + " variants");
        }
        variants.clear();
        variants.addAll(replacement);
    }

    public void replaceAddOns(Collection<AddOn> replacement) {
        if (replacement.size() > MAX_ADD_ONS) {
            throw new IllegalArgumentException("at most " + MAX_ADD_ONS + " add-ons");
        }
        addOns.clear();
        addOns.addAll(replacement);
    }

    static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name.strip();
    }

    static Money requireNonNegative(Money price) {
        if (Objects.requireNonNull(price, "price").isNegative()) {
            throw new IllegalArgumentException("price must not be negative");
        }
        return price;
    }
}
