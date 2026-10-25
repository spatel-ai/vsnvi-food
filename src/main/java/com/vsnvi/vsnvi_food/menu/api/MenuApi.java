package com.vsnvi.vsnvi_food.menu.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** The {@code menu} module's facade for modules above it in the graph. */
public interface MenuApi {

    /**
     * Current prices and availability of the given items. Unknown IDs are simply absent from the result, so callers
     * must check that every requested item came back.
     */
    List<PricedItem> getItemsForPricing(Collection<UUID> itemIds);
}
