package com.trae.admin.modules.rbac.dto;

import lombok.Data;

/**
 * 角色查询DTO
 */
@Data
public class RoleQueryDto {
    
    private Integer page = 1;
    private Integer size = 10;
    private String roleName;
    private String roleKey;
}
