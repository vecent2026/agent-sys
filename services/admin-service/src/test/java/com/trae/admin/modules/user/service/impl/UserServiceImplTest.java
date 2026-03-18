package com.trae.admin.modules.user.service.impl;

import com.trae.admin.modules.user.dto.UserDto;
import com.trae.admin.modules.user.entity.SysUser;
import com.trae.admin.modules.user.mapper.SysUserMapper;
import com.trae.admin.modules.user.mapper.SysUserRoleMapper;
import com.trae.admin.common.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RedisUtil redisUtil;
    
    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void save_Success() {
        // Arrange
        UserDto userDto = new UserDto();
        userDto.setUsername("testuser");
        userDto.setPassword("Test123");
        userDto.setNickname("测试用户");
        userDto.setStatus(1);
        
        when(passwordEncoder.encode(eq("Test123"))).thenReturn("encoded_password");
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        
        // Act
        userService.save(userDto);
        
        // Assert
        verify(sysUserMapper).insert(any(SysUser.class));
        verify(passwordEncoder).encode(eq("Test123"));
    }

    @Test
    void save_DuplicateUsername() {
        // Arrange
        UserDto userDto = new UserDto();
        userDto.setUsername("admin");
        userDto.setPassword("Test123");
        
        when(sysUserMapper.selectCount(any())).thenReturn(1L);
        
        // Act & Assert
        assertThrows(Exception.class, () -> userService.save(userDto));
    }

    @Test
    void changeStatus_Disabled() {
        // Arrange
        Long userId = 1L;
        Integer newStatus = 0;
        
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("testuser");
        user.setStatus(1);
        user.setTokenVersion(0);
        
        when(sysUserMapper.selectById(eq(userId))).thenReturn(user);
        
        // Act
        userService.changeStatus(userId, newStatus);
        
        // Assert
        verify(sysUserMapper, times(2)).updateById(any(SysUser.class));
        verify(redisUtil).set(eq("platform:version:1"), eq("1"));
    }

    @Test
    void delete_Success() {
        // Arrange
        List<Long> userIds = new ArrayList<>();
        userIds.add(1L);
        
        // Act
        userService.delete(userIds);
        
        // Assert
        verify(sysUserMapper).deleteBatchIds(eq(userIds));
        verify(sysUserRoleMapper).delete(any());
    }
}
