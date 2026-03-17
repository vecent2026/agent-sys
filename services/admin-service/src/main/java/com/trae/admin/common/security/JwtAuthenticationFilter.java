package com.trae.admin.common.security;

import com.trae.admin.common.context.TenantContext;
import com.trae.admin.common.utils.RedisUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, RedisUtil redisUtil) {
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        try {
            final String authorizationHeader = request.getHeader("Authorization");

            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                return;
            }

            String jwt = authorizationHeader.substring(7);
            Claims claims;
            try {
                claims = jwtUtil.extractAllClaims(jwt);
            } catch (Exception e) {
                return;
            }

            // 跳过 preToken（仅用于选租户，不需要 SecurityContext）
            if (jwtUtil.isPreToken(claims)) {
                return;
            }

            // 黑名单检查
            String jti = jwtUtil.getJti(claims);
            if (jti != null && redisUtil.hasKey("blacklist:" + jti)) {
                return;
            }

            boolean isPlatform = jwtUtil.isPlatformToken(claims);
            Long userId = jwtUtil.getUserId(claims);

            if (isPlatform) {
                // 平台端：校验 token_version
                int tokenVersion = jwtUtil.getTokenVersion(claims);
                String redisVersion = redisUtil.get("platform:version:" + userId);
                if (redisVersion != null && Integer.parseInt(redisVersion) != tokenVersion) {
                    return;
                }
                // 平台端不设 TenantContext
            } else {
                // 租户端：校验 tenant data_version
                Long tenantId = jwtUtil.getTenantId(claims);
                int tenantVersion = jwtUtil.getTenantVersion(claims);
                String redisVersion = redisUtil.get("tenant:version:" + tenantId);
                if (redisVersion != null && Integer.parseInt(redisVersion) != tenantVersion) {
                    return;
                }
                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId);
                }
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String principal = claims.getSubject();
                List<String> authorities = jwtUtil.getAuthorities(claims);
                boolean isSuper = Boolean.TRUE.equals(claims.get("isSuper"));
                if (isPlatform && isSuper) {
                    authorities = new java.util.ArrayList<>(authorities != null ? authorities : List.of());
                    if (!authorities.contains("ROLE_SUPER_ADMIN")) {
                        authorities.add(0, "ROLE_SUPER_ADMIN");
                    }
                }
                List<SimpleGrantedAuthority> grantedAuthorities = (authorities != null)
                        ? authorities.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())
                        : List.of();

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        principal, null, grantedAuthorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } finally {
            chain.doFilter(request, response);
            TenantContext.clear();
        }
    }
}
