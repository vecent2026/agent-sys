package com.trae.user.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.security.Key;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.debug("JWT expired: prefix={}", token.substring(0, Math.min(10, token.length())));
            return false;
        } catch (Exception e) {
            log.debug("JWT invalid: {}", e.getMessage());
            return false;
        }
    }

    public Boolean validateToken(String token, String username) {
        final String extractedUsername = getUsernameFromToken(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    public String getUsernameFromToken(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * 从 JWT 中读取 userId（需 admin 在签发时写入 claim "userId"）。
     * 若不存在则返回 null，调用方需兼容旧 token。
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Object userId = claims.get("userId");
            if (userId == null) return null;
            if (userId instanceof Number) return ((Number) userId).longValue();
            return Long.parseLong(userId.toString());
        } catch (Exception e) {
            log.debug("Failed to get userId from token: {}", e.getMessage());
            return null;
        }
    }

    public Date getExpirationFromToken(String token) {
        return extractAllClaims(token).getExpiration();
    }

    public Long getVersionFromToken(String token) {
        Claims claims = extractAllClaims(token);
        Object version = claims.get("version");
        return version != null ? Long.valueOf(version.toString()) : 0L;
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }
}
