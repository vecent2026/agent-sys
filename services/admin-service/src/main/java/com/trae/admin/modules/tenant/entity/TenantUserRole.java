package com.trae.admin.modules.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户在租户内的角色关系（三元表）
 */
@Data
@TableName("tenant_user_role")
public class TenantUserRole {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** app_user.id（跨库引用，无外键） */
    private Long userId;

    private Long tenantId;

    /** tenant_role.id */
    private Long roleId;
}
