package com.vsnvi.vsnvi_food.user.domain;

import com.vsnvi.vsnvi_food.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A customer's delivery address with coordinates. Never log its fields. */
@Entity
@Table(name = "addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends BaseEntity {

    private UUID userId;
    private String label;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String postalCode;
    private double latitude;
    private double longitude;

    public static Address create(UUID userId, Details details) {
        var address = new Address();
        address.userId = Objects.requireNonNull(userId, "userId");
        address.replace(details);
        return address;
    }

    /** Full replace, as {@code PUT} does. */
    public void replace(Details details) {
        if (details.latitude() < -90 || details.latitude() > 90) {
            throw new IllegalArgumentException("latitude out of range");
        }
        if (details.longitude() < -180 || details.longitude() > 180) {
            throw new IllegalArgumentException("longitude out of range");
        }
        this.label = details.label();
        this.line1 = details.line1();
        this.line2 = details.line2();
        this.city = details.city();
        this.state = details.state();
        this.postalCode = details.postalCode();
        this.latitude = details.latitude();
        this.longitude = details.longitude();
    }

    public record Details(
            String label,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            double latitude,
            double longitude) {}
}
