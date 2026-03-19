package com.trae.admin.modules.rbac.service.impl;

import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.modules.rbac.dto.RoleDto;
import com.trae.admin.modules.rbac.entity.SysRole;
import com.trae.admin.modules.rbac.mapper.SysRoleMapper;
import com.trae.admin.modules.rbac.mapper.SysRolePermissionMapper;
import com.trae.admin.modules.user.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RoleServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock SysRoleMapper sysRoleMapper;
    @Mock SysRolePermissionMapper sysRolePermissionMapper;
    @Mock SysUserRoleMapper sysUserRoleMapper;

    @InjectMocks
    private RoleServiceImpl roleService;

    // ── delete ─────────────────────────────────────────────

    @Test
    void delete_roleHasNoUsers_deletesSuccessfully() {
        when(sysUserRoleMapper.selectCount(any())).thenReturn(0L);
        when(sysRoleMapper.selectBatchIds(List.of(1L))).thenReturn(List.of());

        roleService.delete(List.of(1L));

        verify(sysRoleMapper).deleteBatchIds(List.of(1L));
        verify(sysRolePermissionMapper).delete(any());
    }

    @Test
    void delete_roleHasUsers_throwsBusinessException() {
        when(sysUserRoleMapper.selectCount(any())).thenReturn(2L);

        assertThrows(BusinessException.class, () -> roleService.delete(List.of(1L)));

        verify(sysRoleMapper, never()).deleteBatchIds(any());
    }

    // ── save ───────────────────────────────────────────────

    @Test
    void save_newRole_insertsSuccessfully() {
        RoleDto dto = new RoleDto();
        dto.setRoleName("测试角色");
        dto.setRoleKey("test_role");

        when(sysRoleMapper.selectCount(any())).thenReturn(0L);

        roleService.save(dto);

        verify(sysRoleMapper).insert(any(SysRole.class));
    }

    @Test
    void save_duplicateRoleKey_throwsBusinessException() {
        RoleDto dto = new RoleDto();
        dto.setRoleName("测试角色");
        dto.setRoleKey("duplicate_key");

        when(sysRoleMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> roleService.save(dto));
        verify(sysRoleMapper, never()).insert(any());
    }
}
