package com.trae.admin.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.common.security.JwtUtil;
import com.trae.admin.common.utils.RedisUtil;
import com.trae.admin.modules.auth.dto.TenantLoginBody;
import com.trae.admin.modules.auth.dto.TenantSelectBody;
import com.trae.admin.modules.platform.entity.PlatformTenant;
import com.trae.admin.modules.platform.mapper.PlatformTenantMapper;
import com.trae.admin.modules.tenant.entity.TenantUserRole;
import com.trae.admin.modules.tenant.mapper.TenantRolePermissionMapper;
import com.trae.admin.modules.tenant.mapper.TenantUserRoleMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.core.ParameterizedTypeReference;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TenantAuthServiceImpl 单元测试
 * 覆盖：单租户登录、多租户 preToken、租户选择、refreshToken、登出
 */
@ExtendWith(MockitoExtension.class)
class TenantAuthServiceImplTest {

    @Mock JwtUtil jwtUtil;
    @Mock RedisUtil redisUtil;
    @Mock PlatformTenantMapper platformTenantMapper;
    @Mock TenantUserRoleMapper tenantUserRoleMapper;
    @Mock TenantRolePermissionMapper tenantRolePermissionMapper;
    @Mock RestTemplate restTemplate;

    @InjectMocks
    private TenantAuthServiceImpl tenantAuthService;

    // ── 辅助 ──────────────────────────────────────────────

    private PlatformTenant activeTenant(Long id, String name) {
        PlatformTenant t = new PlatformTenant();
        t.setId(id);
        t.setTenantName(name);
        t.setStatus(1);
        t.setDataVersion(0);
        return t;
    }

    private TenantUserRole roleEntry(Long userId, Long tenantId, Long roleId) {
        TenantUserRole r = new TenantUserRole();
        r.setUserId(userId);
        r.setTenantId(tenantId);
        r.setRoleId(roleId);
        return r;
    }

    /** 模拟 user-service verify 返回用户信息 */
    private void mockVerifyUser(Long userId, String mobile) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", userId);
        userMap.put("mobile", mobile);
        org.springframework.http.ResponseEntity<Map<String, Object>> resp =
                org.springframework.http.ResponseEntity.ok(userMap);
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.POST),
                any(), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(resp);
    }

    @SuppressWarnings("unchecked")
    private void mockGetUserTenants(Long userId, List<Long> tenantIds) {
        ResponseEntity<List<Long>> resp = ResponseEntity.ok(tenantIds);
        when(restTemplate.exchange(
                contains("/api/internal/users/" + userId + "/tenants"),
                eq(HttpMethod.GET),
                isNull(),
                any(ParameterizedTypeReference.class)
        )).thenReturn((ResponseEntity) resp);
    }

    private void mockCheckTenantAdmin(Long userId, Long tenantId, boolean isAdmin) {
        when(restTemplate.exchange(
                contains("/api/internal/users/" + userId + "/tenant-admin?tenantId=" + tenantId),
                eq(HttpMethod.GET),
                isNull(),
                eq(Boolean.class)
        )).thenReturn(ResponseEntity.ok(isAdmin));
    }

    // ── 单租户登录 ─────────────────────────────────────────

    @Test
    void login_singleTenant_returnsAccessToken() {
        mockVerifyUser(10L, "13800000001");
        mockGetUserTenants(10L, List.of(1L));
        mockCheckTenantAdmin(10L, 1L, false);
        when(platformTenantMapper.selectById(1L)).thenReturn(activeTenant(1L, "默认租户"));
        when(tenantRolePermissionMapper.selectUserPermissionKeys(10L, 1L))
                .thenReturn(List.of("tenant:role:list"));
        when(jwtUtil.createTenantToken(eq(10L), eq("13800000001"), eq(1L), eq(0), anyBoolean(), any()))
                .thenReturn("acc");
        when(jwtUtil.createTenantRefreshToken(any(), any(), any(), anyInt())).thenReturn("ref");

        TenantLoginBody body = new TenantLoginBody();
        body.setMobile("13800000001");
        body.setPassword("pass");

        Map<String, Object> result = tenantAuthService.login(body);

        assertEquals("acc", result.get("accessToken"));
        assertEquals("ref", result.get("refreshToken"));
        assertEquals(1L, result.get("tenantId"));
        assertEquals("默认租户", result.get("tenantName"));
    }

    // ── 多租户 → preToken ─────────────────────────────────

    @Test
    void login_multiTenant_returnsPreTokenAndList() {
        mockVerifyUser(10L, "13800000001");
        mockGetUserTenants(10L, List.of(1L, 2L));
        when(platformTenantMapper.selectById(1L)).thenReturn(activeTenant(1L, "租户A"));
        when(platformTenantMapper.selectById(2L)).thenReturn(activeTenant(2L, "租户B"));
        when(jwtUtil.createPreToken(eq(10L), eq("13800000001"), any())).thenReturn("pre_token");
        Claims preClaims = mock(Claims.class);
        when(jwtUtil.extractAllClaims("pre_token")).thenReturn(preClaims);
        when(jwtUtil.getJti(preClaims)).thenReturn("jti-abc");

        TenantLoginBody body = new TenantLoginBody();
        body.setMobile("13800000001");
        body.setPassword("pass");

        Map<String, Object> result = tenantAuthService.login(body);

        assertEquals("pre_token", result.get("preToken"));
        assertNotNull(result.get("tenants"));
        verify(redisUtil).set(eq("pre_token:jti-abc"), eq("1"), eq(5L), eq(TimeUnit.MINUTES));
    }

    // ── 用户无租户 ─────────────────────────────────────────

    @Test
    void login_noTenant_throwsException() {
        mockVerifyUser(10L, "13800000001");
        mockGetUserTenants(10L, List.of());

        TenantLoginBody body = new TenantLoginBody();
        body.setMobile("13800000001");
        body.setPassword("pass");

        assertThrows(BusinessException.class, () -> tenantAuthService.login(body));
    }

    // ── selectTenant ──────────────────────────────────────

    @Test
    void selectTenant_validPreToken_returnsAccessToken() {
        Claims claims = mock(Claims.class);
        when(jwtUtil.validateToken("pre")).thenReturn(true);
        when(jwtUtil.extractAllClaims("pre")).thenReturn(claims);
        when(jwtUtil.isPreToken(claims)).thenReturn(true);
        when(jwtUtil.getJti(claims)).thenReturn("jti-abc");
        when(redisUtil.hasKey("pre_token:jti-abc")).thenReturn(true);
        when(jwtUtil.getUserId(claims)).thenReturn(10L);
        when(jwtUtil.getMobile(claims)).thenReturn("13800000001");
        mockGetUserTenants(10L, List.of(1L));
        mockCheckTenantAdmin(10L, 1L, false);
        when(platformTenantMapper.selectById(1L)).thenReturn(activeTenant(1L, "默认租户"));
        when(tenantRolePermissionMapper.selectUserPermissionKeys(10L, 1L)).thenReturn(List.of());
        when(jwtUtil.createTenantToken(any(), any(), any(), anyInt(), anyBoolean(), any())).thenReturn("acc2");
        when(jwtUtil.createTenantRefreshToken(any(), any(), any(), anyInt())).thenReturn("ref2");

        TenantSelectBody body = new TenantSelectBody();
        body.setPreToken("pre");
        body.setTenantId(1L);

        Map<String, Object> result = tenantAuthService.selectTenant(body);

        assertEquals("acc2", result.get("accessToken"));
        verify(redisUtil).delete("pre_token:jti-abc"); // preToken 一次性消费
    }

    @Test
    void selectTenant_consumedPreToken_throwsException() {
        Claims claims = mock(Claims.class);
        when(jwtUtil.validateToken("pre")).thenReturn(true);
        when(jwtUtil.extractAllClaims("pre")).thenReturn(claims);
        when(jwtUtil.isPreToken(claims)).thenReturn(true);
        when(jwtUtil.getJti(claims)).thenReturn("jti-abc");
        when(redisUtil.hasKey("pre_token:jti-abc")).thenReturn(false); // 已消费

        TenantSelectBody body = new TenantSelectBody();
        body.setPreToken("pre");
        body.setTenantId(1L);

        assertThrows(BusinessException.class, () -> tenantAuthService.selectTenant(body));
    }

    @Test
    void selectTenant_userNotInTenant_throwsException() {
        Claims claims = mock(Claims.class);
        when(jwtUtil.validateToken("pre")).thenReturn(true);
        when(jwtUtil.extractAllClaims("pre")).thenReturn(claims);
        when(jwtUtil.isPreToken(claims)).thenReturn(true);
        when(jwtUtil.getJti(claims)).thenReturn("jti-abc");
        when(redisUtil.hasKey("pre_token:jti-abc")).thenReturn(true);
        when(jwtUtil.getUserId(claims)).thenReturn(10L);
        when(jwtUtil.getMobile(claims)).thenReturn("13800000001");
        mockGetUserTenants(10L, List.of(1L)); // 不包含 99

        TenantSelectBody body = new TenantSelectBody();
        body.setPreToken("pre");
        body.setTenantId(99L);

        assertThrows(BusinessException.class, () -> tenantAuthService.selectTenant(body));
    }

    // ── refreshToken ──────────────────────────────────────

    @Test
    void refreshToken_validTenantRefresh_returnsNewAccess() {
        Claims claims = mock(Claims.class);
        when(jwtUtil.validateToken("rt")).thenReturn(true);
        when(jwtUtil.extractAllClaims("rt")).thenReturn(claims);
        when(jwtUtil.isPlatformToken(claims)).thenReturn(false);
        when(jwtUtil.getUserId(claims)).thenReturn(10L);
        when(jwtUtil.getTenantId(claims)).thenReturn(1L);
        when(jwtUtil.getMobile(claims)).thenReturn("13800000001");
        when(jwtUtil.getTenantVersion(claims)).thenReturn(0);
        when(redisUtil.get("tenant:version:1")).thenReturn("0");
        mockCheckTenantAdmin(10L, 1L, false);
        when(platformTenantMapper.selectById(1L)).thenReturn(activeTenant(1L, "默认租户"));
        when(tenantRolePermissionMapper.selectUserPermissionKeys(10L, 1L)).thenReturn(List.of());
        when(jwtUtil.createTenantToken(any(), any(), any(), anyInt(), anyBoolean(), any())).thenReturn("newAcc");

        Map<String, Object> result = tenantAuthService.refreshToken("rt");
        assertEquals("newAcc", result.get("accessToken"));
    }

    @Test
    void refreshToken_tenantVersionMismatch_throwsException() {
        Claims claims = mock(Claims.class);
        when(jwtUtil.validateToken("rt")).thenReturn(true);
        when(jwtUtil.extractAllClaims("rt")).thenReturn(claims);
        when(jwtUtil.isPlatformToken(claims)).thenReturn(false);
        when(jwtUtil.getUserId(claims)).thenReturn(10L);
        when(jwtUtil.getTenantId(claims)).thenReturn(1L);
        when(jwtUtil.getMobile(claims)).thenReturn("13800000001");
        when(jwtUtil.getTenantVersion(claims)).thenReturn(0);
        when(redisUtil.get("tenant:version:1")).thenReturn("1"); // bumped

        assertThrows(BusinessException.class, () -> tenantAuthService.refreshToken("rt"));
    }

    // ── logout ────────────────────────────────────────────

    @Test
    void logout_addsTokenToBlacklist() {
        Claims claims = mock(Claims.class);
        when(jwtUtil.getExpirationFromToken("t"))
                .thenReturn(new Date(System.currentTimeMillis() + 3_600_000));
        when(jwtUtil.extractAllClaims("t")).thenReturn(claims);
        when(jwtUtil.getJti(claims)).thenReturn("jti-t");
        tenantAuthService.logout("Bearer t");
        verify(redisUtil).set(eq("blacklist:jti-t"), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }
}
