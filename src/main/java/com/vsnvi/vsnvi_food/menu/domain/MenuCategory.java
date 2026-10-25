package com.vsnvi.vsnvi_food.menu.domain;

import com.vsnvi.vsnvi_food.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "menu_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuCategory extends BaseEntity {

    private UUID restaurantId;
    private String name;
    private int position;

    public static MenuCategory create(UUID restaurantId, String name, int position) {
        var category = new MenuCategory();
        category.restaurantId = Objects.requireNonNull(restaurantId, "restaurantId");
        category.rename(name);
        category.position = position;
        return category;
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name.strip();
    }

    public void moveTo(int position) {
        this.position = position;
    }
}
