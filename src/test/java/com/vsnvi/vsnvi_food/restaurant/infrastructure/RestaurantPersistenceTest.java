package com.vsnvi.vsnvi_food.restaurant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vsnvi.vsnvi_food.TestcontainersConfiguration;
import com.vsnvi.vsnvi_food.common.config.TimeConfig;
import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.restaurant.domain.Restaurant;
import com.vsnvi.vsnvi_food.restaurant.domain.RestaurantRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/** The V7 cuisines CHECK against real Postgres. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, TimeConfig.class})
class RestaurantPersistenceTest {

    private final RestaurantRepository restaurants;
    private final EntityManager entityManager;

    RestaurantPersistenceTest(@Autowired RestaurantRepository restaurants, @Autowired EntityManager entityManager) {
        this.restaurants = restaurants;
        this.entityManager = entityManager;
    }

    @Test
    void should_save_when_cuisine_is_at_max_length() {
        var saved = restaurants.save(restaurant("a".repeat(40)));
        entityManager.flush();

        assertThat(restaurants.findById(saved.getId())).isPresent();
    }

    @Test
    void should_reject_when_cuisine_exceeds_max_length() {
        restaurants.save(restaurant("a".repeat(41)));

        assertThatThrownBy(entityManager::flush).hasMessageContaining("chk_restaurants_cuisines");
    }

    @Test
    void should_save_when_ten_cuisines() {
        var saved = restaurants.save(restaurant("chinese"));
        entityManager.flush();

        setCuisines(saved.getId(), "{a,b,c,d,e,f,g,h,i,j}");

        entityManager.clear();
        assertThat(restaurants.findById(saved.getId()).orElseThrow().getCuisines())
                .hasSize(10);
    }

    // One case per test: a violation aborts the Postgres transaction.
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{a,b,c,d,e,f,g,h,i,j,k}", "{a--b}", "{-a}", "{-}", "{chinese,NULL}"})
    void should_reject_when_cuisines_break_the_check(String cuisinesLiteral) {
        var saved = restaurants.save(restaurant("chinese"));
        entityManager.flush();

        assertThatThrownBy(() -> setCuisines(saved.getId(), cuisinesLiteral))
                .hasMessageContaining("chk_restaurants_cuisines");
    }

    // Native SQL: the domain normalizes cuisines and can't hold a NULL element, so it can't reach these rows.
    private void setCuisines(UUID id, String cuisinesLiteral) {
        entityManager
                .createNativeQuery("UPDATE restaurants SET cuisines = CAST(:cuisines AS text[]) WHERE id = :id")
                .setParameter("cuisines", cuisinesLiteral)
                .setParameter("id", id)
                .executeUpdate();
    }

    private static Restaurant restaurant(String cuisine) {
        var profile = new Restaurant.Profile(
                "Spice Route", null, List.of(cuisine), "1 MG Road", "Bengaluru", 12.97, 77.59, 5, "Asia/Kolkata");
        return Restaurant.register(UUID.randomUUID(), profile, Money.inr("30"), Money.inr("10"));
    }
}
