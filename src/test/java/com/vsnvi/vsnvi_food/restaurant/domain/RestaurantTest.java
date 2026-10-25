package com.vsnvi.vsnvi_food.restaurant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantStatus;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RestaurantTest {

    // 2026-10-26 is a Monday. Asia/Kolkata is UTC+05:30.
    private static final Instant MONDAY_12_00_IST = Instant.parse("2026-10-26T06:30:00Z");
    private static final Instant MONDAY_23_30_IST = Instant.parse("2026-10-26T18:00:00Z");
    private static final Instant TUESDAY_00_30_IST = Instant.parse("2026-10-26T19:00:00Z");
    private static final Instant TUESDAY_02_00_IST = Instant.parse("2026-10-26T20:30:00Z");

    @Test
    void should_start_pending_and_closed_when_registered() {
        var restaurant = approvedWithHours(List.of());
        var pending = Restaurant.register(UUID.randomUUID(), profile(), Money.inr("30"), Money.inr("10"));

        assertThat(pending.getStatus()).isEqualTo(RestaurantStatus.PENDING_APPROVAL);
        assertThat(pending.isOpenAt(MONDAY_12_00_IST)).isFalse();
        assertThat(restaurant.getCuisines()).containsExactly("biryani", "north-indian");
    }

    @Test
    void should_be_open_only_inside_a_slot_when_approved() {
        var restaurant =
                approvedWithHours(List.of(OpeningHours.of(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(15, 0))));

        assertThat(restaurant.isOpenAt(MONDAY_12_00_IST)).isTrue();
        assertThat(restaurant.isOpenAt(MONDAY_23_30_IST)).isFalse();
    }

    @Test
    void should_treat_closing_time_as_exclusive() {
        var restaurant =
                approvedWithHours(List.of(OpeningHours.of(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0))));

        assertThat(restaurant.isOpenAt(MONDAY_12_00_IST)).isFalse();
    }

    @Test
    void should_stay_open_past_midnight_when_slot_runs_overnight() {
        var restaurant =
                approvedWithHours(List.of(OpeningHours.of(DayOfWeek.MONDAY, LocalTime.of(19, 0), LocalTime.of(1, 0))));

        assertThat(restaurant.isOpenAt(MONDAY_23_30_IST)).isTrue();
        assertThat(restaurant.isOpenAt(TUESDAY_00_30_IST)).isTrue();
        assertThat(restaurant.isOpenAt(TUESDAY_02_00_IST)).isFalse();
        assertThat(restaurant.isOpenAt(MONDAY_12_00_IST)).isFalse();
    }

    @Test
    void should_be_closed_when_owner_switches_it_off() {
        var restaurant =
                approvedWithHours(List.of(OpeningHours.of(DayOfWeek.MONDAY, LocalTime.of(0, 0), LocalTime.of(23, 59))));

        restaurant.switchOpen(false);

        assertThat(restaurant.isOpenAt(MONDAY_12_00_IST)).isFalse();
    }

    @Test
    void should_allow_approve_from_pending_and_suspended() {
        var restaurant = Restaurant.register(UUID.randomUUID(), profile(), Money.inr("30"), Money.inr("10"));

        restaurant.approve();
        restaurant.suspend();
        restaurant.approve();

        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.APPROVED);
    }

    @Test
    void should_reject_suspend_when_still_pending() {
        var restaurant = Restaurant.register(UUID.randomUUID(), profile(), Money.inr("30"), Money.inr("10"));

        assertThatThrownBy(restaurant::suspend).isInstanceOf(InvalidRestaurantStateException.class);
    }

    @Test
    void should_be_idempotent_when_suspending_twice() {
        var restaurant = approvedWithHours(List.of());

        restaurant.suspend();
        restaurant.suspend();

        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.SUSPENDED);
    }

    @Test
    void should_keep_unpatched_fields_when_patching_profile() {
        var restaurant = approvedWithHours(List.of());

        restaurant.patchProfile(new Restaurant.ProfilePatch("New Name", null, null, null, null, null, null, 8.0, null));

        assertThat(restaurant.getName()).isEqualTo("New Name");
        assertThat(restaurant.getServiceableRadiusKm()).isEqualTo(8.0);
        assertThat(restaurant.getCity()).isEqualTo("Bengaluru");
    }

    @Test
    void should_reject_radius_beyond_limit() {
        var restaurant = approvedWithHours(List.of());

        assertThatThrownBy(() -> restaurant.patchProfile(
                        new Restaurant.ProfilePatch(null, null, null, null, null, null, null, 31.0, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_unknown_time_zone_as_validation_error() {
        var restaurant = approvedWithHours(List.of());

        assertThatThrownBy(() -> restaurant.patchProfile(
                        new Restaurant.ProfilePatch(null, null, null, null, null, null, null, null, "Mars/Base")))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> restaurant.patchProfile(
                        new Restaurant.ProfilePatch(null, null, null, null, null, null, null, null, "+99:99")))
                .isInstanceOf(ValidationException.class);
    }

    private static Restaurant approvedWithHours(List<OpeningHours> hours) {
        var restaurant = Restaurant.register(UUID.randomUUID(), profile(), Money.inr("30"), Money.inr("10"));
        restaurant.approve();
        restaurant.replaceOpeningHours(hours);
        return restaurant;
    }

    private static Restaurant.Profile profile() {
        return new Restaurant.Profile(
                "Spice Route",
                null,
                List.of("North-Indian", "biryani", "north-indian"),
                "1 MG Road",
                "Bengaluru",
                12.97,
                77.59,
                5,
                "Asia/Kolkata");
    }
}
