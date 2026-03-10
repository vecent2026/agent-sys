package com.trae.user.modules.view.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateViewDTO {
    private String name;
    private List<FilterCondition> filters;
    private List<String> hiddenFields;
    private String filterLogic;
    private Boolean isDefault;
    /**
     * 序号（可选），用于拖拽排序时更新
     */
    private Integer orderNo;
    /**
     * 视图配置：列顺序/列宽等
     */
    private ViewConfig viewConfig;
}
