package com.remidosol.llmmcp.credit.infrastructure.cache;

import com.remidosol.llmmcp.credit.application.CreditCacheEvictor;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

/** "credits" cache: 5 s TTL plus eviction on every write path via CreditCacheEvictor. */
@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    RedisCacheManagerBuilderCustomizer creditsCacheCustomizer() {
        return builder -> builder
                // evict/put AFTER the surrounding transaction commits: an eviction inside the transaction lets a
                // concurrent reader re-cache the not-yet-committed (old) row for the whole TTL (observed in e2e)
                .transactionAware()
                .withCacheConfiguration(
                CreditCacheEvictor.CREDITS_CACHE,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(5)));
    }
}
