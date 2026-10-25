package com.vsnvi.vsnvi_food.menu.domain;

import com.vsnvi.vsnvi_food.common.domain.BaseEntity;
import com.vsnvi.vsnvi_food.common.domain.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A size or portion, e.g. Half or Full. Its price replaces the item's base price. */
@Entity
@Table(name = "item_variants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Variant extends BaseEntity {

    private String name;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "price_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "price_currency"))
    })
    private Money price;

    public static Variant of(String name, Money price) {
        var variant = new Variant();
        variant.name = MenuItem.requireName(name);
        variant.price = MenuItem.requireNonNegative(price);
        return variant;
    }
}
