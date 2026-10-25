package com.vsnvi.vsnvi_food.menu.infrastructure;

import com.vsnvi.vsnvi_food.menu.domain.MenuCategory;
import com.vsnvi.vsnvi_food.menu.domain.MenuCategoryRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MenuCategoryJpaRepository extends JpaRepository<MenuCategory, UUID>, MenuCategoryRepository {}
