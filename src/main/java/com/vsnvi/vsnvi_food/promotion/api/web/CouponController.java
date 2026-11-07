package com.vsnvi.vsnvi_food.promotion.api.web;

import com.vsnvi.vsnvi_food.common.web.PageResponse;
import com.vsnvi.vsnvi_food.common.web.Pageables;
import com.vsnvi.vsnvi_food.promotion.application.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "promotion", description = "Platform and restaurant coupons")
class CouponController {

    static final Set<String> SORTABLE = Set.of("createdAt", "code", "validUntil");

    private final CouponService couponService;

    @GetMapping("/coupons/available")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Coupons you can use at a restaurant now; first-order coupons are flagged",
            operationId = "listAvailableCoupons")
    List<AvailableCouponResponse> available(@RequestParam UUID restaurantId) {
        return couponService.availableAt(restaurantId).stream()
                .map(AvailableCouponResponse::from)
                .toList();
    }

    @PostMapping("/restaurants/{restaurantId}/coupons")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "Create a coupon for your restaurant", operationId = "createRestaurantCoupon")
    ResponseEntity<CouponResponse> create(
            @PathVariable UUID restaurantId, @Valid @RequestBody CreateCouponRequest request) {
        var coupon = couponService.createRestaurantCoupon(restaurantId, request.toDraft());
        return ResponseEntity.created(URI.create("/api/v1/coupons/" + coupon.id()))
                .body(CouponResponse.from(coupon));
    }

    @GetMapping("/restaurants/{restaurantId}/coupons")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @Operation(summary = "List your restaurant's coupons", operationId = "listRestaurantCoupons")
    PageResponse<CouponResponse> list(
            @PathVariable UUID restaurantId,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = couponService.listForRestaurant(restaurantId, Pageables.requireSortableBy(pageable, SORTABLE));
        return PageResponse.from(page, CouponResponse::from);
    }

    @GetMapping("/coupons/{id}")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    @Operation(summary = "Get a coupon (owner of its restaurant, or admin)", operationId = "getCoupon")
    CouponResponse get(@PathVariable UUID id) {
        return CouponResponse.from(couponService.get(id));
    }

    @PatchMapping("/coupons/{id}")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    @Operation(
            summary = "Update a coupon (owner of its restaurant, or admin); code and type are fixed",
            operationId = "updateCoupon")
    CouponResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCouponRequest request) {
        return CouponResponse.from(couponService.update(id, request.toPatch()));
    }

    @PostMapping("/coupons/{id}/deactivate")
    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    @Operation(summary = "Deactivate a coupon (owner of its restaurant, or admin)", operationId = "deactivateCoupon")
    CouponResponse deactivate(@PathVariable UUID id) {
        return CouponResponse.from(couponService.deactivate(id));
    }
}
