package com.vsnvi.vsnvi_food.promotion.api;

import com.vsnvi.vsnvi_food.common.domain.Money;
import java.util.UUID;

/**
 * The discount a coupon gives. {@code itemDiscount} comes off the item subtotal (it lowers the taxable amount);
 * {@code deliveryDiscount} waives the delivery fee. Exactly one of them is non-zero.
 */
public record CouponDiscount(
        UUID couponId,
        String code,
        CouponType type,
        Money itemDiscount,
        Money deliveryDiscount,
        boolean firstOrderOnly) {

    public Money total() {
        return itemDiscount.plus(deliveryDiscount);
    }
}
