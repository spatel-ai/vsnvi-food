package com.vsnvi.vsnvi_food.user.api.web;

import static com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig;
import com.vsnvi.vsnvi_food.common.web.GlobalExceptionHandler;
import com.vsnvi.vsnvi_food.user.api.AddressSummary;
import com.vsnvi.vsnvi_food.user.application.AddressService;
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

@WebMvcTest(AddressController.class)
@Import({WebSecurityTestConfig.class, GlobalExceptionHandler.class})
class AddressControllerTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ADDRESS_ID = UUID.randomUUID();
    private static final String VALID_BODY = """
            {"label":"Home","line1":"12 MG Road","city":"Bengaluru","state":"Karnataka",
             "postalCode":"560001","latitude":12.9716,"longitude":77.5946}
            """;
    private static final AddressSummary HOME = new AddressSummary(
            ADDRESS_ID, CUSTOMER_ID, "Home", "12 MG Road", null, "Bengaluru", "Karnataka", "560001", 12.9716, 77.5946);

    private final MockMvc mvc;

    @MockitoBean
    AddressService addressService;

    AddressControllerTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void should_list_addresses_when_customer() throws Exception {
        given(addressService.list()).willReturn(List.of(HOME));

        mvc.perform(get("/api/v1/users/me/addresses").header(HttpHeaders.AUTHORIZATION, customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ADDRESS_ID.toString()))
                .andExpect(jsonPath("$[0].line2").doesNotExist());
    }

    @Test
    void should_return_created_with_location_when_address_added() throws Exception {
        given(addressService.add(any())).willReturn(HOME);

        mvc.perform(post("/api/v1/users/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                                HttpHeaders.LOCATION, "http://localhost/api/v1/users/me/addresses/" + ADDRESS_ID))
                .andExpect(jsonPath("$.city").value("Bengaluru"));
    }

    @Test
    void should_reject_address_when_latitude_out_of_range() throws Exception {
        mvc.perform(post("/api/v1/users/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("12.9716", "91.0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("latitude"));
    }

    @Test
    void should_reject_address_when_postal_code_is_invalid() throws Exception {
        mvc.perform(post("/api/v1/users/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("560001", "05600")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("postalCode"));
    }

    @Test
    void should_return_not_found_when_address_belongs_to_another_user() throws Exception {
        given(addressService.replace(eq(ADDRESS_ID), any())).willThrow(new NotFoundException("Address"));

        mvc.perform(put("/api/v1/users/me/addresses/" + ADDRESS_ID)
                        .header(HttpHeaders.AUTHORIZATION, customer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_no_content_when_address_deleted() throws Exception {
        mvc.perform(delete("/api/v1/users/me/addresses/" + ADDRESS_ID).header(HttpHeaders.AUTHORIZATION, customer()))
                .andExpect(status().isNoContent());
    }

    @Test
    void should_return_not_found_when_deleting_another_users_address() throws Exception {
        willThrow(new NotFoundException("Address")).given(addressService).delete(ADDRESS_ID);

        mvc.perform(delete("/api/v1/users/me/addresses/" + ADDRESS_ID).header(HttpHeaders.AUTHORIZATION, customer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_forbidden_when_caller_is_not_a_customer() throws Exception {
        mvc.perform(get("/api/v1/users/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer("RESTAURANT_OWNER", CUSTOMER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_return_unauthorized_when_no_token() throws Exception {
        mvc.perform(get("/api/v1/users/me/addresses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static String customer() {
        return bearer("CUSTOMER", CUSTOMER_ID);
    }
}
