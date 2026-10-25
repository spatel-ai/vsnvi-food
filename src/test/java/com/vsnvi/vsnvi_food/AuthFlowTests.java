package com.vsnvi.vsnvi_food;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** End to end through the real filter chain and real JWTs: register, login, use, refresh, block. */
@SpringBootTest(
        properties = {
            "auth.admin-bootstrap.email=root-admin@example.com",
            "auth.admin-bootstrap.password=root-admin-pass"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowTests {

    private final MockMvc mvc;
    private final ObjectMapper json;

    AuthFlowTests(@Autowired MockMvc mvc, @Autowired ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    @Test
    void should_access_profile_and_rotate_tokens_when_user_registers_and_logs_in() throws Exception {
        var email = "flow-" + UUID.randomUUID() + "@example.com";
        register(email, "CUSTOMER");
        var tokens = login(email, "s3cret-pass");

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        var rotated = refresh(tokens.get("refreshToken").asString());
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(rotated)))
                .andExpect(status().isOk());

        // The first refresh token was rotated: using it again is reuse.
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.get("refreshToken").asString())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_reject_tampered_access_token_when_calling_api() throws Exception {
        var email = "tamper-" + UUID.randomUUID() + "@example.com";
        register(email, "CUSTOMER");
        var access = login(email, "s3cret-pass").get("accessToken").asString();

        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access + "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_stop_refresh_and_login_when_admin_blocks_user() throws Exception {
        var email = "blocked-" + UUID.randomUUID() + "@example.com";
        var userId = register(email, "CUSTOMER").get("id").asString();
        var userTokens = login(email, "s3cret-pass");
        var admin = login("root-admin@example.com", "root-admin-pass");

        mvc.perform(post("/api/v1/admin/users/" + userId + "/block")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"fraud\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "s3cret-pass")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));

        // auth revokes refresh tokens asynchronously when it receives UserBlocked.
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> mvc.perform(post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(refreshBody(
                                        userTokens.get("refreshToken").asString())))
                        .andExpect(status().is4xxClientError()));
    }

    @Test
    void should_return_forbidden_when_customer_calls_admin_endpoint() throws Exception {
        var email = "nosy-" + UUID.randomUUID() + "@example.com";
        register(email, "CUSTOMER");

        mvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(login(email, "s3cret-pass"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private JsonNode register(String email, String role) throws Exception {
        var body = """
                {"email":"%s","password":"s3cret-pass","fullName":"Flow User","role":"%s"}
                """.formatted(email, role);
        var response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }

    private JsonNode login(String email, String password) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response);
    }

    private static String bearer(JsonNode tokens) {
        return "Bearer " + tokens.get("accessToken").asString();
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
    }
}
