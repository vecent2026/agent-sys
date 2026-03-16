package com.trae.admin.modules.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 租户角色-权限关联
 */
@Data
@TableName("tenant_role_permission")
public class TenantRolePermission {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long roleId;
    private Long permissionId;
}
