package com.remidosol.llmmcp.credit.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code app.security.*}: API keys come from the environment (never git), comma-separated.
 * {@code mcpOpen} keeps {@code /mcp} unauthenticated in {@code local} only (ADR-0019).
 */
@ConfigurationProperties(prefix = "app.security")
public record ApiKeyProperties(List<String> apiKeys, List<String> adminApiKeys, boolean mcpOpen) {
}
