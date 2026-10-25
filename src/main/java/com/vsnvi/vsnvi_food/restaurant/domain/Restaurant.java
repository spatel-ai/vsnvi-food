package com.vsnvi.vsnvi_food.restaurant.domain;

import com.vsnvi.vsnvi_food.common.domain.BaseEntity;
import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The restaurant aggregate: profile, location, fees, approval status and opening hours. */
@Entity
@Table(name = "restaurants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Restaurant extends BaseEntity {

    public static final double MAX_RADIUS_KM = 30;

    private UUID ownerId;
    private String name;
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> cuisines;

    private String addressLine;
    private String city;
    private double latitude;
    private double longitude;
    private double serviceableRadiusKm;
    private String timeZone;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "delivery_fee_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "delivery_fee_currency"))
    })
    private Money deliveryFee;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "packaging_charge_amount")),
        @AttributeOverride(name = "currency", column = @Column(name = "packaging_charge_currency"))
    })
    private Money packagingCharge;

    @Enumerated(EnumType.STRING)
    private RestaurantStatus status;

    private boolean temporarilyClosed;

    /** Average rating, null until rated. Written by the future review flow. */
    private BigDecimal rating;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @OrderBy("dayOfWeek, opensAt")
    private List<OpeningHours> openingHours = new ArrayList<>();

    public static Restaurant register(UUID ownerId, Profile profile, Money deliveryFee, Money packagingCharge) {
        var restaurant = new Restaurant();
        restaurant.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        restaurant.applyProfile(profile);
        restaurant.changeCharges(deliveryFee, packagingCharge);
        restaurant.status = RestaurantStatus.PENDING_APPROVAL;
        restaurant.temporarilyClosed = false;
        return restaurant;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    public boolean isVisibleToPublic() {
        return status == RestaurantStatus.APPROVED;
    }

    /** Open for orders: approved, not switched off by the owner, and inside an opening slot. */
    public boolean isOpenAt(Instant instant) {
        if (status != RestaurantStatus.APPROVED || temporarilyClosed) {
            return false;
        }
        var local = instant.atZone(ZoneId.of(timeZone)).toLocalDateTime();
        return openingHours.stream().anyMatch(slot -> slot.covers(local));
    }

    public void applyProfile(Profile profile) {
        if (profile.serviceableRadiusKm() <= 0 || profile.serviceableRadiusKm() > MAX_RADIUS_KM) {
            throw new IllegalArgumentException("serviceableRadiusKm out of range");
        }
        ZoneId.of(profile.timeZone()); // rejects unknown zones
        this.name = profile.name().strip();
        this.description = profile.description();
        this.cuisines = normalize(profile.cuisines());
        this.addressLine = profile.addressLine();
        this.city = profile.city();
        this.latitude = profile.latitude();
        this.longitude = profile.longitude();
        this.serviceableRadiusKm = profile.serviceableRadiusKm();
        this.timeZone = profile.timeZone();
    }

    /** Partial update: null fields keep their current value. */
    public void patchProfile(ProfilePatch patch) {
        applyProfile(new Profile(
                patch.name() != null ? patch.name() : name,
                patch.description() != null ? patch.description() : description,
                patch.cuisines() != null ? patch.cuisines() : cuisines,
                patch.addressLine() != null ? patch.addressLine() : addressLine,
                patch.city() != null ? patch.city() : city,
                patch.latitude() != null ? patch.latitude() : latitude,
                patch.longitude() != null ? patch.longitude() : longitude,
                patch.serviceableRadiusKm() != null ? patch.serviceableRadiusKm() : serviceableRadiusKm,
                patch.timeZone() != null ? patch.timeZone() : timeZone));
    }

    public void changeCharges(Money deliveryFee, Money packagingCharge) {
        if (deliveryFee.isNegative() || packagingCharge.isNegative()) {
            throw new IllegalArgumentException("charges must not be negative");
        }
        this.deliveryFee = deliveryFee;
        this.packagingCharge = packagingCharge;
    }

    public void replaceOpeningHours(Collection<OpeningHours> slots) {
        openingHours.clear();
        openingHours.addAll(slots);
    }

    /** The owner's switch. Idempotent. */
    public void switchOpen(boolean open) {
        temporarilyClosed = !open;
    }

    /** From pending (first approval) or suspended (reinstatement). Idempotent when already approved. */
    public void approve() {
        if (status == RestaurantStatus.APPROVED) {
            return;
        }
        status = RestaurantStatus.APPROVED;
    }

    /** Only an approved restaurant can be suspended; a pending one is simply not approved. Idempotent. */
    public void suspend() {
        if (status == RestaurantStatus.SUSPENDED) {
            return;
        }
        if (status != RestaurantStatus.APPROVED) {
            throw new InvalidRestaurantStateException(status, "suspend");
        }
        status = RestaurantStatus.SUSPENDED;
    }

    private static List<String> normalize(List<String> cuisines) {
        return cuisines.stream()
                .map(c -> c.strip().toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }

    /** Everything an owner edits about the restaurant except charges, status and hours. */
    public record Profile(
            String name,
            String description,
            List<String> cuisines,
            String addressLine,
            String city,
            double latitude,
            double longitude,
            double serviceableRadiusKm,
            String timeZone) {}

    /** {@link Profile} with every field optional. */
    public record ProfilePatch(
            String name,
            String description,
            List<String> cuisines,
            String addressLine,
            String city,
            Double latitude,
            Double longitude,
            Double serviceableRadiusKm,
            String timeZone) {}
}
