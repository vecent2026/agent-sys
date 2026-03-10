package com.trae.user.common.security;

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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

        final String authorizationHeader = request.getHeader("Authorization");

        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                username = jwtUtil.getUsernameFromToken(jwt);
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                log.debug("Token expired: prefix={}", jwt.substring(0, Math.min(10, jwt.length())));
            } catch (Exception e) {
                log.debug("Token invalid or expired: {}", e.getMessage());
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtUtil.validateToken(jwt, username)) {
                List<SimpleGrantedAuthority> grantedAuthorities = extractAuthoritiesFromToken(jwt);
                // 优先使用 JWT 中的 userId 作为 principal，供视图等接口解析当前用户 ID
                Long userId = jwtUtil.getUserIdFromToken(jwt);
                Object principal = (userId != null) ? String.valueOf(userId) : username;
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        principal, null, grantedAuthorities);
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        chain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> extractAuthoritiesFromToken(String token) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        try {
            Claims claims = jwtUtil.extractAllClaims(token);
            Object authObj = claims.get("authorities");
            if (authObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> authList = (List<String>) authObj;
                authorities = authList.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
            }
            
            if (authorities.isEmpty()) {
                Object permsObj = claims.get("permissions");
                if (permsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> permList = (List<String>) permsObj;
                    authorities = permList.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract authorities from token: {}", e.getMessage());
        }
        
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        
        return authorities;
    }
}
