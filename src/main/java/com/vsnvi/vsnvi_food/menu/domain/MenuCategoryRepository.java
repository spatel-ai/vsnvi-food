package com.vsnvi.vsnvi_food.menu.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuCategoryRepository {

    MenuCategory save(MenuCategory category);

    void delete(MenuCategory category);

    Optional<MenuCategory> findById(UUID id);

    List<MenuCategory> findAllByRestaurantIdOrderByPositionAscNameAsc(UUID restaurantId);

    int countByRestaurantId(UUID restaurantId);
}
