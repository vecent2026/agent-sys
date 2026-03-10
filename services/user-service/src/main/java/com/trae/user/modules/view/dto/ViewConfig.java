package com.trae.user.modules.view.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 视图配置（将扩展项统一放在一个 JSON 中，便于后续演进）
 */
@Data
public class ViewConfig {
    /**
     * 列顺序（列 key 数组）
     */
    private List<String> columnOrder;

    /**
     * 列宽（预留）
     */
    private Map<String, Integer> columnWidths;
}

