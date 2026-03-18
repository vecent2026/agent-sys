package com.trae.admin.modules.auth.controller;

import com.trae.admin.common.result.Result;
import com.trae.admin.modules.auth.dto.TenantLoginBody;
import com.trae.admin.modules.auth.dto.TenantSelectBody;
import com.trae.admin.modules.auth.dto.TenantSwitchBody;
import com.trae.admin.modules.auth.service.TenantAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 租户端认证控制器
 * 路径前缀：/api/tenant/auth
 */
@Tag(name = "租户端认证", description = "租户用户登录、租户选择、Token 刷新、退出登录")
@RestController
@RequestMapping("/api/tenant/auth")
@RequiredArgsConstructor
public class TenantAuthController {

    private final TenantAuthService tenantAuthService;

    @Operation(summary = "租户端登录（手机号+密码）",
            description = "单租户直接返回 JWT；多租户返回 preToken + tenants 列表")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody TenantLoginBody loginBody) {
        return Result.success(tenantAuthService.login(loginBody));
    }

    @Operation(summary = "选择租户（preToken 换正式 JWT，一次性）")
    @PostMapping({"/select", "/select-tenant"})
    public Result<Map<String, Object>> select(@RequestBody TenantSelectBody body) {
        return Result.success(tenantAuthService.selectTenant(body));
    }

    @Operation(summary = "切换租户（当前 JWT 换新租户 JWT）")
    @PostMapping({"/switch", "/switch-tenant"})
    public Result<Map<String, Object>> switchTenant(@RequestBody TenantSwitchBody body) {
        return Result.success(tenantAuthService.switchTenant(body));
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestParam String refreshToken) {
        return Result.success(tenantAuthService.refreshToken(refreshToken));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        tenantAuthService.logout(request.getHeader("Authorization"));
        return Result.success();
    }

    @Operation(summary = "获取当前租户用户信息")
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.success(tenantAuthService.getCurrentUser());
    }

    @Operation(summary = "获取当前租户用户权限列表")
    @GetMapping("/permissions")
    public Result<List<String>> permissions() {
        return Result.success(tenantAuthService.getCurrentUserPermissions());
    }

    @Operation(summary = "获取当前用户可选租户列表")
    @GetMapping("/my-tenants")
    public Result<List<Map<String, Object>>> myTenants() {
        return Result.success(tenantAuthService.getMyTenants());
    }
}
