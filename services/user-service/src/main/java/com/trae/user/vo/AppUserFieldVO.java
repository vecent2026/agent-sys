package com.trae.user.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AppUserFieldVO {
    private Long id;
    private String fieldName;
    private String fieldKey;
    private String fieldType;
    private Object config;
    private Integer isRequired;
    private Integer isDefault;
    private Integer status;
    private Integer sort;
    private LocalDateTime createTime;
}
