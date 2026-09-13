package com.remidosol.llmmcp.job;

import com.remidosol.llmmcp.job.application.JobTransitionService;
import com.remidosol.llmmcp.job.domain.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves the cache-aside contract of task 1.4: a GET populates the "jobs" cache, a status change
 * evicts it. Scaffolded by Claude; enable after wiring the annotations.
 */
class JobCacheIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private JobTransitionService jobTransitionService;

    @Test
    void get_populates_the_jobs_cache() {
        UUID jobId = createJob();

        get(jobId); // first read: DB, then cache write

        assertThat(jobsCache().get(jobId)).as("cache entry after first GET").isNotNull();
    }

    @Test
    void status_change_evicts_the_cache_entry() {
        UUID jobId = createJob();
        get(jobId);
        assertThat(jobsCache().get(jobId)).isNotNull();

        jobTransitionService.transition(jobId, JobStatus.CREDIT_RESERVED);

        // the cache manager is transaction-aware: the eviction runs right after the commit, not inside the
        // call — so "evicted" is an eventually-true condition measured in milliseconds, not an instant one
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(jobsCache().get(jobId)).as("cache entry after eviction").isNull());
    }

    private Cache jobsCache() {
        Cache cache = cacheManager.getCache("jobs");
        assertThat(cache).as("'jobs' cache must exist").isNotNull();
        return cache;
    }

    private UUID createJob() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "cache-user");
        Map<String, Object> body = rest.exchange(
                "/api/jobs", HttpMethod.POST,
                new HttpEntity<>("{\"prompt\": \"cache me\", \"model\": \"fake:demo\"}", headers),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return UUID.fromString((String) Objects.requireNonNull(body).get("jobId"));
    }

    private void get(UUID jobId) {
        rest.exchange("/api/jobs/" + jobId, HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }
}
