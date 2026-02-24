package com.trae.admin.modules.rbac.service.impl;

import com.trae.admin.modules.rbac.dto.RoleDto;
import com.trae.admin.modules.rbac.entity.SysRole;
import com.trae.admin.modules.rbac.mapper.SysRoleMapper;
import com.trae.admin.modules.rbac.mapper.SysRolePermissionMapper;
import com.trae.admin.modules.user.entity.SysUserRole;
import com.trae.admin.modules.user.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private SysRolePermissionMapper sysRolePermissionMapper;
    
    @InjectMocks
    private RoleServiceImpl roleService;

    @Test
    void delete_HasUsers() {
        // Arrange
        List<Long> roleIds = new ArrayList<>();
        roleIds.add(1L);
        
        // Act
        roleService.delete(roleIds);
        
        // Assert
        verify(sysRoleMapper).deleteBatchIds(eq(roleIds));
        verify(sysRolePermissionMapper).delete(any());
    }

    @Test
    void delete_NoUsers() {
        // Arrange
        List<Long> roleIds = new ArrayList<>();
        roleIds.add(1L);
        
        // Act
        roleService.delete(roleIds);
        
        // Assert
        verify(sysRoleMapper).deleteBatchIds(eq(roleIds));
        verify(sysRolePermissionMapper).delete(any());
    }

    @Test
    void save_Success() {
        // Arrange
        RoleDto roleDto = new RoleDto();
        roleDto.setRoleName("测试角色");
        roleDto.setRoleKey("test_role");
        
        when(sysRoleMapper.selectCount(any())).thenReturn(0L);
        
        // Act
        roleService.save(roleDto);
        
        // Assert
        verify(sysRoleMapper).insert(any(SysRole.class));
    }
}