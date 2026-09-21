package com.hic.util;

import com.hic.model.User.UserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, UserType userType) {
        return generateToken(username, userType, null, null);
    }

    public String generateToken(String username, UserType userType, Long tenantId, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userType", userType.name());
        if (tenantId != null) {
            claims.put("tenantId", tenantId);
        }
        if (userId != null) {
            claims.put("userId", userId);
        }
        return buildToken(claims, username, expiration);
    }

    public String generateRefreshToken(String username) {
        return buildToken(new HashMap<>(), username, refreshExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expirationMs) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public UserType extractUserType(String token) {
        String userTypeStr = extractClaims(token).get("userType", String.class);
        if (userTypeStr != null) {
            return UserType.valueOf(userTypeStr);
        }
        return null;
    }

    public Long extractTenantId(String token) {
        Object tenantIdObj = extractClaims(token).get("tenantId");
        if (tenantIdObj instanceof Integer) {
            return ((Integer) tenantIdObj).longValue();
        } else if (tenantIdObj instanceof Long) {
            return (Long) tenantIdObj;
        }
        return null;
    }

    public Long extractUserId(String token) {
        Object userIdObj = extractClaims(token).get("userId");
        if (userIdObj instanceof Integer) {
            return ((Integer) userIdObj).longValue();
        } else if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        }
        return null;
    }

    public boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
