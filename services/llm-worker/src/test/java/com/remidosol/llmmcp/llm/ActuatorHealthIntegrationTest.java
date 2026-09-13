package com.remidosol.llmmcp.llm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** PRD §4.10: readiness = dependencies answer; breaker state is visible in health and metrics. */
class ActuatorHealthIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void readiness_group_includes_db_redis_and_kafka() throws IOException, InterruptedException {
        JsonNode readiness = get("/actuator/health/readiness");

        assertThat(readiness.path("status").asString()).isEqualTo("UP");
        assertThat(readiness.path("components").propertyNames()).contains("db", "redis", "kafka", "readinessState");
        assertThat(readiness.path("components").path("kafka").path("details").path("nodes").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void health_exposes_one_circuit_breaker_per_provider() throws IOException, InterruptedException {
        JsonNode breakers = get("/actuator/health").path("components").path("circuitBreakers").path("details");

        assertThat(breakers.propertyNames()).contains("fake", "openai", "gemini");
        assertThat(breakers.path("fake").path("details").path("state").asString()).isEqualTo("CLOSED");
    }

    private JsonNode get(String path) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s -> %s", path, response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }
}
