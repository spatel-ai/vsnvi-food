package com.vsnvi.vsnvi_food.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.vsnvi.vsnvi_food.TestcontainersConfiguration;
import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import com.vsnvi.vsnvi_food.common.security.AuthenticatedUser;
import com.vsnvi.vsnvi_food.menu.api.MenuApi;
import com.vsnvi.vsnvi_food.menu.api.PricedItem;
import com.vsnvi.vsnvi_food.menu.application.MenuService;
import com.vsnvi.vsnvi_food.menu.domain.CategoryNotEmptyException;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantApi;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantStatus;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** {@code restaurant} is mocked through its facade; the module boots on its own (standalone mode). */
@ApplicationModuleTest(extraIncludes = "common")
@Import(TestcontainersConfiguration.class)
class MenuModuleTests {

    private final MenuService menuService;
    private final MenuApi menuApi;

    @MockitoBean
    RestaurantApi restaurantApi;

    private UUID ownerId;
    private UUID restaurantId;

    MenuModuleTests(@Autowired MenuService menuService, @Autowired MenuApi menuApi) {
        this.menuService = menuService;
        this.menuApi = menuApi;
    }

    @BeforeEach
    void ownRestaurant() {
        ownerId = UUID.randomUUID();
        restaurantId = UUID.randomUUID();
        given(restaurantApi.isOwnedBy(any(), any())).willReturn(false);
        given(restaurantApi.isOwnedBy(restaurantId, ownerId)).willReturn(true);
        given(restaurantApi.findById(restaurantId)).willReturn(Optional.of(restaurant(RestaurantStatus.APPROVED)));
        signInAs(ownerId, "RESTAURANT_OWNER");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_group_items_by_category_in_display_order_when_reading_menu() {
        var mains = menuService.createCategory(restaurantId, "Mains", 1);
        var starters = menuService.createCategory(restaurantId, "Starters", 0);
        menuService.createItem(restaurantId, draft(mains.id(), "Biryani"));
        menuService.createItem(restaurantId, draft(starters.id(), "Kebab"));
        SecurityContextHolder.clearContext();

        var menu = menuService.menuOf(restaurantId);

        assertThat(menu.categories()).extracting(MenuService.CategoryView::name).containsExactly("Starters", "Mains");
        assertThat(menu.categories().get(1).items())
                .extracting(MenuService.ItemView::name)
                .containsExactly("Biryani");
        assertThat(menu.categories().get(1).items().getFirst().variants()).hasSize(2);
    }

    @Test
    void should_return_prices_with_options_when_cart_asks_for_pricing() {
        var category = menuService.createCategory(restaurantId, "Mains", null);
        var item = menuService.createItem(restaurantId, draft(category.id(), "Biryani"));
        menuService.setAvailability(item.id(), false);

        var priced = menuApi.getItemsForPricing(List.of(item.id(), UUID.randomUUID()));

        assertThat(priced).singleElement().satisfies(p -> {
            assertThat(p.restaurantId()).isEqualTo(restaurantId);
            assertThat(p.basePrice()).isEqualTo(Money.inr("250.00"));
            assertThat(p.available()).isFalse();
            assertThat(p.requiresVariant()).isTrue();
            assertThat(p.variants())
                    .extracting(PricedItem.Option::price)
                    .containsExactly(Money.inr("180.00"), Money.inr("320.00"));
            assertThat(p.addOns()).extracting(PricedItem.Option::name).containsExactly("Raita");
        });
    }

    @Test
    void should_hide_menu_of_unapproved_restaurant_from_public_but_not_from_owner() {
        given(restaurantApi.findById(restaurantId))
                .willReturn(Optional.of(restaurant(RestaurantStatus.PENDING_APPROVAL)));

        assertThat(menuService.menuOf(restaurantId).categories()).isEmpty();
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> menuService.menuOf(restaurantId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void should_return_not_found_when_another_owner_edits_items() {
        var category = menuService.createCategory(restaurantId, "Mains", null);
        var item = menuService.createItem(restaurantId, draft(category.id(), "Biryani"));

        signInAs(UUID.randomUUID(), "RESTAURANT_OWNER");

        assertThatThrownBy(() -> menuService.setAvailability(item.id(), false)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> menuService.deleteCategory(category.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> menuService.createCategory(restaurantId, "Mine now", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void should_reject_category_of_another_restaurant_when_creating_item() {
        var otherRestaurant = UUID.randomUUID();
        given(restaurantApi.isOwnedBy(otherRestaurant, ownerId)).willReturn(true);
        var foreignCategory = menuService.createCategory(otherRestaurant, "Theirs", null);

        assertThatThrownBy(() -> menuService.createItem(restaurantId, draft(foreignCategory.id(), "Biryani")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void should_refuse_to_delete_category_until_it_is_empty() {
        var category = menuService.createCategory(restaurantId, "Mains", null);
        var item = menuService.createItem(restaurantId, draft(category.id(), "Biryani"));

        assertThatThrownBy(() -> menuService.deleteCategory(category.id()))
                .isInstanceOf(CategoryNotEmptyException.class);

        menuService.deleteItem(item.id());
        menuService.deleteCategory(category.id());
        assertThat(menuService.menuOf(restaurantId).categories()).isEmpty();
    }

    @Test
    void should_replace_options_only_when_lists_are_given_in_patch() {
        var category = menuService.createCategory(restaurantId, "Mains", null);
        var item = menuService.createItem(restaurantId, draft(category.id(), "Biryani"));

        var renamed = menuService.updateItem(
                item.id(), new MenuService.ItemPatch(null, "Dum Biryani", null, null, null, null, null));
        var noVariants = menuService.updateItem(
                item.id(), new MenuService.ItemPatch(null, null, null, Money.inr("270"), null, List.of(), null));

        assertThat(renamed.variants()).hasSize(2);
        assertThat(noVariants.variants()).isEmpty();
        assertThat(noVariants.addOns()).hasSize(1);
        assertThat(noVariants.name()).isEqualTo("Dum Biryani");
    }

    private static MenuService.ItemDraft draft(UUID categoryId, String name) {
        return new MenuService.ItemDraft(
                categoryId,
                name,
                null,
                Money.inr("250"),
                false,
                List.of(
                        new MenuService.OptionDraft("Half", Money.inr("180")),
                        new MenuService.OptionDraft("Full", Money.inr("320"))),
                List.of(new MenuService.OptionDraft("Raita", Money.inr("30"))));
    }

    private RestaurantSummary restaurant(RestaurantStatus status) {
        return new RestaurantSummary(
                restaurantId, ownerId, "R", status, 12.97, 77.59, 5, Money.inr("30"), Money.inr("10"), true);
    }

    private static void signInAs(UUID userId, String role) {
        var principal = new AuthenticatedUser(userId, role, Instant.now().plusSeconds(900));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
