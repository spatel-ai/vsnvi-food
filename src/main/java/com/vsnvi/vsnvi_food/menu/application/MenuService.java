package com.vsnvi.vsnvi_food.menu.application;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import com.vsnvi.vsnvi_food.common.security.CurrentUser;
import com.vsnvi.vsnvi_food.menu.api.MenuApi;
import com.vsnvi.vsnvi_food.menu.api.PricedItem;
import com.vsnvi.vsnvi_food.menu.domain.AddOn;
import com.vsnvi.vsnvi_food.menu.domain.CategoryNotEmptyException;
import com.vsnvi.vsnvi_food.menu.domain.MenuCategory;
import com.vsnvi.vsnvi_food.menu.domain.MenuCategoryRepository;
import com.vsnvi.vsnvi_food.menu.domain.MenuItem;
import com.vsnvi.vsnvi_food.menu.domain.MenuItemRepository;
import com.vsnvi.vsnvi_food.menu.domain.Variant;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantApi;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Menus are public for approved restaurants; owner actions check ownership through {@link RestaurantApi} and report
 * another owner's category or item as not found (404).
 */
@Service
@RequiredArgsConstructor
public class MenuService implements MenuApi {

    private final MenuCategoryRepository categories;
    private final MenuItemRepository items;
    private final RestaurantApi restaurantApi;
    private final CurrentUser currentUser;

    @Override
    @Transactional(readOnly = true)
    public List<PricedItem> getItemsForPricing(Collection<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        return items.findAllByIdIn(itemIds).stream()
                .map(MenuService::toPricedItem)
                .toList();
    }

    /** The full menu, grouped by category. Items out of stock are included and marked unavailable. */
    @Transactional(readOnly = true)
    public MenuView menuOf(UUID restaurantId) {
        requireVisible(restaurantId);
        var itemsByCategory = items.findAllByRestaurantIdOrderByNameAsc(restaurantId).stream()
                .collect(Collectors.groupingBy(MenuItem::getCategoryId));
        var sections = categories.findAllByRestaurantIdOrderByPositionAscNameAsc(restaurantId).stream()
                .map(c -> new CategoryView(
                        c.getId(),
                        c.getName(),
                        c.getPosition(),
                        itemsByCategory.getOrDefault(c.getId(), List.of()).stream()
                                .map(MenuService::toView)
                                .toList()))
                .toList();
        return new MenuView(restaurantId, sections);
    }

    @Transactional(readOnly = true)
    public ItemView item(UUID itemId) {
        var item = items.findById(itemId).orElseThrow(() -> new NotFoundException("Menu item " + itemId));
        requireVisible(item.getRestaurantId());
        return toView(item);
    }

    @Transactional
    public CategoryView createCategory(UUID restaurantId, String name, Integer position) {
        requireOwner(restaurantId);
        var category = categories.save(MenuCategory.create(
                restaurantId, name, position != null ? position : categories.countByRestaurantId(restaurantId)));
        return new CategoryView(category.getId(), category.getName(), category.getPosition(), List.of());
    }

    @Transactional
    public CategoryView updateCategory(UUID categoryId, String name, Integer position) {
        var category = ownedCategory(categoryId);
        if (name != null) {
            category.rename(name);
        }
        if (position != null) {
            category.moveTo(position);
        }
        return new CategoryView(category.getId(), category.getName(), category.getPosition(), List.of());
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        var category = ownedCategory(categoryId);
        if (items.existsByCategoryId(categoryId)) {
            throw new CategoryNotEmptyException();
        }
        categories.delete(category);
    }

    @Transactional
    public ItemView createItem(UUID restaurantId, ItemDraft draft) {
        requireOwner(restaurantId);
        requireCategoryOf(restaurantId, draft.categoryId());
        var item = items.save(MenuItem.create(
                restaurantId,
                draft.categoryId(),
                draft.name(),
                draft.description(),
                draft.price(),
                draft.vegetarian(),
                toVariants(draft.variants()),
                toAddOns(draft.addOns())));
        return toView(item);
    }

    /** Null fields stay unchanged; a non-null {@code variants} or {@code addOns} list replaces the existing one. */
    @Transactional
    public ItemView updateItem(UUID itemId, ItemPatch patch) {
        var item = ownedItem(itemId);
        item.rename(patch.name(), patch.description());
        if (patch.price() != null) {
            item.reprice(patch.price());
        }
        if (patch.vegetarian() != null) {
            item.markVegetarian(patch.vegetarian());
        }
        if (patch.categoryId() != null) {
            requireCategoryOf(item.getRestaurantId(), patch.categoryId());
            item.moveToCategory(patch.categoryId());
        }
        if (patch.variants() != null) {
            item.replaceVariants(toVariants(patch.variants()));
        }
        if (patch.addOns() != null) {
            item.replaceAddOns(toAddOns(patch.addOns()));
        }
        return toView(item);
    }

    @Transactional
    public ItemView setAvailability(UUID itemId, boolean available) {
        var item = ownedItem(itemId);
        item.setAvailability(available);
        return toView(item);
    }

    @Transactional
    public void deleteItem(UUID itemId) {
        items.delete(ownedItem(itemId));
    }

    private void requireVisible(UUID restaurantId) {
        var visible = restaurantApi
                .findById(restaurantId)
                .filter(r -> r.status() == RestaurantStatus.APPROVED || callerMayManage(restaurantId))
                .isPresent();
        if (!visible) {
            throw new NotFoundException("Restaurant " + restaurantId);
        }
    }

    private boolean callerMayManage(UUID restaurantId) {
        return currentUser
                .ifAuthenticated()
                .map(user -> "ADMIN".equals(user.role()) || restaurantApi.isOwnedBy(restaurantId, user.id()))
                .orElse(false);
    }

    private void requireOwner(UUID restaurantId) {
        if (!restaurantApi.isOwnedBy(restaurantId, currentUser.id())) {
            throw new NotFoundException("Restaurant " + restaurantId);
        }
    }

    private MenuCategory ownedCategory(UUID categoryId) {
        return categories
                .findById(categoryId)
                .filter(c -> restaurantApi.isOwnedBy(c.getRestaurantId(), currentUser.id()))
                .orElseThrow(() -> new NotFoundException("Menu category " + categoryId));
    }

    private MenuItem ownedItem(UUID itemId) {
        return items.findById(itemId)
                .filter(i -> restaurantApi.isOwnedBy(i.getRestaurantId(), currentUser.id()))
                .orElseThrow(() -> new NotFoundException("Menu item " + itemId));
    }

    private void requireCategoryOf(UUID restaurantId, UUID categoryId) {
        var ok = categories
                .findById(categoryId)
                .filter(c -> c.getRestaurantId().equals(restaurantId))
                .isPresent();
        if (!ok) {
            throw new ValidationException("categoryId must be a category of this restaurant");
        }
    }

    private static List<Variant> toVariants(List<OptionDraft> drafts) {
        return drafts == null
                ? List.of()
                : drafts.stream().map(d -> Variant.of(d.name(), d.price())).toList();
    }

    private static List<AddOn> toAddOns(List<OptionDraft> drafts) {
        return drafts == null
                ? List.of()
                : drafts.stream().map(d -> AddOn.of(d.name(), d.price())).toList();
    }

    private static ItemView toView(MenuItem item) {
        return new ItemView(
                item.getId(),
                item.getRestaurantId(),
                item.getCategoryId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                item.isVegetarian(),
                item.isAvailable(),
                item.getVariants().stream()
                        .map(v -> new OptionView(v.getId(), v.getName(), v.getPrice()))
                        .toList(),
                item.getAddOns().stream()
                        .map(a -> new OptionView(a.getId(), a.getName(), a.getPrice()))
                        .toList());
    }

    private static PricedItem toPricedItem(MenuItem item) {
        return new PricedItem(
                item.getId(),
                item.getRestaurantId(),
                item.getName(),
                item.getPrice(),
                item.isAvailable(),
                item.getVariants().stream()
                        .map(v -> new PricedItem.Option(v.getId(), v.getName(), v.getPrice()))
                        .toList(),
                item.getAddOns().stream()
                        .map(a -> new PricedItem.Option(a.getId(), a.getName(), a.getPrice()))
                        .toList());
    }

    public record OptionDraft(String name, Money price) {}

    public record ItemDraft(
            UUID categoryId,
            String name,
            String description,
            Money price,
            boolean vegetarian,
            List<OptionDraft> variants,
            List<OptionDraft> addOns) {}

    public record ItemPatch(
            UUID categoryId,
            String name,
            String description,
            Money price,
            Boolean vegetarian,
            List<OptionDraft> variants,
            List<OptionDraft> addOns) {}

    /** Fully loaded views, built inside the transaction (open-in-view is off). */
    public record MenuView(UUID restaurantId, List<CategoryView> categories) {}

    public record CategoryView(UUID id, String name, int position, List<ItemView> items) {}

    public record ItemView(
            UUID id,
            UUID restaurantId,
            UUID categoryId,
            String name,
            String description,
            Money price,
            boolean vegetarian,
            boolean available,
            List<OptionView> variants,
            List<OptionView> addOns) {}

    public record OptionView(UUID id, String name, Money price) {}
}
