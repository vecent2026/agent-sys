package com.trae.admin.modules.rbac.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色VO
 */
@Data
public class RoleVo {
    
    private Long id;
    private String roleName;
    private String roleKey;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private Integer userCount;
}
