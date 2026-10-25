package com.vsnvi.vsnvi_food.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** docs/SECURITY.md §6: in prod, Swagger UI and the spec are not on the public allowlist. */
@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@ActiveProfiles("prod")
@Import({WebSecurityTestConfig.class, SecurityConfigTest.ProbeController.class})
class ApiDocsProdSecurityTest {

    private final MockMvc mvc;

    ApiDocsProdSecurityTest(@Autowired MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void should_require_authentication_for_api_docs_when_profile_is_prod() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }
}
