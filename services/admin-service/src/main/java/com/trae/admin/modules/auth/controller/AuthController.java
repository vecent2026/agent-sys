package com.trae.admin.modules.auth.controller;

import com.trae.admin.common.annotation.Log;
import com.trae.admin.common.result.Result;
import com.trae.admin.modules.auth.dto.LoginBody;
import com.trae.admin.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 */
@Tag(name = "认证模块", description = "用户登录、Token刷新、退出登录接口")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "用户登录",
            description = "使用账号密码登录，返回Access Token和Refresh Token",
            responses = {
                    @ApiResponse(responseCode = "200", description = "登录成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "账号或密码错误"),
                    @ApiResponse(responseCode = "500", description = "系统错误")
            }
    )
    @PostMapping("/login")
    @Log(module = "系统认证", action = "用户登录")
    public Result<Map<String, String>> login(@RequestBody LoginBody loginBody) {
        return Result.success(authService.login(loginBody));
    }

    @Operation(
            summary = "刷新Token",
            description = "使用Refresh Token换取新的Access Token",
            parameters = {
                    @Parameter(name = "refreshToken", description = "刷新令牌", required = true, example = "eyJhbGciOiJIUzI1NiJ9...")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "刷新成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "无效的刷新令牌"),
                    @ApiResponse(responseCode = "500", description = "系统错误")
            }
    )
    @PostMapping("/refresh")
    public Result<Map<String, String>> refresh(@RequestParam String refreshToken) {
        return Result.success(authService.refreshToken(refreshToken));
    }

    @Operation(
            summary = "退出登录",
            description = "将当前Token加入黑名单，清除登录状态",
            responses = {
                    @ApiResponse(responseCode = "200", description = "退出成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "500", description = "系统错误")
            }
    )
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        authService.logout(token);
        return Result.success();
    }
    
    @Operation(
            summary = "获取当前用户信息",
            description = "获取当前登录用户的详细信息",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "500", description = "系统错误")
            }
    )
    @GetMapping("/me")
    public Result<Object> getCurrentUser() {
        return Result.success(authService.getCurrentUser());
    }
    
    @Operation(
            summary = "获取用户菜单列表",
            description = "获取当前登录用户的菜单列表",
            responses = {
                    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = Result.class))),
                    @ApiResponse(responseCode = "401", description = "未授权"),
                    @ApiResponse(responseCode = "500", description = "系统错误")
            }
    )
    @GetMapping("/menus")
    public Result<Object> getCurrentUserMenus() {
        return Result.success(authService.getCurrentUserMenus());
    }
}
