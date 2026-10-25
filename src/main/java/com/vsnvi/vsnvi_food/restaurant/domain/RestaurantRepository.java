package com.vsnvi.vsnvi_food.restaurant.domain;

import com.vsnvi.vsnvi_food.restaurant.api.RestaurantStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RestaurantRepository {

    Restaurant save(Restaurant restaurant);

    Optional<Restaurant> findById(UUID id);

    List<Restaurant> findAllById(Iterable<UUID> ids);

    /** The ownership check: empty for someone else's restaurant. */
    Optional<Restaurant> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Restaurant> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    /** Admin listing; a null status matches everything. */
    Page<Restaurant> findAllByStatusIn(Collection<RestaurantStatus> statuses, Pageable pageable);
}
