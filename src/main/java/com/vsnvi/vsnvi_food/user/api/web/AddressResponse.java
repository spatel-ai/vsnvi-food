package com.vsnvi.vsnvi_food.user.api.web;

import com.vsnvi.vsnvi_food.user.api.AddressSummary;
import java.util.UUID;

record AddressResponse(
        UUID id,
        String label,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        double latitude,
        double longitude) {

    static AddressResponse from(AddressSummary a) {
        return new AddressResponse(
                a.id(),
                a.label(),
                a.line1(),
                a.line2(),
                a.city(),
                a.state(),
                a.postalCode(),
                a.latitude(),
                a.longitude());
    }
}
