package com.trae.admin.modules.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_role")
public class PlatformRole {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String roleName;
    private String roleKey;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
