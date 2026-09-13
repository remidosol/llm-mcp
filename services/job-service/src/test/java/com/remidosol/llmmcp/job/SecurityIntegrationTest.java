package com.remidosol.llmmcp.job;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR-0019: /api needs a key, health/docs are open, errors are problem+json. Uses a raw RestTemplate (no default key). */
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private final RestTemplate raw = new RestTemplate();

    @Test
    void api_without_key_is_401_problem_json() {
        ResponseEntity<String> response = call("/api/jobs/00000000-0000-0000-0000-000000000000", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(response.getBody()).contains("\"status\":401");
    }

    @Test
    void api_with_unknown_key_is_401() {
        assertThat(call("/api/jobs/00000000-0000-0000-0000-000000000000", "nope").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void api_with_user_key_passes_authentication() {
        // 404 (unknown job) proves the request reached the controller
        assertThat(call("/api/jobs/00000000-0000-0000-0000-000000000000", TestApiKeyConfiguration.USER_KEY).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void health_prometheus_and_docs_are_open_but_other_actuator_endpoints_are_not() {
        assertThat(call("/actuator/health/readiness", null).getStatusCode()).as("readiness").isEqualTo(HttpStatus.OK);
        assertThat(call("/actuator/prometheus", null).getStatusCode()).as("prometheus").isEqualTo(HttpStatus.OK);
        assertThat(call("/v3/api-docs", null).getStatusCode()).as("openapi").isEqualTo(HttpStatus.OK);
        assertThat(call("/actuator/metrics", null).getStatusCode()).as("metrics anonymous").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call("/actuator/metrics", TestApiKeyConfiguration.USER_KEY).getStatusCode()).as("metrics user").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call("/actuator/metrics", TestApiKeyConfiguration.ADMIN_KEY).getStatusCode()).as("metrics admin").isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> call(String path, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null) {
            headers.set("X-API-Key", apiKey);
        }
        try {
            return raw.exchange("http://localhost:" + port + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).headers(e.getResponseHeaders()).body(e.getResponseBodyAsString());
        }
    }
}
