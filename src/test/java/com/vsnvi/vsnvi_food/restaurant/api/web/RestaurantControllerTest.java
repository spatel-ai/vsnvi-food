package com.vsnvi.vsnvi_food.restaurant.api.web;

import static com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig;
import com.vsnvi.vsnvi_food.common.web.GlobalExceptionHandler;
import com.vsnvi.vsnvi_food.restaurant.application.RestaurantService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RestaurantController.class)
@Import({WebSecurityTestConfig.class, GlobalExceptionHandler.class})
class RestaurantControllerTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final String REGISTER_BODY = """
            {"name":"Spice Route","cuisines":["north-indian"],"addressLine":"1 MG Road","city":"Bengaluru",
             "latitude":12.97,"longitude":77.59,"serviceableRadiusKm":6,
             "deliveryFee":{"amount":"30.00","currency":"INR"},"packagingCharge":{"amount":"10.00","currency":"INR"}}
            """;

    private final MockMvc mvc;

    @MockitoBean
    RestaurantService restaurantService;

    RestaurantControllerTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void should_search_anonymously_and_return_money_as_string() throws Exception {
        var result = new RestaurantService.SearchResult(restaurant(), 2.345, true);
        given(restaurantService.search(any(), any()))
                .willReturn(new PageImpl<>(List.of(result), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/restaurants")
                        .param("lat", "12.97")
                        .param("lng", "77.59")
                        .param("cuisine", "north-indian"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Spice Route"))
                .andExpect(jsonPath("$.content[0].distanceKm").value(2.3))
                .andExpect(jsonPath("$.content[0].deliveryFee.amount").value("30.00"))
                .andExpect(jsonPath("$.content[0].rating").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void should_reject_search_when_only_lat_is_given() throws Exception {
        mvc.perform(get("/api/v1/restaurants").param("lat", "12.97"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void should_reject_search_when_cuisine_is_not_kebab_case() throws Exception {
        mvc.perform(get("/api/v1/restaurants").param("cuisine", "North Indian"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void should_return_details_anonymously_when_visible() throws Exception {
        given(restaurantService.details(RESTAURANT_ID))
                .willReturn(new RestaurantService.Details(restaurant(), List.of(), false));

        mvc.perform(get("/api/v1/restaurants/" + RESTAURANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void should_return_not_found_when_restaurant_is_hidden() throws Exception {
        given(restaurantService.details(RESTAURANT_ID)).willThrow(new NotFoundException("Restaurant"));

        mvc.perform(get("/api/v1/restaurants/" + RESTAURANT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_created_when_owner_registers_restaurant() throws Exception {
        given(restaurantService.register(any(), eq(Money.inr("30.00")), eq(Money.inr("10.00"))))
                .willReturn(new RestaurantService.Details(restaurant(), List.of(), false));

        mvc.perform(post("/api/v1/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"));
    }

    @Test
    void should_reject_registration_when_fee_is_negative_or_not_inr() throws Exception {
        mvc.perform(post("/api/v1/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY.replace(
                                "\"30.00\",\"currency\":\"INR\"", "\"-1.00\",\"currency\":\"USD\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void should_return_forbidden_when_customer_registers_restaurant() throws Exception {
        mvc.perform(post("/api/v1/restaurants")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_return_unauthorized_when_anonymous_registers_restaurant() throws Exception {
        mvc.perform(post("/api/v1/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return_not_found_when_owner_updates_someone_elses_restaurant() throws Exception {
        given(restaurantService.update(eq(RESTAURANT_ID), any(), any(), any()))
                .willThrow(new NotFoundException("Restaurant"));

        mvc.perform(patch("/api/v1/restaurants/" + RESTAURANT_ID)
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryFee\":{\"amount\":\"25.00\",\"currency\":\"INR\"}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_reject_opening_hours_when_slot_is_empty() throws Exception {
        mvc.perform(
                        put("/api/v1/restaurants/" + RESTAURANT_ID + "/opening-hours")
                                .header(HttpHeaders.AUTHORIZATION, owner())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"slots\":[{\"dayOfWeek\":\"MONDAY\",\"opensAt\":\"11:00\",\"closesAt\":\"11:00\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void should_set_state_from_body_when_toggling_open() throws Exception {
        given(restaurantService.switchOpen(RESTAURANT_ID, false))
                .willReturn(new RestaurantService.Details(restaurant(), List.of(), false));

        mvc.perform(post("/api/v1/restaurants/" + RESTAURANT_ID + "/toggle-open")
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"open\":false}"))
                .andExpect(status().isOk());
        then(restaurantService).should().switchOpen(RESTAURANT_ID, false);
    }

    @Test
    void should_route_me_to_owner_list_not_public_details() throws Exception {
        given(restaurantService.mine()).willReturn(List.of());

        mvc.perform(get("/api/v1/restaurants/me").header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/restaurants/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/restaurants/me").header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", OWNER_ID)))
                .andExpect(status().isForbidden());
    }

    private static String owner() {
        return bearer("RESTAURANT_OWNER", OWNER_ID);
    }

    private static Restaurant restaurant() {
        return Restaurant.register(
                OWNER_ID,
                new Restaurant.Profile(
                        "Spice Route",
                        null,
                        List.of("north-indian"),
                        "1 MG Road",
                        "Bengaluru",
                        12.97,
                        77.59,
                        6,
                        "Asia/Kolkata"),
                Money.inr("30"),
                Money.inr("10"));
    }
}
