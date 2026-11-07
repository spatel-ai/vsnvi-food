package com.vsnvi.vsnvi_food.promotion.api.web;

import com.vsnvi.vsnvi_food.common.web.PageResponse;
import com.vsnvi.vsnvi_food.common.web.Pageables;
import com.vsnvi.vsnvi_food.promotion.application.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "promotion", description = "Platform and restaurant coupons")
class CouponAdminController {

    private final CouponService couponService;

    @PostMapping
    @Operation(summary = "Create a platform coupon, valid at every restaurant", operationId = "createPlatformCoupon")
    ResponseEntity<CouponResponse> create(@Valid @RequestBody CreateCouponRequest request) {
        var coupon = couponService.createPlatformCoupon(request.toDraft());
        return ResponseEntity.created(URI.create("/api/v1/coupons/" + coupon.id()))
                .body(CouponResponse.from(coupon));
    }

    @GetMapping
    @Operation(summary = "List all coupons, platform and restaurant", operationId = "listCouponsForAdmin")
    PageResponse<CouponResponse> list(
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = couponService.listAll(Pageables.requireSortableBy(pageable, CouponController.SORTABLE));
        return PageResponse.from(page, CouponResponse::from);
    }
}
