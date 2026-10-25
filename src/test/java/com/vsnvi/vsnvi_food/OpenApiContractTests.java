package com.vsnvi.vsnvi_food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The API contract (testing.md, API-DESIGN §11): unique operationIds, a tag on every operation, public operations
 * without security, shared error responses, and one group per module. Writes {@code target/openapi.json} for CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractTests {

    private static final Set<String> PUBLIC_OPERATIONS = Set.of(
            "register", "login", "refreshTokens", "searchRestaurants", "getRestaurant", "getMenu", "getMenuItem");

    private final MockMvc mvc;
    private final ObjectMapper json;

    OpenApiContractTests(@Autowired MockMvc mvc, @Autowired ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    @Test
    void should_have_unique_operation_ids_and_tags_and_write_spec_when_generating_contract() throws Exception {
        var raw = fetch("/v3/api-docs");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/openapi.json"), raw);

        var operations = operations(json.readTree(raw));
        var ids = operations.stream().map(op -> op.get("operationId").asString()).toList();

        assertThat(operations).hasSizeGreaterThan(30);
        assertThat(ids).doesNotHaveDuplicates().doesNotContainNull();
        assertThat(operations).allSatisfy(op -> assertThat(op.path("tags")).isNotEmpty());
    }

    @Test
    void should_mark_only_documented_public_operations_as_unauthenticated() throws Exception {
        var spec = json.readTree(fetch("/v3/api-docs"));

        assertThat(spec.at("/components/securitySchemes/bearerAuth/scheme").asString())
                .isEqualTo("bearer");
        for (var op : operations(spec)) {
            var id = op.get("operationId").asString();
            var unauthenticated = op.has("security") && op.get("security").isEmpty();
            assertThat(unauthenticated).as("operation %s is public", id).isEqualTo(PUBLIC_OPERATIONS.contains(id));
        }
    }

    @Test
    void should_document_problem_detail_errors_on_every_operation() throws Exception {
        var spec = json.readTree(fetch("/v3/api-docs"));

        assertThat(spec.at("/components/schemas/ProblemDetail/properties/code").isMissingNode())
                .isFalse();
        for (var op : operations(spec)) {
            assertThat(op.at("/responses/500/content/application~1problem+json").isMissingNode())
                    .as("500 on %s", op.get("operationId"))
                    .isFalse();
            assertThat(op.at("/responses/401").isMissingNode()).isFalse();
        }
    }

    @Test
    void should_split_paths_into_module_groups_by_owning_controller() throws Exception {
        var menu = paths("menu");
        var restaurant = paths("restaurant");
        var admin = paths("admin");

        assertThat(menu)
                .contains("/api/v1/restaurants/{restaurantId}/menu", "/api/v1/menu-items/{id}")
                .doesNotContain("/api/v1/restaurants/{id}");
        assertThat(restaurant)
                .contains("/api/v1/restaurants", "/api/v1/restaurants/me", "/api/v1/restaurants/{id}/toggle-open")
                .noneMatch(p -> p.contains("menu"));
        assertThat(admin).contains("/api/v1/admin/users", "/api/v1/admin/restaurants/{id}/approve");
        assertThat(paths("user")).contains("/api/v1/users/me").noneMatch(p -> p.startsWith("/api/v1/admin"));
        assertThat(paths("auth")).contains("/api/v1/auth/login");
    }

    private List<String> paths(String group) throws Exception {
        var spec = json.readTree(fetch("/v3/api-docs/" + group));
        var paths = new ArrayList<String>();
        spec.path("paths").propertyNames().forEach(paths::add);
        return paths;
    }

    private String fetch(String url) throws Exception {
        return mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static List<JsonNode> operations(JsonNode spec) {
        var operations = new ArrayList<JsonNode>();
        spec.path("paths").forEach(path -> path.forEach(operations::add));
        return operations;
    }
}
