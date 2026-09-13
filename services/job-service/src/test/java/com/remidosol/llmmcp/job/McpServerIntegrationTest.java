package com.remidosol.llmmcp.job;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Talks JSON-RPC 2.0 to the stateless MCP endpoint the way any client library does under the hood:
 * one POST per call, no session, no SSE. Verifies the spec surface (tools, resource template,
 * prompt, completion) and that errors come back as tool errors, not HTTP failures.
 */
class McpServerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JsonMapper json;

    @Test
    void lists_the_three_tools_the_resource_template_and_the_prompt() {
        JsonNode tools = rpc("tools/list", Map.of()).path("result").path("tools");
        assertThat(tools.valueStream().map(t -> t.path("name").asString()))
                .containsExactlyInAnyOrder("create_job", "get_job", "list_jobs");
        assertThat(tools.valueStream().filter(t -> t.path("name").asString().equals("create_job")).findFirst().orElseThrow()
                .path("inputSchema").path("required").valueStream().map(JsonNode::asString))
                .containsExactlyInAnyOrder("userId", "prompt", "model");

        JsonNode templates = rpc("resources/templates/list", Map.of()).path("result").path("resourceTemplates");
        assertThat(templates.valueStream().map(t -> t.path("uriTemplate").asString())).contains("job://{jobId}/result");

        JsonNode prompts = rpc("prompts/list", Map.of()).path("result").path("prompts");
        assertThat(prompts.valueStream().map(p -> p.path("name").asString())).contains("compare_models");
    }

    @Test
    void create_job_tool_creates_a_job_that_get_job_and_list_jobs_see() {
        String user = "mcp-" + UUID.randomUUID();
        JsonNode created = call("create_job", Map.of("userId", user, "prompt", "hello from mcp", "model", "fake:demo"));

        assertThat(created.path("isError").asBoolean(false)).isFalse();
        JsonNode job = json.readTree(created.path("content").get(0).path("text").asString());
        assertThat(job.path("status").asString()).isEqualTo("CREATED");
        String jobId = job.path("jobId").asString();

        JsonNode fetched = json.readTree(call("get_job", Map.of("jobId", jobId)).path("content").get(0).path("text").asString());
        assertThat(fetched.path("userId").asString()).isEqualTo(user);

        JsonNode listed = json.readTree(call("list_jobs", Map.of("userId", user)).path("content").get(0).path("text").asString());
        assertThat(listed.valueStream().map(j -> j.path("jobId").asString())).containsExactly(jobId);
    }

    @Test
    void invalid_arguments_come_back_as_tool_errors_not_http_errors() {
        JsonNode result = call("create_job", Map.of("userId", "u1", "prompt", "x", "model", "not-a-model"));

        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").get(0).path("text").asString()).contains("model");

        JsonNode notFound = call("get_job", Map.of("jobId", UUID.randomUUID().toString()));
        assertThat(notFound.path("isError").asBoolean()).isTrue();
    }

    @Test
    void result_resource_is_an_error_until_the_job_completes() {
        JsonNode created = call("create_job", Map.of("userId", "u1", "prompt", "resource test", "model", "fake:demo"));
        String jobId = json.readTree(created.path("content").get(0).path("text").asString()).path("jobId").asString();

        JsonNode response = rpc("resources/read", Map.of("uri", "job://" + jobId + "/result"));

        assertThat(response.has("error")).as("JSON-RPC error for a not-yet-completed job: %s", response).isTrue();
    }

    @Test
    void prompt_and_completion_work() {
        JsonNode prompt = rpc("prompts/get", Map.of("name", "compare_models",
                "arguments", Map.of("prompt", "What is a saga?", "modelA", "fake:demo", "modelB", "fake:echo"))).path("result");
        assertThat(prompt.path("messages").get(0).path("content").path("text").asString())
                .contains("create_job").contains("fake:demo").contains("fake:echo").contains("What is a saga?");

        JsonNode completion = rpc("completion/complete", Map.of(
                "ref", Map.of("type", "ref/prompt", "name", "compare_models"),
                "argument", Map.of("name", "modelA", "value", "ope"))).path("result").path("completion").path("values");
        assertThat(completion.valueStream().map(JsonNode::asString)).containsExactly("openai:gpt-4o-mini");
    }

    @Test
    void mcp_endpoint_requires_an_api_key_unless_opened_by_profile() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        headers.set("X-API-Key", "wrong");
        ResponseEntity<String> response = rest.postForEntity("/mcp",
                new HttpEntity<>("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private JsonNode call(String tool, Map<String, Object> arguments) {
        JsonNode response = rpc("tools/call", Map.of("name", tool, "arguments", arguments));
        assertThat(response.has("error")).as("unexpected JSON-RPC error: %s", response).isFalse();
        return response.path("result");
    }

    private JsonNode rpc(String method, Map<String, Object> params) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        String body = json.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params));
        ResponseEntity<String> response = rest.postForEntity("/mcp", new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("%s -> %s %s", method, response.getStatusCode(), response.getBody()).isTrue();
        String payload = response.getBody();
        if (payload != null && payload.startsWith("event:") || payload != null && payload.contains("\ndata:")) {
            payload = payload.lines().filter(l -> l.startsWith("data:")).map(l -> l.substring(5).strip()).findFirst().orElse(payload);
        }
        return json.readTree(payload);
    }
}
