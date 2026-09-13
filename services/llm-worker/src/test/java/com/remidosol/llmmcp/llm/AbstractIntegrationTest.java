package com.remidosol.llmmcp.llm;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** One shared context; the fake provider runs fast and with failure injection enabled. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.llm.fake.failure-injection=true",
        "app.llm.fake.latency=50ms",
        "app.llm.fake.slow-delay=3s",
        "resilience4j.retry.configs.default.wait-duration=50ms" // [FLAKY] retries in ms, not seconds
})
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {
}
