package com.vsnvi.vsnvi_food.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vsnvi.vsnvi_food.TestcontainersConfiguration;
import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import com.vsnvi.vsnvi_food.common.security.AuthenticatedUser;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantApi;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantStatus;
import com.vsnvi.vsnvi_food.restaurant.application.RestaurantService;
import com.vsnvi.vsnvi_food.restaurant.domain.OpeningHours;
import com.vsnvi.vsnvi_food.restaurant.domain.Restaurant;
import com.vsnvi.vsnvi_food.restaurant.domain.RestaurantSearch;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

@ApplicationModuleTest(extraIncludes = "common")
@Import({TestcontainersConfiguration.class, RestaurantModuleTests.FixedClock.class})
class RestaurantModuleTests {

    /** Monday 2026-10-26, 12:00 in Asia/Kolkata. */
    static final Instant NOW = Instant.parse("2026-10-26T06:30:00Z");

    // Bengaluru centre and two points: ~3 km and ~12 km away.
    private static final double LAT = 12.9716, LNG = 77.5946;
    private static final double NEAR_LAT = 12.9990, NEAR_LNG = 77.5946;
    private static final double FAR_LAT = 13.0800, FAR_LNG = 77.5946;

    private final RestaurantService service;
    private final RestaurantApi api;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    // Fresh per test: the context (and database) is shared, so every search filters by this test's name tag.
    private UUID ownerId;
    private String tag;

    RestaurantModuleTests(
            @Autowired RestaurantService service,
            @Autowired RestaurantApi api,
            @Autowired JdbcClient jdbc,
            @Autowired TransactionTemplate tx) {
        this.service = service;
        this.api = api;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @BeforeEach
    void signInAsOwner() {
        ownerId = UUID.randomUUID();
        tag = "t" + UUID.randomUUID().toString().substring(0, 8);
        signInAs(ownerId, "RESTAURANT_OWNER");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_return_only_approved_restaurants_that_deliver_to_the_point_when_searching_by_location() {
        var near = approved("Near " + tag, NEAR_LAT, NEAR_LNG, 5, "south-indian", allDay());
        var tooFar = approved("Far " + tag, FAR_LAT, FAR_LNG, 5, "south-indian", allDay());
        var farButWideRadius = approved("Wide " + tag, FAR_LAT, FAR_LNG, 15, "south-indian", allDay());
        pending("Pending " + tag, NEAR_LAT, NEAR_LNG);

        var page = service.search(criteria(LAT, LNG, null, null, null), PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(r -> r.restaurant().getId())
                .containsExactly(near, farButWideRadius)
                .doesNotContain(tooFar);
        assertThat(page.getContent().getFirst().distanceKm()).isBetween(2.5, 3.5);
    }

    @Test
    void should_filter_by_cuisine_rating_and_name_when_given() {
        var a = approved("Biryani House " + tag, NEAR_LAT, NEAR_LNG, 5, "biryani", allDay());
        var b = approved("Dosa Corner " + tag, NEAR_LAT, NEAR_LNG, 5, "south-indian", allDay());
        setRating(a, "4.5");
        setRating(b, "3.9");

        assertThat(ids(criteria(null, null, "biryani", null, null))).containsExactly(a);
        assertThat(ids(new RestaurantSearch.Criteria(null, null, null, new BigDecimal("4.0"), null, tag)))
                .containsExactly(a);
        assertThat(ids(new RestaurantSearch.Criteria(null, null, null, null, null, "dosa corner " + tag)))
                .containsExactly(b);
    }

    @Test
    void should_treat_like_wildcards_literally_when_searching_by_name() {
        approved("100% Veg " + tag, NEAR_LAT, NEAR_LNG, 5, "veg", allDay());
        approved("Plain " + tag, NEAR_LAT, NEAR_LNG, 5, "veg", allDay());

        assertThat(ids(new RestaurantSearch.Criteria(null, null, null, null, null, "%" + tag)))
                .isEmpty();
        assertThat(ids(new RestaurantSearch.Criteria(null, null, null, null, null, "100% Veg " + tag)))
                .hasSize(1);
    }

    @Test
    void should_agree_with_domain_open_rule_when_filtering_by_open_now() {
        var lunch = approved("Lunch " + tag, NEAR_LAT, NEAR_LNG, 5, "thali", List.of(slot(DayOfWeek.MONDAY, 11, 15)));
        var lateNight = approved(
                "Late " + tag,
                NEAR_LAT,
                NEAR_LNG,
                5,
                "thali",
                List.of(slot(DayOfWeek.SUNDAY, 20, 13))); // past midnight
        var dinner = approved("Dinner " + tag, NEAR_LAT, NEAR_LNG, 5, "thali", List.of(slot(DayOfWeek.MONDAY, 19, 23)));
        var switchedOff = approved("Off " + tag, NEAR_LAT, NEAR_LNG, 5, "thali", allDay());
        service.switchOpen(switchedOff, false);

        var open = ids(new RestaurantSearch.Criteria(null, null, "thali", null, true, tag));
        var closed = ids(new RestaurantSearch.Criteria(null, null, "thali", null, false, tag));

        assertThat(open).containsExactlyInAnyOrder(lunch, lateNight);
        assertThat(closed).containsExactlyInAnyOrder(dinner, switchedOff);
        for (var id : List.of(lunch, lateNight, dinner, switchedOff)) {
            assertThat(api.findById(id).orElseThrow().openNow())
                    .as("SQL and Java agree for %s", id)
                    .isEqualTo(open.contains(id));
        }
    }

    @Test
    void should_sort_by_rating_with_unrated_last_when_requested() {
        var unrated = approved("U " + tag, NEAR_LAT, NEAR_LNG, 5, "cafe", allDay());
        var high = approved("H " + tag, NEAR_LAT, NEAR_LNG, 5, "cafe", allDay());
        var low = approved("L " + tag, NEAR_LAT, NEAR_LNG, 5, "cafe", allDay());
        setRating(high, "4.8");
        setRating(low, "3.1");

        var page = service.search(
                criteria(null, null, "cafe", null, null), PageRequest.of(0, 10, Sort.by(Sort.Order.desc("rating"))));

        assertThat(page.getContent()).extracting(r -> r.restaurant().getId()).containsExactly(high, low, unrated);
    }

    @Test
    void should_reject_unknown_sort_even_when_nothing_matches() {
        var nothing = criteria(null, null, "no-such-cuisine-" + tag, null, null);

        assertThatThrownBy(() -> service.search(nothing, PageRequest.of(0, 10, Sort.by("bogus"))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.search(nothing, PageRequest.of(0, 10, Sort.by("distance"))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void should_hide_pending_restaurant_from_others_but_show_it_to_owner() {
        var id = pending("Hidden " + tag, NEAR_LAT, NEAR_LNG);

        assertThat(service.details(id).restaurant().getStatus()).isEqualTo(RestaurantStatus.PENDING_APPROVAL);

        signInAs(UUID.randomUUID(), "CUSTOMER");
        assertThatThrownBy(() -> service.details(id)).isInstanceOf(NotFoundException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.details(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void should_return_not_found_when_owner_edits_someone_elses_restaurant() {
        var id = pending("Mine " + tag, NEAR_LAT, NEAR_LNG);

        signInAs(UUID.randomUUID(), "RESTAURANT_OWNER");

        assertThatThrownBy(() -> service.switchOpen(id, false)).isInstanceOf(NotFoundException.class);
        assertThat(api.isOwnedBy(id, ownerId)).isTrue();
        assertThat(api.isOwnedBy(id, UUID.randomUUID())).isFalse();
    }

    @Test
    void should_expose_fees_and_status_through_facade() {
        var id = approved("Facade " + tag, NEAR_LAT, NEAR_LNG, 5, "pizza", allDay());

        var summary = api.findById(id).orElseThrow();

        assertThat(summary.deliveryFee()).isEqualTo(Money.inr("30.00"));
        assertThat(summary.packagingCharge()).isEqualTo(Money.inr("10.00"));
        assertThat(summary.canReceiveOrders()).isTrue();
    }

    private UUID approved(
            String name, double lat, double lng, double radius, String cuisine, List<OpeningHours> hours) {
        var id = service.register(profile(name, lat, lng, radius, cuisine), Money.inr("30"), Money.inr("10"))
                .restaurant()
                .getId();
        service.replaceOpeningHours(id, hours);
        signInAs(UUID.randomUUID(), "ADMIN");
        service.approve(id);
        signInAs(ownerId, "RESTAURANT_OWNER");
        return id;
    }

    private UUID pending(String name, double lat, double lng) {
        return service.register(profile(name, lat, lng, 5, "misc"), Money.inr("30"), Money.inr("10"))
                .restaurant()
                .getId();
    }

    /** The rating has no writer yet (reviews come later), so tests set it in this module's own table. */
    private void setRating(UUID id, String rating) {
        tx.executeWithoutResult(s -> jdbc.sql("UPDATE restaurants SET rating = :r WHERE id = :id")
                .param("r", new BigDecimal(rating))
                .param("id", id)
                .update());
    }

    private List<UUID> ids(RestaurantSearch.Criteria criteria) {
        return service.search(criteria, PageRequest.of(0, 50)).getContent().stream()
                .map(r -> r.restaurant().getId())
                .toList();
    }

    private RestaurantSearch.Criteria criteria(
            Double lat, Double lng, String cuisine, BigDecimal minRating, Boolean open) {
        return new RestaurantSearch.Criteria(lat, lng, cuisine, minRating, open, tag);
    }

    private static Restaurant.Profile profile(String name, double lat, double lng, double radius, String cuisine) {
        return new Restaurant.Profile(
                name, null, List.of(cuisine), "1 Main Rd", "Bengaluru", lat, lng, radius, "Asia/Kolkata");
    }

    private static List<OpeningHours> allDay() {
        return List.of(OpeningHours.of(DayOfWeek.MONDAY, LocalTime.MIN, LocalTime.of(23, 59)));
    }

    private static OpeningHours slot(DayOfWeek day, int from, int to) {
        return OpeningHours.of(day, LocalTime.of(from, 0), LocalTime.of(to, 0));
    }

    private static void signInAs(UUID userId, String role) {
        var principal = new AuthenticatedUser(userId, role, Instant.now().plusSeconds(900));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
