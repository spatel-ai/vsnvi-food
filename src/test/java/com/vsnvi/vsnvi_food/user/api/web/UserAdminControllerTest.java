package com.vsnvi.vsnvi_food.user.api.web;

import static com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig;
import com.vsnvi.vsnvi_food.common.web.GlobalExceptionHandler;
import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserStatus;
import com.vsnvi.vsnvi_food.user.api.UserSummary;
import com.vsnvi.vsnvi_food.user.application.UserService;
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

@WebMvcTest(UserAdminController.class)
@Import({WebSecurityTestConfig.class, GlobalExceptionHandler.class})
class UserAdminControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UserSummary BLOCKED =
            new UserSummary(USER_ID, "asha@example.com", "Asha", null, Role.CUSTOMER, UserStatus.BLOCKED);

    private final MockMvc mvc;

    @MockitoBean
    UserService userService;

    UserAdminControllerTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void should_return_page_envelope_when_admin_lists_users() throws Exception {
        given(userService.search(eq(Role.CUSTOMER), eq(null), any()))
                .willReturn(new PageImpl<>(List.of(BLOCKED), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/admin/users").param("role", "CUSTOMER").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("asha@example.com"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.size").value(20));
    }

    @Test
    void should_reject_listing_when_sort_field_is_not_documented() throws Exception {
        mvc.perform(get("/api/v1/admin/users").param("sort", "phone,asc").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void should_reject_listing_when_role_filter_is_unknown() throws Exception {
        mvc.perform(get("/api/v1/admin/users").param("role", "SUPERUSER").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void should_block_user_when_admin() throws Exception {
        given(userService.block(USER_ID, "fraud")).willReturn(BLOCKED);

        mvc.perform(post("/api/v1/admin/users/" + USER_ID + "/block")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"fraud\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void should_require_reason_when_blocking() throws Exception {
        mvc.perform(post("/api/v1/admin/users/" + USER_ID + "/block")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void should_return_not_found_when_blocking_unknown_user() throws Exception {
        given(userService.block(USER_ID, "fraud")).willThrow(new NotFoundException("User"));

        mvc.perform(post("/api/v1/admin/users/" + USER_ID + "/block")
                        .header(HttpHeaders.AUTHORIZATION, admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"fraud\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_return_forbidden_when_caller_is_not_admin() throws Exception {
        mvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_return_unauthorized_when_no_token() throws Exception {
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    }

    private static String admin() {
        return bearer("ADMIN", ADMIN_ID);
    }
}
