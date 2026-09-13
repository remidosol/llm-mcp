package com.remidosol.llmmcp.job.application;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Carries {@code @CacheEvict} for every saga write path. Cache annotations only work through the
 * Spring proxy, so the saga service calls this bean instead of annotating its own inner methods.
 */
@Component
public class JobCacheEvictor {

    @CacheEvict(cacheNames = "jobs", key = "#jobId")
    public void evict(UUID jobId) {
        // the annotation is the implementation
    }
}
