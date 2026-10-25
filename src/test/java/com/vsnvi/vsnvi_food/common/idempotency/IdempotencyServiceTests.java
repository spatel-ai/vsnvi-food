package com.vsnvi.vsnvi_food.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vsnvi.vsnvi_food.TestcontainersConfiguration;
import com.vsnvi.vsnvi_food.common.domain.DomainException;
import com.vsnvi.vsnvi_food.common.domain.ErrorCode;
import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import com.vsnvi.vsnvi_food.common.security.AuthenticatedUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdempotencyServiceTests {

    record PlaceRequest(String item, int quantity) {}

    record Placed(UUID orderId, String item) {}

    private final IdempotencyService idempotency;
    private final IdempotencyRecordRepository records;
    private final JdbcClient jdbc;

    private UUID userId;
    private String key;
    private final AtomicInteger runs = new AtomicInteger();

    IdempotencyServiceTests(
            @Autowired IdempotencyService idempotency,
            @Autowired IdempotencyRecordRepository records,
            @Autowired JdbcClient jdbc) {
        this.idempotency = idempotency;
        this.records = records;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void signIn() {
        userId = UUID.randomUUID();
        key = UUID.randomUUID().toString();
        runs.set(0);
        signInAs(userId);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_return_stored_response_without_running_again_when_same_key_and_body() {
        var first = place(new PlaceRequest("biryani", 2));
        var second = place(new PlaceRequest("biryani", 2));

        assertThat(second).isEqualTo(first);
        assertThat(runs).hasValue(1);
    }

    @Test
    void should_return_conflict_when_same_key_is_reused_with_different_body() {
        place(new PlaceRequest("biryani", 2));

        assertThatThrownBy(() -> place(new PlaceRequest("biryani", 3)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).errorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        assertThat(runs).hasValue(1);
    }

    @Test
    void should_release_key_when_action_fails_so_retry_runs() {
        assertThatThrownBy(() -> idempotency.execute(key, "placeOrder", new PlaceRequest("x", 1), Placed.class, () -> {
                    throw new IllegalStateException("payment gateway down");
                }))
                .isInstanceOf(IllegalStateException.class);

        place(new PlaceRequest("x", 1));

        assertThat(runs).hasValue(1);
    }

    @Test
    void should_scope_keys_to_the_caller() {
        place(new PlaceRequest("biryani", 2));

        signInAs(UUID.randomUUID());
        place(new PlaceRequest("biryani", 2));

        assertThat(runs).hasValue(2);
    }

    @Test
    void should_treat_same_key_on_another_operation_as_conflict() {
        place(new PlaceRequest("biryani", 2));

        assertThatThrownBy(() -> idempotency.execute(
                        key, "retryPayment", new PlaceRequest("biryani", 2), Placed.class, () -> run("biryani")))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void should_run_again_when_stored_key_has_expired() {
        place(new PlaceRequest("biryani", 2));
        expireAll();

        place(new PlaceRequest("biryani", 2));

        assertThat(runs).hasValue(2);
    }

    @Test
    void should_reject_concurrent_retry_while_first_request_is_still_running() throws Exception {
        var inside = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var principal = SecurityContextHolder.getContext().getAuthentication();
        var first = new Thread(() -> {
            SecurityContextHolder.getContext().setAuthentication(principal);
            idempotency.execute(key, "placeOrder", new PlaceRequest("slow", 1), Placed.class, () -> {
                inside.countDown();
                await(release);
                return run("slow");
            });
        });
        first.start();
        await(inside);

        assertThatThrownBy(() -> place(new PlaceRequest("slow", 1)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("still being processed");

        release.countDown();
        first.join();
        assertThat(place(new PlaceRequest("slow", 1)).item()).isEqualTo("slow");
        assertThat(runs).hasValue(1);
    }

    @Test
    void should_reject_missing_or_non_uuid_key() {
        assertThatThrownBy(() -> idempotency.execute(null, "placeOrder", "x", Placed.class, () -> run("x")))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> idempotency.execute("abc", "placeOrder", "x", Placed.class, () -> run("x")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void should_delete_only_expired_keys_when_cleanup_runs() {
        place(new PlaceRequest("biryani", 2));
        var mine = userId;
        expireAll();
        signInAs(UUID.randomUUID());
        key = UUID.randomUUID().toString();
        place(new PlaceRequest("fresh", 1));

        idempotency.deleteExpired();

        assertThat(records.findAll()).noneMatch(r -> r.getUserId().equals(mine));
        assertThat(records.findAll())
                .anyMatch(r -> r.getOperation().equals("placeOrder") && !r.isExpired(Instant.now()));
    }

    private Placed place(PlaceRequest request) {
        return idempotency.execute(key, "placeOrder", request, Placed.class, () -> run(request.item()));
    }

    private Placed run(String item) {
        runs.incrementAndGet();
        return new Placed(UUID.randomUUID(), item);
    }

    private void expireAll() {
        jdbc.sql("UPDATE idempotency_keys SET expires_at = now() - interval '1 second'")
                .update();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static void signInAs(UUID userId) {
        var principal = new AuthenticatedUser(userId, "CUSTOMER", Instant.now().plusSeconds(900));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
