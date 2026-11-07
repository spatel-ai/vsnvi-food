package com.vsnvi.vsnvi_food.promotion.api.web;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.promotion.api.CouponType;
import com.vsnvi.vsnvi_food.promotion.application.CouponService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A coupon a customer can apply. No limits or usage counts. */
record AvailableCouponResponse(
        String code,

        @Schema(description = "Absent for a platform coupon")
        UUID restaurantId,

        String description,
        CouponType type,
        Money amountOff,
        BigDecimal percentOff,
        Money maxDiscount,
        Money minOrderSubtotal,
        Instant validUntil,

        @Schema(description = "Valid only on the customer's first order; checked at checkout")
        boolean firstOrderOnly) {

    static AvailableCouponResponse from(CouponService.AvailableCouponView c) {
        return new AvailableCouponResponse(
                c.code(),
                c.restaurantId(),
                c.description(),
                c.type(),
                c.amountOff(),
                c.percentOff(),
                c.maxDiscount(),
                c.minOrderSubtotal(),
                c.validUntil(),
                c.firstOrderOnly());
    }
}
