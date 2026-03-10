package com.trae.user.modules.view.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateViewDTO {
    private String name;
    private List<FilterCondition> filters;
    private List<String> hiddenFields;
    private String filterLogic;
    /**
     * 序号（可选），如果前端已计算好顺序，可以带上；否则后端自动分配
     */
    private Integer orderNo;
    /**
     * 视图配置：列顺序/列宽等
     */
    private ViewConfig viewConfig;
}
