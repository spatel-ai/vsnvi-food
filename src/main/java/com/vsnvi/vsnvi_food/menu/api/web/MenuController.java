package com.vsnvi.vsnvi_food.menu.api.web;

import com.vsnvi.vsnvi_food.menu.application.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "menu", description = "Menus, categories, items, variants and add-ons")
class MenuController {

    private final MenuService menuService;

    @GetMapping("/restaurants/{restaurantId}/menu")
    @SecurityRequirements
    @Operation(summary = "Get a restaurant's full menu, grouped by category", operationId = "getMenu")
    MenuResponse menu(@PathVariable UUID restaurantId) {
        return MenuResponse.from(menuService.menuOf(restaurantId));
    }

    @GetMapping("/menu-items/{id}")
    @SecurityRequirements
    @Operation(summary = "Get a menu item with its variants and add-ons", operationId = "getMenuItem")
    MenuItemResponse item(@PathVariable UUID id) {
        return MenuItemResponse.from(menuService.item(id));
    }

    @PostMapping("/restaurants/{restaurantId}/menu-items")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "Add an item with its variants and add-ons", operationId = "createMenuItem")
    ResponseEntity<MenuItemResponse> create(
            @PathVariable UUID restaurantId, @Valid @RequestBody CreateMenuItemRequest request) {
        var item = menuService.createItem(restaurantId, request.toDraft());
        return ResponseEntity.created(URI.create("/api/v1/menu-items/" + item.id()))
                .body(MenuItemResponse.from(item));
    }

    @PatchMapping("/menu-items/{id}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(
            summary = "Update an item; given variant or add-on lists replace the old ones",
            operationId = "updateMenuItem")
    MenuItemResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateMenuItemRequest request) {
        return MenuItemResponse.from(menuService.updateItem(id, request.toPatch()));
    }

    @PostMapping("/menu-items/{id}/availability")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "Mark an item in stock or out of stock", operationId = "setMenuItemAvailability")
    MenuItemResponse availability(@PathVariable UUID id, @Valid @RequestBody SetAvailabilityRequest request) {
        return MenuItemResponse.from(menuService.setAvailability(id, request.available()));
    }

    @DeleteMapping("/menu-items/{id}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "Delete an item", operationId = "deleteMenuItem")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        menuService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }
}
