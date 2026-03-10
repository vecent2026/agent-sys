package com.trae.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppUserFieldDTO {
    private Long id;

    @NotBlank(message = "字段名称不能为空")
    @Size(max = 50, message = "字段名称不能超过50字符")
    private String fieldName;

    @NotBlank(message = "字段标识不能为空")
    @Size(max = 50, message = "字段标识不能超过50字符")
    private String fieldKey;

    @NotBlank(message = "字段类型不能为空")
    private String fieldType;

    private Object config;

    private Integer isRequired;

    private Integer status;

    private Integer sort;

    private java.util.List<Long> fieldIds;
}
