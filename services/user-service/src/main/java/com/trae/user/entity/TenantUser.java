package com.trae.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-租户成员关系表
 */
@Data
@TableName("tenant_user")
public class TenantUser {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long tenantId;

    /** 是否租户管理员：0=否 1=是 */
    private Integer isAdmin;

    private LocalDateTime joinTime;
}
