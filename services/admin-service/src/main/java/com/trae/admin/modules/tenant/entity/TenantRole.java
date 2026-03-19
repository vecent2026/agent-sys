package com.trae.admin.modules.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.trae.admin.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tenant_role")
public class TenantRole extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String roleName;
    private String roleKey;
    private String description;
    private Integer isBuiltin;
    private Integer isSuper;
}
