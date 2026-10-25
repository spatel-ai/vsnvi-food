package com.vsnvi.vsnvi_food.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MoneyTest {

    @Test
    void should_round_half_up_to_two_decimals_when_created() {
        assertThat(Money.inr("10.005").amount()).isEqualByComparingTo("10.01");
        assertThat(Money.inr("10.004").amount()).isEqualByComparingTo("10.00");
        assertThat(Money.inr("7").amount().scale()).isEqualTo(2);
    }

    @Test
    void should_add_subtract_and_multiply_when_currencies_match() {
        var price = Money.inr("249.50");

        assertThat(price.plus(Money.inr("0.50"))).isEqualTo(Money.inr("250.00"));
        assertThat(price.minus(Money.inr("49.50"))).isEqualTo(Money.inr("200.00"));
        assertThat(price.times(3)).isEqualTo(Money.inr("748.50"));
    }

    @Test
    void should_round_percentage_half_up_when_taking_tax() {
        assertThat(Money.inr("99.90").percentOf(BigDecimal.valueOf(5))).isEqualTo(Money.inr("5.00"));
        assertThat(Money.inr("10.10").percentOf(BigDecimal.valueOf(5))).isEqualTo(Money.inr("0.51"));
    }

    @Test
    void should_reject_operation_when_currencies_differ() {
        assertThatThrownBy(() -> Money.inr("1.00").plus(Money.of("1.00", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_unknown_currency_when_created() {
        assertThatThrownBy(() -> Money.of("1.00", "XYZ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_convert_to_paise_when_sending_to_gateway() {
        assertThat(Money.inr("249.99").toMinorUnits()).isEqualTo(24999L);
    }

    @Test
    void should_compare_amounts_when_currencies_match() {
        assertThat(Money.inr("2.00").isGreaterThan(Money.inr("1.99"))).isTrue();
        assertThat(Money.inr("-0.01").isNegative()).isTrue();
    }

    @Test
    void should_serialize_amount_as_string_when_written_as_json() {
        var json = JsonMapper.builder().build();

        assertThat(json.writeValueAsString(Money.inr("249"))).isEqualTo("{\"amount\":\"249.00\",\"currency\":\"INR\"}");
        assertThat(json.readValue("{\"amount\":\"249.5\",\"currency\":\"INR\"}", Money.class))
                .isEqualTo(Money.inr("249.50"));
    }
}
