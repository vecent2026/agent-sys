package com.trae.admin.modules.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("platform_role_permission")
public class PlatformRolePermission implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long roleId;
    private Long permissionId;
}
