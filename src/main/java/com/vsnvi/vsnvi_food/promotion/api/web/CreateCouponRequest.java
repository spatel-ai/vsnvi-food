package com.vsnvi.vsnvi_food.promotion.api.web;

import com.vsnvi.vsnvi_food.common.web.MoneyInput;
import com.vsnvi.vsnvi_food.promotion.api.CouponType;
import com.vsnvi.vsnvi_food.promotion.application.CouponService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A new coupon. Set {@code amountOff} for FLAT, {@code percentOff} and {@code maxDiscount} for PERCENT, and none of
 * them for FREE_DELIVERY. Omitted limits mean unlimited.
 */
record CreateCouponRequest(
        @NotNull
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]{2,29}$", message = "must be 3-30 letters, digits or hyphens")
        @Schema(description = "Stored upper-case; customers may type it in any case", example = "WELCOME50")
        String code,

        @NotNull CouponType type,

        @Size(max = 200)
        @Pattern(regexp = "[^\\p{Cc}\\p{Cf}]*", message = "must not contain control or invisible formatting characters")
        @Schema(example = "50% off up to 100 on your first order")
        String description,

        @Valid @Schema(description = "FLAT only") MoneyInput amountOff,

        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("100")
        @Digits(integer = 3, fraction = 2)
        @Schema(description = "PERCENT only", example = "50")
        BigDecimal percentOff,

        @Valid @Schema(description = "PERCENT only: the cap on the discount")
        MoneyInput maxDiscount,

        @Valid @Schema(description = "Minimum item subtotal; defaults to 0")
        MoneyInput minOrderSubtotal,

        @NotNull Instant validFrom,
        @NotNull @Schema(description = "Exclusive") Instant validUntil,

        @Min(1) @Max(1000) Integer perCustomerLimit,
        @Min(1) @Max(1_000_000) Integer totalLimit,
        Boolean firstOrderOnly) {

    CouponService.NewCoupon toDraft() {
        return new CouponService.NewCoupon(
                code,
                type,
                description,
                amountOff == null ? null : amountOff.toMoney(),
                percentOff,
                maxDiscount == null ? null : maxDiscount.toMoney(),
                minOrderSubtotal == null ? null : minOrderSubtotal.toMoney(),
                validFrom,
                validUntil,
                perCustomerLimit,
                totalLimit,
                Boolean.TRUE.equals(firstOrderOnly));
    }
}
