package com.trae.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_field_value")
public class AppUserFieldValue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long fieldId;
    private String fieldValue;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
