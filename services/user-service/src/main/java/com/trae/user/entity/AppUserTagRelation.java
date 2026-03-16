package com.trae.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tenant_user_tag")
public class AppUserTagRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long tagId;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
