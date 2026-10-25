package com.vsnvi.vsnvi_food.common.security;

import static com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vsnvi.vsnvi_food.common.web.GlobalExceptionHandler;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@Import({WebSecurityTestConfig.class, GlobalExceptionHandler.class, SecurityConfigTest.ProbeController.class})
class SecurityConfigTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String RESTAURANT_ID = "22222222-2222-2222-2222-222222222222";

    private final MockMvc mvc;

    SecurityConfigTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void should_return_unauthorized_when_protected_endpoint_has_no_token() throws Exception {
        mvc.perform(get("/api/v1/probe/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/probe/me"));
    }

    @Test
    void should_return_token_expired_when_access_token_is_expired() throws Exception {
        mvc.perform(get("/api/v1/probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void should_return_unauthorized_when_token_is_invalid_even_on_public_endpoint() throws Exception {
        mvc.perform(get("/api/v1/restaurants/" + RESTAURANT_ID).header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_ignore_stale_bearer_header_when_calling_credential_endpoints() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer expired"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isOk());
    }

    @Test
    void should_expose_authenticated_user_id_when_token_is_valid() throws Exception {
        mvc.perform(get("/api/v1/probe/me").header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().string(USER_ID.toString()));
    }

    @Test
    void should_return_forbidden_when_role_is_wrong() throws Exception {
        mvc.perform(get("/api/v1/probe/admin-only").header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_return_unauthorized_not_forbidden_when_anonymous_hits_role_protected_endpoint() throws Exception {
        mvc.perform(get("/api/v1/probe/admin-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_allow_anonymous_when_path_is_on_public_allowlist() throws Exception {
        mvc.perform(get("/api/v1/restaurants/" + RESTAURANT_ID)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/restaurants")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login")).andExpect(status().isOk());
    }

    @Test
    void should_require_token_when_owner_path_looks_like_public_restaurant_path() throws Exception {
        mvc.perform(get("/api/v1/restaurants/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_require_token_when_public_path_is_used_with_another_method() throws Exception {
        mvc.perform(post("/api/v1/restaurants")).andExpect(status().isUnauthorized());
    }

    @RestController
    @RequiredArgsConstructor
    static class ProbeController {

        private final CurrentUser currentUser;

        @GetMapping("/api/v1/probe/me")
        String me() {
            return currentUser.id().toString();
        }

        @GetMapping("/api/v1/probe/admin-only")
        @PreAuthorize("hasRole('ADMIN')")
        String adminOnly() {
            return "ok";
        }

        @GetMapping("/api/v1/restaurants")
        String restaurants() {
            return "ok";
        }

        @PostMapping("/api/v1/restaurants")
        String createRestaurant() {
            return "ok";
        }

        @GetMapping("/api/v1/restaurants/{id}")
        String restaurant(@PathVariable String id) {
            return "ok";
        }

        @PostMapping("/api/v1/auth/login")
        String login() {
            return "ok";
        }

        @PostMapping("/api/v1/auth/refresh")
        String refresh() {
            return "ok";
        }
    }
}
