package com.trae.admin.modules.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trae.admin.common.annotation.Log;
import com.trae.admin.common.result.Result;
import com.trae.admin.modules.user.dto.UserDto;
import com.trae.admin.modules.user.dto.UserQueryDto;
import com.trae.admin.modules.user.service.UserService;
import com.trae.admin.modules.user.vo.UserVo;
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
 * 用户管理控制器
 */
@Tag(name = "用户模块", description = "用户信息管理、密码重置、角色关联")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "分页查询用户",
            description = "根据条件分页查询用户列表，支持用户名、手机号、状态筛选",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足")
            }
    )
    @GetMapping
    @PreAuthorize("hasAuthority('sys:user:list')")
    public Result<Page<UserVo>> page(UserQueryDto queryDto) {
        return Result.success(userService.page(queryDto));
    }

    @Operation(
            summary = "获取用户详情",
            description = "根据ID获取用户详细信息",
            parameters = {
                    @Parameter(name = "id", description = "用户ID", required = true, example = "1")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足"),
                    @ApiResponse(responseCode = "404", description = "用户不存在")
            }
    )
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:user:query')")
    public Result<UserVo> get(@PathVariable Long id) {
        return Result.success(userService.get(id));
    }

    @Operation(
            summary = "新增用户",
            description = "创建新用户，包括基本信息和角色分配",
            responses = {
                    @ApiResponse(responseCode = "200", description = "创建成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足"),
                    @ApiResponse(responseCode = "409", description = "用户名已存在")
            }
    )
    @PostMapping
    @PreAuthorize("hasAuthority('sys:user:add')")
    @Log(module = "用户管理", action = "新增用户")
    public Result<Void> save(@RequestBody UserDto userDto) {
        userService.save(userDto);
        return Result.success();
    }

    @Operation(
            summary = "修改用户",
            description = "更新用户信息，包括基本信息和角色分配",
            responses = {
                    @ApiResponse(responseCode = "200", description = "更新成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足"),
                    @ApiResponse(responseCode = "404", description = "用户不存在")
            }
    )
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    @Log(module = "用户管理", action = "修改用户")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserDto userDto) {
        userDto.setId(id);
        userService.update(userDto);
        return Result.success();
    }

    @Operation(
            summary = "删除用户",
            description = "批量删除用户，支持逻辑删除",
            responses = {
                    @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足")
            }
    )
    @DeleteMapping("/{ids}")
    @PreAuthorize("hasAuthority('sys:user:remove')")
    @Log(module = "用户管理", action = "删除用户")
    public Result<Void> delete(@PathVariable String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());
        userService.delete(idList);
        return Result.success();
    }

    @Operation(
            summary = "修改密码",
            description = "用户修改自己的密码，需要验证旧密码",
            responses = {
                    @ApiResponse(responseCode = "200", description = "修改成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "旧密码错误")
            }
    )
    @PostMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        String oldPassword = (String) params.get("oldPassword");
        String newPassword = (String) params.get("newPassword");
        userService.changePassword(id, oldPassword, newPassword);
        return Result.success();
    }

    @Operation(
            summary = "重置密码",
            description = "管理员重置用户密码，不需要验证旧密码",
            parameters = {
                    @Parameter(name = "id", description = "用户ID", required = true, example = "1")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "重置成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足")
            }
    )
    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('sys:user:reset')")
    @Log(module = "用户管理", action = "重置密码")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> params) {
        String password = params.get("password");
        userService.resetPassword(id, password);
        return Result.success();
    }

    @Operation(
            summary = "修改用户状态",
            description = "修改用户启用/禁用状态",
            parameters = {
                    @Parameter(name = "id", description = "用户ID", required = true, example = "1")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "修改成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足"),
                    @ApiResponse(responseCode = "404", description = "用户不存在")
            }
    )
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    @Log(module = "用户管理", action = "修改状态")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        Integer status = Integer.valueOf(params.get("status").toString());
        userService.changeStatus(id, status);
        return Result.success();
    }
    
    @Operation(
            summary = "获取用户角色ID列表",
            description = "获取指定用户关联的角色ID列表",
            parameters = {
                    @Parameter(name = "id", description = "用户ID", required = true, example = "1")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足")
            }
    )
    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('sys:user:query')")
    public Result<List<Long>> getUserRoles(@PathVariable Long id) {
        return Result.success(userService.getUserRoleIds(id));
    }
    
    @Operation(
            summary = "分配用户角色",
            description = "为指定用户分配角色",
            parameters = {
                    @Parameter(name = "id", description = "用户ID", required = true, example = "1")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "分配成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "403", description = "权限不足")
            }
    )
    @PostMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    @Log(module = "用户管理", action = "分配角色")
    public Result<Void> assignUserRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> requestBody) {
        userService.assignUserRoles(id, requestBody.get("roleIds"));
        return Result.success();
    }
}
