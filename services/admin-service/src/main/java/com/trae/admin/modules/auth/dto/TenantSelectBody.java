package com.trae.admin.modules.auth.dto;

import lombok.Data;

/**
 * 租户选择请求（preToken + tenantId）
 */
@Data
public class TenantSelectBody {
    private String preToken;
    private Long tenantId;
}
