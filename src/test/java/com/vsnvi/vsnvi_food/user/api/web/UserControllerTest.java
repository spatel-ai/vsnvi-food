package com.vsnvi.vsnvi_food.user.api.web;

import static com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig.bearer;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vsnvi.vsnvi_food.common.domain.NotFoundException;
import com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig;
import com.vsnvi.vsnvi_food.common.web.GlobalExceptionHandler;
import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserStatus;
import com.vsnvi.vsnvi_food.user.api.UserSummary;
import com.vsnvi.vsnvi_food.user.application.UserService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({WebSecurityTestConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UserSummary ASHA =
            new UserSummary(USER_ID, "asha@example.com", "Asha", "+919876543210", Role.CUSTOMER, UserStatus.ACTIVE);

    private final MockMvc mvc;

    @MockitoBean
    UserService userService;

    UserControllerTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void should_return_profile_when_authenticated() throws Exception {
        given(userService.currentProfile()).willReturn(ASHA);

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void should_return_profile_for_any_role_when_authenticated() throws Exception {
        given(userService.currentProfile()).willReturn(ASHA);

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer("RIDER", USER_ID)))
                .andExpect(status().isOk());
    }

    @Test
    void should_return_unauthorized_when_no_token() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_return_not_found_when_token_user_no_longer_exists() throws Exception {
        given(userService.currentProfile()).willThrow(new NotFoundException("User not found"));

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void should_update_profile_when_request_is_valid() throws Exception {
        given(userService.updateCurrentProfile("Asha R", null)).willReturn(ASHA);

        mvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Asha R\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void should_reject_phone_when_not_e164() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"98765\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
    }

    @Test
    void should_reject_blank_name_when_updating() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
