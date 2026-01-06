package com.trae.admin.modules.rbac.service.impl;

import com.trae.admin.modules.rbac.entity.SysPermission;
import com.trae.admin.modules.rbac.mapper.SysPermissionMapper;
import com.trae.admin.modules.rbac.mapper.SysRolePermissionMapper;
import com.trae.admin.modules.rbac.vo.PermissionVo;
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
class PermissionServiceImplTest {

    @Mock
    private SysPermissionMapper sysPermissionMapper;
    @Mock
    private SysRolePermissionMapper sysRolePermissionMapper;
    
    @InjectMocks
    private PermissionServiceImpl permissionService;

    @Test
    void listTree_Structure() {
        // Arrange
        List<SysPermission> permissions = new ArrayList<>();
        
        // 根目录
        SysPermission root = new SysPermission();
        root.setId(1L);
        root.setParentId(0L);
        root.setName("系统管理");
        root.setType("DIR");
        permissions.add(root);
        
        // 子菜单
        SysPermission menu = new SysPermission();
        menu.setId(2L);
        menu.setParentId(1L);
        menu.setName("用户管理");
        menu.setType("MENU");
        permissions.add(menu);
        
        // 按钮
        SysPermission button = new SysPermission();
        button.setId(3L);
        button.setParentId(2L);
        button.setName("用户查询");
        button.setType("BTN");
        button.setPermissionKey("sys:user:list");
        permissions.add(button);
        
        when(sysPermissionMapper.selectList(any())).thenReturn(permissions);
        
        // Act
        List<PermissionVo> tree = permissionService.listTree();
        
        // Assert
        assertNotNull(tree);
        assertEquals(1, tree.size());
        assertEquals("系统管理", tree.get(0).getName());
        assertNotNull(tree.get(0).getChildren());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("用户管理", tree.get(0).getChildren().get(0).getName());
    }

    @Test
    void delete_HasChildren() {
        // Arrange
        Long permissionId = 1L;
        
        // Act
        permissionService.delete(permissionId);
        
        // Assert
        verify(sysPermissionMapper).deleteById(eq(permissionId));
    }

    @Test
    void delete_NoChildren() {
        // Arrange
        Long permissionId = 1L;
        
        // Act
        permissionService.delete(permissionId);
        
        // Assert
        verify(sysPermissionMapper).deleteById(eq(permissionId));
    }
}
