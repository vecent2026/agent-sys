package com.trae.admin.modules.auth.dto;

import lombok.Data;

/**
 * 租户端登录请求（手机号 + 密码）
 */
@Data
public class TenantLoginBody {
    private String mobile;
    private String password;
}
