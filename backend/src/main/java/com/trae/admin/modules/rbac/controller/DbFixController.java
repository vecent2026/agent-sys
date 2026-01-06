package com.trae.admin.modules.rbac.controller;

import com.trae.admin.common.result.Result;
import com.trae.admin.modules.rbac.entity.SysPermission;
import com.trae.admin.modules.rbac.mapper.SysPermissionMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据库修复控制器
 */
@Tag(name = "数据库修复", description = "用于修复数据库中的路径问题")
@RestController
@RequestMapping("/api/db")
@RequiredArgsConstructor
public class DbFixController {

    private final SysPermissionMapper sysPermissionMapper;

    @Operation(summary = "修复权限管理路径")
    @PostMapping("/fix-permission-path")
    public Result<Map<String, Object>> fixPermissionPath() {
        Map<String, Object> result = new HashMap<>();
        
        // 更新权限管理的路径
        int updated = sysPermissionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysPermission>()
                .eq(SysPermission::getName, "权限管理")
                .eq(SysPermission::getType, "MENU")
                .set(SysPermission::getPath, "/system/perm")
                .set(SysPermission::getComponent, "system/perm/index"));
        
        result.put("updated", updated);
        result.put("message", "权限管理路径已更新为 /system/perm");
        
        return Result.success(result);
    }

    @Operation(summary = "检查权限管理路径")
    @GetMapping("/check-permission-path")
    public Result<SysPermission> checkPermissionPath() {
        SysPermission permission = sysPermissionMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getName, "权限管理")
                .eq(SysPermission::getType, "MENU"));
        
        return Result.success(permission);
    }
}
