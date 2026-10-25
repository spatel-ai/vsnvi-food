package com.vsnvi.vsnvi_food.menu.api.web;

import com.vsnvi.vsnvi_food.menu.application.MenuService;
import java.util.List;
import java.util.UUID;

record MenuResponse(UUID restaurantId, List<MenuCategoryResponse> categories) {

    static MenuResponse from(MenuService.MenuView menu) {
        return new MenuResponse(
                menu.restaurantId(),
                menu.categories().stream().map(MenuCategoryResponse::from).toList());
    }
}
