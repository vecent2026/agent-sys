package com.trae.admin.modules.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户元数据实体
 */
@Data
@TableName("platform_tenant")
public class PlatformTenant {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String tenantCode;
    private String tenantName;

    /** 描述 */
    private String description;

    /** 联系人姓名 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 状态: 1=启用 0=禁用 */
    private Integer status;

    /** NULL=永不过期 */
    private LocalDateTime expireTime;

    private Integer maxUsers;

    /** 禁用/配置变更时+1，即时失效当前所有 JWT */
    private Integer dataVersion;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
