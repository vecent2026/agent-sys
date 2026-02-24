package com.trae.admin.modules.rbac.controller;

import com.trae.admin.common.annotation.Log;
import com.trae.admin.common.result.Result;
import com.trae.admin.modules.rbac.dto.PermissionDto;
import com.trae.admin.modules.rbac.service.PermissionService;
import com.trae.admin.modules.rbac.vo.PermissionVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限管理控制器
 */
@Tag(name = "权限模块", description = "菜单、按钮、API权限管理，树形结构展示")
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(
            summary = "获取权限树",
            description = "以树形结构展示所有权限节点，支持目录、菜单、按钮类型",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足")
            }
    )
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('sys:menu:list')")
    public Result<List<PermissionVo>> listTree() {
        return Result.success(permissionService.listTree());
    }

    @Operation(
            summary = "获取所有权限列表",
            description = "获取系统中所有权限节点，非树形结构",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足")
            }
    )
    @GetMapping
    @PreAuthorize("hasAuthority('sys:menu:list')")
    public Result<List<PermissionVo>> listAll(@RequestParam(required = false) String name) {
        return Result.success(permissionService.listAll(name));
    }

    @Operation(
            summary = "新增权限节点",
            description = "创建新的权限节点，支持目录、菜单、按钮类型",
            responses = {
                    @ApiResponse(responseCode = "200", description = "创建成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足")
            }
    )
    @PostMapping
    @PreAuthorize("hasAuthority('sys:menu:add')")
    @Log(module = "权限管理", action = "新增权限")
    public Result<Void> save(@RequestBody PermissionDto permissionDto) {
        permissionService.save(permissionDto);
        return Result.success();
    }

    @Operation(
            summary = "修改权限节点",
            description = "更新权限节点信息",
            responses = {
                    @ApiResponse(responseCode = "200", description = "更新成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足")
            }
    )
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:menu:edit')")
    @Log(module = "权限管理", action = "修改权限")
    public Result<Void> update(@PathVariable Long id, @RequestBody PermissionDto permissionDto) {
        permissionDto.setId(id);
        permissionService.update(permissionDto);
        return Result.success();
    }

    @Operation(
            summary = "删除权限节点",
            description = "删除权限节点，如有子节点则无法删除",
            parameters = {
                    @Parameter(name = "id", description = "权限ID", required = true, example = "1")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足"),
                    @ApiResponse(responseCode = "409", description = "存在子节点，无法删除")
            }
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:menu:remove')")
    @Log(module = "权限管理", action = "删除权限")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return Result.success();
    }
}
