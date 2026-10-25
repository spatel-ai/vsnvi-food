package com.vsnvi.vsnvi_food.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.vsnvi.vsnvi_food.TestcontainersConfiguration;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RateLimiterTests {

    private final RateLimiter limiter;
    private final StringRedisTemplate redis;

    RateLimiterTests(@Autowired RateLimiter limiter, @Autowired StringRedisTemplate redis) {
        this.limiter = limiter;
        this.redis = redis;
    }

    @Test
    void should_allow_up_to_limit_then_block_with_retry_after_within_window() {
        var key = "test:" + UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.hit(key, 3, Duration.ofMinutes(1)).allowed()).isTrue();
        }
        var blocked = limiter.hit(key, 3, Duration.ofMinutes(1));

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));
        assertThatThrownBy(blocked::orThrow).isInstanceOf(RateLimitedException.class);
        assertThat(redis.getExpire(RateLimiter.PREFIX + key)).isPositive();
    }

    @Test
    void should_count_keys_independently_and_peek_without_counting() {
        var a = "test:" + UUID.randomUUID();
        var b = "test:" + UUID.randomUUID();
        limiter.hit(a, 1, Duration.ofMinutes(1));

        assertThat(limiter.peek(a, 1, Duration.ofMinutes(1)).allowed()).isFalse();
        assertThat(limiter.peek(b, 1, Duration.ofMinutes(1)).allowed()).isTrue();
        assertThat(limiter.peek(b, 1, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void should_allow_again_when_reset_or_window_has_passed() {
        var reset = "test:" + UUID.randomUUID();
        limiter.hit(reset, 1, Duration.ofMinutes(1));
        limiter.reset(reset);
        assertThat(limiter.hit(reset, 1, Duration.ofMinutes(1)).allowed()).isTrue();

        var expiring = "test:" + UUID.randomUUID();
        limiter.hit(expiring, 1, Duration.ofMillis(300));
        assertThat(limiter.hit(expiring, 1, Duration.ofMillis(300)).allowed()).isFalse();
        await().atMost(Duration.ofSeconds(3))
                .until(() -> limiter.peek(expiring, 1, Duration.ofMillis(300)).allowed());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_fail_open_when_redis_is_unavailable() {
        var broken = mock(StringRedisTemplate.class);
        given(broken.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyString()))
                .willThrow(new RedisConnectionFailureException("down"));

        assertThat(new RateLimiter(broken).hit("k", 1, Duration.ofMinutes(1)).allowed())
                .isTrue();
    }

    @Test
    void should_round_retry_after_up_to_whole_seconds() {
        assertThat(new RateLimitedException(Duration.ofMillis(1200)).retryAfterSeconds())
                .isEqualTo(2);
        assertThat(new RateLimitedException(Duration.ZERO).retryAfterSeconds()).isEqualTo(1);
    }
}
