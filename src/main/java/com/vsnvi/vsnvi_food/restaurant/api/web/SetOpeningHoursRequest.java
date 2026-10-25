package com.vsnvi.vsnvi_food.restaurant.api.web;

import com.vsnvi.vsnvi_food.restaurant.domain.OpeningHours;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/** Full replace. An empty list means the restaurant is never open until hours are set again. */
record SetOpeningHoursRequest(@NotNull @Size(max = 28) List<@NotNull @Valid Slot> slots) {

    List<OpeningHours> toSlots() {
        return slots.stream()
                .map(s -> OpeningHours.of(s.dayOfWeek(), s.opensAt(), s.closesAt()))
                .toList();
    }

    /** {@code closesAt} earlier than {@code opensAt} means the slot runs past midnight. */
    record Slot(
            @NotNull @Schema(example = "MONDAY") DayOfWeek dayOfWeek,

            @NotNull @Schema(type = "string", example = "11:00")
            LocalTime opensAt,

            @NotNull @Schema(type = "string", example = "23:00")
            LocalTime closesAt) {

        @AssertTrue(message = "opensAt and closesAt must differ")
        @Schema(hidden = true)
        boolean isNonEmpty() {
            return opensAt == null || !opensAt.equals(closesAt);
        }
    }
}
