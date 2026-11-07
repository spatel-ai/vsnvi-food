package com.vsnvi.vsnvi_food.promotion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;
import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import com.vsnvi.vsnvi_food.promotion.api.CouponRejectedException;
import com.vsnvi.vsnvi_food.promotion.api.CouponType;
import com.vsnvi.vsnvi_food.promotion.api.PricingContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CouponTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final UUID RESTAURANT = UUID.randomUUID();
    private static final Money ZERO = Money.inr("0");

    // --- creation and terms ---

    @Test
    void should_store_code_upper_case_and_stripped_when_created() {
        var coupon = Coupon.create("  welcome-50 ", null, CouponType.FREE_DELIVERY, terms(null, null, null));

        assertThat(coupon.getCode()).isEqualTo("WELCOME-50");
        assertThat(coupon.isActive()).isTrue();
        assertThat(coupon.isPlatform()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB", "-ABC", "HAS SPACE", "EMOJI😀", "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345"})
    void should_reject_code_when_malformed(String code) {
        assertThatThrownBy(() -> Coupon.create(code, null, CouponType.FREE_DELIVERY, terms(null, null, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void should_reject_flat_coupon_when_amount_off_is_missing_or_percent_given() {
        assertThatThrownBy(() -> flat(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() ->
                        Coupon.create("FLAT100", null, CouponType.FLAT, terms(Money.inr("100"), BigDecimal.TEN, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void should_reject_percent_coupon_when_cap_is_missing_or_percent_out_of_range() {
        assertThatThrownBy(() -> percent("50", null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> percent("100.01", "100")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> percent("0", "100")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> percent("10.005", "100")).isInstanceOf(ValidationException.class);
    }

    @Test
    void should_reject_free_delivery_coupon_when_an_amount_is_given() {
        assertThatThrownBy(() ->
                        Coupon.create("FREEDEL", null, CouponType.FREE_DELIVERY, terms(Money.inr("10"), null, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void should_reject_terms_when_window_is_empty_or_limits_are_inconsistent() {
        var base = terms(Money.inr("100"), null, null);
        var emptyWindow = new Coupon.Terms(null, base.amountOff(), null, null, ZERO, NOW, NOW, null, null, false);
        var perCustomerAboveTotal = new Coupon.Terms(
                null, base.amountOff(), null, null, ZERO, base.validFrom(), base.validUntil(), 5, 2, false);
        var zeroLimit = new Coupon.Terms(
                null, base.amountOff(), null, null, ZERO, base.validFrom(), base.validUntil(), 0, null, false);

        assertThatThrownBy(() -> Coupon.create("FLAT1", null, CouponType.FLAT, emptyWindow))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Coupon.create("FLAT2", null, CouponType.FLAT, perCustomerAboveTotal))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Coupon.create("FLAT3", null, CouponType.FLAT, zeroLimit))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void should_revalidate_all_terms_when_terms_change() {
        var coupon = percent("50", "100");
        var current = coupon.terms();

        assertThatThrownBy(() -> coupon.changeTerms(new Coupon.Terms(
                        current.description(),
                        current.amountOff(),
                        current.percentOff(),
                        null,
                        current.minOrderSubtotal(),
                        current.validFrom(),
                        current.validUntil(),
                        current.perCustomerLimit(),
                        current.totalLimit(),
                        current.firstOrderOnly())))
                .isInstanceOf(ValidationException.class);
        assertThat(coupon.getMaxDiscount()).isEqualTo(Money.inr("100"));
    }

    // --- discount maths ---

    @Test
    void should_take_fixed_amount_off_subtotal_when_flat() {
        var discount = flat(Money.inr("100")).apply(context("500", "40"), NOW, Coupon.Usage.NONE, Optional.empty());

        assertThat(discount.itemDiscount()).isEqualTo(Money.inr("100"));
        assertThat(discount.deliveryDiscount()).isEqualTo(ZERO);
        assertThat(discount.total()).isEqualTo(Money.inr("100"));
    }

    @Test
    void should_cap_flat_discount_at_subtotal_when_amount_off_exceeds_it() {
        var discount = flat(Money.inr("100")).apply(context("60", "40"), NOW, Coupon.Usage.NONE, Optional.empty());

        assertThat(discount.itemDiscount()).isEqualTo(Money.inr("60"));
    }

    @Test
    void should_take_percentage_rounded_half_up_when_percent_is_under_cap() {
        // 12.5% of 99.99 = 12.49875 -> 12.50
        var discount = percent("12.5", "100").apply(context("99.99", "40"), NOW, Coupon.Usage.NONE, Optional.empty());

        assertThat(discount.itemDiscount()).isEqualTo(Money.inr("12.50"));
    }

    @Test
    void should_cap_percent_discount_when_percentage_exceeds_max_discount() {
        var discount = percent("50", "100").apply(context("1000", "40"), NOW, Coupon.Usage.NONE, Optional.empty());

        assertThat(discount.itemDiscount()).isEqualTo(Money.inr("100"));
    }

    @Test
    void should_never_exceed_subtotal_when_percent_is_one_hundred() {
        var discount = percent("100", "500").apply(context("80", "40"), NOW, Coupon.Usage.NONE, Optional.empty());

        assertThat(discount.itemDiscount()).isEqualTo(Money.inr("80"));
    }

    @Test
    void should_waive_delivery_fee_and_leave_items_untouched_when_free_delivery() {
        var discount = freeDelivery().apply(context("300", "45"), NOW, Coupon.Usage.NONE, Optional.empty());

        assertThat(discount.itemDiscount()).isEqualTo(ZERO);
        assertThat(discount.deliveryDiscount()).isEqualTo(Money.inr("45"));
    }

    @Test
    void should_split_recorded_discount_by_type_when_replaying_a_redemption() {
        assertThat(freeDelivery().discountOf(Money.inr("45")).deliveryDiscount())
                .isEqualTo(Money.inr("45"));
        assertThat(flat(Money.inr("100")).discountOf(Money.inr("100")).itemDiscount())
                .isEqualTo(Money.inr("100"));
    }

    // --- rules ---

    @Test
    void should_reject_as_invalid_when_coupon_is_inactive() {
        var coupon = flat(Money.inr("100"));
        coupon.deactivate();

        assertRejected(
                () -> coupon.apply(context("500", "40"), NOW, Coupon.Usage.NONE, Optional.empty()),
                ErrorCode.COUPON_INVALID);
    }

    @Test
    void should_reject_as_invalid_when_outside_validity_window() {
        var coupon = flat(Money.inr("100"));

        assertRejected(
                () -> coupon.apply(
                        context("500", "40"),
                        coupon.getValidFrom().minusSeconds(1),
                        Coupon.Usage.NONE,
                        Optional.empty()),
                ErrorCode.COUPON_INVALID);
        assertRejected(
                () -> coupon.apply(context("500", "40"), coupon.getValidUntil(), Coupon.Usage.NONE, Optional.empty()),
                ErrorCode.COUPON_INVALID);
        assertThat(coupon.apply(context("500", "40"), coupon.getValidFrom(), Coupon.Usage.NONE, Optional.empty()))
                .isNotNull();
    }

    @Test
    void should_reject_restaurant_coupon_when_cart_is_from_another_restaurant() {
        var coupon = Coupon.create("OURS10", RESTAURANT, CouponType.FLAT, terms(Money.inr("10"), null, null));
        var elsewhere = new PricingContext(UUID.randomUUID(), Money.inr("500"), Money.inr("40"));

        // Reported like an unknown code, so customers can't learn other restaurants' codes.
        assertRejected(
                () -> coupon.apply(elsewhere, NOW, Coupon.Usage.NONE, Optional.empty()), ErrorCode.COUPON_INVALID);
        assertThat(coupon.apply(context("500", "40"), NOW, Coupon.Usage.NONE, Optional.empty())
                        .itemDiscount())
                .isEqualTo(Money.inr("10"));
    }

    @Test
    void should_reject_when_subtotal_is_below_minimum_and_accept_at_minimum() {
        var coupon = Coupon.create(
                "MIN300",
                null,
                CouponType.FLAT,
                new Coupon.Terms(
                        null,
                        Money.inr("50"),
                        null,
                        null,
                        Money.inr("300"),
                        NOW.minus(Duration.ofDays(1)),
                        NOW.plus(Duration.ofDays(1)),
                        null,
                        null,
                        false));

        assertRejected(
                () -> coupon.apply(context("299.99", "40"), NOW, Coupon.Usage.NONE, Optional.empty()),
                ErrorCode.COUPON_NOT_APPLICABLE);
        assertThat(coupon.apply(context("300", "40"), NOW, Coupon.Usage.NONE, Optional.empty()))
                .isNotNull();
    }

    @Test
    void should_flag_in_preview_and_enforce_at_checkout_when_coupon_is_first_order_only() {
        var coupon = Coupon.create(
                "FIRST50",
                null,
                CouponType.FLAT,
                new Coupon.Terms(
                        null,
                        Money.inr("50"),
                        null,
                        null,
                        ZERO,
                        NOW.minus(Duration.ofDays(1)),
                        NOW.plus(Duration.ofDays(1)),
                        null,
                        null,
                        true));

        assertThat(coupon.apply(context("500", "40"), NOW, Coupon.Usage.NONE, Optional.empty())
                        .firstOrderOnly())
                .isTrue();
        assertThat(coupon.apply(context("500", "40"), NOW, Coupon.Usage.NONE, Optional.of(true)))
                .isNotNull();
        assertRejected(
                () -> coupon.apply(context("500", "40"), NOW, Coupon.Usage.NONE, Optional.of(false)),
                ErrorCode.COUPON_NOT_APPLICABLE);
    }

    @Test
    void should_reject_free_delivery_when_restaurant_already_delivers_free() {
        assertRejected(
                () -> freeDelivery().apply(context("300", "0"), NOW, Coupon.Usage.NONE, Optional.empty()),
                ErrorCode.COUPON_NOT_APPLICABLE);
    }

    @Test
    void should_reject_when_total_limit_is_reached() {
        var coupon = limited(3, 10);

        assertRejected(
                () -> coupon.apply(context("500", "40"), NOW, new Coupon.Usage(10, 0), Optional.empty()),
                ErrorCode.COUPON_LIMIT_REACHED);
        assertThat(coupon.apply(context("500", "40"), NOW, new Coupon.Usage(9, 0), Optional.empty()))
                .isNotNull();
    }

    @Test
    void should_reject_when_per_customer_limit_is_reached() {
        var coupon = limited(3, 10);

        assertRejected(
                () -> coupon.apply(context("500", "40"), NOW, new Coupon.Usage(5, 3), Optional.empty()),
                ErrorCode.COUPON_LIMIT_REACHED);
        assertThat(coupon.isLimitReached(new Coupon.Usage(5, 2))).isFalse();
        assertThat(coupon.isLimitReached(new Coupon.Usage(5, 3))).isTrue();
    }

    @Test
    void should_ignore_usage_when_coupon_has_no_limits() {
        assertThat(flat(Money.inr("10")).isLimitReached(new Coupon.Usage(1_000_000, 1_000)))
                .isFalse();
    }

    @Test
    void should_allow_one_use_per_customer_when_first_order_coupon_has_no_per_customer_limit() {
        var coupon = Coupon.create(
                "FIRSTONLY",
                null,
                CouponType.FLAT,
                new Coupon.Terms(
                        null,
                        Money.inr("50"),
                        null,
                        null,
                        ZERO,
                        NOW.minus(Duration.ofDays(1)),
                        NOW.plus(Duration.ofDays(1)),
                        null,
                        null,
                        true));

        assertThat(coupon.isLimitReached(new Coupon.Usage(5, 0))).isFalse();
        assertThat(coupon.isLimitReached(new Coupon.Usage(5, 1))).isTrue();
        assertRejected(
                () -> coupon.apply(context("500", "40"), NOW, new Coupon.Usage(5, 1), Optional.of(true)),
                ErrorCode.COUPON_LIMIT_REACHED);
    }

    @Test
    void should_reject_terms_when_window_is_outside_supported_range() {
        var farFuture = new Coupon.Terms(
                null, Money.inr("10"), null, null, ZERO, NOW, Instant.parse("2200-01-01T00:00:00Z"), null, null, false);
        var ancient = new Coupon.Terms(
                null, Money.inr("10"), null, null, ZERO, Instant.parse("1999-12-31T00:00:00Z"), NOW, null, null, false);

        assertThatThrownBy(() -> Coupon.create("FAR1", null, CouponType.FLAT, farFuture))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Coupon.create("OLD1", null, CouponType.FLAT, ancient))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void should_accept_percent_when_extra_decimals_are_only_trailing_zeros() {
        assertThat(percent("10.000", "100").getPercentOff()).isEqualByComparingTo("10");
    }

    @Test
    void should_reject_as_not_applicable_when_context_uses_another_currency() {
        var usd = new PricingContext(RESTAURANT, Money.of("500", "USD"), Money.of("40", "USD"));

        assertRejected(
                () -> flat(Money.inr("100")).apply(usd, NOW, Coupon.Usage.NONE, Optional.empty()),
                ErrorCode.COUPON_NOT_APPLICABLE);
    }

    @Test
    void should_reject_terms_when_first_order_coupon_allows_more_than_one_use_per_customer() {
        var terms = new Coupon.Terms(
                null,
                Money.inr("50"),
                null,
                null,
                ZERO,
                NOW.minus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(1)),
                2,
                null,
                true);

        assertThatThrownBy(() -> Coupon.create("FIRST2", null, CouponType.FLAT, terms))
                .isInstanceOf(ValidationException.class);
    }

    // --- helpers ---

    private static void assertRejected(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(CouponRejectedException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(code);
    }

    private static PricingContext context(String subtotal, String deliveryFee) {
        return new PricingContext(RESTAURANT, Money.inr(subtotal), Money.inr(deliveryFee));
    }

    private static Coupon.Terms terms(Money amountOff, BigDecimal percentOff, Money maxDiscount) {
        return new Coupon.Terms(
                null,
                amountOff,
                percentOff,
                maxDiscount,
                ZERO,
                NOW.minus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(1)),
                null,
                null,
                false);
    }

    private static Coupon flat(Money amountOff) {
        return Coupon.create("FLAT100", null, CouponType.FLAT, terms(amountOff, null, null));
    }

    private static Coupon percent(String percentOff, String cap) {
        return Coupon.create(
                "PCT",
                null,
                CouponType.PERCENT,
                terms(null, new BigDecimal(percentOff), cap == null ? null : Money.inr(cap)));
    }

    private static Coupon freeDelivery() {
        return Coupon.create("FREEDEL", null, CouponType.FREE_DELIVERY, terms(null, null, null));
    }

    private static Coupon limited(int perCustomer, int total) {
        return Coupon.create(
                "LIMITED",
                null,
                CouponType.FLAT,
                new Coupon.Terms(
                        null,
                        Money.inr("10"),
                        null,
                        null,
                        ZERO,
                        NOW.minus(Duration.ofDays(1)),
                        NOW.plus(Duration.ofDays(1)),
                        perCustomer,
                        total,
                        false));
    }
}
