package com.trae.user.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;
import java.util.ArrayList;

/**
 * 统一筛选查询DTO - 统一筛选系统查询参数
 * 
 * 包含筛选条件、逻辑组合、分页和排序信息
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnifiedFilterQuery {
    
    /**
     * 筛选条件列表
     */
    @Valid
    private List<UnifiedFilterCondition> conditions;
    
    /**
     * 筛选逻辑 (AND | OR)
     */
    @Builder.Default
    private FilterLogic logic = FilterLogic.AND;
    
    /**
     * 分页信息
     */
    @Valid
    @Builder.Default
    private PaginationInfo pagination = new PaginationInfo();
    
    /**
     * 排序信息
     */
    @Valid
    private SortInfo sort;
    
    /**
     * 筛选逻辑枚举
     */
    public enum FilterLogic {
        /**
         * 所有条件都满足 (AND)
         */
        AND("AND", "所有条件"),
        
        /**
         * 任一条件满足 (OR)
         */
        OR("OR", "任一条件");
        
        private final String code;
        private final String label;
        
        FilterLogic(String code, String label) {
            this.code = code;
            this.label = label;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getLabel() {
            return label;
        }
        
        public static FilterLogic fromCode(String code) {
            for (FilterLogic logic : values()) {
                if (logic.code.equalsIgnoreCase(code)) {
                    return logic;
                }
            }
            return AND; // 默认为 AND
        }
    }
    
    /**
     * 分页信息内部类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaginationInfo {
        /**
         * 页码 (从1开始)
         */
        @Min(value = 1, message = "页码必须大于0")
        @Builder.Default
        private Integer page = 1;
        
        /**
         * 每页大小
         */
        @Min(value = 1, message = "每页大小必须大于0")
        @Max(value = 1000, message = "每页大小不能超过1000")
        @Builder.Default
        private Integer size = 10;
        
        /**
         * 获取偏移量 (用于 LIMIT offset, size)
         */
        public long getOffset() {
            return (long) (page - 1) * size;
        }
    }
    
    /**
     * 排序信息内部类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SortInfo {
        /**
         * 排序字段
         */
        @NotNull(message = "排序字段不能为空")
        private String field;
        
        /**
         * 排序方向
         */
        @Builder.Default
        private SortDirection direction = SortDirection.DESC;
        
        /**
         * 多字段排序 (可选)
         */
        private List<SortField> multiSort;
        
        /**
         * 排序方向枚举
         */
        public enum SortDirection {
            /**
             * 升序
             */
            ASC("ASC", "升序"),
            
            /**
             * 降序
             */
            DESC("DESC", "降序");
            
            private final String code;
            private final String label;
            
            SortDirection(String code, String label) {
                this.code = code;
                this.label = label;
            }
            
            public String getCode() {
                return code;
            }
            
            public String getLabel() {
                return label;
            }
        }
        
        /**
         * 多字段排序项
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SortField {
            private String field;
            private SortDirection direction;
        }
    }
    
    /**
     * 创建简单查询 (单个筛选条件)
     */
    public static UnifiedFilterQuery simple(UnifiedFilterCondition condition) {
        List<UnifiedFilterCondition> conditions = new ArrayList<>();
        conditions.add(condition);
        
        return UnifiedFilterQuery.builder()
                .conditions(conditions)
                .logic(FilterLogic.AND)
                .build();
    }
    
    /**
     * 创建多条件AND查询
     */
    public static UnifiedFilterQuery and(List<UnifiedFilterCondition> conditions) {
        return UnifiedFilterQuery.builder()
                .conditions(conditions)
                .logic(FilterLogic.AND)
                .build();
    }
    
    /**
     * 创建多条件OR查询
     */
    public static UnifiedFilterQuery or(List<UnifiedFilterCondition> conditions) {
        return UnifiedFilterQuery.builder()
                .conditions(conditions)
                .logic(FilterLogic.OR)
                .build();
    }
    
    /**
     * 添加筛选条件
     */
    public UnifiedFilterQuery addCondition(UnifiedFilterCondition condition) {
        if (this.conditions == null) {
            this.conditions = new ArrayList<>();
        }
        this.conditions.add(condition);
        return this;
    }
    
    /**
     * 设置分页参数
     */
    public UnifiedFilterQuery withPagination(int page, int size) {
        this.pagination = PaginationInfo.builder()
                .page(page)
                .size(size)
                .build();
        return this;
    }
    
    /**
     * 设置排序参数
     */
    public UnifiedFilterQuery withSort(String field, SortInfo.SortDirection direction) {
        this.sort = SortInfo.builder()
                .field(field)
                .direction(direction)
                .build();
        return this;
    }
    
    /**
     * 验证查询是否有效
     */
    public boolean isValid() {
        // 检查基本参数
        if (logic == null) {
            return false;
        }
        
        // 验证筛选条件
        if (conditions != null) {
            for (UnifiedFilterCondition condition : conditions) {
                if (condition == null || !condition.isValid()) {
                    return false;
                }
            }
        }
        
        // 验证分页参数
        if (pagination != null) {
            if (pagination.getPage() < 1 || pagination.getSize() < 1 || pagination.getSize() > 1000) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 获取有效的筛选条件数量
     */
    public int getValidConditionCount() {
        if (conditions == null) {
            return 0;
        }
        return (int) conditions.stream()
                .filter(c -> c != null && c.isValid())
                .count();
    }
    
    /**
     * 获取查询摘要 (用于日志记录)
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("UnifiedFilterQuery{");
        summary.append("conditions=").append(getValidConditionCount());
        summary.append(", logic=").append(logic.getCode());
        if (pagination != null) {
            summary.append(", page=").append(pagination.getPage());
            summary.append(", size=").append(pagination.getSize());
        }
        if (sort != null) {
            summary.append(", sort=").append(sort.getField()).append(" ").append(sort.getDirection().getCode());
        }
        summary.append("}");
        return summary.toString();
    }
    
    
}