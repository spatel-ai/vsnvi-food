package com.vsnvi.vsnvi_food.restaurant.infrastructure;

import com.vsnvi.vsnvi_food.restaurant.domain.Restaurant;
import com.vsnvi.vsnvi_food.restaurant.domain.RestaurantRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RestaurantJpaRepository extends JpaRepository<Restaurant, UUID>, RestaurantRepository {}
