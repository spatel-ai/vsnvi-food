package com.vsnvi.vsnvi_food.menu.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemRepository {

    MenuItem save(MenuItem item);

    /** Flushes too, so variants and add-ons added to a managed item get their IDs. */
    MenuItem saveAndFlush(MenuItem item);

    void delete(MenuItem item);

    Optional<MenuItem> findById(UUID id);

    List<MenuItem> findAllByRestaurantIdOrderByNameAsc(UUID restaurantId);

    List<MenuItem> findAllByIdIn(Collection<UUID> ids);

    boolean existsByCategoryId(UUID categoryId);
}
