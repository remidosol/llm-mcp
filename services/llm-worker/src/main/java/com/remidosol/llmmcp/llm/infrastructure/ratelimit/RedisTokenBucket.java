package com.remidosol.llmmcp.llm.infrastructure.ratelimit;

import com.remidosol.llmmcp.llm.domain.LlmCallException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cluster-wide token bucket per provider, in Redis (PRD §4.7/4.8, ADR-0018). The Lua script does
 * refill + take ATOMICALLY on the Redis side — two worker replicas cannot both take the last token.
 * A caller waits up to {@code max-wait} for a token, then is shed with a retryable failure and
 * {@code ratelimit.rejected{provider}} increments: back-pressure first, load shedding second.
 */
@Component
public class RedisTokenBucket {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucket.class);
    private static final Duration POLL = Duration.ofMillis(100);

    /** KEYS[1]=bucket, ARGV: capacity, refillPerSecond, nowMillis, requested → 1 (granted) | 0 */
    static final String LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            local data = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then tokens = capacity; ts = now end
            local elapsed = math.max(0, now - ts) / 1000.0
            tokens = math.min(capacity, tokens + elapsed * refill)
            local granted = 0
            if tokens >= requested then tokens = tokens - requested; granted = 1 end
            redis.call('HSET', key, 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', key, math.ceil(capacity / refill * 1000) + 60000)
            return granted
            """;

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final MeterRegistry metrics;
    private final DefaultRedisScript<Long> script;

    public RedisTokenBucket(StringRedisTemplate redis, RateLimitProperties properties, MeterRegistry metrics) {
        this.redis = redis;
        this.properties = properties;
        this.metrics = metrics;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
    }

    /** Blocks (virtual thread) until a token is granted or {@code max-wait} elapses, then sheds. */
    public void acquire(String provider) {
        RateLimitProperties.Bucket bucket = properties.providers() == null ? null : properties.providers().get(provider);
        if (bucket == null) {
            return;
        }
        Instant deadline = Instant.now().plus(properties.maxWait());
        while (true) {
            if (tryAcquire(provider, bucket)) {
                return;
            }
            if (!Instant.now().isBefore(deadline)) {
                metrics.counter("ratelimit.rejected", "provider", provider).increment();
                log.warn("rate limit for {} exceeded after waiting {} — shedding", provider, properties.maxWait());
                throw new LlmCallException(provider + " rate limit exceeded (capacity " + bucket.capacity()
                        + ", refill " + bucket.refillPerSecond() + "/s)", true);
            }
            sleep(POLL);
        }
    }

    boolean tryAcquire(String provider, RateLimitProperties.Bucket bucket) {
        Long granted = redis.execute(script, List.of("ratelimit:" + provider),
                String.valueOf(bucket.capacity()), String.valueOf(bucket.refillPerSecond()),
                String.valueOf(System.currentTimeMillis()), "1");
        return granted != null && granted == 1L;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmCallException("interrupted while waiting for a rate-limit token", true, e);
        }
    }
}
