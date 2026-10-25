package com.vsnvi.vsnvi_food.restaurant.api.web;

import static com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig;
import com.vsnvi.vsnvi_food.common.web.GlobalExceptionHandler;
import com.vsnvi.vsnvi_food.restaurant.api.RestaurantStatus;
import com.vsnvi.vsnvi_food.restaurant.application.RestaurantService;
import com.vsnvi.vsnvi_food.restaurant.domain.InvalidRestaurantStateException;
import com.vsnvi.vsnvi_food.restaurant.domain.Restaurant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RestaurantAdminController.class)
@Import({WebSecurityTestConfig.class, GlobalExceptionHandler.class})
class RestaurantAdminControllerTest {

    private static final UUID RESTAURANT_ID = UUID.randomUUID();

    private final MockMvc mvc;

    @MockitoBean
    RestaurantService restaurantService;

    RestaurantAdminControllerTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void should_list_pending_restaurants_when_admin() throws Exception {
        given(restaurantService.list(eq(RestaurantStatus.PENDING_APPROVAL), any()))
                .willReturn(new PageImpl<>(List.of(details()), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/admin/restaurants")
                        .param("status", "PENDING_APPROVAL")
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING_APPROVAL"));
    }

    @Test
    void should_approve_when_admin() throws Exception {
        given(restaurantService.approve(RESTAURANT_ID)).willReturn(details());

        mvc.perform(post("/api/v1/admin/restaurants/" + RESTAURANT_ID + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk());
    }

    @Test
    void should_return_conflict_when_suspending_pending_restaurant() throws Exception {
        given(restaurantService.suspend(RESTAURANT_ID))
                .willThrow(new InvalidRestaurantStateException(RestaurantStatus.PENDING_APPROVAL, "suspend"));

        mvc.perform(post("/api/v1/admin/restaurants/" + RESTAURANT_ID + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESTAURANT_STATE"));
    }

    @Test
    void should_return_not_found_when_approving_unknown_restaurant() throws Exception {
        given(restaurantService.approve(RESTAURANT_ID)).willThrow(new NotFoundException("Restaurant"));

        mvc.perform(post("/api/v1/admin/restaurants/" + RESTAURANT_ID + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_forbidden_when_owner_approves() throws Exception {
        mvc.perform(post("/api/v1/admin/restaurants/" + RESTAURANT_ID + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer("RESTAURANT_OWNER", UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_return_unauthorized_when_anonymous() throws Exception {
        mvc.perform(get("/api/v1/admin/restaurants")).andExpect(status().isUnauthorized());
    }

    private static String admin() {
        return bearer("ADMIN", UUID.randomUUID());
    }

    private static RestaurantService.Details details() {
        var restaurant = Restaurant.register(
                UUID.randomUUID(),
                new Restaurant.Profile("R", null, List.of("cafe"), "1 Rd", "Pune", 18.5, 73.8, 5, "Asia/Kolkata"),
                Money.inr("20"),
                Money.inr("5"));
        return new RestaurantService.Details(restaurant, List.of(), false);
    }
}
