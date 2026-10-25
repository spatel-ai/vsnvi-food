package com.vsnvi.vsnvi_food.common.ratelimit;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counters in Redis under {@code ratelimit:*} (docs/DATABASE-DESIGN.md §5). Each counter expires with
 * its window, so losing Redis only resets limits. If Redis is unreachable the limiter fails open and logs a warning:
 * an outage must not take login down with it.
 *
 * <p>Keys must never contain personal data; hash emails or phone numbers first.
 */
@Slf4j
@Component
public class RateLimiter {

    static final String PREFIX = "ratelimit:";

    /** INCR, set the expiry on the first hit, return [count, remaining ms]. Atomic, so concurrent hits can't race. */
    @SuppressWarnings("rawtypes") // Redis returns a mixed-type array
    private static final RedisScript<List> HIT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return {count, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Counts one hit and decides whether it is within {@code limit} for the current window. */
    public Decision hit(String key, int limit, Duration window) {
        try {
            var result = redis.execute(HIT, List.of(PREFIX + key), String.valueOf(window.toMillis()));
            long count = ((Number) result.get(0)).longValue();
            long ttlMillis = ((Number) result.get(1)).longValue();
            return new Decision(count <= limit, retryAfter(ttlMillis, window));
        } catch (RuntimeException ex) {
            log.warn("Rate limiter unavailable, allowing request: {}", ex.getMessage());
            return Decision.ALLOWED;
        }
    }

    /** Checks without counting: whether earlier hits already reached {@code limit}. */
    public Decision peek(String key, int limit, Duration window) {
        try {
            var value = redis.opsForValue().get(PREFIX + key);
            if (value == null || Long.parseLong(value) < limit) {
                return Decision.ALLOWED;
            }
            var ttl = redis.getExpire(PREFIX + key);
            return new Decision(false, retryAfter(ttl == null ? -1 : ttl * 1000, window));
        } catch (RuntimeException ex) {
            log.warn("Rate limiter unavailable, allowing request: {}", ex.getMessage());
            return Decision.ALLOWED;
        }
    }

    public void reset(String key) {
        try {
            redis.delete(PREFIX + key);
        } catch (RuntimeException ex) {
            log.warn("Rate limiter unavailable, couldn't reset a counter: {}", ex.getMessage());
        }
    }

    private static Duration retryAfter(long ttlMillis, Duration window) {
        return ttlMillis > 0 ? Duration.ofMillis(ttlMillis) : window;
    }

    /** {@code retryAfter} is meaningful only when not allowed. */
    public record Decision(boolean allowed, Duration retryAfter) {

        static final Decision ALLOWED = new Decision(true, Duration.ZERO);

        public void orThrow() {
            if (!allowed) {
                throw new RateLimitedException(retryAfter);
            }
        }
    }
}
