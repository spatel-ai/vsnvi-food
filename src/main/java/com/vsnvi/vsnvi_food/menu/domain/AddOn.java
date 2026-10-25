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

/** An optional extra, e.g. Extra cheese. Its price is added to the unit price. */
@Entity
@Table(name = "item_add_ons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AddOn extends BaseEntity {

    private String name;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "price_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "price_currency"))
    })
    private Money price;

    public static AddOn of(String name, Money price) {
        var addOn = new AddOn();
        addOn.name = MenuItem.requireName(name);
        addOn.price = MenuItem.requireNonNegative(price);
        return addOn;
    }
}
