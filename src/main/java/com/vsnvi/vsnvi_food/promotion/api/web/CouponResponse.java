package com.vsnvi.vsnvi_food.promotion.api.web;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.promotion.api.CouponType;
import com.vsnvi.vsnvi_food.promotion.application.CouponService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A coupon as its owner or an admin sees it. */
record CouponResponse(
        UUID id,
        String code,

        @Schema(description = "Absent for a platform coupon")
        UUID restaurantId,

        String description,
        CouponType type,
        Money amountOff,
        BigDecimal percentOff,
        Money maxDiscount,
        Money minOrderSubtotal,
        Instant validFrom,
        Instant validUntil,
        boolean active,
        @Schema(description = "Absent means unlimited") Integer perCustomerLimit,
        @Schema(description = "Absent means unlimited") Integer totalLimit,
        boolean firstOrderOnly,
        Instant createdAt) {

    static CouponResponse from(CouponService.CouponView c) {
        return new CouponResponse(
                c.id(),
                c.code(),
                c.restaurantId(),
                c.description(),
                c.type(),
                c.amountOff(),
                c.percentOff(),
                c.maxDiscount(),
                c.minOrderSubtotal(),
                c.validFrom(),
                c.validUntil(),
                c.active(),
                c.perCustomerLimit(),
                c.totalLimit(),
                c.firstOrderOnly(),
                c.createdAt());
    }
}
