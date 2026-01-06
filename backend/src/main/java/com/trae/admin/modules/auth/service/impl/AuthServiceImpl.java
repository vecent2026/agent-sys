package com.trae.admin.modules.auth.service.impl;

import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.common.security.JwtUtil;
import com.trae.admin.common.utils.IpUtil;
import com.trae.admin.common.utils.RedisUtil;
import com.trae.admin.modules.auth.dto.LoginBody;
import com.trae.admin.modules.auth.service.AuthService;
import com.trae.admin.modules.log.entity.SysLogDocument;
import com.trae.admin.modules.log.repository.SysLogRepository;
import com.trae.admin.modules.user.entity.SysUser;
import com.trae.admin.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final SysUserMapper sysUserMapper;
    private final com.trae.admin.modules.rbac.service.PermissionService permissionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SysLogRepository sysLogRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> login(LoginBody loginBody) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginBody.getUsername(), loginBody.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String username = authentication.getName();

            // Get or create token version
            String versionKey = "auth:version:" + username;
            String versionStr = redisUtil.get(versionKey);
            Long version = versionStr != null ? Long.valueOf(versionStr) : 0L;

            String accessToken = jwtUtil.createAccessToken(username, version);
            String refreshToken = jwtUtil.createRefreshToken(username, version);

            // Store refresh token in Redis (TTL: 7 days)
            redisUtil.set("auth:refresh:" + username, refreshToken, 7, TimeUnit.DAYS);

            // Update last login time and IP
            SysUser user = sysUserMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, username));
            if (user != null) {
                // Get IP address
                String ipAddress = "unknown";
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    ipAddress = IpUtil.getIpAddress(request);
                }
                
                user.setLastLoginTime(LocalDateTime.now());
                user.setLastLoginIp(ipAddress);
                sysUserMapper.updateById(user);
            }

            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", accessToken);
            tokens.put("refreshToken", refreshToken);
            return tokens;
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public Map<String, String> refreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken) || !jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException("Invalid refresh token");
        }
        String username = jwtUtil.getUsernameFromToken(refreshToken);

        // Validate refresh token from Redis
        String storedRefreshToken = redisUtil.get("auth:refresh:" + username);
        if (!refreshToken.equals(storedRefreshToken)) {
            throw new BusinessException("Invalid refresh token");
        }

        // Get current version
        String versionKey = "auth:version:" + username;
        String versionStr = redisUtil.get(versionKey);
        Long version = versionStr != null ? Long.valueOf(versionStr) : 0L;

        String newAccessToken = jwtUtil.createAccessToken(username, version);

        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", newAccessToken);
        tokens.put("refreshToken", refreshToken);
        return tokens;
    }

    @Override
    public void logout(String token) {
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (StringUtils.hasText(token)) {
            // Get username from token
            String username = jwtUtil.getUsernameFromToken(token);

            // Delete refresh token from Redis
            redisUtil.delete("auth:refresh:" + username);

            // Add access token to blacklist
            long expiration = jwtUtil.getExpirationFromToken(token).getTime() - System.currentTimeMillis();
            if (expiration > 0) {
                redisUtil.set("blacklist:" + token, "1", expiration, TimeUnit.MILLISECONDS);
            }
        }
        SecurityContextHolder.clearContext();
    }



    @Override
    public Object getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            // 查询用户信息
            return sysUserMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.trae.admin.modules.user.entity.SysUser>()
                    .eq(com.trae.admin.modules.user.entity.SysUser::getUsername, username));
        }
        return null;
    }

    @Override
    public Object getCurrentUserMenus() {
        // 获取当前用户的菜单列表
        return permissionService.listTree();
    }
}
