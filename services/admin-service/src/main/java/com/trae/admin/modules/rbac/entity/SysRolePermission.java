package com.trae.admin.modules.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色权限关联实体类
 */
@Data
@TableName("tenant_role_permission")
public class SysRolePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID（由 MyBatis-Plus 租户插件自动注入） */
    private Long tenantId;

    private Long roleId;

    private Long permissionId;
}
