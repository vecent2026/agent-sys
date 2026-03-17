package com.trae.user.common.security;

import com.trae.user.common.context.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器 - 支持租户端双 Token 结构
 *
 * 从 Authorization 头解析 JWT，提取 tenantId 设置 TenantContext，
 * 请求结束后在 finally 中清理 ThreadLocal。
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String jwt = extractJwt(request);
            if (jwt != null && jwtUtil.validateToken(jwt)) {
                Claims claims = jwtUtil.extractAllClaims(jwt);

                // 跳过 preToken（仅在 /api/tenant/auth/select 使用，不设 SecurityContext）
                if (Boolean.TRUE.equals(claims.get("isPre"))) {
                    return;
                }

                // 设置租户上下文（平台端 token 无 tenantId，不设置）
                Object tenantIdObj = claims.get("tenantId");
                if (tenantIdObj != null) {
                    TenantContext.setTenantId(Long.valueOf(tenantIdObj.toString()));
                }

                // 设置 SecurityContext
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    List<String> authList = extractAuthorities(claims);
                    List<SimpleGrantedAuthority> grantedAuthorities = authList.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

                    // 使用 userId 或 mobile 作为 principal
                    Object userIdObj = claims.get("userId");
                    String principal = (userIdObj != null) ? userIdObj.toString() : claims.getSubject();

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(principal, null, grantedAuthorities);
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            log.debug("JWT filter error: {}", e.getMessage());
        } finally {
            chain.doFilter(request, response);
            TenantContext.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractAuthorities(Claims claims) {
        Object val = claims.get("authorities");
        if (val instanceof List) return (List<String>) val;
        return Collections.singletonList("ROLE_USER");
    }

    private String extractJwt(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
    }
}
