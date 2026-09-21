package com.hic.config;

import com.hic.repository.UserRepository;
import com.hic.util.JwtUtil;
import com.hic.util.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Always clear tenant context at start of each request
        TenantContext.clear();

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            if (jwtUtil.validateToken(token)) {
                String username = jwtUtil.extractUsername(token);
                var userType = jwtUtil.extractUserType(token);
                Long tenantId = jwtUtil.extractTenantId(token);
                Long userId = jwtUtil.extractUserId(token);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    String role = userType != null ? "ROLE_" + userType.name() : "ROLE_USER";
                    var authToken = new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority(role))
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // Set tenant context from JWT claims
                    if (tenantId != null) {
                        TenantContext.setTenantId(tenantId);
                    } else {
                        // Fallback: load tenant from user record for backward compatibility
                        userRepository.findByUsername(username).ifPresent(user -> {
                            if (user.getTenantId() != null) {
                                TenantContext.setTenantId(user.getTenantId());
                            }
                        });
                    }

                    if (userId != null) {
                        TenantContext.setUserId(userId);
                    } else {
                        userRepository.findByUsername(username).ifPresent(user ->
                                TenantContext.setUserId(user.getId()));
                    }

                    TenantContext.setUsername(username);
                }
            }
        } catch (Exception e) {
            log.warn("JWT authentication failed: {}", e.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear tenant context after request to prevent thread reuse leaks
            TenantContext.clear();
        }
    }
}

