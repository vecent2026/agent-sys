package com.trae.admin.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录表单
 */
@Data
@Schema(description = "登录请求参数")
public class LoginBody {
    /**
     * 用户名
     */
    @Schema(description = "用户名", required = true, example = "admin", minLength = 4, maxLength = 50)
    private String username;

    /**
     * 密码
     */
    @Schema(description = "密码", required = true, example = "123456", minLength = 6, maxLength = 20)
    private String password;
}
