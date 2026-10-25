package com.vsnvi.vsnvi_food.menu.api.web;

import com.vsnvi.vsnvi_food.common.web.MoneyInput;
import com.vsnvi.vsnvi_food.menu.application.MenuService;
import com.vsnvi.vsnvi_food.menu.domain.MenuItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Partial update. A given {@code variants} or {@code addOns} list replaces the whole list ({@code []} clears it). */
record UpdateMenuItemRequest(
        UUID categoryId,
        @Size(min = 1, max = 120) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Size(max = 1000) String description,
        @Valid MoneyInput price,
        Boolean vegetarian,
        @Size(max = MenuItem.MAX_VARIANTS) List<@NotNull @Valid OptionRequest> variants,
        @Size(max = MenuItem.MAX_ADD_ONS) List<@NotNull @Valid OptionRequest> addOns) {

    MenuService.ItemPatch toPatch() {
        return new MenuService.ItemPatch(
                categoryId,
                name,
                description,
                price == null ? null : price.toMoney(),
                vegetarian,
                OptionRequest.toDrafts(variants),
                OptionRequest.toDrafts(addOns));
    }
}
