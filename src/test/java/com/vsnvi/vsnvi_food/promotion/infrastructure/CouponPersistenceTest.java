package com.vsnvi.vsnvi_food.promotion.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vsnvi.vsnvi_food.TestcontainersConfiguration;
import com.vsnvi.vsnvi_food.common.config.TimeConfig;
import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.promotion.api.CouponType;
import com.vsnvi.vsnvi_food.promotion.domain.Coupon;
import com.vsnvi.vsnvi_food.promotion.domain.CouponCodeTakenException;
import com.vsnvi.vsnvi_food.promotion.domain.CouponRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/** The {@code CouponStore} fragment against real Postgres: constraint mapping and nullable money columns. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, TimeConfig.class})
class CouponPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    private final CouponRepository coupons;
    private final EntityManager entityManager;

    CouponPersistenceTest(@Autowired CouponRepository coupons, @Autowired EntityManager entityManager) {
        this.coupons = coupons;
        this.entityManager = entityManager;
    }

    // The pre-check in CouponService is skipped here, as when two creates race: only the constraint is left.
    @Test
    void should_report_code_taken_when_the_unique_constraint_is_violated() {
        coupons.insert(freeDelivery("RACE1"));

        assertThatThrownBy(() -> coupons.insert(freeDelivery("RACE1"))).isInstanceOf(CouponCodeTakenException.class);
    }

    @Test
    void should_reload_coupon_with_null_discount_columns_when_type_is_free_delivery() {
        var saved = coupons.insert(freeDelivery("FREEDEL1"));
        entityManager.clear();

        var loaded = coupons.findByIdForUpdate(saved.getId()).orElseThrow();

        assertThat(loaded.getAmountOff()).isNull();
        assertThat(loaded.getMaxDiscount()).isNull();
        assertThat(loaded.getMinOrderSubtotal()).isEqualTo(Money.inr("0"));
    }

    private static Coupon freeDelivery(String code) {
        return Coupon.create(
                code,
                null,
                CouponType.FREE_DELIVERY,
                new Coupon.Terms(
                        null,
                        null,
                        null,
                        null,
                        Money.inr("0"),
                        NOW.minus(Duration.ofDays(1)),
                        NOW.plus(Duration.ofDays(1)),
                        null,
                        null,
                        false));
    }
}
