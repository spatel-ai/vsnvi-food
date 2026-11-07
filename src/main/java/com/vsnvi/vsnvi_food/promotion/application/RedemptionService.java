package com.vsnvi.vsnvi_food.promotion.application;

import com.vsnvi.vsnvi_food.promotion.api.CouponDiscount;
import com.vsnvi.vsnvi_food.promotion.api.CouponRejectedException;
import com.vsnvi.vsnvi_food.promotion.api.PricingContext;
import com.vsnvi.vsnvi_food.promotion.api.PromotionApi;
import com.vsnvi.vsnvi_food.promotion.domain.Coupon;
import com.vsnvi.vsnvi_food.promotion.domain.CouponRedemption;
import com.vsnvi.vsnvi_food.promotion.domain.CouponRedemptionRepository;
import com.vsnvi.vsnvi_food.promotion.domain.CouponRepository;
import com.vsnvi.vsnvi_food.promotion.domain.RedemptionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PromotionApi}: the coupon preview for {@code cart}, and redeem/release for {@code order}. Limits are enforced
 * by locking the coupon row ({@code PESSIMISTIC_WRITE}) inside the order transaction, so two checkouts can't both take
 * the last use (ARCHITECTURE §8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedemptionService implements PromotionApi {

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;
    private final Clock clock;

    /**
     * A rejection must not doom the caller's transaction: {@code cart} catches it to show the reason and still commits
     * its read.
     */
    @Override
    @Transactional(readOnly = true, noRollbackFor = CouponRejectedException.class)
    public CouponDiscount evaluate(String code, UUID customerId, PricingContext context) {
        Objects.requireNonNull(customerId, "customerId");
        var coupon = lookup(code).flatMap(coupons::findByCode).orElseThrow(RedemptionService::unknown);
        return coupon.apply(context, Instant.now(clock), usageOf(coupon, customerId), Optional.empty());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CouponDiscount redeem(
            String code, UUID customerId, UUID orderId, PricingContext context, boolean isFirstOrder) {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(orderId, "orderId");
        var existing = redemptions.findByOrderId(orderId);
        if (existing.isPresent()) {
            return replay(existing.get(), code, customerId);
        }
        var coupon = lookup(code).flatMap(coupons::findByCodeForUpdate).orElseThrow(RedemptionService::unknown);
        var discount =
                coupon.apply(context, Instant.now(clock), usageOf(coupon, customerId), Optional.of(isFirstOrder));
        redemptions.save(CouponRedemption.redeem(coupon.getId(), customerId, orderId, discount.total()));
        log.info("Coupon {} redeemed for order {}", coupon.getId(), orderId);
        return discount;
    }

    @Override
    @Transactional
    public void release(UUID orderId) {
        redemptions.findByOrderId(orderId).ifPresent(redemption -> {
            if (redemption.release(Instant.now(clock))) {
                log.info("Coupon {} released for order {}", redemption.getCouponId(), orderId);
            }
        });
    }

    /**
     * The same order redeeming again (e.g. a retried checkout) gets the recorded discount, not a second use. Anything
     * else for an order that already has a redemption (released, another code or another customer) is a caller bug.
     */
    private CouponDiscount replay(CouponRedemption redemption, String code, UUID customerId) {
        var coupon = coupons.findById(redemption.getCouponId()).orElseThrow();
        if (!redemption.isRedeemed()
                || !redemption.getCustomerId().equals(customerId)
                || !coupon.getCode().equals(Coupon.normalizeCode(code))) {
            throw new IllegalStateException("Order " + redemption.getOrderId() + " already has a coupon redemption");
        }
        return coupon.discountOf(redemption.getDiscount());
    }

    private Coupon.Usage usageOf(Coupon coupon, UUID customerId) {
        return new Coupon.Usage(
                redemptions.countByCouponIdAndStatus(coupon.getId(), RedemptionStatus.REDEEMED),
                redemptions.countByCouponIdAndCustomerIdAndStatus(
                        coupon.getId(), customerId, RedemptionStatus.REDEEMED));
    }

    private static Optional<String> lookup(String code) {
        var normalized = Coupon.normalizeCode(code);
        return Coupon.isWellFormed(normalized) ? Optional.of(normalized) : Optional.empty();
    }

    private static CouponRejectedException unknown() {
        return CouponRejectedException.invalid("Unknown coupon code");
    }
}
