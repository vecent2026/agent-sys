package com.trae.admin.modules.auth.service;

import com.trae.admin.modules.auth.dto.LoginBody;

import java.util.List;
import java.util.Map;

/**
 * 平台端认证服务
 */
public interface PlatformAuthService {

    /** 用户名+密码登录，返回 accessToken + refreshToken */
    Map<String, Object> login(LoginBody loginBody);

    /** 用 refreshToken 换取新 accessToken */
    Map<String, Object> refreshToken(String refreshToken);

    /** 主动登出（jti 加黑名单） */
    void logout(String bearerToken);

    /** 获取当前平台用户信息 */
    Map<String, Object> getCurrentUser();

    /** 获取当前平台用户权限 key 列表 */
    List<String> getCurrentUserPermissions();
}
