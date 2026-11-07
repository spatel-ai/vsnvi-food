package com.vsnvi.vsnvi_food.promotion.domain;

import com.vsnvi.vsnvi_food.common.domain.BaseEntity;
import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import com.vsnvi.vsnvi_food.promotion.api.CouponDiscount;
import com.vsnvi.vsnvi_food.promotion.api.CouponRejectedException;
import com.vsnvi.vsnvi_food.promotion.api.CouponType;
import com.vsnvi.vsnvi_food.promotion.api.PricingContext;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A platform coupon ({@code restaurantId == null}, created by an admin) or a restaurant coupon (created by its owner).
 * Holds every rule of ARCHITECTURE §8 and the discount maths; usage counts come from {@link CouponRedemption} rows and
 * are passed in as {@link Usage}. The code and type never change after creation.
 */
@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

    /** Window bounds: keeps instants inside PostgreSQL's timestamptz range and catches typos like year 20260. */
    static final Instant EARLIEST = Instant.parse("2020-01-01T00:00:00Z");

    static final Instant LATEST = Instant.parse("2100-01-01T00:00:00Z");
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9-]{2,29}$");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private String code;
    private UUID restaurantId;
    private String description;

    @Enumerated(EnumType.STRING)
    private CouponType type;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "amount_off_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "amount_off_currency"))
    })
    private Money amountOff;

    private BigDecimal percentOff;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "max_discount_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "max_discount_currency"))
    })
    private Money maxDiscount;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "min_order_subtotal_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "min_order_subtotal_currency"))
    })
    private Money minOrderSubtotal;

    private Instant validFrom;
    private Instant validUntil;
    private boolean active;
    private Integer perCustomerLimit;
    private Integer totalLimit;
    private boolean firstOrderOnly;

    public static Coupon create(String code, UUID restaurantId, CouponType type, Terms terms) {
        var normalized = normalizeCode(code);
        if (!isWellFormed(normalized)) {
            throw new ValidationException(
                    "code must be 3-30 letters, digits or hyphens, starting with a letter or digit");
        }
        var coupon = new Coupon();
        coupon.code = normalized;
        coupon.restaurantId = restaurantId;
        coupon.type = Objects.requireNonNull(type, "type");
        coupon.active = true;
        coupon.changeTerms(terms);
        return coupon;
    }

    /** Codes match regardless of case and surrounding spaces. */
    public static String normalizeCode(String code) {
        return code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
    }

    /** Lets lookups skip the database for input that can't be a code. */
    public static boolean isWellFormed(String normalizedCode) {
        return CODE.matcher(normalizedCode).matches();
    }

    public boolean isPlatform() {
        return restaurantId == null;
    }

    public Terms terms() {
        return new Terms(
                description,
                amountOff,
                percentOff,
                maxDiscount,
                minOrderSubtotal,
                validFrom,
                validUntil,
                perCustomerLimit,
                totalLimit,
                firstOrderOnly);
    }

    /** Replaces every term at once, so the type-specific rules are always checked against the full set. */
    public void changeTerms(Terms terms) {
        Objects.requireNonNull(terms, "terms");
        validate(type, terms);
        this.description = terms.description() == null || terms.description().isBlank()
                ? null
                : terms.description().strip();
        this.amountOff = terms.amountOff();
        this.percentOff = terms.percentOff();
        this.maxDiscount = terms.maxDiscount();
        this.minOrderSubtotal = terms.minOrderSubtotal();
        this.validFrom = terms.validFrom();
        this.validUntil = terms.validUntil();
        this.perCustomerLimit = terms.perCustomerLimit();
        this.totalLimit = terms.totalLimit();
        this.firstOrderOnly = terms.firstOrderOnly();
    }

    public void deactivate() {
        this.active = false;
    }

    /** Active and inside its validity window ({@code validUntil} is exclusive). */
    public boolean isUsableAt(Instant now) {
        return active && !now.isBefore(validFrom) && now.isBefore(validUntil);
    }

    public boolean isLimitReached(Usage usage) {
        return (totalLimit != null && usage.totalUses() >= totalLimit) || isCustomerLimitReached(usage);
    }

    /**
     * A first-order coupon can be used once per customer even without a {@code perCustomerLimit}. Checked under the
     * coupon row lock, so two concurrent checkouts can't both use the same first-order coupon.
     */
    private boolean isCustomerLimitReached(Usage usage) {
        Integer limit = firstOrderOnly ? Integer.valueOf(1) : perCustomerLimit;
        return limit != null && usage.customerUses() >= limit;
    }

    /**
     * Checks every rule and returns the discount.
     *
     * @param isFirstOrder empty for the cart preview, which can't know the order history; checkout passes the value
     *     from {@code order}
     * @throws CouponRejectedException when a rule fails
     */
    public CouponDiscount apply(PricingContext context, Instant now, Usage usage, Optional<Boolean> isFirstOrder) {
        if (!active) {
            throw CouponRejectedException.invalid("This coupon is no longer active");
        }
        if (now.isBefore(validFrom)) {
            throw CouponRejectedException.invalid("This coupon is not valid yet");
        }
        if (!now.isBefore(validUntil)) {
            throw CouponRejectedException.invalid("This coupon has expired");
        }
        if (!isPlatform() && !restaurantId.equals(context.restaurantId())) {
            throw CouponRejectedException.notApplicable("This coupon is not valid at this restaurant");
        }
        if (minOrderSubtotal.isGreaterThan(context.itemSubtotal())) {
            throw CouponRejectedException.notApplicable(
                    "Add items worth at least " + minOrderSubtotal.amount() + " " + minOrderSubtotal.currency());
        }
        if (firstOrderOnly && isFirstOrder.isPresent() && !isFirstOrder.get()) {
            throw CouponRejectedException.notApplicable("This coupon is only valid on your first order");
        }
        if (type == CouponType.FREE_DELIVERY && !context.deliveryFee().isGreaterThan(zero(context))) {
            throw CouponRejectedException.notApplicable("Delivery is already free at this restaurant");
        }
        if (totalLimit != null && usage.totalUses() >= totalLimit) {
            throw CouponRejectedException.limitReached("This coupon has been fully used");
        }
        if (isCustomerLimitReached(usage)) {
            throw CouponRejectedException.limitReached("You have already used this coupon the maximum number of times");
        }
        return discountFor(context);
    }

    /** The discount amounts for a context that already passed the rules. */
    public CouponDiscount discountFor(PricingContext context) {
        var zero = zero(context);
        var subtotal = context.itemSubtotal();
        return switch (type) {
            case FLAT -> discount(min(amountOff, subtotal), zero);
            case PERCENT -> discount(min(min(subtotal.percentOf(percentOff), maxDiscount), subtotal), zero);
            case FREE_DELIVERY -> discount(zero, context.deliveryFee());
        };
    }

    /** Rebuilds a recorded discount (one stored amount) into the item/delivery split. */
    public CouponDiscount discountOf(Money recorded) {
        var zero = Money.zero(recorded.currency());
        return type == CouponType.FREE_DELIVERY ? discount(zero, recorded) : discount(recorded, zero);
    }

    private CouponDiscount discount(Money itemDiscount, Money deliveryDiscount) {
        return new CouponDiscount(getId(), code, type, itemDiscount, deliveryDiscount, firstOrderOnly);
    }

    private static Money zero(PricingContext context) {
        return Money.zero(context.itemSubtotal().currency());
    }

    private static Money min(Money a, Money b) {
        return a.isGreaterThan(b) ? b : a;
    }

    private static void validate(CouponType type, Terms terms) {
        if (terms.minOrderSubtotal() == null || terms.minOrderSubtotal().isNegative()) {
            throw new ValidationException("minOrderSubtotal must be zero or more");
        }
        if (terms.validFrom() == null || terms.validUntil() == null) {
            throw new ValidationException("validFrom and validUntil are required");
        }
        if (terms.validFrom().isBefore(EARLIEST) || terms.validUntil().isAfter(LATEST)) {
            throw new ValidationException("validFrom and validUntil must be between 2020 and 2100");
        }
        if (!terms.validUntil().isAfter(terms.validFrom())) {
            throw new ValidationException("validUntil must be after validFrom");
        }
        if (terms.perCustomerLimit() != null && terms.perCustomerLimit() < 1) {
            throw new ValidationException("perCustomerLimit must be at least 1");
        }
        if (terms.totalLimit() != null && terms.totalLimit() < 1) {
            throw new ValidationException("totalLimit must be at least 1");
        }
        if (terms.perCustomerLimit() != null
                && terms.totalLimit() != null
                && terms.perCustomerLimit() > terms.totalLimit()) {
            throw new ValidationException("perCustomerLimit can't exceed totalLimit");
        }
        switch (type) {
            case FLAT -> {
                if (terms.amountOff() == null || terms.amountOff().amount().signum() <= 0) {
                    throw new ValidationException("A FLAT coupon needs an amountOff above zero");
                }
                if (terms.percentOff() != null || terms.maxDiscount() != null) {
                    throw new ValidationException("A FLAT coupon takes no percentOff or maxDiscount");
                }
                requireSameCurrency(terms.amountOff(), terms.minOrderSubtotal());
            }
            case PERCENT -> {
                if (terms.percentOff() == null
                        || terms.percentOff().signum() <= 0
                        || terms.percentOff().compareTo(HUNDRED) > 0
                        || terms.percentOff().scale() > 2) {
                    throw new ValidationException("A PERCENT coupon needs a percentOff above 0 and at most 100");
                }
                if (terms.maxDiscount() == null || terms.maxDiscount().amount().signum() <= 0) {
                    throw new ValidationException("A PERCENT coupon needs a maxDiscount cap above zero");
                }
                if (terms.amountOff() != null) {
                    throw new ValidationException("A PERCENT coupon takes no amountOff");
                }
                requireSameCurrency(terms.maxDiscount(), terms.minOrderSubtotal());
            }
            case FREE_DELIVERY -> {
                if (terms.amountOff() != null || terms.percentOff() != null || terms.maxDiscount() != null) {
                    throw new ValidationException(
                            "A FREE_DELIVERY coupon takes no amountOff, percentOff or maxDiscount");
                }
            }
        }
    }

    private static void requireSameCurrency(Money a, Money b) {
        if (!a.currency().equals(b.currency())) {
            throw new ValidationException("All coupon amounts must use the same currency");
        }
    }

    /**
     * Everything an owner or admin may change after creation. {@code amountOff} is for FLAT, {@code percentOff} and
     * {@code maxDiscount} are for PERCENT; null limits mean unlimited.
     */
    public record Terms(
            String description,
            Money amountOff,
            BigDecimal percentOff,
            Money maxDiscount,
            Money minOrderSubtotal,
            Instant validFrom,
            Instant validUntil,
            Integer perCustomerLimit,
            Integer totalLimit,
            boolean firstOrderOnly) {}

    /** Current {@code REDEEMED} redemptions of a coupon: overall and by one customer. */
    public record Usage(long totalUses, long customerUses) {

        public static final Usage NONE = new Usage(0, 0);
    }
}
