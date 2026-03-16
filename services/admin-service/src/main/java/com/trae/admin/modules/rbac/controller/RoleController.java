package com.trae.admin.modules.rbac.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.common.annotation.Log;
import com.trae.admin.common.result.Result;
import com.trae.admin.modules.rbac.dto.RoleDto;
import com.trae.admin.modules.rbac.dto.RoleQueryDto;
import com.trae.admin.modules.rbac.service.RoleService;
import com.trae.admin.modules.rbac.vo.RoleVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色管理控制器
 */
@Tag(name = "角色模块", description = "角色信息管理、权限分配")
@RestController
@RequestMapping("/api/tenant/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(
            summary = "分页查询角色",
            description = "根据条件分页查询角色列表，支持角色名称、角色标识筛选",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足")
            }
    )
    @GetMapping
    @PreAuthorize("hasAuthority('tenant:role:list')")
    public Result<Page<RoleVo>> page(RoleQueryDto queryDto) {
        return Result.success(roleService.page(queryDto));
    }

    @Operation(
            summary = "获取所有角色",
            description = "获取系统中所有角色列表",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = Result.class)))
            }
    )
    @GetMapping("/all")
    public Result<List<RoleVo>> listAll() {
        return Result.success(roleService.listAll());
    }

    @Operation(
            summary = "获取角色详情",
            description = "根据ID获取角色详细信息",
            parameters = {
                    @Parameter(name = "id", description = "角色ID", required = true, example = "1")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足"),
                    @ApiResponse(responseCode = "404", description = "角色不存在")
            }
    )
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:role:query')")
    public Result<RoleVo> get(@PathVariable Long id) {
        return Result.success(roleService.get(id));
    }

    @Operation(
            summary = "获取角色权限ID列表",
            description = "获取指定角色关联的权限ID列表",
            parameters = {
                    @Parameter(name = "id", description = "角色ID", required = true, example = "1")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足")
            }
    )
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('tenant:role:query')")
    public Result<List<Long>> getRolePermissionIds(@PathVariable Long id) {
        return Result.success(roleService.getRolePermissionIds(id));
    }
    
    @Operation(
            summary = "分配角色权限",
            description = "为指定角色分配权限",
            parameters = {
                    @Parameter(name = "id", description = "角色ID", required = true, example = "1")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "分配成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足")
            }
    )
    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('tenant:role:assign')")
    @Log(module = "角色管理", action = "分配权限")
    public Result<Void> assignRolePermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> requestBody) {
        // Call the new assignPermissions method for permission assignment
        roleService.assignPermissions(id, requestBody.get("permissionIds"));
        return Result.success();
    }

    @Operation(
            summary = "新增角色",
            description = "创建新角色，包括角色信息和权限分配",
            responses = {
                    @ApiResponse(responseCode = "200", description = "创建成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足"),
                    @ApiResponse(responseCode = "409", description = "角色标识已存在")
            }
    )
    @PostMapping
    @PreAuthorize("hasAuthority('tenant:role:add')")
    @Log(module = "角色管理", action = "新增角色")
    public Result<Void> save(@RequestBody RoleDto roleDto) {
        roleService.save(roleDto);
        return Result.success();
    }

    @Operation(
            summary = "修改角色",
            description = "更新角色信息，包括角色信息和权限分配",
            responses = {
                    @ApiResponse(responseCode = "200", description = "更新成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足"),
                    @ApiResponse(responseCode = "404", description = "角色不存在")
            }
    )
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:role:edit')")
    @Log(module = "角色管理", action = "修改角色")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> requestBody) {
        // Create a RoleDto and set the id from path variable
        RoleDto roleDto = new RoleDto();
        roleDto.setId(id);
        // Set other fields from request body
        roleDto.setRoleName((String) requestBody.get("roleName"));
        roleDto.setRoleKey((String) requestBody.get("roleKey"));
        roleDto.setDescription((String) requestBody.get("description"));
        // Call the existing update method
        roleService.update(roleDto);
        return Result.success();
    }

    @Operation(
            summary = "删除角色",
            description = "批量删除角色，删除前会检查是否关联用户",
            responses = {
                    @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足"),
                    @ApiResponse(responseCode = "409", description = "角色已关联用户，无法删除")
            }
    )
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('tenant:role:remove')")
    @Log(module = "角色管理", action = "删除角色")
    public Result<Void> delete(@PathVariable String ids) {
        // Convert comma-separated string to List<Long>
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());
        roleService.delete(idList);
        return Result.success();
    }
}
