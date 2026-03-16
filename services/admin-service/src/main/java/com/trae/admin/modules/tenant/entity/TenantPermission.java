package com.trae.admin.modules.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台对租户的权限授权
 */
@Data
@TableName("tenant_permission")
public class TenantPermission {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long permissionId;

    private LocalDateTime createTime;
}
