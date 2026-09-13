# Verified versions

Rule (PRD §0.5, Appendix A): nothing in this table is used from memory — every value was read from
an official source on the date given. Update this file BEFORE first use of a new component.

Verified 2026-08-29 unless noted.

## Framework and libraries

| Item | Verified value | Source |
|---|---|---|
| Spring Boot | 4.1.1 (latest GA; 4.2 only at M1) | <https://github.com/spring-projects/spring-boot/releases> |
| Supported JDK for Boot 4.1.1 | Java 17 min, up to and including 26; project targets 21 LTS | <https://docs.spring.io/spring-boot/system-requirements.html> |
| Jackson | Jackson 3 (`tools.jackson`) is the Boot 4 default; auto-configured entry point is `JsonMapper` | Boot 4.0 migration guide |
| Spring Kafka | 4.1.1 (Boot-managed); kafka-clients 4.2.1 | Boot 4.1.1 `spring-boot-dependencies` |
| Kafka serializers (Jackson 3) | `org.springframework.kafka.support.serializer.JacksonJsonSerializer` / `JacksonJsonDeserializer` (Jackson 2 variants deprecated) | spring-kafka 4.1 javadoc + change history |
| Kafka error handling | `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` + `ExponentialBackOffWithMaxRetries` — current API in 4.1 | <https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html> |
| Kafka observation | `spring.kafka.template.observation-enabled` / `spring.kafka.listener.observation-enabled` (default false) | `KafkaProperties.java` @ v4.1.1 tag |
| Producer idempotence | no dedicated Boot key — `spring.kafka.producer.properties.enable.idempotence=true`; `spring.kafka.producer.acks` exists | `KafkaProperties.java` @ v4.1.1 tag |
| Spring AI | 2.0.1 (latest 2.0.x) | <https://github.com/spring-projects/spring-ai/releases> |
| MCP server starter | `spring-ai-starter-mcp-server-webmvc`; annotations in `org.springframework.ai.mcp.annotation` (`@McpTool` `@McpResource` `@McpPrompt` `@McpComplete` `@McpToolParam` `@McpArg`) | <https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html> |
| MCP server config | `spring.ai.mcp.server.protocol=SSE\|STREAMABLE\|STATELESS`; `spring.ai.mcp.server.streamable-http.mcp-endpoint` (default `/mcp`); stateless: `spring.ai.mcp.server.stateless.mcp-endpoint` | <https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html> |
| Gemini starter | `spring-ai-starter-model-google-genai`; key `spring.ai.google.genai.api-key` | <https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html> |
| OpenAI starter | `spring-ai-starter-model-openai`; `spring.ai.openai.api-key`; `spring.ai.openai.base-url` overridable (WireMock) | <https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html> |
| Spring AI 2.0.1 model construction (verified from jars 2026-09-13) | OpenAI: `OpenAiChatModel.builder().openAiClient(com.openai.client.OpenAIClient)` — the official openai-java SDK (`openai-java-core` 4.49.0); client = `new OpenAIClientImpl(ClientOptions.builder().httpClient(SpringAiOpenAiHttpClient.builder().build()).apiKey().baseUrl().maxRetries(0).build())`; options `OpenAiChatOptions.builder().maxTokens().model(String)`. Gemini: `GoogleGenAiChatModel.builder().genAiClient(com.google.genai.Client.builder().apiKey().build())`; options `GoogleGenAiChatOptions.builder().maxOutputTokens().model(String)`. `Usage.getPromptTokens()/getCompletionTokens()` return `Integer`. Spring AI 2.0 has NO `TransientAiException`/`NonTransientAiException` — classify via SDK exceptions (`OpenAIServiceException.statusCode()`, `com.google.genai.errors.ApiException.code()`). `spring.ai.model.chat=none` disables chat auto-config. | `~/.m2` jars (javap), `OpenAiChatAutoConfiguration` |
| ChatClient with multiple models | `spring.ai.chat.client.enabled=false` verified in source; 2.0 docs now prefer `@Primary` / `ChatClientBuilderConfigurer` — decide in ADR-0017 era | `ChatClientAutoConfiguration.java` (spring-ai main) |
| Testcontainers | 2.0.5 (managed by Boot BOM); artifacts renamed `testcontainers-postgresql` / `-kafka` / `-junit-jupiter`; containers NOT generic anymore (raw `PostgreSQLContainer`); `org.testcontainers.kafka.KafkaContainer` supports `apache/kafka` + `apache/kafka-native` | <https://github.com/testcontainers/testcontainers-java/releases>, <https://java.testcontainers.org/modules/kafka/> |
| `@ServiceConnection` for Redis | plain `GenericContainer` requires `@ServiceConnection(name = "redis")` | <https://docs.spring.io/spring-boot/reference/testing/testcontainers.html> |
| ArchUnit | 1.5.0 (`com.tngtech.archunit:archunit-junit5`) | <https://github.com/TNG/ArchUnit/releases> |
| Awaitility | 4.3.0 (Boot-managed — no override) | Boot 4.1.1 `spring-boot-dependencies` |
| WireMock | 3.13.1, use `org.wiremock:wiremock-standalone` (shades Jackson 2/Jetty; WireMock has no Jackson 3 support yet — issue #3254) | <https://github.com/wiremock/wiremock/releases> |
| json-schema-validator (networknt) | 3.0.5 — Jackson-3 native line (3.0.x depends on `tools.jackson`); 3.0.5 is compiled against Jackson 3.1.1, matching Boot 4.1.1's managed 3.1.5; latest 3.0.7 targets 3.2.1 (not yet managed by Boot) | Maven Central poms, 2026-09-13 |
| springdoc | 3.1.0 (`springdoc-openapi-starter-webmvc-ui`; 3.x = Boot 4 line) | <https://springdoc.org/> |
| Resilience4j | 2.4.0 — use `io.github.resilience4j:resilience4j-spring-boot4` (new module in 2.4.0, its only version; `spring-boot3` module fails fast on Boot 4) | <https://github.com/resilience4j/resilience4j/releases>, PR #2384 |
| maven-enforcer-plugin | 3.6.3 | Maven Central maven-metadata.xml |
| Lombok on JDK 26 | **unverified** — the enforcer's `[21,26)` upper bound is precautionary (Lombok patches javac internals and lags new JDKs); verify before lifting the bound (ADR-0011) | — |
| GitHub Actions | `actions/checkout@v7` (v7.0.1), `actions/setup-java@v6` (v6.0.0) | GitHub releases API |

## Container images (pin exact tags; `:latest` is banned)

| Image | Tag | Source |
|---|---|---|
| `apache/kafka` | 4.3.1 (KRaft single-node combined works out of the box; overriding ANY env var discards ALL default config) | Docker Hub |
| `apache/kafka-native` | 4.3.1 — tests only (GraalVM native, fast startup; not production); tag verified 2026-09-13 | Docker Hub |
| `postgres` | 17.11 | Docker Hub |
| `redis` | 8.10.1 (8.x is current stable; satisfies "Redis 7+"; RSALv2/SSPLv1/AGPLv3 tri-license — fine for local/dev) | Docker Hub |
| `ghcr.io/kafbat/kafka-ui` | v1.5.0 | <https://github.com/kafbat/kafka-ui/releases> |
| `quay.io/debezium/connect` | 3.6.1.Final | quay.io API |
| `grafana/otel-lgtm` | 0.32.0 (ports 3000 Grafana, 4317 OTLP gRPC, 4318 OTLP HTTP) | Docker Hub + <https://github.com/grafana/docker-otel-lgtm> |

## Boot 4 relocations discovered while building (not in the migration guide tables)

| Class | Boot 4 location |
|---|---|
| `RedisCacheManagerBuilderCustomizer` | `org.springframework.boot.cache.autoconfigure` |
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `TestRestTemplate` | `org.springframework.boot.resttestclient` (+`.autoconfigure.AutoConfigureTestRestTemplate`); included in `spring-boot-starter-webmvc-test` |

## Corrections to PRD assumptions (recorded per PRD §0.1)

- **Boot 4.1 has NO Kafka Docker Compose service connection** (regression vs Boot 3.2–3.5; verified against the 4.1.1 dev-services table and Boot source). PRD §7 assumed auto-wiring → `spring.kafka.bootstrap-servers` is set explicitly from Phase 2 on, and the compose kafka service carries `org.springframework.boot.ignore: "true"`. Testcontainers `@ServiceConnection` still auto-wires Kafka in tests.
- **Debezium outbox SMT option key is `table.op.invalid.behavior`** (PRD Appendix B sketch says `op.invalid.behavior`, which does not exist in the 3.6 docs).
- **Strimzi is now versioned 1.x** (1.2.0; KRaft-only, `KafkaNodePool` required; ZooKeeper support removed since 0.46).
- **Debezium 3.6 `snapshot.mode` values** are `initial`, `initial_only`, `no_data`, `always`, … (older `never`/`schema_only` names are gone); outbox connectors use `no_data` so already-published rows are not re-emitted (verified 2026-09-13 on the 3.6 Postgres connector page).
- **CloudNativePG defaults `wal_level=logical`** — no extra config needed for Debezium (v1.30.0; `Database` CRD exists).

## Phase 5 verifications (2026-09-13, from the resolved jars in ~/.m2 unless noted)

| Item | Value | Where verified |
|---|---|---|
| Boot 4 AOP starter | `spring-boot-starter-aspectj` (there is no `spring-boot-starter-aop` in the 4.1.1 BOM) | `spring-boot-dependencies-4.1.1.pom` |
| Boot 4 health API | `org.springframework.boot.health.contributor.{Health,HealthIndicator,Status}` (module `spring-boot-health`) | jar listing |
| Resilience4j | `resilience4j-spring-boot4:2.4.0`; properties `resilience4j.retry.configs.default.retry-exception-predicate=<class>`, `resilience4j.circuitbreaker.configs.default.ignore-exception-predicate=<class>`, `register-health-indicator`; the `/actuator/health` `circuitBreakers` component additionally needs `management.health.circuitbreakers.enabled=true` (`@ConditionalOnProperty`, no matchIfMissing); `Decorators` lives in `resilience4j-all`, which the starter does not pull — compose with `Retry.decorateSupplier(CircuitBreaker.decorateSupplier(...))` | https://resilience4j.readme.io/docs/getting-started-3 (Boot starter section) + build |
| MCP Java SDK | 2.0.0 (`io.modelcontextprotocol.sdk:mcp`, `mcp-json-jackson3`) pulled by `spring-ai-starter-mcp-server-webmvc:2.0.1` | dependency tree |
| MCP stateless endpoint property | `spring.ai.mcp.server.protocol=STATELESS` reads `spring.ai.mcp.server.streamable-http.mcp-endpoint` (default `/mcp`); no `stateless.*` prefix exists | `McpServerStatelessWebMvcAutoConfiguration` bytecode (javap) |
| MCP annotation return types | `@McpComplete` → `CompleteResult`/`CompleteCompletion`/`List<String>`/`String`; `@McpResource` → `ReadResourceResult`/`List<ResourceContents>`/`ResourceContents`/`String`; `@McpPrompt` → `GetPromptResult`/`List<PromptMessage>`/`PromptMessage`/`String` | `Sync*MethodCallback.validateReturnType` bytecode |
| WireMock | `org.wiremock:wiremock-standalone:3.13.1`; API package still `com.github.tomakehurst.wiremock` | build |
| Structured logging | `logging.structured.format.console=ecs` (Boot ≥ 3.4) | https://docs.spring.io/spring-boot/reference/features/logging.html#features.logging.structured |
| MCP Inspector CLI | `npx @modelcontextprotocol/inspector --cli <url> --transport http --method tools/list` | https://github.com/modelcontextprotocol/inspector#cli-mode |

## Phase 4+ items still to verify before first use

- Spring Data Redis 4 Jackson-3 JSON cache serializer class (cache currently uses JDK serialization)
- Jib 3.5.2 config keys (Phase 6), Strimzi `KafkaConnect.spec.build` field names (Phase 6), CDKTN setup (Phase 7), `grafana/otel-lgtm` OTLP property keys for Boot 4 (Phase 7), Helm 4.2 compatibility with the Strimzi chart (Phase 6)
