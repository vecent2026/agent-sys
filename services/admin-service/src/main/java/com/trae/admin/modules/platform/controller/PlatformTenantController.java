package com.trae.admin.modules.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.common.annotation.Log;
import com.trae.admin.common.result.Result;
import com.trae.admin.modules.platform.dto.TenantDto;
import com.trae.admin.modules.platform.dto.TenantQueryDto;
import com.trae.admin.modules.platform.service.PlatformTenantService;
import com.trae.admin.modules.platform.vo.TenantVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "租户管理", description = "平台级租户信息管理")
@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
public class PlatformTenantController {

    private final PlatformTenantService tenantService;

    @Operation(summary = "分页查询租户")
    @GetMapping
    @PreAuthorize("hasAuthority('platform:tenant:list')")
    public Result<Page<TenantVo>> page(TenantQueryDto query) {
        return Result.success(tenantService.page(query));
    }

    @Operation(summary = "获取全部租户（下拉用）")
    @GetMapping("/all")
    public Result<List<TenantVo>> listAll() {
        return Result.success(tenantService.listAll());
    }

    @Operation(summary = "租户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('platform:tenant:query')")
    public Result<TenantVo> get(@PathVariable Long id) {
        return Result.success(tenantService.get(id));
    }

    @Operation(summary = "新增租户")
    @PostMapping
    @PreAuthorize("hasAuthority('platform:tenant:add')")
    @Log(module = "租户管理", action = "新增租户")
    public Result<Void> save(@Valid @RequestBody TenantDto dto) {
        tenantService.save(dto);
        return Result.success();
    }

    @Operation(summary = "修改租户")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('platform:tenant:edit')")
    @Log(module = "租户管理", action = "修改租户")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TenantDto dto) {
        dto.setId(id);
        tenantService.update(dto);
        return Result.success();
    }

    @Operation(summary = "删除租户")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('platform:tenant:remove')")
    @Log(module = "租户管理", action = "删除租户")
    public Result<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return Result.success();
    }

    @Operation(summary = "修改租户状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('platform:tenant:edit')")
    @Log(module = "租户管理", action = "修改状态")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        tenantService.changeStatus(id, Integer.valueOf(params.get("status").toString()));
        return Result.success();
    }

    @Operation(summary = "获取租户权限ID列表")
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('platform:tenant:query')")
    public Result<List<Long>> getPermissions(@PathVariable Long id) {
        return Result.success(tenantService.getPermissionIds(id));
    }

    @Operation(summary = "设置租户权限")
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('platform:tenant:edit')")
    @Log(module = "租户管理", action = "设置权限")
    public Result<Void> updatePermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        tenantService.updatePermissions(id, body.getOrDefault("permissionIds", List.of()));
        return Result.success();
    }

    @Operation(summary = "校验租户编码是否可用")
    @GetMapping("/check-code")
    @PreAuthorize("hasAuthority('platform:tenant:add') or hasAuthority('platform:tenant:edit')")
    public Result<Boolean> checkCode(@RequestParam String code,
                                     @RequestParam(required = false) Long id) {
        return Result.success(tenantService.checkCodeAvailable(code, id));
    }

    @Operation(summary = "租户统计")
    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAuthority('platform:tenant:query')")
    public Result<Map<String, Object>> stats(@PathVariable Long id) {
        return Result.success(tenantService.getStats(id));
    }

    @Operation(summary = "租户成员列表（平台视角）")
    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('platform:tenant:query')")
    public Result<List<Map<String, Object>>> members(@PathVariable Long id) {
        return Result.success(tenantService.listMembers(id));
    }
}
