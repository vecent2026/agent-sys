package com.trae.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tenant_tag_category")
public class AppUserTagCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String color;
    private String description;
    private Integer sort;
    
    @TableLogic
    private Integer isDeleted;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
