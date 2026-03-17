package com.trae.admin.modules.platform.dto;

import lombok.Data;

@Data
public class PlatformTenantUpdateDto {
    private String tenantName;
    private Integer status;
    private String expireTime;
    private Integer maxUsers;
}
