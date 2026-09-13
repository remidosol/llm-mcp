package com.remidosol.llmmcp.llm.infrastructure.ratelimit;

import com.remidosol.llmmcp.llm.AbstractIntegrationTest;
import com.remidosol.llmmcp.llm.domain.LlmCallException;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** The Lua bucket: capacity, refill over time, and shedding after max-wait. */
class RedisTokenBucketIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private MeterRegistry metrics;

    @Test
    void takes_up_to_capacity_then_refills_over_time() {
        String provider = "bucket-" + UUID.randomUUID();
        RedisTokenBucket bucket = new RedisTokenBucket(redis,
                new RateLimitProperties(Duration.ZERO, Map.of(provider, new RateLimitProperties.Bucket(2, 5.0))), metrics);
        RateLimitProperties.Bucket cfg = new RateLimitProperties.Bucket(2, 5.0);

        assertThat(bucket.tryAcquire(provider, cfg)).isTrue();
        assertThat(bucket.tryAcquire(provider, cfg)).isTrue();
        assertThat(bucket.tryAcquire(provider, cfg)).as("bucket empty").isFalse();

        await().atMost(Duration.ofSeconds(2)).until(() -> bucket.tryAcquire(provider, cfg)); // 5 tokens/s refill
    }

    @Test
    void sheds_with_a_retryable_failure_and_a_metric_when_max_wait_elapses() {
        String provider = "shed-" + UUID.randomUUID();
        RedisTokenBucket bucket = new RedisTokenBucket(redis,
                new RateLimitProperties(Duration.ofMillis(300), Map.of(provider, new RateLimitProperties.Bucket(1, 0.1))), metrics);
        double before = metrics.counter("ratelimit.rejected", "provider", provider).count();

        bucket.acquire(provider); // the single token
        assertThatThrownBy(() -> bucket.acquire(provider))
                .isInstanceOf(LlmCallException.class)
                .satisfies(e -> assertThat(((LlmCallException) e).isRetryable()).isTrue());
        assertThat(metrics.counter("ratelimit.rejected", "provider", provider).count()).isEqualTo(before + 1);
    }

    @Test
    void unknown_providers_are_not_limited() {
        new RedisTokenBucket(redis, new RateLimitProperties(Duration.ZERO, Map.of()), metrics).acquire("unlimited");
    }
}
