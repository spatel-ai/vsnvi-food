package com.vsnvi.vsnvi_food.restaurant.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Public search over approved restaurants. Implemented with SQL in {@code infrastructure}. */
public interface RestaurantSearch {

    /** Sort properties a client may request. {@code distance} needs a location. */
    String SORT_DISTANCE = "distance";

    String SORT_RATING = "rating";
    String SORT_NAME = "name";

    Page<Hit> search(Criteria criteria, Instant now, Pageable pageable);

    /**
     * All fields optional. With {@code latitude}/{@code longitude}, only restaurants whose own serviceable radius
     * covers that point are returned.
     */
    record Criteria(
            Double latitude, Double longitude, String cuisine, BigDecimal minRating, Boolean isOpen, String query) {

        public boolean hasLocation() {
            return latitude != null && longitude != null;
        }
    }

    /** {@code distanceKm} is null without a location. */
    record Hit(UUID restaurantId, Double distanceKm, boolean openNow) {}
}
