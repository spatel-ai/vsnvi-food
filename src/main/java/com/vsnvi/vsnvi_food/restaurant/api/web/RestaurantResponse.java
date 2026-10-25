package com.vsnvi.vsnvi_food.restaurant.api.web;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantStatus;
import com.vsnvi.vsnvi_food.restaurant.application.RestaurantService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

record RestaurantResponse(
        UUID id,
        String name,
        String description,
        List<String> cuisines,
        String addressLine,
        String city,
        double latitude,
        double longitude,
        double serviceableRadiusKm,
        String timeZone,
        Money deliveryFee,
        Money packagingCharge,
        RestaurantStatus status,
        boolean temporarilyClosed,
        boolean openNow,
        BigDecimal rating,
        List<Slot> openingHours) {

    static RestaurantResponse from(RestaurantService.Details details) {
        var r = details.restaurant();
        return new RestaurantResponse(
                r.getId(),
                r.getName(),
                r.getDescription(),
                r.getCuisines(),
                r.getAddressLine(),
                r.getCity(),
                r.getLatitude(),
                r.getLongitude(),
                r.getServiceableRadiusKm(),
                r.getTimeZone(),
                r.getDeliveryFee(),
                r.getPackagingCharge(),
                r.getStatus(),
                r.isTemporarilyClosed(),
                details.openNow(),
                r.getRating(),
                details.openingHours().stream()
                        .map(h -> new Slot(h.day(), h.getOpensAt(), h.getClosesAt()))
                        .toList());
    }

    record Slot(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {}
}
