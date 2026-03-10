package com.trae.user.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AppUserQueryDTO {
    private Integer page = 1;
    private Integer size = 10;
    private String keyword;
    private String registerSource;
    private Integer status;
    private String tagIds;
    
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registerStartTime;
    
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registerEndTime;
    
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginStartTime;
    
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginEndTime;

    private String filters; // JSON string of FilterCondition[]
    private String filterLogic; // AND | OR
}
