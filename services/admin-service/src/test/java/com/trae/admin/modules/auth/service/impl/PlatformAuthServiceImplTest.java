package com.trae.admin.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.common.security.JwtUtil;
import com.trae.admin.common.utils.RedisUtil;
import com.trae.admin.modules.auth.dto.LoginBody;
import com.trae.admin.modules.rbac.entity.SysPermission;
import com.trae.admin.modules.rbac.mapper.SysPermissionMapper;
import com.trae.admin.modules.user.entity.SysUser;
import com.trae.admin.modules.user.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PlatformAuthServiceImpl 单元测试
 * 覆盖：登录成功/失败、token 刷新、登出、tokenVersion 版本号
 */
@ExtendWith(MockitoExtension.class)
class PlatformAuthServiceImplTest {

    @Mock JwtUtil jwtUtil;
    @Mock RedisUtil redisUtil;
    @Mock SysUserMapper sysUserMapper;
    @Mock SysPermissionMapper sysPermissionMapper;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks
    private PlatformAuthServiceImpl platformAuthService;

    // ── 辅助 ──────────────────────────────────────────────

    private SysUser mockUser(Long id, String username, String hashedPwd, Integer isSuper, Integer tokenVersion) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername(username);
        u.setPassword(hashedPwd);
        u.setIsSuper(isSuper);
        u.setTokenVersion(tokenVersion);
        u.setStatus(1);
        return u;
    }

    // ── login ─────────────────────────────────────────────

    @Test
    void login_success_returnsTokensAndMeta() {
        SysUser user = mockUser(1L, "admin", "$hash$", 1, 0);
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("123456", "$hash$")).thenReturn(true);
        when(sysPermissionMapper.selectPermissionsByUserId(1L)).thenReturn(List.of());
        when(jwtUtil.createPlatformToken(eq(1L), eq("admin"), eq(true), eq(0), any())).thenReturn("acc");
        when(jwtUtil.createPlatformRefreshToken(eq(1L), eq("admin"), eq(0))).thenReturn("ref");

        LoginBody body = new LoginBody();
        body.setUsername("admin");
        body.setPassword("123456");

        Map<String, Object> result = platformAuthService.login(body);

        assertEquals("acc", result.get("accessToken"));
        assertEquals("ref", result.get("refreshToken"));
        assertEquals(1L, result.get("userId"));
        assertEquals("admin", result.get("username"));
        verify(redisUtil).set(eq("platform:version:1"), eq("0"), eq(30L), eq(TimeUnit.DAYS));
    }

    @Test
    void login_userNotFound_throwsBusinessException() {
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        LoginBody body = new LoginBody();
        body.setUsername("nobody");
        body.setPassword("x");

        assertThrows(BusinessException.class, () -> platformAuthService.login(body));
    }

    @Test
    void login_wrongPassword_throwsBusinessException() {
        SysUser user = mockUser(1L, "admin", "$hash$", 0, 0);
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("wrong", "$hash$")).thenReturn(false);

        LoginBody body = new LoginBody();
        body.setUsername("admin");
        body.setPassword("wrong");

        assertThrows(BusinessException.class, () -> platformAuthService.login(body));
    }

    @Test
    void login_disabledUser_throwsBusinessException() {
        SysUser user = mockUser(1L, "admin", "$hash$", 0, 0);
        user.setStatus(0);
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        LoginBody body = new LoginBody();
        body.setUsername("admin");
        body.setPassword("123456");

        assertThrows(BusinessException.class, () -> platformAuthService.login(body));
    }

    @Test
    void login_permissionsIncludedInToken() {
        SysUser user = mockUser(1L, "admin", "$hash$", 0, 0);
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        SysPermission perm = new SysPermission();
        perm.setPermissionKey("platform:user:list");
        when(sysPermissionMapper.selectPermissionsByUserId(1L)).thenReturn(List.of(perm));
        when(jwtUtil.createPlatformToken(any(), any(), anyBoolean(), anyInt(),
                eq(List.of("platform:user:list")))).thenReturn("tokenWithPerm");
        when(jwtUtil.createPlatformRefreshToken(any(), any(), anyInt())).thenReturn("ref");

        LoginBody body = new LoginBody();
        body.setUsername("admin");
        body.setPassword("123456");
        platformAuthService.login(body);

        verify(jwtUtil).createPlatformToken(any(), any(), anyBoolean(), anyInt(),
                eq(List.of("platform:user:list")));
    }

    // ── refreshToken ──────────────────────────────────────

    @Test
    void refreshToken_validToken_returnsNewAccessToken() {
        Claims claims = mock(Claims.class);
        when(jwtUtil.validateToken("rt")).thenReturn(true);
        when(jwtUtil.extractAllClaims("rt")).thenReturn(claims);
        when(jwtUtil.isPlatformToken(claims)).thenReturn(true);
        when(jwtUtil.getUserId(claims)).thenReturn(1L);
        when(jwtUtil.getTokenVersion(claims)).thenReturn(0);
        when(redisUtil.get("platform:version:1")).thenReturn("0");

        SysUser user = mockUser(1L, "admin", "$h$", 1, 0);
        when(sysUserMapper.selectById(1L)).thenReturn(user);
        when(sysPermissionMapper.selectPermissionsByUserId(1L)).thenReturn(List.of());
        when(jwtUtil.createPlatformToken(any(), any(), anyBoolean(), anyInt(), any())).thenReturn("newAcc");

        Map<String, Object> result = platformAuthService.refreshToken("rt");
        assertEquals("newAcc", result.get("accessToken"));
        assertEquals("rt", result.get("refreshToken"));
    }

    @Test
    void refreshToken_versionMismatch_throwsException() {
        Claims claims = mock(Claims.class);
        when(jwtUtil.validateToken("rt")).thenReturn(true);
        when(jwtUtil.extractAllClaims("rt")).thenReturn(claims);
        when(jwtUtil.isPlatformToken(claims)).thenReturn(true);
        when(jwtUtil.getUserId(claims)).thenReturn(1L);
        when(jwtUtil.getTokenVersion(claims)).thenReturn(0);
        when(redisUtil.get("platform:version:1")).thenReturn("1"); // version bumped

        assertThrows(BusinessException.class, () -> platformAuthService.refreshToken("rt"));
    }

    @Test
    void refreshToken_invalidToken_throwsException() {
        when(jwtUtil.validateToken("bad")).thenReturn(false);
        assertThrows(BusinessException.class, () -> platformAuthService.refreshToken("bad"));
    }

    // ── logout ────────────────────────────────────────────

    @Test
    void logout_addsToBlacklist() {
        when(jwtUtil.getExpirationFromToken("token"))
                .thenReturn(new Date(System.currentTimeMillis() + 3_600_000));
        platformAuthService.logout("Bearer token");
        verify(redisUtil).set(eq("blacklist:token"), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void logout_emptyToken_doesNothing() {
        platformAuthService.logout("");
        verifyNoInteractions(redisUtil);
    }
}
