package com.trae.admin.modules.platform.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantVo {
    private Long id;
    private String tenantCode;
    private String tenantName;
    private String description;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private Integer status;
    private LocalDateTime expireTime;
    private Integer maxUsers;
    private Integer dataVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
