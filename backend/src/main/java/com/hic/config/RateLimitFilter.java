package com.hic.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiter for auth endpoints only (brute-force protection).
 * Normal authenticated SPA traffic is not throttled — a single page refresh
 * can fire dozens of API calls and must not return 429.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${rate-limit.login-max-per-minute:10}")
    private int loginMaxPerMinute;

    @Value("${rate-limit.trusted-proxies:127.0.0.1,::1,172.16.0.0/12}")
    private String trustedProxies;

    private static final long WINDOW_MS = 60_000L;

    // IP -> [window start ms, count]
    private final Map<String, long[]> loginCounts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isAuthEndpoint = "POST".equalsIgnoreCase(request.getMethod())
                && (path.contains("/auth/login")
                || path.contains("/auth/signup")
                || path.contains("/auth/initial-setup"));

        if (isAuthEndpoint) {
            String ip = getClientIp(request);
            if (isRateLimited(ip, loginCounts, loginMaxPerMinute)) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many login attempts. Please try again later.\"}");
                log.warn("Rate limit exceeded for auth from IP: {}", ip);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(String ip, Map<String, long[]> counts, int maxPerMinute) {
        long now = System.currentTimeMillis();

        counts.compute(ip, (key, val) -> {
            if (val == null || now - val[0] > WINDOW_MS) {
                return new long[]{now, 1};
            }
            val[1]++;
            return val;
        });

        long[] data = counts.get(ip);
        return data != null && data[1] > maxPerMinute;
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String realIp = normalizeIp(request.getHeader("X-Real-IP"));
        return realIp != null ? realIp : remoteAddress;
    }

    private boolean isTrustedProxy(String remoteAddress) {
        if (remoteAddress == null || trustedProxies == null) {
            return false;
        }

        for (String proxyRange : trustedProxies.split(",")) {
            String candidate = proxyRange.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            try {
                if (new IpAddressMatcher(candidate).matches(remoteAddress)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                log.warn("Ignoring invalid trusted proxy range: {}", candidate);
            }
        }
        return false;
    }

    private String normalizeIp(String value) {
        if (value == null) {
            return null;
        }

        String candidate = value.trim();
        if (candidate.isEmpty() || !candidate.matches("[0-9a-fA-F:.]+")) {
            return null;
        }

        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Periodically remove expired entries to prevent unbounded map growth.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedDelay = 300_000)
    public void cleanExpiredEntries() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, long[]>> it = loginCounts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, long[]> entry = it.next();
            if (now - entry.getValue()[0] > WINDOW_MS) {
                it.remove();
            }
        }
    }
}
