package com.vsnvi.vsnvi_food.auth.api.web;

import static com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig.bearer;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vsnvi.vsnvi_food.auth.application.AuthService;
import com.vsnvi.vsnvi_food.auth.domain.AccountBlockedException;
import com.vsnvi.vsnvi_food.auth.domain.InvalidCredentialsException;
import com.vsnvi.vsnvi_food.auth.domain.InvalidRefreshTokenException;
import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;
import com.vsnvi.vsnvi_food.common.ratelimit.RateLimitedException;
import com.vsnvi.vsnvi_food.common.security.WebSecurityTestConfig;
import com.vsnvi.vsnvi_food.common.web.GlobalExceptionHandler;
import com.vsnvi.vsnvi_food.user.api.Role;
import com.vsnvi.vsnvi_food.user.api.UserStatus;
import com.vsnvi.vsnvi_food.user.api.UserSummary;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({WebSecurityTestConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String REGISTER_BODY = """
            {"email":"asha@example.com","password":"s3cret-pass","fullName":"Asha","role":"%s"}
            """;
    private static final AuthService.Tokens TOKENS = new AuthService.Tokens(
            "access", Instant.parse("2026-10-25T10:15:00Z"), "refresh", Instant.parse("2026-11-24T10:00:00Z"));

    private final MockMvc mvc;

    @MockitoBean
    AuthService authService;

    AuthControllerTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void should_return_created_when_registration_is_valid() throws Exception {
        given(authService.register("asha@example.com", "s3cret-pass", "Asha", null, Role.RIDER))
                .willReturn(new UserSummary(USER_ID, "asha@example.com", "Asha", null, Role.RIDER, UserStatus.ACTIVE));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY.formatted("RIDER")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/users/me"))
                .andExpect(jsonPath("$.role").value("RIDER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void should_reject_registration_when_role_is_admin() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY.formatted("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void should_reject_registration_when_password_is_too_short() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY.formatted("CUSTOMER").replace("s3cret-pass", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void should_reject_registration_when_password_exceeds_72_bytes() throws Exception {
        var emoji = "\uD83D\uDE00".repeat(30); // 60 chars, 120 bytes: within @Size, over BCrypt's limit
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY.formatted("CUSTOMER").replace("s3cret-pass", emoji)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        then(authService).shouldHaveNoInteractions();
    }

    @Test
    void should_reject_registration_when_email_is_invalid() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY.formatted("CUSTOMER").replace("asha@example.com", "not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void should_return_conflict_when_email_is_already_registered() throws Exception {
        given(authService.register("asha@example.com", "s3cret-pass", "Asha", null, Role.CUSTOMER))
                .willThrow(new DomainException(ErrorCode.EMAIL_ALREADY_REGISTERED, "taken") {});

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY.formatted("CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void should_return_tokens_when_login_succeeds() throws Exception {
        given(authService.login(eq("asha@example.com"), eq("s3cret-pass"), anyString())).willReturn(TOKENS);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.accessTokenExpiresAt").value("2026-10-25T10:15:00Z"));
    }

    @Test
    void should_return_invalid_credentials_when_login_fails() throws Exception {
        given(authService.login(eq("asha@example.com"), eq("s3cret-pass"), anyString())).willThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void should_return_account_blocked_when_user_is_blocked() throws Exception {
        given(authService.login(eq("asha@example.com"), eq("s3cret-pass"), anyString())).willThrow(new AccountBlockedException());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
    }

    @Test
    void should_return_too_many_requests_with_retry_after_when_rate_limited() throws Exception {
        given(authService.login(eq("asha@example.com"), eq("s3cret-pass"), anyString()))
                .willThrow(new RateLimitedException(Duration.ofSeconds(90)));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "90"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void should_rotate_tokens_when_refresh_token_is_valid() throws Exception {
        given(authService.refresh("old")).willReturn(TOKENS);

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
    }

    @Test
    void should_return_unauthorized_when_refresh_token_is_invalid() throws Exception {
        given(authService.refresh("reused")).willThrow(new InvalidRefreshTokenException());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"reused\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_return_no_content_when_logged_out() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer("CUSTOMER", USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"r\"}"))
                .andExpect(status().isNoContent());
        then(authService).should().logout("r");
    }

    @Test
    void should_require_access_token_when_logging_out() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"r\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static String loginBody() {
        return "{\"email\":\"asha@example.com\",\"password\":\"s3cret-pass\"}";
    }
}
