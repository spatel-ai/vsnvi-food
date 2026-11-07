package com.vsnvi.vsnvi_food.promotion.api.web;

import com.vsnvi.vsnvi_food.common.web.MoneyInput;
import com.vsnvi.vsnvi_food.promotion.application.CouponService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Partial update: null fields stay unchanged. The code and type can't change, and limits can't be removed. */
record UpdateCouponRequest(
        @Size(max = 200)
        @Pattern(regexp = "[^\\p{Cc}\\p{Cf}]*", message = "must not contain control or invisible formatting characters")
        String description,

        @Valid MoneyInput amountOff,

        @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal percentOff,

        @Valid MoneyInput maxDiscount,
        @Valid MoneyInput minOrderSubtotal,
        Instant validFrom,
        Instant validUntil,
        @Min(1) @Max(1000) Integer perCustomerLimit,
        @Min(1) @Max(1_000_000) Integer totalLimit,
        Boolean firstOrderOnly) {

    CouponService.CouponPatch toPatch() {
        return new CouponService.CouponPatch(
                description,
                amountOff == null ? null : amountOff.toMoney(),
                percentOff,
                maxDiscount == null ? null : maxDiscount.toMoney(),
                minOrderSubtotal == null ? null : minOrderSubtotal.toMoney(),
                validFrom,
                validUntil,
                perCustomerLimit,
                totalLimit,
                firstOrderOnly);
    }
}
