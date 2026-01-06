package com.trae.admin.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户创建/更新DTO
 */
@Data
public class UserDto {
    
    private Long id;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度必须在4-20个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    /**
     * 密码 (仅创建时需要，更新时可选)
     */
    @Size(min = 6, max = 20, message = "密码长度必须在6-20个字符之间")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "密码必须包含字母和数字")
    private String password;

    /**
     * 昵称
     */
    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 20, message = "昵称长度必须在2-20个字符之间")
    private String nickname;

    /**
     * 手机号
     */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 状态(1:启用, 0:禁用)
     */
    @NotNull(message = "状态不能为空")
    private Integer status;

    /**
     * 角色ID列表
     */
    private List<Long> roleIds;
}
