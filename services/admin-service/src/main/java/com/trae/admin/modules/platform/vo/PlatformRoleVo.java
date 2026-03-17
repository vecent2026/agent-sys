package com.trae.admin.modules.platform.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlatformRoleVo {
    private Long id;
    private String roleName;
    private String roleKey;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private Integer userCount;
}
