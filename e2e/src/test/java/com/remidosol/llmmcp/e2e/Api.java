package com.remidosol.llmmcp.e2e;

import com.remidosol.llmmcp.contracts.ContractsJson;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/** Minimal REST client: the e2e suite talks to the services exactly like a user would. */
final class Api {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private Api() {
    }

    static UUID createJob(String userId, String prompt, String model) {
        String body = ContractsJson.MAPPER.writeValueAsString(java.util.Map.of("prompt", prompt, "model", model));
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(Env.JOB_URL + "/api/jobs"))
                .header("Content-Type", "application/json").header("X-User-Id", userId).header("X-API-Key", Env.API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        if (response.statusCode() != 202) {
            throw new AssertionError("POST /api/jobs -> " + response.statusCode() + " " + response.body());
        }
        return UUID.fromString(ContractsJson.MAPPER.readTree(response.body()).get("jobId").asString());
    }

    static JsonNode job(UUID jobId) {
        return json(Env.JOB_URL + "/api/jobs/" + jobId);
    }

    static String status(UUID jobId) {
        return job(jobId).get("status").asString();
    }

    static HttpResponse<String> result(UUID jobId) {
        return send(HttpRequest.newBuilder(URI.create(Env.JOB_URL + "/api/jobs/" + jobId + "/result"))
                .header("X-API-Key", Env.API_KEY).GET().build());
    }

    static JsonNode credits(String userId) {
        return json(Env.CREDIT_URL + "/api/credits/" + userId);
    }

    /** Opens/funds an isolated account so tests never share balances (topup is open in the local profile). */
    static String freshUser(long credits) {
        String userId = "e2e-" + UUID.randomUUID().toString().substring(0, 8);
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(Env.CREDIT_URL + "/api/credits/" + userId + "/topup"))
                .header("Content-Type", "application/json").header("X-API-Key", Env.ADMIN_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString("{\"amount\": " + credits + "}")).build());
        if (response.statusCode() != 200) {
            throw new AssertionError("topup -> " + response.statusCode() + " " + response.body());
        }
        return userId;
    }

    static double metric(String name) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(Env.JOB_URL + "/actuator/prometheus")).GET().build());
        return response.body().lines()
                .filter(line -> line.startsWith(name + " ") || line.startsWith(name + "{"))
                .mapToDouble(line -> Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
                .sum();
    }

    private static JsonNode json(String url) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url)).header("X-API-Key", Env.API_KEY).GET().build());
        if (response.statusCode() != 200) {
            throw new AssertionError("GET " + url + " -> " + response.statusCode() + " " + response.body());
        }
        return ContractsJson.MAPPER.readTree(response.body());
    }

    private static HttpResponse<String> send(HttpRequest request) {
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AssertionError("HTTP failed: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }
}
