package com.remidosol.llmmcp.credit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR-0019: top-up is admin-only; reads need any key; anonymous is 401. */
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private final RestTemplate raw = new RestTemplate();

    @Test
    void topup_requires_the_admin_key() {
        String user = "sec-" + UUID.randomUUID();

        assertThat(call(HttpMethod.POST, "/api/credits/" + user + "/topup", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<String> forbidden = call(HttpMethod.POST, "/api/credits/" + user + "/topup", TestApiKeyConfiguration.USER_KEY);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getHeaders().getContentType()).isNotNull();
        assertThat(forbidden.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(call(HttpMethod.POST, "/api/credits/" + user + "/topup", TestApiKeyConfiguration.ADMIN_KEY).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/api/credits/" + user, TestApiKeyConfiguration.USER_KEY).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null) {
            headers.set("X-API-Key", apiKey);
        }
        try {
            return raw.exchange("http://localhost:" + port + path, method, new HttpEntity<>("{\"amount\": 5}", headers), String.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).headers(e.getResponseHeaders()).body(e.getResponseBodyAsString());
        }
    }
}
