package com.remidosol.llmmcp.credit;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** One shared context for all full-stack tests: containers start once per run. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.api-keys=test-user-key",
        "app.security.admin-api-keys=test-admin-key"
})
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, TestApiKeyConfiguration.class})
public abstract class AbstractIntegrationTest {
}
