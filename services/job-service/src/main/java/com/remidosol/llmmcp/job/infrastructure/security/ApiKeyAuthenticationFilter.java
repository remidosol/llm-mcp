package com.remidosol.llmmcp.job.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Resolves {@code X-API-Key} to an authenticated principal with ROLE_USER or ROLE_ADMIN. It only
 * ESTABLISHES identity; whether a request is allowed is decided by the authorization rules in
 * {@link SecurityConfig} — the classic split between authentication and authorization.
 * {@code OncePerRequestFilter} guarantees a single execution per request even across dispatches.
 * Deliberately NOT a {@code @Component}: Boot registers every Filter bean with the servlet container
 * as well, so the filter would run a second time outside the security chain. SecurityConfig builds it.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyProperties properties;

    public ApiKeyAuthenticationFilter(ApiKeyProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key != null && !key.isBlank()) {
            List<GrantedAuthority> authorities = authoritiesFor(key.strip());
            if (!authorities.isEmpty()) {
                var authentication = new UsernamePasswordAuthenticationToken("api-key", null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    private List<GrantedAuthority> authoritiesFor(String key) {
        if (properties.adminApiKeys() != null && properties.adminApiKeys().contains(key)) {
            return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"));
        }
        if (properties.apiKeys() != null && properties.apiKeys().contains(key)) {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return List.of();
    }
}
