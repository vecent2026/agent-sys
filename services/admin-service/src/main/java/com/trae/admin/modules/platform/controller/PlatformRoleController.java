package com.trae.admin.modules.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.common.annotation.Log;
import com.trae.admin.common.result.Result;
import com.trae.admin.modules.platform.service.PlatformRoleService;
import com.trae.admin.modules.platform.vo.PlatformRoleVo;
import com.trae.admin.modules.rbac.dto.RoleDto;
import com.trae.admin.modules.rbac.dto.RoleQueryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "平台角色管理", description = "平台角色 CRUD、权限分配")
@RestController
@RequestMapping("/api/platform/roles")
@RequiredArgsConstructor
public class PlatformRoleController {

    private final PlatformRoleService platformRoleService;

    @Operation(summary = "分页查询角色")
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:role:list')")
    public Result<Page<PlatformRoleVo>> page(RoleQueryDto queryDto) {
        return Result.success(platformRoleService.page(queryDto));
    }

    @Operation(summary = "获取所有角色")
    @GetMapping("/all")
    public Result<List<PlatformRoleVo>> listAll() {
        return Result.success(platformRoleService.listAll());
    }

    @Operation(summary = "获取角色详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:role:list')")
    public Result<PlatformRoleVo> get(@PathVariable Long id) {
        return Result.success(platformRoleService.get(id));
    }

    @Operation(summary = "获取角色权限 ID 列表")
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:role:list')")
    public Result<List<Long>> getRolePermissionIds(@PathVariable Long id) {
        return Result.success(platformRoleService.getRolePermissionIds(id));
    }

    @Operation(summary = "分配角色权限")
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:role:edit')")
    @Log(module = "角色管理", action = "分配权限")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        platformRoleService.assignPermissions(id, body.get("permissionIds"));
        return Result.success();
    }

    @Operation(summary = "创建角色")
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:role:add')")
    @Log(module = "角色管理", action = "新增角色")
    public Result<Void> save(@RequestBody RoleDto roleDto) {
        platformRoleService.save(roleDto);
        return Result.success();
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:role:edit')")
    @Log(module = "角色管理", action = "修改角色")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        RoleDto roleDto = new RoleDto();
        roleDto.setId(id);
        roleDto.setRoleName((String) body.get("roleName"));
        roleDto.setRoleKey((String) body.get("roleKey"));
        roleDto.setDescription((String) body.get("description"));
        roleDto.setPermissionIds((List<Long>) body.get("permissionIds"));
        platformRoleService.update(id, roleDto);
        return Result.success();
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('platform:role:remove')")
    @Log(module = "角色管理", action = "删除角色")
    public Result<Void> delete(@PathVariable String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());
        platformRoleService.delete(idList);
        return Result.success();
    }
}
