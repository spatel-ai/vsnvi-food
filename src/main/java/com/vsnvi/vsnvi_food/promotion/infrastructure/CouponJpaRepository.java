package com.vsnvi.vsnvi_food.promotion.infrastructure;

import com.vsnvi.vsnvi_food.promotion.domain.Coupon;
import com.vsnvi.vsnvi_food.promotion.domain.CouponRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** {@code findByCodeForUpdate} comes from the {@link CouponLocking} fragment. */
interface CouponJpaRepository extends JpaRepository<Coupon, UUID>, CouponRepository, CouponLocking {

    @Override
    @Query("""
            select c from Coupon c
            where c.active = true and c.validFrom <= :now and c.validUntil > :now
              and (c.restaurantId is null or c.restaurantId = :restaurantId)
            order by c.validUntil asc, c.code asc""")
    List<Coupon> findUsableAt(@Param("restaurantId") UUID restaurantId, @Param("now") Instant now);
}
