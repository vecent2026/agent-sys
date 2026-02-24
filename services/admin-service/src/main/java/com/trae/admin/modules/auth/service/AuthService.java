package com.trae.admin.modules.auth.service;

import com.trae.admin.modules.auth.dto.LoginBody;
import java.util.Map;

/**
 * 认证服务接口
 */
public interface AuthService {
    /**
     * 登录
     * @param loginBody 登录信息
     * @return Token Map
     */
    Map<String, String> login(LoginBody loginBody);

    /**
     * 刷新Token
     * @param refreshToken 刷新Token
     * @return Token Map
     */
    Map<String, String> refreshToken(String refreshToken);

    /**
     * 退出登录
     * @param token 当前Access Token
     */
    void logout(String token);

    /**
     * 获取当前用户信息
     * @return 用户信息
     */
    Object getCurrentUser();

    /**
     * 获取当前用户菜单列表
     * @return 菜单列表
     */
    Object getCurrentUserMenus();
}
