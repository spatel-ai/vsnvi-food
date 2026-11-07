package com.vsnvi.vsnvi_food.promotion.application;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.security.CurrentUser;
import com.vsnvi.vsnvi_food.promotion.api.CouponType;
import com.vsnvi.vsnvi_food.promotion.domain.Coupon;
import com.vsnvi.vsnvi_food.promotion.domain.CouponCodeTakenException;
import com.vsnvi.vsnvi_food.promotion.domain.CouponQuotaReachedException;
import com.vsnvi.vsnvi_food.promotion.domain.CouponRedemptionRepository;
import com.vsnvi.vsnvi_food.promotion.domain.CouponRepository;
import com.vsnvi.vsnvi_food.promotion.domain.CouponUsageRow;
import com.vsnvi.vsnvi_food.promotion.domain.RestaurantNotApprovedException;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantApi;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coupon management. Admins manage platform coupons and may change any coupon; owners manage their own restaurants'
 * coupons, checked through {@link RestaurantApi}. Another owner's coupon, or a platform coupon for an owner, is reported
 * as not found (404). Updates lock the coupon row, so concurrent edits (or an edit racing a deactivation) can't
 * overwrite each other.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private static final String ADMIN = "ADMIN";

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;
    private final RestaurantApi restaurantApi;
    private final CurrentUser currentUser;
    private final Clock clock;

    @Transactional
    public CouponView createPlatformCoupon(NewCoupon draft) {
        var coupon = saveNew(draft.toCoupon(null));
        log.info("Platform coupon {} created by admin {}", coupon.getId(), currentUser.id());
        return toView(coupon);
    }

    /**
     * Only for an approved restaurant (otherwise {@code INVALID_RESTAURANT_STATE}), and at most
     * {@link Coupon#MAX_ACTIVE_PER_RESTAURANT} active coupons per restaurant. The cap is a soft limit: two concurrent
     * creates may both pass it by one.
     */
    @Transactional
    public CouponView createRestaurantCoupon(UUID restaurantId, NewCoupon draft) {
        requireOwner(restaurantId);
        var approved = restaurantApi
                .findById(restaurantId)
                .filter(r -> r.status() == RestaurantStatus.APPROVED)
                .isPresent();
        if (!approved) {
            throw new RestaurantNotApprovedException();
        }
        if (coupons.countActiveByRestaurantId(restaurantId, Instant.now(clock)) >= Coupon.MAX_ACTIVE_PER_RESTAURANT) {
            throw new CouponQuotaReachedException();
        }
        var coupon = saveNew(draft.toCoupon(restaurantId));
        log.info("Coupon {} created for restaurant {}", coupon.getId(), restaurantId);
        return toView(coupon);
    }

    /** The owner of the coupon's restaurant, or an admin; anyone else gets 404. */
    @Transactional(readOnly = true)
    public CouponView get(UUID couponId) {
        return toView(manageable(couponId, coupons::findById));
    }

    @Transactional(readOnly = true)
    public Page<CouponView> listAll(Pageable pageable) {
        return coupons.findAll(pageable).map(CouponService::toView);
    }

    @Transactional(readOnly = true)
    public Page<CouponView> listForRestaurant(UUID restaurantId, Pageable pageable) {
        requireOwner(restaurantId);
        return coupons.findAllByRestaurantId(restaurantId, pageable).map(CouponService::toView);
    }

    /** Null fields stay unchanged. The code and type can't change; limits can be changed but not removed. */
    @Transactional
    public CouponView update(UUID couponId, CouponPatch patch) {
        var coupon = manageable(couponId, coupons::findByIdForUpdate);
        var current = coupon.terms();
        coupon.changeTerms(new Coupon.Terms(
                patch.description() != null ? patch.description() : current.description(),
                patch.amountOff() != null ? patch.amountOff() : current.amountOff(),
                patch.percentOff() != null ? patch.percentOff() : current.percentOff(),
                patch.maxDiscount() != null ? patch.maxDiscount() : current.maxDiscount(),
                patch.minOrderSubtotal() != null ? patch.minOrderSubtotal() : current.minOrderSubtotal(),
                patch.validFrom() != null ? patch.validFrom() : current.validFrom(),
                patch.validUntil() != null ? patch.validUntil() : current.validUntil(),
                patch.perCustomerLimit() != null ? patch.perCustomerLimit() : current.perCustomerLimit(),
                patch.totalLimit() != null ? patch.totalLimit() : current.totalLimit(),
                patch.firstOrderOnly() != null ? patch.firstOrderOnly() : current.firstOrderOnly()));
        return toView(coupon);
    }

    /** Idempotent. Existing redemptions stay; the coupon just can't be applied any more. */
    @Transactional
    public CouponView deactivate(UUID couponId) {
        var coupon = manageable(couponId, coupons::findByIdForUpdate);
        coupon.deactivate();
        log.info("Coupon {} deactivated by user {}", couponId, currentUser.id());
        return toView(coupon);
    }

    /**
     * Coupons the signed-in customer can use at an approved restaurant right now: active, in their window, and under
     * both limits. First-order coupons are listed and flagged; checkout makes the final check. A free-delivery coupon
     * isn't listed where delivery is already free, because applying it would be rejected.
     */
    @Transactional(readOnly = true)
    public List<AvailableCouponView> availableAt(UUID restaurantId) {
        var restaurant = restaurantApi
                .findById(restaurantId)
                .filter(r -> r.status() == RestaurantStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Restaurant " + restaurantId));
        var freeDelivery = restaurant.deliveryFee().amount().signum() == 0;
        var candidates = coupons.findUsableAt(restaurantId, Instant.now(clock)).stream()
                .filter(c -> !(freeDelivery && c.getType() == CouponType.FREE_DELIVERY))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        var usage = redemptions
                .countUsage(candidates.stream().map(Coupon::getId).toList(), currentUser.id())
                .stream()
                .collect(Collectors.toMap(CouponUsageRow::couponId, Function.identity()));
        return candidates.stream()
                .filter(c -> !c.isLimitReached(
                        usage.containsKey(c.getId()) ? usage.get(c.getId()).toUsage() : Coupon.Usage.NONE))
                .map(CouponService::toAvailableView)
                .toList();
    }

    /** The pre-check gives the usual answer; {@code insert} catches a concurrent create of the same code. */
    private Coupon saveNew(Coupon coupon) {
        if (coupons.existsByCode(coupon.getCode())) {
            throw new CouponCodeTakenException();
        }
        return coupons.insert(coupon);
    }

    private void requireOwner(UUID restaurantId) {
        if (!restaurantApi.isOwnedBy(restaurantId, currentUser.id())) {
            throw new NotFoundException("Restaurant " + restaurantId);
        }
    }

    private Coupon manageable(UUID couponId, Function<UUID, Optional<Coupon>> loader) {
        var admin = ADMIN.equals(currentUser.role());
        return loader.apply(couponId)
                .filter(c ->
                        admin || (!c.isPlatform() && restaurantApi.isOwnedBy(c.getRestaurantId(), currentUser.id())))
                .orElseThrow(() -> new NotFoundException("Coupon " + couponId));
    }

    private static CouponView toView(Coupon c) {
        return new CouponView(
                c.getId(),
                c.getCode(),
                c.getRestaurantId(),
                c.getDescription(),
                c.getType(),
                c.getAmountOff(),
                c.getPercentOff(),
                c.getMaxDiscount(),
                c.getMinOrderSubtotal(),
                c.getValidFrom(),
                c.getValidUntil(),
                c.isActive(),
                c.getPerCustomerLimit(),
                c.getTotalLimit(),
                c.isFirstOrderOnly(),
                c.getCreatedAt());
    }

    private static AvailableCouponView toAvailableView(Coupon c) {
        return new AvailableCouponView(
                c.getCode(),
                c.getRestaurantId(),
                c.getDescription(),
                c.getType(),
                c.getAmountOff(),
                c.getPercentOff(),
                c.getMaxDiscount(),
                c.getMinOrderSubtotal(),
                c.getValidUntil(),
                c.isFirstOrderOnly());
    }

    /**
     * A new coupon. {@code amountOff} is for FLAT, {@code percentOff} and {@code maxDiscount} for PERCENT; null limits
     * mean unlimited. A null {@code minOrderSubtotal} means 0 INR.
     */
    public record NewCoupon(
            String code,
            CouponType type,
            String description,
            Money amountOff,
            BigDecimal percentOff,
            Money maxDiscount,
            Money minOrderSubtotal,
            Instant validFrom,
            Instant validUntil,
            Integer perCustomerLimit,
            Integer totalLimit,
            boolean firstOrderOnly) {

        Coupon toCoupon(UUID restaurantId) {
            return Coupon.create(
                    code,
                    restaurantId,
                    type,
                    new Coupon.Terms(
                            description,
                            amountOff,
                            percentOff,
                            maxDiscount,
                            minOrderSubtotal != null ? minOrderSubtotal : Money.zero(Money.INR),
                            validFrom,
                            validUntil,
                            perCustomerLimit,
                            totalLimit,
                            firstOrderOnly));
        }
    }

    public record CouponPatch(
            String description,
            Money amountOff,
            BigDecimal percentOff,
            Money maxDiscount,
            Money minOrderSubtotal,
            Instant validFrom,
            Instant validUntil,
            Integer perCustomerLimit,
            Integer totalLimit,
            Boolean firstOrderOnly) {}

    public record CouponView(
            UUID id,
            String code,
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
            Integer perCustomerLimit,
            Integer totalLimit,
            boolean firstOrderOnly,
            Instant createdAt) {}

    /** What a customer sees: no limits or usage counts. */
    public record AvailableCouponView(
            String code,
            UUID restaurantId,
            String description,
            CouponType type,
            Money amountOff,
            BigDecimal percentOff,
            Money maxDiscount,
            Money minOrderSubtotal,
            Instant validUntil,
            boolean firstOrderOnly) {}
}
