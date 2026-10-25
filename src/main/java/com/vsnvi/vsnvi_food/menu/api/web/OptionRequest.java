package com.vsnvi.vsnvi_food.menu.api.web;

import com.vsnvi.vsnvi_food.common.web.MoneyInput;
import com.vsnvi.vsnvi_food.menu.application.MenuService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** A variant (its price replaces the base price) or an add-on (its price is added). */
record OptionRequest(
        @NotBlank @Size(max = 60) @Schema(example = "Full") String name,
        @NotNull @Valid MoneyInput price) {

    static List<MenuService.OptionDraft> toDrafts(List<OptionRequest> options) {
        return options == null
                ? null
                : options.stream()
                        .map(o ->
                                new MenuService.OptionDraft(o.name(), o.price().toMoney()))
                        .toList();
    }
}
