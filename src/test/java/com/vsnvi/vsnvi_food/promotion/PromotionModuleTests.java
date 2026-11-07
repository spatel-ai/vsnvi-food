package com.vsnvi.vsnvi_food.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.vsnvi.vsnvi_food.TestcontainersConfiguration;
import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;
import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.security.AuthenticatedUser;
import com.vsnvi.vsnvi_food.promotion.api.CouponDiscount;
import com.vsnvi.vsnvi_food.promotion.api.CouponRejectedException;
import com.vsnvi.vsnvi_food.promotion.api.CouponType;
import com.vsnvi.vsnvi_food.promotion.api.PricingContext;
import com.vsnvi.vsnvi_food.promotion.api.PromotionApi;
import com.vsnvi.vsnvi_food.promotion.application.CouponService;
import com.vsnvi.vsnvi_food.promotion.domain.Coupon;
import com.vsnvi.vsnvi_food.promotion.domain.CouponCodeTakenException;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantApi;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantStatus;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantSummary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code restaurant} is mocked through its facade. The database is shared by all tests in this class and isn't rolled
 * back, so every test uses its own random coupon codes, customers and orders.
 */
@ApplicationModuleTest(extraIncludes = "common")
@Import({TestcontainersConfiguration.class, PromotionModuleTests.FixedClock.class})
class PromotionModuleTests {

    static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    private final CouponService couponService;
    private final PromotionApi promotionApi;
    private final TransactionTemplate tx;

    @MockitoBean
    RestaurantApi restaurantApi;

    private UUID ownerId;
    private UUID restaurantId;

    PromotionModuleTests(
            @Autowired CouponService couponService,
            @Autowired PromotionApi promotionApi,
            @Autowired PlatformTransactionManager transactionManager) {
        this.couponService = couponService;
        this.promotionApi = promotionApi;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void ownRestaurant() {
        ownerId = UUID.randomUUID();
        restaurantId = UUID.randomUUID();
        given(restaurantApi.isOwnedBy(any(), any())).willReturn(false);
        given(restaurantApi.isOwnedBy(restaurantId, ownerId)).willReturn(true);
        given(restaurantApi.findById(restaurantId)).willReturn(Optional.of(restaurant(RestaurantStatus.APPROVED)));
        signInAs(ownerId, "RESTAURANT_OWNER");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // --- management ---

    @Test
    void should_store_code_upper_case_when_owner_creates_coupon() {
        var code = code();

        var coupon = couponService.createRestaurantCoupon(restaurantId, flat(code.toLowerCase(Locale.ROOT), "50"));

        assertThat(coupon.code()).isEqualTo(code);
        assertThat(coupon.restaurantId()).isEqualTo(restaurantId);
        assertThat(coupon.active()).isTrue();
        assertThat(coupon.createdAt()).isEqualTo(NOW);
    }

    @Test
    void should_reject_code_when_it_differs_from_an_existing_one_only_by_case() {
        var code = code();
        couponService.createRestaurantCoupon(restaurantId, flat(code, "50"));
        signInAs(UUID.randomUUID(), "ADMIN");

        assertThatThrownBy(() -> couponService.createPlatformCoupon(flat(code.toLowerCase(Locale.ROOT), "10")))
                .isInstanceOf(CouponCodeTakenException.class);
    }

    @Test
    void should_return_not_found_when_another_owner_manages_coupons() {
        var coupon = couponService.createRestaurantCoupon(restaurantId, flat(code(), "50"));
        signInAs(UUID.randomUUID(), "RESTAURANT_OWNER");

        assertThatThrownBy(() -> couponService.deactivate(coupon.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> couponService.createRestaurantCoupon(restaurantId, flat(code(), "5")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> couponService.listForRestaurant(restaurantId, Pageable.unpaged()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void should_hide_platform_coupon_from_owner_when_admin_can_change_any_coupon() {
        var ownCoupon = couponService.createRestaurantCoupon(restaurantId, flat(code(), "50"));
        signInAs(UUID.randomUUID(), "ADMIN");
        var platform = couponService.createPlatformCoupon(flat(code(), "20"));

        var changedByAdmin = couponService.update(ownCoupon.id(), patchMinimum("199"));

        assertThat(changedByAdmin.minOrderSubtotal()).isEqualTo(Money.inr("199"));
        signInAs(ownerId, "RESTAURANT_OWNER");
        assertThatThrownBy(() -> couponService.update(platform.id(), patchMinimum("1")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void should_keep_unpatched_terms_when_patching_and_stay_inactive_when_deactivated_twice() {
        var coupon = couponService.createRestaurantCoupon(restaurantId, percent(code(), "20", "75"));

        var updated = couponService.update(coupon.id(), patchMinimum("150"));
        couponService.deactivate(coupon.id());
        var deactivated = couponService.deactivate(coupon.id());

        assertThat(updated.percentOff()).isEqualByComparingTo("20");
        assertThat(updated.maxDiscount()).isEqualTo(Money.inr("75"));
        assertThat(updated.minOrderSubtotal()).isEqualTo(Money.inr("150"));
        assertThat(deactivated.active()).isFalse();
    }

    // --- evaluate (cart preview) ---

    @Test
    void should_reserve_nothing_when_evaluating_in_any_case() {
        var code = code();
        couponService.createRestaurantCoupon(restaurantId, flat(code, "50", 1, 1, false));
        var customer = UUID.randomUUID();

        var first = promotionApi.evaluate(" " + code.toLowerCase(Locale.ROOT), customer, context("400"));
        var second = promotionApi.evaluate(code, customer, context("400"));

        assertThat(first.itemDiscount()).isEqualTo(Money.inr("50"));
        assertThat(second.itemDiscount()).isEqualTo(Money.inr("50"));
    }

    @Test
    void should_reject_as_invalid_when_code_is_unknown_or_malformed() {
        assertRejected(
                () -> promotionApi.evaluate(code(), UUID.randomUUID(), context("400")), ErrorCode.COUPON_INVALID);
        assertRejected(() -> promotionApi.evaluate("x", UUID.randomUUID(), context("400")), ErrorCode.COUPON_INVALID);
        assertRejected(() -> promotionApi.evaluate(null, UUID.randomUUID(), context("400")), ErrorCode.COUPON_INVALID);
    }

    @Test
    void should_waive_delivery_fee_when_free_delivery_coupon_is_loaded_from_database() {
        var code = code();
        couponService.createRestaurantCoupon(
                restaurantId, new CouponService.NewCoupon(code, CouponType.FREE_DELIVERY, terms(null, null, null)));

        var discount = promotionApi.evaluate(code, UUID.randomUUID(), context("400"));

        assertThat(discount.deliveryDiscount()).isEqualTo(Money.inr("40"));
        assertThat(discount.itemDiscount()).isEqualTo(Money.inr("0"));
    }

    // --- redeem and release (checkout) ---

    @Test
    void should_fail_when_redeem_is_called_outside_a_transaction() {
        var code = code();
        couponService.createRestaurantCoupon(restaurantId, flat(code, "50"));

        assertThatThrownBy(() -> promotionApi.redeem(code, UUID.randomUUID(), UUID.randomUUID(), context("400"), true))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void should_enforce_per_customer_limit_and_give_the_use_back_when_released() {
        var code = code();
        couponService.createRestaurantCoupon(restaurantId, flat(code, "50", 1, null, false));
        var customer = UUID.randomUUID();
        var order = UUID.randomUUID();

        redeem(code, customer, order, true);

        assertRejected(() -> promotionApi.evaluate(code, customer, context("400")), ErrorCode.COUPON_LIMIT_REACHED);
        assertRejected(() -> redeem(code, customer, UUID.randomUUID(), true), ErrorCode.COUPON_LIMIT_REACHED);
        assertThat(promotionApi.evaluate(code, UUID.randomUUID(), context("400")))
                .isNotNull();

        promotionApi.release(order);
        promotionApi.release(order); // idempotent

        assertThat(redeem(code, customer, UUID.randomUUID(), true).itemDiscount())
                .isEqualTo(Money.inr("50"));
    }

    @Test
    void should_do_nothing_when_releasing_an_order_without_coupon() {
        promotionApi.release(UUID.randomUUID());
    }

    @Test
    void should_return_recorded_discount_without_a_second_use_when_same_order_redeems_again() {
        var code = code();
        couponService.createRestaurantCoupon(restaurantId, flat(code, "50", null, 2, false));
        var customer = UUID.randomUUID();
        var order = UUID.randomUUID();

        var first = redeem(code, customer, order, true);
        var again = redeem(code.toLowerCase(Locale.ROOT), customer, order, true);
        redeem(code, UUID.randomUUID(), UUID.randomUUID(), true);

        assertThat(again).isEqualTo(first);
        assertRejected(() -> redeem(code, UUID.randomUUID(), UUID.randomUUID(), true), ErrorCode.COUPON_LIMIT_REACHED);
    }

    @Test
    void should_reject_first_order_coupon_at_checkout_when_customer_has_ordered_before() {
        var code = code();
        couponService.createRestaurantCoupon(restaurantId, flat(code, "50", null, null, true));
        var customer = UUID.randomUUID();

        assertThat(promotionApi.evaluate(code, customer, context("400")).firstOrderOnly())
                .isTrue();
        assertRejected(() -> redeem(code, customer, UUID.randomUUID(), false), ErrorCode.COUPON_NOT_APPLICABLE);
        assertThat(redeem(code, customer, UUID.randomUUID(), true)).isNotNull();
    }

    @Test
    void should_let_only_one_checkout_take_the_last_use_when_two_redeem_concurrently() throws Exception {
        var code = code();
        couponService.createRestaurantCoupon(restaurantId, flat(code, "50", null, 1, false));
        var firstRedeemed = new CountDownLatch(1);
        var commitFirst = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(
                    () -> tx.execute(status -> {
                        var discount =
                                promotionApi.redeem(code, UUID.randomUUID(), UUID.randomUUID(), context("400"), true);
                        firstRedeemed.countDown();
                        await(commitFirst); // hold the coupon row lock
                        return discount;
                    }),
                    pool);
            assertThat(firstRedeemed.await(10, TimeUnit.SECONDS)).isTrue();

            var second =
                    CompletableFuture.supplyAsync(() -> redeem(code, UUID.randomUUID(), UUID.randomUUID(), true), pool);

            // The second checkout must wait on the row lock while the first transaction is open.
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            commitFirst.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).itemDiscount()).isEqualTo(Money.inr("50"));
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(CouponRejectedException.class)
                    .extracting(e -> ((DomainException) e).errorCode())
                    .isEqualTo(ErrorCode.COUPON_LIMIT_REACHED);
        }
    }

    @Test
    void should_let_caller_commit_when_it_catches_a_rejected_evaluation_inside_its_transaction() {
        var readOnly = new TransactionTemplate(tx.getTransactionManager());
        readOnly.setReadOnly(true);

        // As cart will do: catch the rejection, show the reason, and still commit the read. Without
        // noRollbackFor the commit would throw UnexpectedRollbackException.
        var reason = readOnly.execute(status -> {
            try {
                promotionApi.evaluate(code(), UUID.randomUUID(), context("400"));
                return "accepted";
            } catch (CouponRejectedException ex) {
                return ex.errorCode().name();
            }
        });

        assertThat(reason).isEqualTo("COUPON_INVALID");
    }

    @Test
    void should_see_a_deactivation_committed_meanwhile_when_redeem_follows_evaluate_in_one_transaction()
            throws Exception {
        var code = code();
        var coupon = couponService.createRestaurantCoupon(restaurantId, flat(code, "50"));

        try (var pool = Executors.newSingleThreadExecutor()) {
            assertRejected(
                    () -> tx.execute(status -> {
                        // Loads the coupon into this transaction's persistence context.
                        promotionApi.evaluate(code, UUID.randomUUID(), context("400"));
                        deactivateInAnotherTransaction(pool, coupon.id());
                        return promotionApi.redeem(code, UUID.randomUUID(), UUID.randomUUID(), context("400"), true);
                    }),
                    ErrorCode.COUPON_INVALID);
        }
    }

    @Test
    void should_fail_when_same_order_redeems_again_for_another_customer_or_after_release() {
        var code = code();
        couponService.createRestaurantCoupon(restaurantId, flat(code, "50"));
        var customer = UUID.randomUUID();
        var order = UUID.randomUUID();
        redeem(code, customer, order, true);

        assertThatThrownBy(() -> redeem(code, UUID.randomUUID(), order, true))
                .isInstanceOf(IllegalStateException.class);

        promotionApi.release(order);

        assertThatThrownBy(() -> redeem(code, customer, order, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_allow_one_use_per_customer_when_first_order_coupon_has_no_per_customer_limit() {
        var code = code();
        couponService.createRestaurantCoupon(restaurantId, flat(code, "50", null, null, true));
        var customer = UUID.randomUUID();

        redeem(code, customer, UUID.randomUUID(), true);

        assertRejected(() -> redeem(code, customer, UUID.randomUUID(), true), ErrorCode.COUPON_LIMIT_REACHED);
    }

    // --- available coupons (customer) ---

    @Test
    void should_list_only_usable_coupons_when_customer_asks_at_a_restaurant() {
        var usable = code();
        var firstOrder = code();
        var usedUp = code();
        var inactive = code();
        var otherRestaurants = code();
        var notYetValid = code();
        var expired = code();
        var usedUpByOthers = code();
        couponService.createRestaurantCoupon(restaurantId, flat(usable, "50"));
        couponService.createRestaurantCoupon(restaurantId, flat(usedUpByOthers, "50", null, 1, false));
        couponService.createRestaurantCoupon(
                restaurantId,
                new CouponService.NewCoupon(
                        expired,
                        CouponType.FLAT,
                        new Coupon.Terms(
                                null,
                                Money.inr("10"),
                                null,
                                null,
                                Money.inr("0"),
                                NOW.minus(Duration.ofDays(2)),
                                NOW, // validUntil is exclusive
                                null,
                                null,
                                false)));
        couponService.createRestaurantCoupon(restaurantId, flat(firstOrder, "50", null, null, true));
        couponService.createRestaurantCoupon(restaurantId, flat(usedUp, "50", 1, null, false));
        var toDeactivate = couponService.createRestaurantCoupon(restaurantId, flat(inactive, "50"));
        couponService.deactivate(toDeactivate.id());
        couponService.createRestaurantCoupon(
                restaurantId,
                new CouponService.NewCoupon(
                        notYetValid,
                        CouponType.FLAT,
                        new Coupon.Terms(
                                null,
                                Money.inr("10"),
                                null,
                                null,
                                Money.inr("0"),
                                NOW.plusSeconds(60),
                                NOW.plus(Duration.ofDays(2)),
                                null,
                                null,
                                false)));
        var otherRestaurant = UUID.randomUUID();
        given(restaurantApi.isOwnedBy(otherRestaurant, ownerId)).willReturn(true);
        couponService.createRestaurantCoupon(otherRestaurant, flat(otherRestaurants, "50"));
        signInAs(UUID.randomUUID(), "ADMIN");
        var platform = code();
        couponService.createPlatformCoupon(flat(platform, "25"));

        var customer = UUID.randomUUID();
        redeem(usedUp, customer, UUID.randomUUID(), true);
        redeem(usedUpByOthers, UUID.randomUUID(), UUID.randomUUID(), true);
        signInAs(customer, "CUSTOMER");

        var available = couponService.availableAt(restaurantId).stream()
                .map(CouponService.AvailableCouponView::code)
                .toList();

        assertThat(available)
                .contains(usable, firstOrder, platform)
                .doesNotContain(usedUp, usedUpByOthers, inactive, otherRestaurants, notYetValid, expired);
        assertThat(couponService.availableAt(restaurantId))
                .filteredOn(c -> c.code().equals(firstOrder))
                .singleElement()
                .satisfies(c -> assertThat(c.firstOrderOnly()).isTrue());
    }

    @Test
    void should_hide_free_delivery_coupon_when_restaurant_already_delivers_free() {
        var freeDelivery = code();
        couponService.createRestaurantCoupon(
                restaurantId,
                new CouponService.NewCoupon(freeDelivery, CouponType.FREE_DELIVERY, terms(null, null, null)));
        var noDeliveryFee = new RestaurantSummary(
                restaurantId,
                ownerId,
                "R",
                RestaurantStatus.APPROVED,
                12.97,
                77.59,
                5,
                Money.inr("0"),
                Money.inr("10"),
                true);
        signInAs(UUID.randomUUID(), "CUSTOMER");

        assertThat(couponService.availableAt(restaurantId))
                .extracting(CouponService.AvailableCouponView::code)
                .contains(freeDelivery);
        given(restaurantApi.findById(restaurantId)).willReturn(Optional.of(noDeliveryFee));
        assertThat(couponService.availableAt(restaurantId))
                .extracting(CouponService.AvailableCouponView::code)
                .doesNotContain(freeDelivery);
    }

    @Test
    void should_return_not_found_when_listing_coupons_of_an_unapproved_restaurant() {
        given(restaurantApi.findById(restaurantId))
                .willReturn(Optional.of(restaurant(RestaurantStatus.PENDING_APPROVAL)));
        signInAs(UUID.randomUUID(), "CUSTOMER");

        assertThatThrownBy(() -> couponService.availableAt(restaurantId)).isInstanceOf(NotFoundException.class);
    }

    // --- helpers ---

    private CouponDiscount redeem(String code, UUID customer, UUID order, boolean isFirstOrder) {
        return tx.execute(status -> promotionApi.redeem(code, customer, order, context("400"), isFirstOrder));
    }

    private PricingContext context(String subtotal) {
        return new PricingContext(restaurantId, Money.inr(subtotal), Money.inr("40"));
    }

    private static void assertRejected(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(CouponRejectedException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(code);
    }

    private static String code() {
        return "T"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private static CouponService.NewCoupon flat(String code, String amountOff) {
        return flat(code, amountOff, null, null, false);
    }

    private static CouponService.NewCoupon flat(
            String code, String amountOff, Integer perCustomer, Integer total, boolean firstOrderOnly) {
        return new CouponService.NewCoupon(
                code,
                CouponType.FLAT,
                new Coupon.Terms(
                        null,
                        Money.inr(amountOff),
                        null,
                        null,
                        Money.inr("0"),
                        NOW.minus(Duration.ofDays(1)),
                        NOW.plus(Duration.ofDays(1)),
                        perCustomer,
                        total,
                        firstOrderOnly));
    }

    private static CouponService.NewCoupon percent(String code, String percentOff, String cap) {
        return new CouponService.NewCoupon(
                code, CouponType.PERCENT, terms(null, new BigDecimal(percentOff), Money.inr(cap)));
    }

    private static Coupon.Terms terms(Money amountOff, BigDecimal percentOff, Money maxDiscount) {
        return new Coupon.Terms(
                null,
                amountOff,
                percentOff,
                maxDiscount,
                Money.inr("0"),
                NOW.minus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(1)),
                null,
                null,
                false);
    }

    private static CouponService.CouponPatch patchMinimum(String minimum) {
        return new CouponService.CouponPatch(null, null, null, null, Money.inr(minimum), null, null, null, null, null);
    }

    private RestaurantSummary restaurant(RestaurantStatus status) {
        return new RestaurantSummary(
                restaurantId, ownerId, "R", status, 12.97, 77.59, 5, Money.inr("40"), Money.inr("10"), true);
    }

    /** Runs as the owner on another thread (own transaction, own security context) and waits for the commit. */
    private void deactivateInAnotherTransaction(ExecutorService pool, UUID couponId) {
        try {
            pool.submit(() -> {
                        signInAs(ownerId, "RESTAURANT_OWNER");
                        try {
                            couponService.deactivate(couponId);
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    })
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the test to release the lock");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static void signInAs(UUID userId, String role) {
        var principal = new AuthenticatedUser(userId, role, NOW.plusSeconds(900));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
