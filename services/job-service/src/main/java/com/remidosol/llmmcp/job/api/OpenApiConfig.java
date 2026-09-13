package com.remidosol.llmmcp.job.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc metadata for the OpenAPI UI at /swagger-ui.html. Exists so the API is explorable
 * without reading code — the demo script opens this page.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "job-service",
        version = "v1",
        description = "Credit-based LLM job pipeline - public REST API. X-User-Id header is a temporary identity mechanism (see docs/adr)."))
class OpenApiConfig {
}
