package com.trae.admin.common.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试
 * 覆盖：平台 token / 租户 token / preToken / 提取方法 / 校验
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // 注入 @Value 字段（测试环境无 Spring 容器）
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "test-secret-key-must-be-at-least-32-bytes!!");
        ReflectionTestUtils.setField(jwtUtil, "expiration",       7_200_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604_800_000L);
    }

    // ── 平台 token ─────────────────────────────────────────

    @Test
    void createPlatformToken_claimsCorrect() {
        String token = jwtUtil.createPlatformToken(1L, "admin", true, 0, List.of("platform:user:list"));
        Claims claims = jwtUtil.extractAllClaims(token);

        assertTrue((Boolean) claims.get("isPlatform"));
        assertEquals(1, ((Number) claims.get("userId")).intValue());
        assertTrue((Boolean) claims.get("isSuper"));
        assertEquals(0, ((Number) claims.get("tokenVersion")).intValue());
        assertEquals("admin", claims.getSubject());
    }

    @Test
    void isPlatformToken_trueForPlatformToken() {
        String token = jwtUtil.createPlatformToken(1L, "admin", false, 0, List.of());
        assertTrue(jwtUtil.isPlatformToken(jwtUtil.extractAllClaims(token)));
    }

    @Test
    void createPlatformRefreshToken_isRefreshFlag() {
        String token = jwtUtil.createPlatformRefreshToken(1L, "admin", 0);
        Claims claims = jwtUtil.extractAllClaims(token);
        assertTrue((Boolean) claims.get("isRefresh"));
        assertTrue((Boolean) claims.get("isPlatform"));
    }

    // ── 租户 token ─────────────────────────────────────────

    @Test
    void createTenantToken_claimsCorrect() {
        String token = jwtUtil.createTenantToken(10L, "13800000001", 2L, 3, false, List.of("tenant:role:list"));
        Claims claims = jwtUtil.extractAllClaims(token);

        assertFalse((Boolean) claims.get("isPlatform"));
        assertEquals(10, ((Number) claims.get("userId")).intValue());
        assertEquals(2, ((Number) claims.get("tenantId")).intValue());
        assertEquals(3, ((Number) claims.get("tenantVersion")).intValue());
        assertEquals("13800000001", jwtUtil.getMobile(claims));
    }

    @Test
    void isPlatformToken_falseForTenantToken() {
        String token = jwtUtil.createTenantToken(10L, "13800000001", 2L, 0, false, List.of());
        assertFalse(jwtUtil.isPlatformToken(jwtUtil.extractAllClaims(token)));
    }

    @Test
    void getTenantId_returnsCorrectValue() {
        String token = jwtUtil.createTenantToken(10L, "13800000001", 99L, 0, false, List.of());
        Claims claims = jwtUtil.extractAllClaims(token);
        assertEquals(99L, jwtUtil.getTenantId(claims));
    }

    // ── preToken ───────────────────────────────────────────

    @Test
    void createPreToken_isPreFlagSet() {
        String token = jwtUtil.createPreToken(10L, "13800000001", List.of());
        Claims claims = jwtUtil.extractAllClaims(token);
        assertTrue(jwtUtil.isPreToken(claims));
        assertFalse(jwtUtil.isPlatformToken(claims));
    }

    @Test
    void createPreToken_hasJti() {
        String token = jwtUtil.createPreToken(10L, "13800000001", List.of());
        Claims claims = jwtUtil.extractAllClaims(token);
        assertNotNull(jwtUtil.getJti(claims));
        assertFalse(jwtUtil.getJti(claims).isBlank());
    }

    @Test
    void isPreToken_falseForAccessToken() {
        String token = jwtUtil.createTenantToken(10L, "13800000001", 1L, 0, false, List.of());
        assertFalse(jwtUtil.isPreToken(jwtUtil.extractAllClaims(token)));
    }

    // ── 权限列表 ───────────────────────────────────────────

    @Test
    void getAuthorities_returnsCorrectList() {
        List<String> perms = List.of("tenant:role:list", "tenant:role:add");
        String token = jwtUtil.createTenantToken(1L, "13800000001", 1L, 0, false, perms);
        List<String> extracted = jwtUtil.getAuthorities(jwtUtil.extractAllClaims(token));
        assertEquals(perms, extracted);
    }

    // ── 校验 ───────────────────────────────────────────────

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = jwtUtil.createPlatformToken(1L, "admin", false, 0, List.of());
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_tamperedToken_returnsFalse() {
        String token = jwtUtil.createPlatformToken(1L, "admin", false, 0, List.of());
        assertFalse(jwtUtil.validateToken(token + "tampered"));
    }

    // ── getUserId ──────────────────────────────────────────

    @Test
    void getUserId_fromPlatformToken() {
        String token = jwtUtil.createPlatformToken(42L, "admin", false, 0, List.of());
        Claims claims = jwtUtil.extractAllClaims(token);
        assertEquals(42L, jwtUtil.getUserId(claims));
    }
}
