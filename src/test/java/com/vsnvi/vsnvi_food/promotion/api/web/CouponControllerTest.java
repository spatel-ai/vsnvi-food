package com.vsnvi.vsnvi_food.promotion.api.web;

import static com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig.NOW;
import static com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vsnvi.vsnvi_food.common.domain.Money;
import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig;
import com.vsnvi.vsnvi_food.common.web.GlobalExceptionHandler;
import com.vsnvi.vsnvi_food.promotion.api.CouponType;
import com.vsnvi.vsnvi_food.promotion.application.CouponService;
import com.vsnvi.vsnvi_food.promotion.domain.CouponCodeTakenException;
import com.vsnvi.vsnvi_food.promotion.domain.CouponQuotaReachedException;
import com.vsnvi.vsnvi_food.promotion.domain.RestaurantNotApprovedException;
import java.math.BigDecimal;
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

@WebMvcTest({CouponController.class, CouponAdminController.class})
@Import({WebSecurityTestConfig.class, GlobalExceptionHandler.class})
class CouponControllerTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final UUID COUPON_ID = UUID.randomUUID();
    private static final CouponService.CouponView COUPON = new CouponService.CouponView(
            COUPON_ID,
            "WELCOME50",
            RESTAURANT_ID,
            "50% off up to 100",
            CouponType.PERCENT,
            null,
            new BigDecimal("50"),
            Money.inr("100"),
            Money.inr("0"),
            NOW,
            NOW.plusSeconds(86_400),
            true,
            1,
            null,
            true,
            NOW);
    private static final String CREATE_BODY = """
            {"code":"welcome50","type":"PERCENT","percentOff":50,
             "maxDiscount":{"amount":"100.00","currency":"INR"},
             "validFrom":"2026-10-25T10:00:00Z","validUntil":"2026-10-26T10:00:00Z","perCustomerLimit":1,
             "firstOrderOnly":true}
            """;

    private final MockMvc mvc;

    @MockitoBean
    CouponService couponService;

    CouponControllerTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    // --- owner: create and list ---

    @Test
    void should_return_created_with_location_when_owner_creates_coupon() throws Exception {
        given(couponService.createRestaurantCoupon(eq(RESTAURANT_ID), any())).willReturn(COUPON);

        mvc.perform(post(restaurantCoupons())
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/coupons/" + COUPON_ID))
                .andExpect(jsonPath("$.code").value("WELCOME50"))
                .andExpect(jsonPath("$.maxDiscount.amount").value("100.00"))
                .andExpect(jsonPath("$.totalLimit").doesNotExist());
        then(couponService)
                .should()
                .createRestaurantCoupon(
                        eq(RESTAURANT_ID),
                        argThat(d -> d.type() == CouponType.PERCENT
                                && d.code().equals("welcome50")
                                && d.firstOrderOnly()
                                && d.maxDiscount().equals(Money.inr("100"))
                                && d.minOrderSubtotal() == null));
    }

    @Test
    void should_reject_coupon_when_percent_is_above_one_hundred() throws Exception {
        mvc.perform(post(restaurantCoupons())
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.replace("\"percentOff\":50", "\"percentOff\":101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("percentOff"));
    }

    @Test
    void should_reject_coupon_when_code_has_spaces() throws Exception {
        mvc.perform(post(restaurantCoupons())
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.replace("welcome50", "welcome 50")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("code"));
    }

    @Test
    void should_return_bad_request_when_terms_do_not_fit_the_type() throws Exception {
        given(couponService.createRestaurantCoupon(eq(RESTAURANT_ID), any()))
                .willThrow(new ValidationException("A PERCENT coupon needs a maxDiscount cap above zero"));

        mvc.perform(post(restaurantCoupons())
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void should_return_conflict_when_code_is_taken() throws Exception {
        given(couponService.createRestaurantCoupon(eq(RESTAURANT_ID), any())).willThrow(new CouponCodeTakenException());

        mvc.perform(post(restaurantCoupons())
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_CODE_TAKEN"));
    }

    @Test
    void should_return_not_found_when_owner_creates_coupon_for_someone_elses_restaurant() throws Exception {
        given(couponService.createRestaurantCoupon(eq(RESTAURANT_ID), any()))
                .willThrow(new NotFoundException("Restaurant"));

        mvc.perform(post(restaurantCoupons())
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_forbidden_when_customer_creates_restaurant_coupon() throws Exception {
        mvc.perform(post(restaurantCoupons())
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_return_unauthorized_when_anonymous_lists_restaurant_coupons() throws Exception {
        mvc.perform(get(restaurantCoupons()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_return_page_when_owner_lists_coupons() throws Exception {
        given(couponService.listForRestaurant(eq(RESTAURANT_ID), any()))
                .willReturn(new PageImpl<>(List.of(COUPON), PageRequest.of(0, 20), 1));

        mvc.perform(get(restaurantCoupons()).header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(COUPON_ID.toString()))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void should_reject_list_when_sort_field_is_not_allowed() throws Exception {
        mvc.perform(get(restaurantCoupons() + "?sort=percentOff").header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // --- owner or admin: update and deactivate ---

    @Test
    void should_update_coupon_when_admin_patches() throws Exception {
        given(couponService.update(eq(COUPON_ID), any())).willReturn(COUPON);

        mvc.perform(patch("/api/v1/coupons/" + COUPON_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN", ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalLimit\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COUPON_ID.toString()));
        then(couponService).should().update(eq(COUPON_ID), argThat(p -> p.totalLimit() == 500));
    }

    @Test
    void should_reject_update_when_limit_is_zero() throws Exception {
        mvc.perform(patch("/api/v1/coupons/" + COUPON_ID)
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perCustomerLimit\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void should_return_not_found_when_owner_patches_someone_elses_coupon() throws Exception {
        given(couponService.update(eq(COUPON_ID), any())).willThrow(new NotFoundException("Coupon"));

        mvc.perform(patch("/api/v1/coupons/" + COUPON_ID)
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"mine now\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_forbidden_when_rider_deactivates_coupon() throws Exception {
        mvc.perform(post("/api/v1/coupons/" + COUPON_ID + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, bearer("RIDER", UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_return_ok_when_owner_deactivates_coupon() throws Exception {
        given(couponService.deactivate(COUPON_ID)).willReturn(COUPON);

        mvc.perform(post("/api/v1/coupons/" + COUPON_ID + "/deactivate").header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isOk());
    }

    // --- customer: available coupons ---

    @Test
    void should_list_available_coupons_without_limits_when_customer_asks() throws Exception {
        given(couponService.availableAt(RESTAURANT_ID))
                .willReturn(List.of(new CouponService.AvailableCouponView(
                        "WELCOME50",
                        null,
                        null,
                        CouponType.PERCENT,
                        null,
                        new BigDecimal("50"),
                        Money.inr("100"),
                        Money.inr("0"),
                        NOW.plusSeconds(86_400),
                        true)));

        mvc.perform(get("/api/v1/coupons/available?restaurantId=" + RESTAURANT_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("WELCOME50"))
                .andExpect(jsonPath("$[0].firstOrderOnly").value(true))
                .andExpect(jsonPath("$[0].totalLimit").doesNotExist())
                .andExpect(jsonPath("$[0].perCustomerLimit").doesNotExist());
    }

    @Test
    void should_return_not_found_when_restaurant_is_hidden_from_customer() throws Exception {
        given(couponService.availableAt(RESTAURANT_ID)).willThrow(new NotFoundException("Restaurant"));

        mvc.perform(get("/api/v1/coupons/available?restaurantId=" + RESTAURANT_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_reject_available_coupons_when_restaurant_id_is_missing() throws Exception {
        mvc.perform(get("/api/v1/coupons/available")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void should_return_forbidden_when_owner_asks_for_available_coupons() throws Exception {
        mvc.perform(get("/api/v1/coupons/available?restaurantId=" + RESTAURANT_ID)
                        .header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return_unauthorized_when_anonymous_asks_for_available_coupons() throws Exception {
        mvc.perform(get("/api/v1/coupons/available?restaurantId=" + RESTAURANT_ID))
                .andExpect(status().isUnauthorized());
    }

    // --- admin: platform coupons ---

    @Test
    void should_return_created_when_admin_creates_platform_coupon() throws Exception {
        given(couponService.createPlatformCoupon(any())).willReturn(COUPON);

        mvc.perform(post("/api/v1/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN", ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/coupons/" + COUPON_ID));
    }

    @Test
    void should_return_forbidden_when_owner_creates_platform_coupon() throws Exception {
        mvc.perform(post("/api/v1/admin/coupons")
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_return_page_when_admin_lists_all_coupons() throws Exception {
        given(couponService.listAll(any())).willReturn(new PageImpl<>(List.of(COUPON), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/admin/coupons").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN", ADMIN_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("WELCOME50"));
    }

    @Test
    void should_return_unauthorized_when_token_is_expired() throws Exception {
        mvc.perform(get("/api/v1/admin/coupons").header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    // --- the remaining 401 / 403 / 404 cases per endpoint ---

    @Test
    void should_return_unauthorized_when_anonymous_creates_restaurant_coupon() throws Exception {
        mvc.perform(post(restaurantCoupons())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_return_forbidden_when_customer_lists_restaurant_coupons() throws Exception {
        mvc.perform(get(restaurantCoupons()).header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_return_not_found_when_owner_lists_coupons_of_someone_elses_restaurant() throws Exception {
        given(couponService.listForRestaurant(eq(RESTAURANT_ID), any())).willThrow(new NotFoundException("Restaurant"));

        mvc.perform(get(restaurantCoupons()).header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_unauthorized_when_anonymous_patches_coupon() throws Exception {
        mvc.perform(patch("/api/v1/coupons/" + COUPON_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalLimit\":5}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_return_forbidden_when_customer_patches_coupon() throws Exception {
        mvc.perform(patch("/api/v1/coupons/" + COUPON_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalLimit\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_reject_update_when_description_contains_control_characters() throws Exception {
        mvc.perform(patch("/api/v1/coupons/" + COUPON_ID)
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"50% off\\u0007\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("description"));
    }

    @Test
    void should_return_unauthorized_when_anonymous_deactivates_coupon() throws Exception {
        mvc.perform(post("/api/v1/coupons/" + COUPON_ID + "/deactivate"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_return_not_found_when_owner_deactivates_someone_elses_coupon() throws Exception {
        given(couponService.deactivate(COUPON_ID)).willThrow(new NotFoundException("Coupon"));

        mvc.perform(post("/api/v1/coupons/" + COUPON_ID + "/deactivate").header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_unauthorized_when_anonymous_creates_platform_coupon() throws Exception {
        mvc.perform(post("/api/v1/admin/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_return_forbidden_when_owner_lists_all_coupons() throws Exception {
        mvc.perform(get("/api/v1/admin/coupons").header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // --- review fixes: GET /coupons/{id}, approval, quota, invisible characters ---

    @Test
    void should_return_coupon_when_owner_gets_it() throws Exception {
        given(couponService.get(COUPON_ID)).willReturn(COUPON);

        mvc.perform(get("/api/v1/coupons/" + COUPON_ID).header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COUPON_ID.toString()))
                .andExpect(jsonPath("$.perCustomerLimit").value(1));
    }

    @Test
    void should_return_not_found_when_owner_gets_someone_elses_coupon() throws Exception {
        given(couponService.get(COUPON_ID)).willThrow(new NotFoundException("Coupon"));

        mvc.perform(get("/api/v1/coupons/" + COUPON_ID).header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_forbidden_when_customer_gets_a_coupon() throws Exception {
        mvc.perform(get("/api/v1/coupons/" + COUPON_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_return_unauthorized_when_anonymous_gets_a_coupon() throws Exception {
        mvc.perform(get("/api/v1/coupons/" + COUPON_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_return_conflict_when_restaurant_is_not_approved() throws Exception {
        given(couponService.createRestaurantCoupon(eq(RESTAURANT_ID), any()))
                .willThrow(new RestaurantNotApprovedException());

        mvc.perform(post(restaurantCoupons())
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESTAURANT_STATE"));
    }

    @Test
    void should_return_unprocessable_when_restaurant_has_too_many_active_coupons() throws Exception {
        given(couponService.createRestaurantCoupon(eq(RESTAURANT_ID), any()))
                .willThrow(new CouponQuotaReachedException());

        mvc.perform(post(restaurantCoupons())
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("COUPON_QUOTA_REACHED"));
    }

    @Test
    void should_reject_description_when_it_contains_a_bidi_override() throws Exception {
        mvc.perform(post(restaurantCoupons())
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.replace("\"type\"", "\"description\":\"50% off\\u202E\",\"type\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("description"));
    }

    private static String restaurantCoupons() {
        return "/api/v1/restaurants/" + RESTAURANT_ID + "/coupons";
    }

    private static String owner() {
        return bearer("RESTAURANT_OWNER", OWNER_ID);
    }
}
