package com.vsnvi.vsnvi_food.menu.infrastructure;

import com.vsnvi.vsnvi_food.menu.domain.MenuItem;
import com.vsnvi.vsnvi_food.menu.domain.MenuItemRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MenuItemJpaRepository extends JpaRepository<MenuItem, UUID>, MenuItemRepository {}
