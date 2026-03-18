package com.trae.admin.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.admin.modules.rbac.entity.SysPermission;
import com.trae.admin.modules.rbac.entity.SysRole;
import com.trae.admin.modules.rbac.entity.SysRolePermission;
import com.trae.admin.modules.rbac.mapper.SysPermissionMapper;
import com.trae.admin.modules.rbac.mapper.SysRoleMapper;
import com.trae.admin.modules.rbac.mapper.SysRolePermissionMapper;
import com.trae.admin.modules.user.entity.SysUser;
import com.trae.admin.modules.user.entity.SysUserRole;
import com.trae.admin.modules.user.mapper.SysUserMapper;
import com.trae.admin.modules.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitService implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        try {
            // Check if admin user exists
            SysUser adminUser = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, "admin"));

            if (adminUser == null) {
                log.info("Initializing default admin user and data...");
                initData();
                log.info("Data initialization completed.");
            } else {
                log.info("Admin user already exists, skipping initialization.");
            }
        } catch (Exception e) {
            log.error("Data initialization failed", e);
        }
    }

    private void initData() {
        // 1. Initialize permissions
        List<SysPermission> permissions = initPermissions();
        permissions.forEach(sysPermissionMapper::insert);

        // 2. Initialize super admin role
        SysRole adminRole = new SysRole();
        adminRole.setRoleName("超级管理员");
        adminRole.setRoleKey("admin");
        adminRole.setDescription("拥有系统所有权限");
        adminRole.setCreateTime(LocalDateTime.now());
        adminRole.setUpdateTime(LocalDateTime.now());
        sysRoleMapper.insert(adminRole);

        // 3. Assign all permissions to admin role
        List<SysRolePermission> rolePermissions = new ArrayList<>();
        for (SysPermission permission : permissions) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(adminRole.getId());
            rp.setPermissionId(permission.getId());
            rolePermissions.add(rp);
        }
        rolePermissions.forEach(sysRolePermissionMapper::insert);

        // 4. Initialize admin user
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("123456"));
        admin.setNickname("超级管理员");
        admin.setStatus(1);
        admin.setCreateTime(LocalDateTime.now());
        admin.setUpdateTime(LocalDateTime.now());
        sysUserMapper.insert(admin);

        // 5. Assign admin role to admin user
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(admin.getId());
        userRole.setRoleId(adminRole.getId());
        sysUserRoleMapper.insert(userRole);

        log.info("Initialized admin user: admin/123456");
    }

    private List<SysPermission> initPermissions() {
        List<SysPermission> permissions = new ArrayList<>();
        Long id = 1L;

        // System Management (Directory)
        SysPermission systemDir = createPermission(id++, 0L, "系统管理", "DIR", null, "/system", null, 11);
        permissions.add(systemDir);

        // User Management (Menu)
        SysPermission userMenu = createPermission(id++, systemDir.getId(), "管理员", "MENU", null, "/system/user", "system/user/index", 1);
        permissions.add(userMenu);

        // User Management Buttons
        permissions.add(createPermission(id++, userMenu.getId(), "用户查询", "BTN", "sys:user:list", null, null, 1));
        permissions.add(createPermission(id++, userMenu.getId(), "用户详情", "BTN", "sys:user:query", null, null, 2));
        permissions.add(createPermission(id++, userMenu.getId(), "用户新增", "BTN", "sys:user:add", null, null, 3));
        permissions.add(createPermission(id++, userMenu.getId(), "用户修改", "BTN", "sys:user:edit", null, null, 4));
        permissions.add(createPermission(id++, userMenu.getId(), "用户删除", "BTN", "sys:user:remove", null, null, 5));
        permissions.add(createPermission(id++, userMenu.getId(), "用户重置密码", "BTN", "sys:user:reset", null, null, 6));

        // Role Management (Menu)
        SysPermission roleMenu = createPermission(id++, systemDir.getId(), "角色管理", "MENU", null, "/system/role", "system/role/index", 2);
        permissions.add(roleMenu);

        // Role Management Buttons
        permissions.add(createPermission(id++, roleMenu.getId(), "角色列表", "BTN", "sys:role:list", null, null, 1));
        permissions.add(createPermission(id++, roleMenu.getId(), "角色详情", "BTN", "sys:role:query", null, null, 2));
        permissions.add(createPermission(id++, roleMenu.getId(), "角色新增", "BTN", "sys:role:add", null, null, 3));
        permissions.add(createPermission(id++, roleMenu.getId(), "角色修改", "BTN", "sys:role:edit", null, null, 4));
        permissions.add(createPermission(id++, roleMenu.getId(), "角色删除", "BTN", "sys:role:remove", null, null, 5));
        permissions.add(createPermission(id++, roleMenu.getId(), "分配权限", "BTN", "role:assign", null, null, 6));

        // Permission Management (Menu)
        SysPermission permMenu = createPermission(id++, systemDir.getId(), "权限管理", "MENU", null, "/system/permission", "system/permission/index", 3);
        permissions.add(permMenu);

        // Permission Management Buttons
        permissions.add(createPermission(id++, permMenu.getId(), "权限列表", "BTN", "platform:perm:list", null, null, 1));
        permissions.add(createPermission(id++, permMenu.getId(), "权限新增", "BTN", "platform:perm:add", null, null, 2));
        permissions.add(createPermission(id++, permMenu.getId(), "权限修改", "BTN", "platform:perm:edit", null, null, 3));
        permissions.add(createPermission(id++, permMenu.getId(), "权限删除", "BTN", "platform:perm:remove", null, null, 4));

        // Log Management (Menu)
        SysPermission logMenu = createPermission(id++, systemDir.getId(), "操作日志", "MENU", null, "/system/log", "system/log/index", 4);
        permissions.add(logMenu);

        // Log Management Buttons
        permissions.add(createPermission(id++, logMenu.getId(), "日志查询", "BTN", "sys:log:list", null, null, 1));

        // App User Management (Directory)
        SysPermission appUserDir = createPermission(id++, 0L, "用户中心", "DIR", null, "/app-user", null, 10);
        permissions.add(appUserDir);

        // App User Management (Menu)
        SysPermission appUserMenu = createPermission(id++, appUserDir.getId(), "用户管理", "MENU", null, "/app-user/user", "app-user/user/index", 1);
        permissions.add(appUserMenu);

        // App User Management Buttons
        permissions.add(createPermission(id++, appUserMenu.getId(), "用户查询", "BTN", "app:user:list", null, null, 1));
        permissions.add(createPermission(id++, appUserMenu.getId(), "用户详情", "BTN", "app:user:view", null, null, 2));
        permissions.add(createPermission(id++, appUserMenu.getId(), "用户状态", "BTN", "app:user:status", null, null, 3));
        permissions.add(createPermission(id++, appUserMenu.getId(), "批量打标签", "BTN", "app:user:tag", null, null, 4));
        permissions.add(createPermission(id++, appUserMenu.getId(), "导出用户", "BTN", "app:user:export", null, null, 5));
        permissions.add(createPermission(id++, appUserMenu.getId(), "导入用户", "BTN", "app:user:import", null, null, 6));

        // App Tag Management (Menu)
        SysPermission appTagMenu = createPermission(id++, appUserDir.getId(), "标签管理", "MENU", null, "/app-user/tag", "app-user/tag/index", 2);
        permissions.add(appTagMenu);

        // App Tag Management Buttons
        permissions.add(createPermission(id++, appTagMenu.getId(), "标签查询", "BTN", "app:tag:list", null, null, 1));
        permissions.add(createPermission(id++, appTagMenu.getId(), "标签新增", "BTN", "app:tag:add", null, null, 2));
        permissions.add(createPermission(id++, appTagMenu.getId(), "标签编辑", "BTN", "app:tag:edit", null, null, 3));
        permissions.add(createPermission(id++, appTagMenu.getId(), "标签删除", "BTN", "app:tag:delete", null, null, 4));
        permissions.add(createPermission(id++, appTagMenu.getId(), "标签状态", "BTN", "app:tag:status", null, null, 5));

        // App Field Management (Menu)
        SysPermission appFieldMenu = createPermission(id++, appUserDir.getId(), "字段管理", "MENU", null, "/app-user/field", "app-user/field/index", 3);
        permissions.add(appFieldMenu);

        // App Field Management Buttons
        permissions.add(createPermission(id++, appFieldMenu.getId(), "字段查询", "BTN", "app:field:list", null, null, 1));
        permissions.add(createPermission(id++, appFieldMenu.getId(), "字段新增", "BTN", "app:field:add", null, null, 2));
        permissions.add(createPermission(id++, appFieldMenu.getId(), "字段编辑", "BTN", "app:field:edit", null, null, 3));
        permissions.add(createPermission(id++, appFieldMenu.getId(), "字段删除", "BTN", "app:field:remove", null, null, 4));
        permissions.add(createPermission(id++, appFieldMenu.getId(), "字段状态", "BTN", "app:field:status", null, null, 5));

        return permissions;
    }

    private SysPermission createPermission(Long id, Long parentId, String name, String type, String permissionKey, String path, String component, Integer sort) {
        SysPermission permission = new SysPermission();
        permission.setId(id);
        permission.setParentId(parentId);
        permission.setName(name);
        permission.setType(type);
        permission.setPermissionKey(permissionKey);
        permission.setPath(path);
        permission.setComponent(component);
        permission.setSort(sort);
        permission.setCreateTime(LocalDateTime.now());
        permission.setUpdateTime(LocalDateTime.now());
        return permission;
    }
}
