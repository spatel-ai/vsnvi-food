package com.vsnvi.vsnvi_food.restaurant.domain;

import com.vsnvi.vsnvi_food.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One opening slot, as the half-open interval [opensAt, closesAt) in the restaurant's local time. A slot whose
 * {@code closesAt} is earlier than {@code opensAt} runs past midnight into the next day. A day may have several slots.
 */
@Entity
@Table(name = "opening_hours")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpeningHours extends BaseEntity {

    /** ISO day of week, 1 = Monday. */
    private short dayOfWeek;

    private LocalTime opensAt;
    private LocalTime closesAt;

    public static OpeningHours of(DayOfWeek day, LocalTime opensAt, LocalTime closesAt) {
        Objects.requireNonNull(day, "day");
        if (Objects.requireNonNull(opensAt, "opensAt").equals(Objects.requireNonNull(closesAt, "closesAt"))) {
            throw new IllegalArgumentException("opensAt and closesAt must differ");
        }
        var slot = new OpeningHours();
        slot.dayOfWeek = (short) day.getValue();
        slot.opensAt = opensAt;
        slot.closesAt = closesAt;
        return slot;
    }

    public DayOfWeek day() {
        return DayOfWeek.of(dayOfWeek);
    }

    public boolean runsPastMidnight() {
        return closesAt.isBefore(opensAt);
    }

    public boolean covers(LocalDateTime local) {
        var day = local.getDayOfWeek();
        var time = local.toLocalTime();
        if (!runsPastMidnight()) {
            return day == day() && !time.isBefore(opensAt) && time.isBefore(closesAt);
        }
        return (day == day() && !time.isBefore(opensAt)) || (day == day().plus(1) && time.isBefore(closesAt));
    }
}
