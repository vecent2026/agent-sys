package com.trae.user.modules.view.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ViewConfigDTO {
    private String id;
    private String name;
    private List<FilterCondition> filters;
    private List<String> hiddenFields;
    private String filterLogic;
    private Boolean isDefault;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /**
     * 视图序号，从 1 开始，越小越靠前
     */
    private Integer orderNo;
    /**
     * 视图配置：列顺序/列宽等
     */
    private ViewConfig viewConfig;
}

@Data
class FilterCondition {
    private String id;
    private String field;
    private String operator;
    private Object value;
    private String type;
}
