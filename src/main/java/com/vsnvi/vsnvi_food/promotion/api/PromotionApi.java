package com.vsnvi.vsnvi_food.promotion.api;

import java.util.UUID;

/**
 * The {@code promotion} module's facade for {@code cart} and {@code order} (ARCHITECTURE §8). It is the only place that
 * calculates a coupon discount. Codes match regardless of case.
 */
public interface PromotionApi {

    /**
     * Preview for the cart: checks every rule except first-order (the cart can't know the order history, so the result
     * carries {@link CouponDiscount#firstOrderOnly()} instead). Reserves nothing. A rejection doesn't mark the caller's
     * transaction rollback-only, so {@code cart} can catch it and still commit.
     *
     * @throws CouponRejectedException with {@code COUPON_INVALID}, {@code COUPON_NOT_APPLICABLE} or
     *     {@code COUPON_LIMIT_REACHED}
     */
    CouponDiscount evaluate(String code, UUID customerId, PricingContext context);

    /**
     * Checkout: locks the coupon row, re-checks every rule including first-order, records the redemption and returns
     * the final discount. Must run inside the caller's (order) transaction, so the redemption commits or rolls back
     * with the order; calling it outside a transaction fails. Calling it again for the same order returns the
     * existing redemption's discount.
     *
     * <p>Caller requirements ({@code order}):
     *
     * <ul>
     *   <li>Use READ COMMITTED (the default): usage is counted after the lock, and must see other checkouts' commits.
     *   <li>A rejection marks the order transaction rollback-only; checkout fails anyway.
     *   <li>Serialize a customer's checkouts (e.g. lock the customer's row) before computing {@code isFirstOrder}. The
     *       coupon lock stops one first-order coupon being used twice, but two different first-order coupons lock
     *       different rows.
     * </ul>
     *
     * @throws CouponRejectedException as for {@link #evaluate}
     */
    CouponDiscount redeem(String code, UUID customerId, UUID orderId, PricingContext context, boolean isFirstOrder);

    /**
     * Gives the coupon use back when the order is rejected or cancelled. A no-op when the order used no coupon or was
     * already released, so it is safe to call more than once.
     */
    void release(UUID orderId);
}
