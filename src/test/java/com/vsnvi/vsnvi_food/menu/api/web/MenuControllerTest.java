package com.vsnvi.vsnvi_food.menu.api.web;

import static com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig;
import com.vsnvi.vsnvi_food.common.web.GlobalExceptionHandler;
import com.vsnvi.vsnvi_food.menu.application.MenuService;
import com.vsnvi.vsnvi_food.menu.domain.CategoryNotEmptyException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({MenuController.class, MenuCategoryController.class})
@Import({WebSecurityTestConfig.class, GlobalExceptionHandler.class})
class MenuControllerTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final MenuService.ItemView ITEM = new MenuService.ItemView(
            ITEM_ID,
            RESTAURANT_ID,
            CATEGORY_ID,
            "Biryani",
            null,
            Money.inr("250"),
            false,
            true,
            List.of(new MenuService.OptionView(UUID.randomUUID(), "Full", Money.inr("320"))),
            List.of());
    private static final String CREATE_BODY = """
            {"categoryId":"%s","name":"Biryani","price":{"amount":"250.00","currency":"INR"},"vegetarian":false,
             "variants":[{"name":"Full","price":{"amount":"320.00","currency":"INR"}}]}
            """.formatted(CATEGORY_ID);

    private final MockMvc mvc;

    @MockitoBean
    MenuService menuService;

    MenuControllerTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void should_return_menu_anonymously() throws Exception {
        given(menuService.menuOf(RESTAURANT_ID))
                .willReturn(new MenuService.MenuView(
                        RESTAURANT_ID, List.of(new MenuService.CategoryView(CATEGORY_ID, "Mains", 0, List.of(ITEM)))));

        mvc.perform(get("/api/v1/restaurants/" + RESTAURANT_ID + "/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].items[0].variants[0].price.amount")
                        .value("320.00"));
    }

    @Test
    void should_return_not_found_when_menu_restaurant_is_hidden() throws Exception {
        given(menuService.menuOf(RESTAURANT_ID)).willThrow(new NotFoundException("Restaurant"));

        mvc.perform(get("/api/v1/restaurants/" + RESTAURANT_ID + "/menu"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_item_anonymously() throws Exception {
        given(menuService.item(ITEM_ID)).willReturn(ITEM);

        mvc.perform(get("/api/v1/menu-items/" + ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Biryani"));
    }

    @Test
    void should_return_created_when_owner_adds_item() throws Exception {
        given(menuService.createItem(eq(RESTAURANT_ID), any())).willReturn(ITEM);

        mvc.perform(post("/api/v1/restaurants/" + RESTAURANT_ID + "/menu-items")
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/menu-items/" + ITEM_ID));
    }

    @Test
    void should_reject_item_when_price_has_three_decimals() throws Exception {
        mvc.perform(post("/api/v1/restaurants/" + RESTAURANT_ID + "/menu-items")
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.replace("\"250.00\"", "\"250.001\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("price.amount"));
    }

    @Test
    void should_return_forbidden_when_customer_adds_item() throws Exception {
        mvc.perform(post("/api/v1/restaurants/" + RESTAURANT_ID + "/menu-items")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_return_unauthorized_when_anonymous_adds_item() throws Exception {
        mvc.perform(post("/api/v1/restaurants/" + RESTAURANT_ID + "/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return_not_found_when_owner_changes_someone_elses_item() throws Exception {
        given(menuService.setAvailability(ITEM_ID, false)).willThrow(new NotFoundException("Menu item"));

        mvc.perform(post("/api/v1/menu-items/" + ITEM_ID + "/availability")
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_no_content_when_item_deleted() throws Exception {
        mvc.perform(delete("/api/v1/menu-items/" + ITEM_ID).header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isNoContent());
    }

    @Test
    void should_return_created_when_owner_adds_category() throws Exception {
        given(menuService.createCategory(RESTAURANT_ID, "Mains", null))
                .willReturn(new MenuService.CategoryView(CATEGORY_ID, "Mains", 0, List.of()));

        mvc.perform(post("/api/v1/restaurants/" + RESTAURANT_ID + "/menu-categories")
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mains\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(0));
    }

    @Test
    void should_return_conflict_when_deleting_non_empty_category() throws Exception {
        willThrow(new CategoryNotEmptyException()).given(menuService).deleteCategory(CATEGORY_ID);

        mvc.perform(delete("/api/v1/menu-categories/" + CATEGORY_ID).header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_EMPTY"));
    }

    @Test
    void should_return_forbidden_when_rider_edits_category() throws Exception {
        mvc.perform(delete("/api/v1/menu-categories/" + CATEGORY_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer("RIDER", OWNER_ID)))
                .andExpect(status().isForbidden());
    }

    private static String owner() {
        return bearer("RESTAURANT_OWNER", OWNER_ID);
    }
}
