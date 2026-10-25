package com.vsnvi.vsnvi_food.menu.api.web;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.menu.application.MenuService;
import java.util.List;
import java.util.UUID;

record MenuItemResponse(
        UUID id,
        UUID restaurantId,
        UUID categoryId,
        String name,
        String description,
        Money price,
        boolean vegetarian,
        boolean available,
        List<OptionResponse> variants,
        List<OptionResponse> addOns) {

    static MenuItemResponse from(MenuService.ItemView item) {
        return new MenuItemResponse(
                item.id(),
                item.restaurantId(),
                item.categoryId(),
                item.name(),
                item.description(),
                item.price(),
                item.vegetarian(),
                item.available(),
                item.variants().stream().map(OptionResponse::from).toList(),
                item.addOns().stream().map(OptionResponse::from).toList());
    }

    record OptionResponse(UUID id, String name, Money price) {

        static OptionResponse from(MenuService.OptionView option) {
            return new OptionResponse(option.id(), option.name(), option.price());
        }
    }
}
