package com.vsnvi.vsnvi_food.menu.api.web;

import com.vsnvi.vsnvi_food.menu.application.MenuService;
import java.util.List;
import java.util.UUID;

record MenuCategoryResponse(UUID id, String name, int position, List<MenuItemResponse> items) {

    static MenuCategoryResponse from(MenuService.CategoryView category) {
        return new MenuCategoryResponse(
                category.id(),
                category.name(),
                category.position(),
                category.items().stream().map(MenuItemResponse::from).toList());
    }
}
