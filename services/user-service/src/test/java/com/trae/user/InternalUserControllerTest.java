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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;
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

    @Test
    void ensureUser_setsDefaultRegisterSource_whenCreatingNewUser() {
        when(appUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            AppUser inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        }).when(appUserMapper).insert(any(AppUser.class));

        Map<String, Object> result = controller.ensureUser(Map.of(
                "mobile", "19541189242",
                "nickname", "风雷翅"
        ));

        assertEquals(100L, result.get("id"));
        verify(appUserMapper).insert(argThat(user ->
                "19541189242".equals(user.getMobile())
                        && "风雷翅".equals(user.getNickname())
                        && "SYSTEM".equals(user.getRegisterSource())
                        && Integer.valueOf(1).equals(user.getStatus())
        ));
    }

    @Test
    void ensureUser_returnsExistingUser_whenConcurrentInsertHitsUniqueKey() {
        AppUser existing = makeUser(101L, "19541189242", null);
        existing.setNickname("风雷翅");

        when(appUserMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null)
                .thenReturn(existing);
        doThrow(new DuplicateKeyException("Duplicate entry")).when(appUserMapper).insert(any(AppUser.class));

        Map<String, Object> result = controller.ensureUser(Map.of(
                "mobile", "19541189242",
                "nickname", "风雷翅"
        ));

        assertEquals(101L, result.get("id"));
        assertEquals("19541189242", result.get("mobile"));
        verify(appUserMapper).insert(any(AppUser.class));
        verify(appUserMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void ensureUser_backfillsPassword_whenExistingUserHasNoPassword() {
        AppUser existing = makeUser(102L, "19541189242", null);
        existing.setNickname("风雷翅");
        when(appUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(passwordEncoder.encode("Admin@123")).thenReturn("encoded_pwd");

        Map<String, Object> result = controller.ensureUser(Map.of(
                "mobile", "19541189242",
                "nickname", "风雷翅",
                "password", "Admin@123"
        ));

        assertEquals(102L, result.get("id"));
        verify(passwordEncoder).encode("Admin@123");
        verify(appUserMapper).updateById(argThat(user ->
                user.getId().equals(102L) && StringUtils.hasText(user.getPassword())
        ));
    }
}
