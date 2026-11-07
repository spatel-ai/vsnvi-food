package com.vsnvi.vsnvi_food.promotion.domain;

import com.vsnvi.vsnvi_food.common.domain.BaseEntity;
import com.vsnvi.vsnvi_food.common.domain.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One order's use of a coupon. Releasing it (order rejected or cancelled) gives the use back. */
@Entity
@Table(name = "coupon_redemptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponRedemption extends BaseEntity {

    private UUID couponId;
    private UUID customerId;
    private UUID orderId;

    /** The total discount given: item discount, or the waived delivery fee for FREE_DELIVERY. */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "discount_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "discount_currency"))
    })
    private Money discount;

    @Enumerated(EnumType.STRING)
    private RedemptionStatus status;

    private Instant releasedAt;

    public static CouponRedemption redeem(UUID couponId, UUID customerId, UUID orderId, Money discount) {
        var redemption = new CouponRedemption();
        redemption.couponId = Objects.requireNonNull(couponId, "couponId");
        redemption.customerId = Objects.requireNonNull(customerId, "customerId");
        redemption.orderId = Objects.requireNonNull(orderId, "orderId");
        redemption.discount = Objects.requireNonNull(discount, "discount");
        redemption.status = RedemptionStatus.REDEEMED;
        return redemption;
    }

    public boolean isRedeemed() {
        return status == RedemptionStatus.REDEEMED;
    }

    /** @return false when it was already released (idempotent) */
    public boolean release(Instant now) {
        if (!isRedeemed()) {
            return false;
        }
        status = RedemptionStatus.RELEASED;
        releasedAt = Objects.requireNonNull(now, "now");
        return true;
    }
}
