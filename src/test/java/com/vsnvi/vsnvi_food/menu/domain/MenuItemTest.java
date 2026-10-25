package com.vsnvi.vsnvi_food.menu.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vsnvi.vsnvi_food.common.domain.Money;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MenuItemTest {

    @Test
    void should_start_available_with_given_options_when_created() {
        var item = item(List.of(Variant.of("Half", Money.inr("180")), Variant.of("Full", Money.inr("320"))));

        assertThat(item.isAvailable()).isTrue();
        assertThat(item.getVariants()).extracting(Variant::getName).containsExactly("Half", "Full");
    }

    @Test
    void should_replace_whole_list_when_variants_replaced() {
        var item = item(List.of(Variant.of("Half", Money.inr("180"))));

        item.replaceVariants(List.of(Variant.of("Regular", Money.inr("200"))));

        assertThat(item.getVariants()).extracting(Variant::getName).containsExactly("Regular");
    }

    @Test
    void should_reject_more_than_ten_variants() {
        var tooMany = Collections.nCopies(11, Variant.of("V", Money.inr("1")));

        assertThatThrownBy(() -> item(tooMany)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_negative_price_when_repricing() {
        var item = item(List.of());

        assertThatThrownBy(() -> item.reprice(Money.inr("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AddOn.of("Cheese", Money.inr("-5"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_toggle_availability_idempotently() {
        var item = item(List.of());

        item.setAvailability(false);
        item.setAvailability(false);

        assertThat(item.isAvailable()).isFalse();
    }

    private static MenuItem item(List<Variant> variants) {
        return MenuItem.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                " Chicken Biryani ",
                null,
                Money.inr("250"),
                false,
                variants,
                List.of(AddOn.of("Raita", Money.inr("30"))));
    }
}
