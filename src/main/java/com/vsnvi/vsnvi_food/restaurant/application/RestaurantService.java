package com.vsnvi.vsnvi_food.restaurant.application;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.security.CurrentUser;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantApi;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantStatus;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantSummary;
import com.vsnvi.vsnvi_food.restaurant.domain.OpeningHours;
import com.vsnvi.vsnvi_food.restaurant.domain.Restaurant;
import com.vsnvi.vsnvi_food.restaurant.domain.RestaurantRepository;
import com.vsnvi.vsnvi_food.restaurant.domain.RestaurantSearch;
import java.time.Clock;
import java.util.Collection;
import java.util.EnumSet;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantService implements RestaurantApi {

    private final RestaurantRepository restaurants;
    private final RestaurantSearch search;
    private final CurrentUser currentUser;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<RestaurantSummary> findById(UUID restaurantId) {
        return restaurants.findById(restaurantId).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnedBy(UUID restaurantId, UUID userId) {
        return restaurants.existsByIdAndOwnerId(restaurantId, userId);
    }

    @Transactional(readOnly = true)
    public Page<SearchResult> search(RestaurantSearch.Criteria criteria, Pageable pageable) {
        var hits = search.search(criteria, clock.instant(), pageable);
        var byId = restaurants
                .findAllById(hits.map(RestaurantSearch.Hit::restaurantId).getContent())
                .stream()
                .collect(Collectors.toMap(Restaurant::getId, Function.identity()));
        return hits.map(hit -> new SearchResult(byId.get(hit.restaurantId()), hit.distanceKm(), hit.openNow()));
    }

    /**
     * Public details. A restaurant that isn't approved is visible only to its owner and to admins; everyone else gets
     * 404, so pending restaurants can't be discovered.
     */
    @Transactional(readOnly = true)
    public Details details(UUID restaurantId) {
        var restaurant = restaurants
                .findById(restaurantId)
                .filter(r -> r.isVisibleToPublic() || callerMayManage(r))
                .orElseThrow(() -> new NotFoundException("Restaurant " + restaurantId));
        return toDetails(restaurant);
    }

    @Transactional
    public Details register(Restaurant.Profile profile, Money deliveryFee, Money packagingCharge) {
        var restaurant = restaurants.save(Restaurant.register(currentUser.id(), profile, deliveryFee, packagingCharge));
        log.info("Restaurant {} registered by owner {}, pending approval", restaurant.getId(), restaurant.getOwnerId());
        return toDetails(restaurant);
    }

    @Transactional(readOnly = true)
    public List<Details> mine() {
        return restaurants.findAllByOwnerIdOrderByCreatedAtAsc(currentUser.id()).stream()
                .map(this::toDetails)
                .toList();
    }

    /** Null fields and charges leave that part unchanged. */
    @Transactional
    public Details update(UUID restaurantId, Restaurant.ProfilePatch patch, Money deliveryFee, Money packagingCharge) {
        var restaurant = owned(restaurantId);
        restaurant.patchProfile(patch);
        if (deliveryFee != null || packagingCharge != null) {
            restaurant.changeCharges(
                    deliveryFee != null ? deliveryFee : restaurant.getDeliveryFee(),
                    packagingCharge != null ? packagingCharge : restaurant.getPackagingCharge());
        }
        return toDetails(restaurant);
    }

    @Transactional
    public Details replaceOpeningHours(UUID restaurantId, Collection<OpeningHours> slots) {
        var restaurant = owned(restaurantId);
        restaurant.replaceOpeningHours(slots);
        return toDetails(restaurant);
    }

    @Transactional
    public Details switchOpen(UUID restaurantId, boolean open) {
        var restaurant = owned(restaurantId);
        restaurant.switchOpen(open);
        return toDetails(restaurant);
    }

    @Transactional(readOnly = true)
    public Page<Details> list(RestaurantStatus status, Pageable pageable) {
        var statuses = status == null ? EnumSet.allOf(RestaurantStatus.class) : EnumSet.of(status);
        return restaurants.findAllByStatusIn(statuses, pageable).map(this::toDetails);
    }

    @Transactional
    public Details approve(UUID restaurantId) {
        var restaurant = existing(restaurantId);
        restaurant.approve();
        log.info("Restaurant {} approved by admin {}", restaurantId, currentUser.id());
        return toDetails(restaurant);
    }

    @Transactional
    public Details suspend(UUID restaurantId) {
        var restaurant = existing(restaurantId);
        restaurant.suspend();
        log.info("Restaurant {} suspended by admin {}", restaurantId, currentUser.id());
        return toDetails(restaurant);
    }

    /** Another owner's restaurant is reported as not found (404). */
    private Restaurant owned(UUID restaurantId) {
        return restaurants
                .findByIdAndOwnerId(restaurantId, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Restaurant " + restaurantId));
    }

    private Restaurant existing(UUID restaurantId) {
        return restaurants
                .findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant " + restaurantId));
    }

    private boolean callerMayManage(Restaurant restaurant) {
        return currentUser
                .ifAuthenticated()
                .map(user -> "ADMIN".equals(user.role()) || restaurant.isOwnedBy(user.id()))
                .orElse(false);
    }

    private RestaurantSummary toSummary(Restaurant r) {
        return new RestaurantSummary(
                r.getId(),
                r.getOwnerId(),
                r.getName(),
                r.getStatus(),
                r.getLatitude(),
                r.getLongitude(),
                r.getServiceableRadiusKm(),
                r.getDeliveryFee(),
                r.getPackagingCharge(),
                r.isOpenAt(clock.instant()));
    }

    private Details toDetails(Restaurant r) {
        return new Details(r, List.copyOf(r.getOpeningHours()), r.isOpenAt(clock.instant()));
    }

    /** A restaurant with its hours loaded, for the web layer. */
    public record Details(Restaurant restaurant, List<OpeningHours> openingHours, boolean openNow) {}

    public record SearchResult(Restaurant restaurant, Double distanceKm, boolean openNow) {}
}
