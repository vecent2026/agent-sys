package com.trae.admin.modules.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.common.annotation.Log;
import com.trae.admin.common.result.Result;
import com.trae.admin.modules.platform.dto.PlatformTenantCreateDto;
import com.trae.admin.modules.platform.dto.PlatformTenantQueryDto;
import com.trae.admin.modules.platform.dto.PlatformTenantUpdateDto;
import com.trae.admin.modules.platform.service.PlatformTenantService;
import com.trae.admin.modules.platform.vo.PlatformTenantVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "平台租户管理", description = "租户 CRUD、状态、权限配置")
@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
public class PlatformTenantController {

    private final PlatformTenantService platformTenantService;

    @Operation(summary = "分页查询租户")
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:tenant:list')")
    public Result<Page<PlatformTenantVo>> page(PlatformTenantQueryDto queryDto) {
        return Result.success(platformTenantService.page(queryDto));
    }

    @Operation(summary = "获取租户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:tenant:list')")
    public Result<PlatformTenantVo> get(@PathVariable Long id) {
        return Result.success(platformTenantService.get(id));
    }

    @Operation(summary = "创建租户")
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:tenant:add')")
    @Log(module = "租户管理", action = "创建租户")
    public Result<Void> create(@RequestBody PlatformTenantCreateDto dto) {
        platformTenantService.create(dto);
        return Result.success();
    }

    @Operation(summary = "更新租户")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:tenant:edit')")
    @Log(module = "租户管理", action = "更新租户")
    public Result<Void> update(@PathVariable Long id, @RequestBody PlatformTenantUpdateDto dto) {
        platformTenantService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "启用/禁用租户")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:tenant:disable')")
    @Log(module = "租户管理", action = "修改状态")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        platformTenantService.updateStatus(id, body.get("status"));
        return Result.success();
    }

    @Operation(summary = "获取租户权限 ID 列表")
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:tenant:permission')")
    public Result<List<Long>> getPermissionIds(@PathVariable Long id) {
        return Result.success(platformTenantService.getPermissionIds(id));
    }

    @Operation(summary = "更新租户权限")
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:tenant:permission')")
    @Log(module = "租户管理", action = "配置权限")
    public Result<Void> updatePermissionIds(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        platformTenantService.updatePermissionIds(id, body.get("permissionIds"));
        return Result.success();
    }
}
