package com.trae.admin.modules.platform.dto;

import lombok.Data;

@Data
public class PlatformTenantCreateDto {
    private String tenantCode;
    private String tenantName;
    private Integer status = 1;
    private String expireTime;
    private Integer maxUsers;
    private AdminUserDto adminUser;

    @Data
    public static class AdminUserDto {
        private String mobile;
        private String nickname;
    }
}
