package com.remidosol.llmmcp.job.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** RFC 9457 bodies for 401/403, so security errors look like every other API error (PRD §4.5). */
final class ProblemDetailAuthHandlers {

    private ProblemDetailAuthHandlers() {
    }

    static AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) -> write(request, response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Missing or invalid " + ApiKeyAuthenticationFilter.HEADER + " header");
    }

    static AccessDeniedHandler forbidden() {
        return (request, response, ex) -> write(request, response, HttpStatus.FORBIDDEN, "Forbidden",
                "This operation requires an admin API key");
    }

    private static void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                              String title, String detail) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name()); // before setContentType, or the container appends ISO-8859-1
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + detail + "\",\"instance\":\"" + request.getRequestURI() + "\"}");
    }
}
