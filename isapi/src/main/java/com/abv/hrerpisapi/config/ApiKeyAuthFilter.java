package com.abv.hrerpisapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Shared-secret gate for all ISAPI HTTP APIs (backend → isapi).
 * Health endpoints stay open for compose healthchecks.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final Environment environment;

    @Value("${isapi.api-key:}")
    private String apiKey;

    public ApiKeyAuthFilter(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && (path.equals("/actuator/health")
                || path.equals("/api/health")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        boolean prod = isProd();
        if (!StringUtils.hasText(apiKey)) {
            if (prod) {
                response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "ISAPI_API_KEY not configured");
                return;
            }
            // Non-prod without key: allow (unit tests / local without Docker secrets)
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader("X-API-Key");
        if (!apiKey.equals(provided)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid or missing X-API-Key");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isProd() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
