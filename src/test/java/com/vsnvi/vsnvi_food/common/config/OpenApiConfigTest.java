package com.vsnvi.vsnvi_food.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.vsnvi.vsnvi_food.common.idempotency.IdempotencyKeyHeader;
import io.swagger.v3.oas.models.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

class OpenApiConfigTest {

    static class SampleController {

        @IdempotencyKeyHeader
        void placeOrder() {}

        void getOrder() {}
    }

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void should_add_required_uuid_header_when_handler_is_annotated() throws Exception {
        var operation = config.idempotencyKeyHeader().customize(new Operation(), handler("placeOrder"));

        assertThat(operation.getParameters()).singleElement().satisfies(header -> {
            assertThat(header.getName()).isEqualTo("Idempotency-Key");
            assertThat(header.getIn()).isEqualTo("header");
            assertThat(header.getRequired()).isTrue();
            assertThat(header.getSchema().getFormat()).isEqualTo("uuid");
        });
    }

    @Test
    void should_leave_other_operations_alone() throws Exception {
        var operation = config.idempotencyKeyHeader().customize(new Operation(), handler("getOrder"));

        assertThat(operation.getParameters()).isNullOrEmpty();
    }

    private static HandlerMethod handler(String name) throws NoSuchMethodException {
        return new HandlerMethod(new SampleController(), SampleController.class.getDeclaredMethod(name));
    }
}
