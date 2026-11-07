package com.vsnvi.vsnvi_food.promotion.infrastructure;

import com.vsnvi.vsnvi_food.promotion.domain.CouponRedemption;
import com.vsnvi.vsnvi_food.promotion.domain.CouponRedemptionRepository;
import com.vsnvi.vsnvi_food.promotion.domain.CouponUsageRow;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CouponRedemptionJpaRepository extends JpaRepository<CouponRedemption, UUID>, CouponRedemptionRepository {

    @Override
    @Query("""
            select new com.vsnvi.vsnvi_food.promotion.domain.CouponUsageRow(
                r.couponId, count(r), sum(case when r.customerId = :customerId then 1L else 0L end))
            from CouponRedemption r
            where r.couponId in :couponIds
              and r.status = com.vsnvi.vsnvi_food.promotion.domain.RedemptionStatus.REDEEMED
            group by r.couponId""")
    List<CouponUsageRow> countUsage(
            @Param("couponIds") Collection<UUID> couponIds, @Param("customerId") UUID customerId);
}
