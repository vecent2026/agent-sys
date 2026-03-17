package com.trae.admin.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    /** preToken 有效期：5 分钟 */
    private static final long PRE_TOKEN_EXPIRATION = 5 * 60 * 1000L;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // ──────────────────────────────────────────────────────
    //  平台端 Token
    // ──────────────────────────────────────────────────────

    /**
     * 创建平台端 accessToken
     * claims: isPlatform=true, userId, tokenVersion, authorities
     */
    public String createPlatformToken(Long userId, String username, boolean isSuper,
                                       int tokenVersion, List<String> authorities) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("isPlatform", true);
        claims.put("userId", userId);
        claims.put("isSuper", isSuper);
        claims.put("tokenVersion", tokenVersion);
        claims.put("authorities", authorities);
        return buildToken(claims, username, expiration);
    }

    /**
     * 创建平台端 refreshToken
     */
    public String createPlatformRefreshToken(Long userId, String username, int tokenVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("isPlatform", true);
        claims.put("isRefresh", true);
        claims.put("userId", userId);
        claims.put("tokenVersion", tokenVersion);
        return buildToken(claims, username, refreshExpiration);
    }

    // ──────────────────────────────────────────────────────
    //  租户端 Token
    // ──────────────────────────────────────────────────────

    /**
     * 创建租户端 accessToken
     * claims: isPlatform=false, userId, mobile, tenantId, tenantVersion, authorities
     */
    public String createTenantToken(Long userId, String mobile, Long tenantId,
                                     int tenantVersion, boolean isTenantAdmin, List<String> authorities) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("isPlatform", false);
        claims.put("userId", userId);
        claims.put("mobile", mobile);
        claims.put("tenantId", tenantId);
        claims.put("tenantVersion", tenantVersion);
        claims.put("isTenantAdmin", isTenantAdmin);
        claims.put("authorities", authorities);
        return buildToken(claims, mobile, expiration);
    }

    /**
     * 创建租户端 refreshToken
     */
    public String createTenantRefreshToken(Long userId, String mobile, Long tenantId, int tenantVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("isPlatform", false);
        claims.put("isRefresh", true);
        claims.put("userId", userId);
        claims.put("mobile", mobile);
        claims.put("tenantId", tenantId);
        claims.put("tenantVersion", tenantVersion);
        return buildToken(claims, mobile, refreshExpiration);
    }

    // ──────────────────────────────────────────────────────
    //  preToken（一次性租户选择 Token）
    // ──────────────────────────────────────────────────────

    /**
     * 创建 preToken（多租户登录时，临时选择 Token）
     * tenants: [{tenantId, tenantName, ...}, ...]
     */
    public String createPreToken(Long userId, String mobile, List<Map<String, Object>> tenants) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("isPre", true);
        claims.put("userId", userId);
        claims.put("mobile", mobile);
        claims.put("tenants", tenants);
        return buildToken(claims, mobile, PRE_TOKEN_EXPIRATION);
    }

    // ──────────────────────────────────────────────────────
    //  Claims 提取工具方法
    // ──────────────────────────────────────────────────────

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isPlatformToken(Claims claims) {
        Object val = claims.get("isPlatform");
        return Boolean.TRUE.equals(val);
    }

    public boolean isPreToken(Claims claims) {
        Object val = claims.get("isPre");
        return Boolean.TRUE.equals(val);
    }

    public Long getUserId(Claims claims) {
        Object val = claims.get("userId");
        return val != null ? Long.valueOf(val.toString()) : null;
    }

    public String getMobile(Claims claims) {
        return (String) claims.get("mobile");
    }

    public Long getTenantId(Claims claims) {
        Object val = claims.get("tenantId");
        return val != null ? Long.valueOf(val.toString()) : null;
    }

    public int getTenantVersion(Claims claims) {
        Object val = claims.get("tenantVersion");
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    public int getTokenVersion(Claims claims) {
        Object val = claims.get("tokenVersion");
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    public String getJti(Claims claims) {
        String jti = (String) claims.get("jti");
        return jti != null ? jti : claims.getId();
    }

    @SuppressWarnings("unchecked")
    public List<String> getAuthorities(Claims claims) {
        Object val = claims.get("authorities");
        if (val instanceof List) {
            return (List<String>) val;
        }
        return Collections.emptyList();
    }

    // ──────────────────────────────────────────────────────
    //  Token 校验
    // ──────────────────────────────────────────────────────

    public boolean validateToken(String token) {
        try {
            return !extractAllClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public Date getExpirationFromToken(String token) {
        return extractAllClaims(token).getExpiration();
    }

    // ──────────────────────────────────────────────────────
    //  向后兼容（旧 AuthServiceImpl 使用，后续可移除）
    // ──────────────────────────────────────────────────────

    /** @deprecated 使用 {@link #createPlatformToken} */
    @Deprecated
    public String createAccessToken(String username, Long userId, Long version,
                                    java.util.Collection<? extends GrantedAuthority> authorities) {
        List<String> authList = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        Map<String, Object> claims = new HashMap<>();
        claims.put("authorities", authList);
        if (userId != null) claims.put("userId", userId);
        claims.put("version", version);
        return buildToken(claims, username, expiration);
    }

    /** @deprecated 使用 {@link #createPlatformRefreshToken} */
    @Deprecated
    public String createRefreshToken(String username, Long version) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("version", version);
        return buildToken(claims, username, refreshExpiration);
    }

    /** @deprecated 使用 {@link #getUserId(Claims)} */
    @Deprecated
    public String getUsernameFromToken(String token) {
        return extractAllClaims(token).getSubject();
    }

    /** @deprecated */
    @Deprecated
    public Long getVersionFromToken(String token) {
        Object v = extractAllClaims(token).get("version");
        return v != null ? Long.valueOf(v.toString()) : 0L;
    }

    // ──────────────────────────────────────────────────────
    //  内部
    // ──────────────────────────────────────────────────────

    private String buildToken(Map<String, Object> claims, String subject, long ttlMs) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** @deprecated 使用 {@link #validateToken(String)} */
    @Deprecated
    public Boolean validateToken(String token, String username) {
        try {
            Claims c = extractAllClaims(token);
            return username.equals(c.getSubject()) && !c.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
