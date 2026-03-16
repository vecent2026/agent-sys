package com.trae.admin.modules.auth.service;

import com.trae.admin.modules.auth.dto.TenantLoginBody;
import com.trae.admin.modules.auth.dto.TenantSelectBody;
import com.trae.admin.modules.auth.dto.TenantSwitchBody;

import java.util.List;
import java.util.Map;

/**
 * 租户端认证服务
 */
public interface TenantAuthService {

    /**
     * 手机号+密码登录
     * - 单租户：直接返回 tenant JWT
     * - 多租户：返回 preToken + tenants 列表
     */
    Map<String, Object> login(TenantLoginBody loginBody);

    /**
     * preToken 换取正式 tenant JWT（一次性使用）
     */
    Map<String, Object> selectTenant(TenantSelectBody body);

    /**
     * 当前 JWT 切换到另一个租户（必须是该用户的成员）
     */
    Map<String, Object> switchTenant(TenantSwitchBody body);

    /** 用 refreshToken 换取新 accessToken */
    Map<String, Object> refreshToken(String refreshToken);

    /** 主动登出 */
    void logout(String bearerToken);

    /** 获取当前租户用户信息 */
    Map<String, Object> getCurrentUser();

    /** 获取当前租户用户权限 key 列表 */
    List<String> getCurrentUserPermissions();
}
