package com.trae.user.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AppUserTagVO {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String color;
    private String description;
    private Integer status;
    private Integer userCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
