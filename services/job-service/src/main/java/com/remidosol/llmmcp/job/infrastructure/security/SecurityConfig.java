package com.remidosol.llmmcp.job.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Deliberately minimal security (ADR-0019): stateless, no CSRF (no browser sessions),
 * health/metrics/docs open for the cluster, everything under /api behind an API key, and /mcp
 * open only where {@code app.security.mcp-open} says so (local).
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ApiKeyProperties properties) throws Exception {
        var apiKeyFilter = new ApiKeyAuthenticationFilter(properties);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**", "/actuator/prometheus", "/swagger-ui/**",
                            "/swagger-ui.html", "/v3/api-docs/**", "/error").permitAll();
                    if (properties.mcpOpen()) {
                        auth.requestMatchers("/mcp/**", "/mcp").permitAll();
                    } else {
                        auth.requestMatchers("/mcp/**", "/mcp").hasRole("USER");
                    }
                    auth.requestMatchers("/api/**").hasRole("USER");
                    auth.anyRequest().hasRole("ADMIN"); // remaining actuator endpoints (metrics, caches, …)
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(ProblemDetailAuthHandlers.unauthorized())
                        .accessDeniedHandler(ProblemDetailAuthHandlers.forbidden()))
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .anonymous(Customizer.withDefaults());
        return http.build();
    }
}
