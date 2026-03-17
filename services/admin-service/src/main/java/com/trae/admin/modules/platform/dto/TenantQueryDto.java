package com.trae.admin.modules.platform.dto;

import lombok.Data;

@Data
public class TenantQueryDto {
    private Integer page = 1;
    private Integer size = 10;
    private String tenantName;
    private String tenantCode;
    private Integer status;
}
