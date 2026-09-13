package com.remidosol.llmmcp.job.infrastructure.cache;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

/**
 * Cache-aside setup for job reads (PRD §4.7): cache "jobs", TTL 60 s, evicted on every status
 * change. A customizer (rather than replacing the CacheManager) keeps Boot's auto-configuration —
 * metrics, further caches from properties — intact while pinning this one cache's TTL in code,
 * where the eviction points can reference its name.
 */
@Configuration
@EnableCaching
class CacheConfig {

    /** Cache name used by @Cacheable/@CacheEvict in the application layer (task 1.4). */
    public static final String JOBS_CACHE = "jobs";

    @Bean
    RedisCacheManagerBuilderCustomizer jobsCacheCustomizer() {
        return builder -> builder
                // evict/put AFTER the surrounding transaction commits: an eviction inside the transaction lets a
                // concurrent reader re-cache the not-yet-committed (old) row for the whole TTL (observed in e2e)
                .transactionAware()
                .withCacheConfiguration(
                JOBS_CACHE,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(60)));
    }
}
