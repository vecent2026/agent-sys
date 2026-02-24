package com.trae.admin.common.security;

import com.trae.admin.common.utils.RedisUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final RedisUtil redisUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService, RedisUtil redisUtil) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.redisUtil = redisUtil;
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
            } catch (Exception e) {
                // Token invalid or expired
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Check blacklist
            if (redisUtil.hasKey("blacklist:" + jwt)) {
                chain.doFilter(request, response);
                return;
            }

            // Check token version for force logout
            Long tokenVersion = jwtUtil.getVersionFromToken(jwt);
            String redisVersionKey = "auth:version:" + username;
            String redisVersion = redisUtil.get(redisVersionKey);
            if (redisVersion != null) {
                Long currentVersion = Long.valueOf(redisVersion);
                if (!currentVersion.equals(tokenVersion)) {
                    chain.doFilter(request, response);
                    return;
                }
            }

            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            if (jwtUtil.validateToken(jwt, userDetails.getUsername())) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        chain.doFilter(request, response);
    }
}
