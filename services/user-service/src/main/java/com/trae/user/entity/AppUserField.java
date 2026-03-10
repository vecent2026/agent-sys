package com.trae.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "user_field", autoResultMap = true)
public class AppUserField {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fieldName;
    private String fieldKey;
    private String fieldType;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object config;
    
    private Integer isRequired;
    private Integer isDefault;
    private Integer status;
    private Integer sort;
    
    @TableLogic
    private Integer isDeleted;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
