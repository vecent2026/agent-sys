package com.trae.admin.modules.user.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户VO
 */
@Data
public class UserVo {

    private Long id;
    private String username;
    private String nickname;
    private String mobile;
    private String email;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    
    /**
     * 角色名称列表
     */
    private List<String> roleNames;
    
    /**
     * 角色ID列表
     */
    private List<Long> roleIds;
}
