package com.trae.user.modules.view.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_views")
public class UserView {
    @TableId
    private String id;
    private String name;
    private Long userId;
    /** 所属租户ID（由 TenantLineInnerInterceptor 自动过滤） */
    private Long tenantId;
    private String filters; // JSON 字符串
    private String hiddenFields; // JSON 字符串
    private String viewConfig; // JSON 字符串
    private String filterLogic;
    private Boolean isDefault;
    /**
     * 视图序号，从 1 开始，越小越靠前
     */
    private Integer orderNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
