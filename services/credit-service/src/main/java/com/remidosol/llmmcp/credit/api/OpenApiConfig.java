package com.remidosol.llmmcp.credit.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc metadata for /swagger-ui.html. The API-key security scheme makes the "Authorize" button
 * appear so every "Try it out" call carries the X-API-Key header (ADR-0019).
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "credit-service", version = "v1", description = "Credit accounts and reservations. balance - reserved = available. Reads need a user key, top-up needs an admin key (X-API-Key)."),
        security = @SecurityRequirement(name = OpenApiConfig.API_KEY))
@SecurityScheme(name = OpenApiConfig.API_KEY, type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER,
        paramName = "X-API-Key", description = "APP_API_KEYS (user) or APP_ADMIN_API_KEYS (admin); local default local-dev-key / local-admin-key")
class OpenApiConfig {

    static final String API_KEY = "api-key";
}
