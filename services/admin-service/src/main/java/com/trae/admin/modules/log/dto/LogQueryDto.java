package com.trae.admin.modules.log.dto;

import lombok.Data;

/**
 * 日志查询DTO
 */
@Data
public class LogQueryDto {
    
    private Integer page = 1;
    private Integer size = 10;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String status;
    private String startTime;
    private String endTime;
    private Long tenantId;
    private Boolean isPlatform;
}
