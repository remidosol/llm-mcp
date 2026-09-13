package com.remidosol.llmmcp.job;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base class for full-context integration tests. All subclasses share ONE Spring context (same
 * configuration → Boot's context cache), so THEIR Postgres and Redis containers start once for
 * the whole group — no reuse flags, no static singletons. Note: slice tests like
 * {@code @DataJpaTest} form a second, separate context with their own containers; that cost is
 * accepted for slice isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.api-keys=test-user-key",
        "app.security.admin-api-keys=test-admin-key",
        "management.prometheus.metrics.export.enabled=true", // @SpringBootTest disables exporters; the security test probes /actuator/prometheus
        "app.saga.job-timeout=10s",      // watchdog tests wait ~10 s instead of 2 min
        "app.saga.watchdog-interval=1s",
        "logging.level.org.springframework.cache=TRACE" // eviction/put trail for the cache tests
})
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, TestApiKeyConfiguration.class})
public abstract class AbstractIntegrationTest {
}
