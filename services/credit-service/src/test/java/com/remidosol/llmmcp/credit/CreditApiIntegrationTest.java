package com.remidosol.llmmcp.credit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** GET/topup contract (PRD §4.5) and the 5 s cache-aside behaviour (PRD §4.7). */
class CreditApiIntegrationTest extends AbstractIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void unknown_account_is_404_problem_json() {
        ResponseEntity<Map<String, Object>> response =
                rest.exchange("/api/credits/nobody-" + UUID.randomUUID(), HttpMethod.GET, HttpEntity.EMPTY, MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void topup_opens_the_account_and_get_reports_available_credits() {
        String user = "api-" + UUID.randomUUID();

        ResponseEntity<Map<String, Object>> topup = topUp(user, 40);
        assertThat(topup.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(topup.getBody()).containsEntry("balance", 40).containsEntry("reserved", 0).containsEntry("available", 40);

        ResponseEntity<Map<String, Object>> get = rest.exchange("/api/credits/" + user, HttpMethod.GET, HttpEntity.EMPTY, MAP);
        assertThat(get.getBody()).containsEntry("available", 40);
        assertThat(cacheManager.getCache("credits").get(user)).as("cached after GET").isNotNull();

        topUp(user, 10);
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(cacheManager.getCache("credits").get(user)).as("evicted by topup (after commit)").isNull());
        assertThat(rest.exchange("/api/credits/" + user, HttpMethod.GET, HttpEntity.EMPTY, MAP).getBody())
                .containsEntry("balance", 50);
    }

    @Test
    void invalid_topup_amount_is_400_problem_json() {
        ResponseEntity<Map<String, Object>> response = topUp("api-" + UUID.randomUUID(), 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private ResponseEntity<Map<String, Object>> topUp(String user, long amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/credits/" + user + "/topup", HttpMethod.POST,
                new HttpEntity<>("{\"amount\": " + amount + "}", headers), MAP);
    }
}
