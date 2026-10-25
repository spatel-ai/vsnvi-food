package com.vsnvi.vsnvi_food.common.web;

import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import java.util.Set;
import org.springframework.data.domain.Pageable;

/** Only documented sort fields are accepted (docs/API-DESIGN.md §5). Page size is capped by configuration. */
public final class Pageables {

    private Pageables() {}

    public static Pageable requireSortableBy(Pageable pageable, Set<String> allowed) {
        for (var order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new ValidationException("Unsupported sort field; allowed: " + String.join(", ", allowed));
            }
        }
        return pageable;
    }
}
