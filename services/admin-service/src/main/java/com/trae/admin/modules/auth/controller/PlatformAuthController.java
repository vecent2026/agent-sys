package com.trae.admin.modules.auth.controller;

import com.trae.admin.common.result.Result;
import com.trae.admin.modules.auth.dto.LoginBody;
import com.trae.admin.modules.auth.service.PlatformAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 平台端认证控制器
 * 路径前缀：/api/platform/auth
 */
@Tag(name = "平台端认证", description = "平台管理员登录、Token 刷新、退出登录")
@RestController
@RequestMapping("/api/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

    private final PlatformAuthService platformAuthService;

    @Operation(summary = "平台端登录（用户名+密码）")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginBody loginBody) {
        return Result.success(platformAuthService.login(loginBody));
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestParam String refreshToken) {
        return Result.success(platformAuthService.refreshToken(refreshToken));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        platformAuthService.logout(request.getHeader("Authorization"));
        return Result.success();
    }

    @Operation(summary = "获取当前平台用户信息")
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.success(platformAuthService.getCurrentUser());
    }

    @Operation(summary = "获取当前平台用户权限列表")
    @GetMapping("/permissions")
    public Result<List<String>> permissions() {
        return Result.success(platformAuthService.getCurrentUserPermissions());
    }
}
