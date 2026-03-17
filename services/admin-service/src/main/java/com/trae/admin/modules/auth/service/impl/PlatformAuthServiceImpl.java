package com.trae.admin.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.common.security.JwtUtil;
import com.trae.admin.common.utils.IpUtil;
import com.trae.admin.common.utils.RedisUtil;
import com.trae.admin.modules.auth.dto.LoginBody;
import com.trae.admin.modules.auth.service.PlatformAuthService;
import com.trae.admin.modules.rbac.entity.SysPermission;
import com.trae.admin.modules.rbac.mapper.SysPermissionMapper;
import com.trae.admin.modules.user.entity.SysUser;
import com.trae.admin.modules.user.mapper.SysUserRoleMapper;
import com.trae.admin.modules.user.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 平台端认证服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAuthServiceImpl implements PlatformAuthService {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Map<String, Object> login(LoginBody loginBody) {
        // 查询平台用户
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, loginBody.getUsername())
                        .eq(SysUser::getIsDeleted, 0));
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(loginBody.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }

        // 获取权限列表（超级管理员拥有全部权限）
        boolean isSuper = sysUserRoleMapper.existsSuperRole(user.getId());
        List<SysPermission> perms = isSuper
                ? sysPermissionMapper.selectAllPermissions()
                : sysPermissionMapper.selectPermissionsByUserId(user.getId());
        List<String> authorities = perms.stream()
                .filter(p -> StringUtils.hasText(p.getPermissionKey()))
                .map(SysPermission::getPermissionKey)
                .collect(Collectors.toList());

        // 获取或初始化 tokenVersion
        int tokenVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
        // 写入 Redis 版本（用于即时失效）
        String versionKey = "platform:version:" + user.getId();
        redisUtil.set(versionKey, String.valueOf(tokenVersion), 30, TimeUnit.DAYS);

        // 生成 tokens
        String accessToken = jwtUtil.createPlatformToken(
                user.getId(), user.getUsername(),
                isSuper,
                tokenVersion, authorities);
        String refreshToken = jwtUtil.createPlatformRefreshToken(
                user.getId(), user.getUsername(), tokenVersion);

        // 更新最后登录信息
        updateLastLogin(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("isSuper", isSuper);
        return result;
    }

    @Override
    public Map<String, Object> refreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken) || !jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException("无效的 refreshToken");
        }
        Claims claims = jwtUtil.extractAllClaims(refreshToken);
        if (!jwtUtil.isPlatformToken(claims)) {
            throw new BusinessException("非平台端 refreshToken");
        }

        Long userId = jwtUtil.getUserId(claims);
        int tokenVersion = jwtUtil.getTokenVersion(claims);

        // 校验版本
        String versionKey = "platform:version:" + userId;
        String storedVersion = redisUtil.get(versionKey);
        if (storedVersion != null && !storedVersion.equals(String.valueOf(tokenVersion))) {
            throw new BusinessException("Token 已失效，请重新登录");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BusinessException("用户不存在或已禁用");
        }

        // 重新获取权限（超级管理员拥有全部权限）
        boolean isSuper = sysUserRoleMapper.existsSuperRole(userId);
        List<SysPermission> perms = isSuper
                ? sysPermissionMapper.selectAllPermissions()
                : sysPermissionMapper.selectPermissionsByUserId(userId);
        List<String> authorities = perms.stream()
                .filter(p -> StringUtils.hasText(p.getPermissionKey()))
                .map(SysPermission::getPermissionKey)
                .collect(Collectors.toList());

        String newAccessToken = jwtUtil.createPlatformToken(
                user.getId(), user.getUsername(),
                isSuper,
                tokenVersion, authorities);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        result.put("refreshToken", refreshToken);
        return result;
    }

    @Override
    public void logout(String bearerToken) {
        String token = stripBearer(bearerToken);
        if (!StringUtils.hasText(token)) return;
        try {
            long ttl = jwtUtil.getExpirationFromToken(token).getTime() - System.currentTimeMillis();
            if (ttl > 0) {
                Claims claims = jwtUtil.extractAllClaims(token);
                String jti = jwtUtil.getJti(claims);
                if (StringUtils.hasText(jti)) {
                    redisUtil.set("blacklist:" + jti, "1", ttl, TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            log.warn("logout: token parse failed", e);
        }
        SecurityContextHolder.clearContext();
    }

    @Override
    public Map<String, Object> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String username = auth.getName();
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) return null;
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("nickname", user.getNickname());
        info.put("email", user.getEmail());
        info.put("mobile", user.getMobile());
        info.put("isSuper", sysUserRoleMapper.existsSuperRole(user.getId()));
        info.put("status", user.getStatus());
        return info;
    }

    @Override
    public List<String> getCurrentUserPermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Collections.emptyList();
        String username = auth.getName();
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) return Collections.emptyList();
        // 超级管理员拥有全部权限
        boolean isSuper = sysUserRoleMapper.existsSuperRole(user.getId());
        List<SysPermission> perms = isSuper
                ? sysPermissionMapper.selectAllPermissions()
                : sysPermissionMapper.selectPermissionsByUserId(user.getId());
        return perms.stream()
                .filter(p -> StringUtils.hasText(p.getPermissionKey()))
                .map(SysPermission::getPermissionKey)
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────

    private void updateLastLogin(Long userId) {
        try {
            SysUser upd = new SysUser();
            upd.setId(userId);
            upd.setLastLoginTime(LocalDateTime.now());
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                upd.setLastLoginIp(IpUtil.getIpAddress(req));
            }
            sysUserMapper.updateById(upd);
        } catch (Exception e) {
            log.warn("updateLastLogin failed for userId={}", userId, e);
        }
    }

    private String stripBearer(String token) {
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}
