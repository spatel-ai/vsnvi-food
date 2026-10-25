package com.vsnvi.vsnvi_food.common.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An exact amount with its ISO 4217 currency, always at scale 2 and rounded half-up. Stored as {@code NUMERIC(12,2)}
 * plus {@code CHAR(3)}; map it with {@code @AttributeOverrides} to {@code <name>_amount} and {@code <name>_currency}.
 * Serialized as {@code {"amount": "249.00", "currency": "INR"}}.
 */
@Embeddable
@JsonAutoDetect(isGetterVisibility = JsonAutoDetect.Visibility.NONE) // only amount and currency
@Schema(description = "Exact amount with currency")
public record Money(
        @Column(nullable = false, precision = 12, scale = 2)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(type = "string", pattern = "^-?\\d+\\.\\d{2}$", example = "249.00")
        BigDecimal amount,

        @Column(nullable = false, length = 3) @Schema(example = "INR")
        String currency) {

    public static final String INR = "INR";
    private static final int SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Currency.getInstance(currency); // rejects unknown codes
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money inr(String amount) {
        return of(amount, INR);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money times(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    /** {@code percent} of this amount, e.g. {@code percentOf(5)} for 5% tax. */
    public Money percentOf(BigDecimal percent) {
        return new Money(
                amount.multiply(percent).divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    /** Amount in minor units (paise for INR), as Razorpay expects. */
    public long toMinorUnits() {
        return amount.movePointRight(SCALE).longValueExact();
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}
