package com.vsnvi.vsnvi_food.common.web;

import com.vsnvi.vsnvi_food.common.domain.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * A money amount in a request body, bounds-checked (docs/SECURITY.md §6): 0 to 99,999.99, at most two decimals, and
 * INR only for now.
 */
@Schema(description = "Exact amount with currency")
public record MoneyInput(
        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("99999.99")
        @Digits(integer = 5, fraction = 2)
        @Schema(type = "string", example = "40.00")
        BigDecimal amount,

        @NotNull @Pattern(regexp = Money.INR, message = "only INR is supported") @Schema(example = "INR")
        String currency) {

    public Money toMoney() {
        return Money.of(amount, currency);
    }
}
