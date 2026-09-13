package com.remidosol.llmmcp.job;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end HTTP behaviour of the REST API surface: happy paths return the job view, error
 * paths return RFC 9457 problem+json. Runs against real Postgres/Redis via Testcontainers.
 */
class JobApiIntegrationTest extends AbstractIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {
            };

    @Autowired
    private TestRestTemplate rest;

    @Test
    void create_returns_202_with_jobId_and_created_status() {
        ResponseEntity<Map<String, Object>> response = post("u1", """
                {"prompt": "hello there", "model": "fake:demo"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody())
                .containsEntry("status", "CREATED")
                .containsEntry("userId", "u1")
                .containsKey("jobId");
        assertThat((Integer) response.getBody().get("estimatedCredits")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void get_returns_the_created_job() {
        String jobId = (String) post("u1", """
                {"prompt": "fetch me back", "model": "fake:demo"}
                """).getBody().get("jobId");

        ResponseEntity<Map<String, Object>> response =
                rest.exchange("/api/jobs/" + jobId, HttpMethod.GET, HttpEntity.EMPTY, MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("jobId", jobId)
                .containsEntry("status", "CREATED");
    }

    @Test
    void list_returns_only_jobs_of_the_calling_user() {
        post("list-user-a", """
                {"prompt": "job of a", "model": "fake:demo"}
                """);
        post("list-user-b", """
                {"prompt": "job of b", "model": "fake:demo"}
                """);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "list-user-a");
        ResponseEntity<Map<String, Object>[]> response = rest.exchange(
                "/api/jobs", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>[]>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0]).containsEntry("userId", "list-user-a");
    }

    @Test
    void out_of_bounds_limit_is_400_problem_json() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "u1");
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/jobs?limit=0", HttpMethod.GET, new HttpEntity<>(headers), MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void missing_user_header_is_400_problem_json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/jobs", HttpMethod.POST,
                new HttpEntity<>("{\"prompt\": \"p\", \"model\": \"fake:demo\"}", headers), MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void invalid_body_is_400_problem_json() {
        ResponseEntity<Map<String, Object>> response = post("u1", """
                {"prompt": "", "model": "not-a-model-id"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void unknown_job_is_404_problem_json() {
        ResponseEntity<Map<String, Object>> response =
                rest.exchange("/api/jobs/" + UUID.randomUUID(), HttpMethod.GET, HttpEntity.EMPTY, MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("title", "Job not found");
    }

    private ResponseEntity<Map<String, Object>> post(String userId, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", userId);
        return rest.exchange("/api/jobs", HttpMethod.POST, new HttpEntity<>(body, headers), MAP);
    }
}
