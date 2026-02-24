package com.trae.admin.modules.user.dto;

import lombok.Data;

/**
 * 用户查询DTO
 */
@Data
public class UserQueryDto {
    
    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer size = 10;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 状态
     */
    private Integer status;
}
