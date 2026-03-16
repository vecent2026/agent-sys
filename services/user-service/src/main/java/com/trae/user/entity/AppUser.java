package com.trae.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("app_user")
public class AppUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String nickname;
    private String avatar;
    private String mobile;
    /** 密码（BCrypt 加密存储，用于租户端登录） */
    private String password;
    private String email;
    private Integer gender;
    private LocalDate birthday;
    private String registerSource;
    private Integer status;
    private LocalDateTime registerTime;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    
    @TableLogic
    private Integer isDeleted;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
