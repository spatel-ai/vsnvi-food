package com.vsnvi.vsnvi_food.common.config;

import com.vsnvi.vsnvi_food.common.idempotency.IdempotencyKeyHeader;
import com.vsnvi.vsnvi_food.common.idempotency.IdempotencyService;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The generated API contract (docs/API-DESIGN.md §11): info, servers, the global {@code bearerAuth} scheme, one group
 * per module plus {@code admin}, a shared {@code ProblemDetail} schema with the default error responses, and the
 * {@code Idempotency-Key} header for {@link IdempotencyKeyHeader} handlers. Whether the spec is served at all is a
 * profile setting ({@code springdoc.*.enabled}).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";
    static final String PROBLEM_DETAIL = "ProblemDetail";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final Set<PathItem.HttpMethod> MUTATING = Set.of(
            PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT, PathItem.HttpMethod.PATCH, PathItem.HttpMethod.DELETE);

    @Bean
    OpenAPI vsnviOpenApi(
            @Value("${info.app.version:dev}") String version, @Value("${openapi.servers:}") String[] servers) {
        var openApi = new OpenAPI()
                .info(new Info()
                        .title("vsnvi-food API")
                        .version(version)
                        .description("Food delivery backend: restaurants, menus, carts, orders, payments and live"
                                + " tracking. Errors are RFC 9457 Problem Details; see the `code` field.")
                        .contact(new Contact().name("vsnvi-food backend team")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addSchemas(PROBLEM_DETAIL, problemDetailSchema()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
        Arrays.stream(servers).filter(s -> !s.isBlank()).forEach(url -> openApi.addServersItem(new Server().url(url)));
        return openApi;
    }

    /** Adds the shared error responses to every operation in every group, unless the operation declares its own. */
    @Bean
    GlobalOpenApiCustomizer defaultErrorResponses() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths()
                    .values()
                    .forEach(path -> path.readOperationsMap().forEach((method, operation) -> {
                        var responses =
                                operation.getResponses() != null ? operation.getResponses() : new ApiResponses();
                        errorResponses(MUTATING.contains(method)).forEach(responses::putIfAbsent);
                        operation.setResponses(responses);
                    }));
            openApi.getComponents().addSchemas(PROBLEM_DETAIL, problemDetailSchema());
        };
    }

    @Bean
    GlobalOperationCustomizer idempotencyKeyHeader() {
        return (operation, handlerMethod) -> {
            if (handlerMethod.hasMethodAnnotation(IdempotencyKeyHeader.class)) {
                operation.addParametersItem(new HeaderParameter()
                        .name(IdempotencyService.HEADER)
                        .required(true)
                        .description("A UUID per logical request; a retry with the same key and body returns the"
                                + " stored response")
                        .schema(new UUIDSchema()));
            }
            return operation;
        };
    }

    @Bean
    GroupedOpenApi authGroup() {
        return group("auth", "/api/v1/auth/**");
    }

    @Bean
    GroupedOpenApi userGroup() {
        return GroupedOpenApi.builder()
                .group("user")
                .pathsToMatch("/api/v1/users/**")
                .pathsToExclude("/api/v1/users/me/orders")
                .build();
    }

    @Bean
    GroupedOpenApi restaurantGroup() {
        return group(
                "restaurant",
                "/api/v1/restaurants",
                "/api/v1/restaurants/*",
                "/api/v1/restaurants/*/opening-hours",
                "/api/v1/restaurants/*/toggle-open");
    }

    @Bean
    GroupedOpenApi menuGroup() {
        return group(
                "menu",
                "/api/v1/restaurants/*/menu",
                "/api/v1/restaurants/*/menu-*",
                "/api/v1/menu-items/**",
                "/api/v1/menu-categories/**");
    }

    @Bean
    GroupedOpenApi cartGroup() {
        return group("cart", "/api/v1/cart/**");
    }

    @Bean
    GroupedOpenApi promotionGroup() {
        return group("promotion", "/api/v1/coupons/**", "/api/v1/restaurants/*/coupons");
    }

    @Bean
    GroupedOpenApi orderGroup() {
        return GroupedOpenApi.builder()
                .group("order")
                .pathsToMatch("/api/v1/orders/**", "/api/v1/users/me/orders", "/api/v1/restaurants/*/orders")
                .pathsToExclude("/api/v1/orders/*/tracking")
                .build();
    }

    @Bean
    GroupedOpenApi paymentGroup() {
        return group("payment", "/api/v1/payments/**");
    }

    @Bean
    GroupedOpenApi deliveryGroup() {
        return group("delivery", "/api/v1/delivery/**", "/api/v1/delivery-tasks/**", "/api/v1/orders/*/tracking");
    }

    @Bean
    GroupedOpenApi adminGroup() {
        return group("admin", "/api/v1/admin/**");
    }

    private static GroupedOpenApi group(String name, String... paths) {
        return GroupedOpenApi.builder().group(name).pathsToMatch(paths).build();
    }

    private static Map<String, ApiResponse> errorResponses(boolean mutating) {
        var responses = new LinkedHashMap<String, ApiResponse>();
        responses.put(
                "400", problem("Malformed request or validation failure (`VALIDATION_FAILED`, `MALFORMED_REQUEST`)"));
        responses.put("401", problem("Missing, invalid or expired access token (`UNAUTHORIZED`, `TOKEN_EXPIRED`)"));
        responses.put("403", problem("The caller's role isn't allowed (`FORBIDDEN`)"));
        responses.put("404", problem("Not found, or not yours (`RESOURCE_NOT_FOUND`)"));
        if (mutating) {
            responses.put("409", problem("Conflict with the current state (see `code`)"));
            responses.put("422", problem("A business rule was violated (see `code`)"));
        }
        responses.put("429", problem("Rate limited (`RATE_LIMITED`); see `Retry-After`"));
        responses.put("500", problem("Unexpected error (`INTERNAL_ERROR`); quote `traceId` when reporting it"));
        return responses;
    }

    private static ApiResponse problem(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content()
                        .addMediaType(
                                PROBLEM_JSON,
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL))));
    }

    @SuppressWarnings("rawtypes")
    private static Schema problemDetailSchema() {
        return new ObjectSchema()
                .description("RFC 9457 Problem Details with the extensions in docs/API-DESIGN.md §4")
                .addProperty(
                        "type",
                        new StringSchema().format("uri").example("https://api.vsnvi.com/errors/validation-failed"))
                .addProperty("title", new StringSchema().example("Validation failed"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("detail", new StringSchema().example("Request has 1 invalid fields"))
                .addProperty("instance", new StringSchema().format("uri").example("/api/v1/users/me"))
                .addProperty("code", new StringSchema().example("VALIDATION_FAILED"))
                .addProperty("traceId", new StringSchema().example("a1b2c3d4e5f60718"))
                .addProperty("timestamp", new StringSchema().format("date-time"))
                .addProperty(
                        "errors",
                        new ArraySchema()
                                .description("Validation failures only")
                                .items(new ObjectSchema()
                                        .addProperty("field", new StringSchema().example("phone"))
                                        .addProperty(
                                                "message", new StringSchema().example("must be an E.164 number"))));
    }
}
