package com.vsnvi.vsnvi_food.restaurant.api.web;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.web.MoneyInput;
import com.vsnvi.vsnvi_food.common.web.PageResponse;
import com.vsnvi.vsnvi_food.restaurant.application.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
@Validated
@Tag(name = "restaurant", description = "Restaurant search, details and owner management")
class RestaurantController {

    private final RestaurantService restaurantService;

    @GetMapping
    @SecurityRequirements
    @Operation(
            summary = "Search approved restaurants that deliver to a location",
            operationId = "searchRestaurants",
            description = "Sort by `distance` (needs lat and lng; the default with a location), `rating` or `name`.")
    PageResponse<RestaurantCardResponse> search(
            @Valid @ParameterObject SearchRestaurantsRequest request, @ParameterObject Pageable pageable) {
        return PageResponse.from(
                restaurantService.search(request.toCriteria(), pageable), RestaurantCardResponse::from);
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Get a restaurant's details and opening hours", operationId = "getRestaurant")
    RestaurantResponse get(@PathVariable UUID id) {
        return RestaurantResponse.from(restaurantService.details(id));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "List the caller's own restaurants", operationId = "listMyRestaurants")
    List<RestaurantResponse> mine() {
        return restaurantService.mine().stream().map(RestaurantResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "Register a restaurant; it starts pending approval", operationId = "registerRestaurant")
    ResponseEntity<RestaurantResponse> register(@Valid @RequestBody RegisterRestaurantRequest request) {
        var details = restaurantService.register(
                request.toProfile(),
                request.deliveryFee().toMoney(),
                request.packagingCharge().toMoney());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(details.restaurant().getId())
                .toUri();
        return ResponseEntity.created(location).body(RestaurantResponse.from(details));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "Update your own restaurant, including fees", operationId = "updateRestaurant")
    RestaurantResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRestaurantRequest request) {
        return RestaurantResponse.from(restaurantService.update(
                id, request.toPatch(), toMoneyOrNull(request.deliveryFee()), toMoneyOrNull(request.packagingCharge())));
    }

    @PutMapping("/{id}/opening-hours")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "Replace your restaurant's opening hours", operationId = "setOpeningHours")
    RestaurantResponse setOpeningHours(@PathVariable UUID id, @Valid @RequestBody SetOpeningHoursRequest request) {
        return RestaurantResponse.from(restaurantService.replaceOpeningHours(id, request.toSlots()));
    }

    @PostMapping("/{id}/toggle-open")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(
            summary = "Temporarily open or close your restaurant",
            operationId = "toggleRestaurantOpen",
            description = "Sets the state given in the body, so repeating the call is safe.")
    RestaurantResponse toggleOpen(@PathVariable UUID id, @Valid @RequestBody ToggleOpenRequest request) {
        return RestaurantResponse.from(restaurantService.switchOpen(id, request.open()));
    }

    private static Money toMoneyOrNull(MoneyInput input) {
        return input == null ? null : input.toMoney();
    }
}
