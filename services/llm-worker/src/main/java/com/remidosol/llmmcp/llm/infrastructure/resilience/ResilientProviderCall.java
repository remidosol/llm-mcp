package com.remidosol.llmmcp.llm.infrastructure.resilience;

import com.remidosol.llmmcp.llm.application.port.LlmProvider;
import com.remidosol.llmmcp.llm.application.port.ProviderGateway;
import com.remidosol.llmmcp.llm.domain.LlmCallException;
import com.remidosol.llmmcp.llm.domain.LlmResult;
import com.remidosol.llmmcp.llm.infrastructure.ratelimit.RedisTokenBucket;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The resilience stack around a provider call (ADR-0018), outermost first: Redis token bucket →
 * retry → circuit breaker → provider. Instances are looked up by provider name, so each provider
 * gets the policy from {@code resilience4j.*.instances.<name>} without annotations or static
 * names. Every retry attempt passes the breaker; a breaker that is open fails fast with a
 * retryable failure so the saga compensates instead of waiting.
 */
@Component
public class ResilientProviderCall implements ProviderGateway {

    private static final Logger log = LoggerFactory.getLogger(ResilientProviderCall.class);

    private final RetryRegistry retries;
    private final CircuitBreakerRegistry breakers;
    private final RedisTokenBucket rateLimiter;

    public ResilientProviderCall(RetryRegistry retries, CircuitBreakerRegistry breakers, RedisTokenBucket rateLimiter) {
        this.retries = retries;
        this.breakers = breakers;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public Outcome call(LlmProvider provider, String model, String prompt, int maxTokens) {
        rateLimiter.acquire(provider.name()); // sheds with a retryable LlmCallException when the bucket is dry
        Retry retry = retries.retry(provider.name());
        CircuitBreaker breaker = breakers.circuitBreaker(provider.name());
        AtomicInteger tries = new AtomicInteger();
        Supplier<LlmResult> guarded = CircuitBreaker.decorateSupplier(breaker,
                () -> provider.complete(model, prompt, maxTokens, tries.incrementAndGet()));
        Supplier<LlmResult> decorated = Retry.decorateSupplier(retry, guarded); // retry OUTSIDE the breaker
        try {
            return new Outcome(decorated.get(), tries.get());
        } catch (CallNotPermittedException e) {
            log.warn("circuit breaker {} is {} — failing fast", provider.name(), breaker.getState());
            throw new LlmCallException(provider.name() + " circuit breaker open", true, e);
        } catch (LlmCallException e) {
            throw e.withTries(Math.max(1, tries.get()));
        }
    }
}
