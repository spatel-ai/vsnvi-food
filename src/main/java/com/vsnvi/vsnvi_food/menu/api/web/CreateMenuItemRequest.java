package com.vsnvi.vsnvi_food.menu.api.web;

import com.vsnvi.vsnvi_food.common.web.MoneyInput;
import com.vsnvi.vsnvi_food.menu.application.MenuService;
import com.vsnvi.vsnvi_food.menu.domain.MenuItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

record CreateMenuItemRequest(
        @NotNull UUID categoryId,

        @NotBlank @Size(max = 120) @Schema(example = "Chicken Biryani")
        String name,

        @Size(max = 1000) String description,

        @NotNull @Valid @Schema(description = "Base price; a chosen variant's price replaces it")
        MoneyInput price,

        @NotNull Boolean vegetarian,
        @Size(max = MenuItem.MAX_VARIANTS) List<@NotNull @Valid OptionRequest> variants,
        @Size(max = MenuItem.MAX_ADD_ONS) List<@NotNull @Valid OptionRequest> addOns) {

    MenuService.ItemDraft toDraft() {
        return new MenuService.ItemDraft(
                categoryId,
                name,
                description,
                price.toMoney(),
                vegetarian,
                OptionRequest.toDrafts(variants),
                OptionRequest.toDrafts(addOns));
    }
}
