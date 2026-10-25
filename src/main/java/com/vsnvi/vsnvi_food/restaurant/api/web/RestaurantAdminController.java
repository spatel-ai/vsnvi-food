package com.vsnvi.vsnvi_food.restaurant.api.web;

import com.vsnvi.vsnvi_food.common.web.PageResponse;
import com.vsnvi.vsnvi_food.common.web.Pageables;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantStatus;
import com.vsnvi.vsnvi_food.restaurant.application.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/restaurants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "restaurant", description = "Restaurant search, details and owner management")
class RestaurantAdminController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "name");

    private final RestaurantService restaurantService;

    @GetMapping
    @Operation(summary = "List restaurants, filtered by status", operationId = "listRestaurantsForAdmin")
    PageResponse<RestaurantResponse> list(
            @RequestParam(required = false) RestaurantStatus status,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        var page = restaurantService.list(status, Pageables.requireSortableBy(pageable, SORTABLE));
        return PageResponse.from(page, RestaurantResponse::from);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending or suspended restaurant", operationId = "approveRestaurant")
    RestaurantResponse approve(@PathVariable UUID id) {
        return RestaurantResponse.from(restaurantService.approve(id));
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend an approved restaurant", operationId = "suspendRestaurant")
    RestaurantResponse suspend(@PathVariable UUID id) {
        return RestaurantResponse.from(restaurantService.suspend(id));
    }
}
