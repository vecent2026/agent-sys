package com.trae.admin.modules.platform.dto;

import lombok.Data;

@Data
public class PlatformTenantQueryDto {
    private Integer page = 1;
    private Integer size = 20;
    private String keyword;
    private Integer status;
}
