package com.vsnvi.vsnvi_food.user.api;

import java.util.UUID;

/** Facade view of an address, e.g. for the order's drop-off snapshot. Never log it. */
public record AddressSummary(
        UUID id,
        UUID userId,
        String label,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        double latitude,
        double longitude) {}
