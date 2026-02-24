package com.trae.admin.modules.auth.service.impl;

import com.trae.admin.modules.auth.dto.LoginBody;
import com.trae.admin.modules.auth.service.AuthService;
import com.trae.admin.common.security.JwtUtil;
import com.trae.admin.common.utils.RedisUtil;
import com.trae.admin.modules.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private SysUserMapper sysUserMapper;
    
    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void login_Success() {
        // Arrange
        LoginBody loginBody = new LoginBody();
        loginBody.setUsername("admin");
        loginBody.setPassword("123456");
        
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        
        when(jwtUtil.createAccessToken(anyString(), anyLong())).thenReturn("access_token");
        when(jwtUtil.createRefreshToken(anyString(), anyLong())).thenReturn("refresh_token");
        
        // Act
        Map<String, String> result = authService.login(loginBody);
        
        // Assert
        assertNotNull(result);
        assertEquals("access_token", result.get("accessToken"));
        assertEquals("refresh_token", result.get("refreshToken"));
        verify(redisUtil).set(eq("auth:refresh:admin"), eq("refresh_token"), anyLong(), any());
    }

    @Test
    void login_WrongPassword() {
        // Arrange
        LoginBody loginBody = new LoginBody();
        loginBody.setUsername("admin");
        loginBody.setPassword("wrong_password");
        
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));
        
        // Act & Assert
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> authService.login(loginBody));
    }

    @Test
    void logout_Success() {
        // Arrange
        String token = "Bearer access_token";
        when(jwtUtil.getUsernameFromToken(eq("access_token"))).thenReturn("admin");
        when(jwtUtil.getExpirationFromToken(eq("access_token"))).thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
        
        // Act
        authService.logout(token);
        
        // Assert
        verify(redisUtil).delete(eq("auth:refresh:admin"));
        verify(redisUtil).set(eq("blacklist:access_token"), eq("1"), anyLong(), any());
    }
}