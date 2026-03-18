package com.trae.admin.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.admin.common.security.JwtUtil;
import com.trae.admin.common.utils.RedisUtil;
import com.trae.admin.modules.auth.dto.LoginBody;
import com.trae.admin.modules.rbac.mapper.SysPermissionMapper;
import com.trae.admin.modules.rbac.service.PermissionService;
import com.trae.admin.modules.user.entity.SysUser;
import com.trae.admin.modules.user.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock JwtUtil jwtUtil;
    @Mock RedisUtil redisUtil;
    @Mock SysUserMapper sysUserMapper;
    @Mock SysPermissionMapper sysPermissionMapper;
    @Mock PermissionService permissionService;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @Mock com.trae.admin.modules.log.repository.SysLogRepository sysLogRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void login_success_returnsTokens() {
        LoginBody body = new LoginBody();
        body.setUsername("admin");
        body.setPassword("123456");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin");
        when(auth.getAuthorities()).thenReturn((Collection) List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtUtil.createAccessToken(anyString(), any(), any(), any())).thenReturn("access_token");
        when(jwtUtil.createRefreshToken(anyString(), any())).thenReturn("refresh_token");

        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        Map<String, String> result = authService.login(body);

        assertNotNull(result);
        assertEquals("access_token", result.get("accessToken"));
        assertEquals("refresh_token", result.get("refreshToken"));
        verify(redisUtil).set(eq("auth:refresh:admin"), eq("refresh_token"), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    void login_wrongPassword_throwsException() {
        LoginBody body = new LoginBody();
        body.setUsername("admin");
        body.setPassword("wrong");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));
        assertThrows(BadCredentialsException.class, () -> authService.login(body));
    }

    @Test
    void logout_invalidatesToken() {
        Claims claims = mock(Claims.class);
        when(jwtUtil.getUsernameFromToken("mytoken")).thenReturn("admin");
        when(jwtUtil.getExpirationFromToken("mytoken"))
                .thenReturn(new Date(System.currentTimeMillis() + 3_600_000));
        when(jwtUtil.extractAllClaims("mytoken")).thenReturn(claims);
        when(jwtUtil.getJti(claims)).thenReturn("jti-mytoken");

        authService.logout("Bearer mytoken");

        verify(redisUtil).delete("auth:refresh:admin");
        verify(redisUtil).set(eq("blacklist:jti-mytoken"), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void logout_expiredToken_skipsBlacklist() {
        when(jwtUtil.getUsernameFromToken("oldtoken")).thenReturn("admin");
        when(jwtUtil.getExpirationFromToken("oldtoken"))
                .thenReturn(new Date(System.currentTimeMillis() - 1_000));

        authService.logout("Bearer oldtoken");

        verify(redisUtil).delete("auth:refresh:admin");
        verify(redisUtil, never()).set(startsWith("blacklist:"), any(), anyLong(), any());
    }
}
