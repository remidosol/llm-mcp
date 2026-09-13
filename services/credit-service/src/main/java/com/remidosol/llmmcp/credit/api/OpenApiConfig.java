package com.remidosol.llmmcp.credit.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** springdoc metadata for /swagger-ui.html. */
@Configuration
@OpenAPIDefinition(info = @Info(title = "credit-service", version = "v1",
        description = "Credit accounts and reservations. balance - reserved = available."))
class OpenApiConfig {
}
