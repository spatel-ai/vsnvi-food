package com.vsnvi.vsnvi_food.menu.api.web;

import com.vsnvi.vsnvi_food.menu.application.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESTAURANT_OWNER')")
@Tag(name = "menu", description = "Menus, categories, items, variants and add-ons")
class MenuCategoryController {

    private final MenuService menuService;

    @PostMapping("/restaurants/{restaurantId}/menu-categories")
    @Operation(summary = "Create a menu category", operationId = "createMenuCategory")
    ResponseEntity<MenuCategoryResponse> create(
            @PathVariable UUID restaurantId, @Valid @RequestBody CreateMenuCategoryRequest request) {
        var category = menuService.createCategory(restaurantId, request.name(), request.position());
        return ResponseEntity.created(URI.create("/api/v1/menu-categories/" + category.id()))
                .body(MenuCategoryResponse.from(category));
    }

    @PatchMapping("/menu-categories/{id}")
    @Operation(summary = "Rename or reorder a category", operationId = "updateMenuCategory")
    MenuCategoryResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateMenuCategoryRequest request) {
        return MenuCategoryResponse.from(menuService.updateCategory(id, request.name(), request.position()));
    }

    @DeleteMapping("/menu-categories/{id}")
    @Operation(summary = "Delete an empty category", operationId = "deleteMenuCategory")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        menuService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
