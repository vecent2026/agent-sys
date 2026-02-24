package com.trae.admin.modules.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色DTO
 */
@Data
public class RoleDto {
    
    private Long id;

    /**
     * 角色名称
     */
    @NotBlank(message = "角色名称不能为空")
    @Size(min = 2, max = 20, message = "角色名称长度必须在2-20个字符之间")
    private String roleName;

    /**
     * 角色标识
     */
    @NotBlank(message = "角色标识不能为空")
    @Size(min = 2, max = 50, message = "角色标识长度必须在2-50个字符之间")
    private String roleKey;

    /**
     * 描述
     */
    @Size(max = 200, message = "描述长度不能超过200个字符")
    private String description;

    /**
     * 权限ID列表
     */
    private List<Long> permissionIds;
}
