package com.trae.admin.modules.platform.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantDto {
    private Long id;
    private String tenantCode;
    private String tenantName;
    private Integer status;
    private LocalDateTime expireTime;
    private Integer maxUsers;
}
