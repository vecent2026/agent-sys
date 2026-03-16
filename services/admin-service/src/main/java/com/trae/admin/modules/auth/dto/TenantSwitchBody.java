package com.trae.admin.modules.auth.dto;

import lombok.Data;

/**
 * 租户切换请求（新 tenantId）
 */
@Data
public class TenantSwitchBody {
    private Long tenantId;
}
