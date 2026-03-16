package com.trae.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.user.controller.InternalUserController;
import com.trae.user.entity.AppUser;
import com.trae.user.entity.TenantUser;
import com.trae.user.mapper.AppUserMapper;
import com.trae.user.mapper.TenantUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * InternalUserController 单元测试
 * 覆盖：by-mobile 查询、verify 凭证验证（成功/失败）、密码不暴露
 */
@ExtendWith(MockitoExtension.class)
class InternalUserControllerTest {

    @Mock AppUserMapper appUserMapper;
    @Mock TenantUserMapper tenantUserMapper;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks
    private InternalUserController controller;

    // ── 辅助 ──────────────────────────────────────────────

    private AppUser makeUser(Long id, String mobile, String hashedPwd) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setMobile(mobile);
        u.setPassword(hashedPwd);
        u.setIsDeleted(0);
        return u;
    }

    // ── getUserByMobile ────────────────────────────────────

    @Test
    void getUserByMobile_found_returnsMap() {
        when(appUserMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(makeUser(1L, "13800000001", "$hash$"));

        Map<String, Object> result = controller.getUserByMobile("13800000001");

        assertFalse(result.isEmpty());
        assertEquals(1L, result.get("id"));
        assertEquals("13800000001", result.get("mobile"));
        // 密码字段不应暴露
        assertFalse(result.containsKey("password"), "password must not be exposed");
    }

    @Test
    void getUserByMobile_notFound_returnsEmptyMap() {
        when(appUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        Map<String, Object> result = controller.getUserByMobile("00000000000");
        assertTrue(result.isEmpty());
    }

    // ── verifyUser ─────────────────────────────────────────

    @Test
    void verifyUser_correctCredentials_returnsUserMap() {
        when(appUserMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(makeUser(1L, "13800000001", "$hash$"));
        when(passwordEncoder.matches("123456", "$hash$")).thenReturn(true);

        Map<String, String> body = Map.of("mobile", "13800000001", "password", "123456");
        Map<String, Object> result = controller.verifyUser(body);

        assertFalse(result.isEmpty());
        assertEquals(1L, result.get("id"));
        assertFalse(result.containsKey("password"), "password must not be exposed");
    }

    @Test
    void verifyUser_wrongPassword_returnsEmptyMap() {
        when(appUserMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(makeUser(1L, "13800000001", "$hash$"));
        when(passwordEncoder.matches("wrong", "$hash$")).thenReturn(false);

        Map<String, String> body = Map.of("mobile", "13800000001", "password", "wrong");
        Map<String, Object> result = controller.verifyUser(body);

        assertTrue(result.isEmpty());
    }

    @Test
    void verifyUser_userNotFound_returnsEmptyMap() {
        when(appUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Map<String, String> body = Map.of("mobile", "99999999999", "password", "123456");
        Map<String, Object> result = controller.verifyUser(body);

        assertTrue(result.isEmpty());
    }

    @Test
    void verifyUser_noPassword_returnsEmptyMap() {
        AppUser user = makeUser(1L, "13800000001", null); // 未设置密码
        when(appUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        Map<String, String> body = Map.of("mobile", "13800000001", "password", "123456");
        Map<String, Object> result = controller.verifyUser(body);

        assertTrue(result.isEmpty());
    }
}
