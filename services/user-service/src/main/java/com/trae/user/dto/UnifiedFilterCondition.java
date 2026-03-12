package com.trae.user.dto;

import com.trae.user.enums.FieldType;
import com.trae.user.enums.FilterOperator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 统一筛选条件DTO - 统一筛选系统核心数据结构
 * 
 * 支持所有字段类型的统一筛选条件定义
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnifiedFilterCondition {
    
    /**
     * 字段键名 (如: nickname, mobile, tags, custom_field_key)
     */
    @NotBlank(message = "字段键名不能为空")
    private String fieldKey;
    
    /**
     * 字段类型
     */
    @NotNull(message = "字段类型不能为空")
    private FieldType fieldType;
    
    /**
     * 筛选操作符
     */
    @NotNull(message = "操作符不能为空")
    private FilterOperator operator;
    
    /**
     * 筛选值 (类型根据字段类型确定)
     * - String: 字符串值
     * - Number: 数字值
     * - Date: 日期字符串 (ISO 8601格式)
     * - List: 多值数组
     * - Map: 复杂对象 (如标签级联值)
     */
    private Object value;
    
    /**
     * 元数据 (可选)
     * 用于存储字段相关的配置信息，如枚举选项、字段配置等
     */
    private Map<String, Object> metadata;
    
    /**
     * 创建内置字符串字段筛选条件
     */
    public static UnifiedFilterCondition builtin(String fieldKey, FilterOperator operator, String value) {
        return UnifiedFilterCondition.builder()
                .fieldKey(fieldKey)
                .fieldType(FieldType.BUILTIN_STRING)
                .operator(operator)
                .value(value)
                .build();
    }
    
    /**
     * 创建内置枚举字段筛选条件
     */
    public static UnifiedFilterCondition builtinEnum(String fieldKey, FilterOperator operator, Object value) {
        return UnifiedFilterCondition.builder()
                .fieldKey(fieldKey)
                .fieldType(FieldType.BUILTIN_ENUM)
                .operator(operator)
                .value(value)
                .build();
    }
    
    /**
     * 创建内置日期字段筛选条件
     */
    public static UnifiedFilterCondition builtinDate(String fieldKey, FilterOperator operator, String dateValue) {
        return UnifiedFilterCondition.builder()
                .fieldKey(fieldKey)
                .fieldType(FieldType.BUILTIN_DATE)
                .operator(operator)
                .value(dateValue)
                .build();
    }
    
    /**
     * 创建动态字段筛选条件
     */
    public static UnifiedFilterCondition custom(String fieldKey, FieldType customType, FilterOperator operator, Object value) {
        if (!customType.isCustom()) {
            throw new IllegalArgumentException("Field type must be custom type: " + customType);
        }
        return UnifiedFilterCondition.builder()
                .fieldKey(fieldKey)
                .fieldType(customType)
                .operator(operator)
                .value(value)
                .build();
    }
    
    /**
     * 创建标签筛选条件
     */
    public static UnifiedFilterCondition tags(FilterOperator operator, TagCascadeValue tagValue) {
        return UnifiedFilterCondition.builder()
                .fieldKey("tags")
                .fieldType(FieldType.TAG_CASCADE)
                .operator(operator)
                .value(tagValue)
                .build();
    }
    
    /**
     * 标签级联值内部类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TagCascadeValue {
        /**
         * 标签分类ID (可选)
         */
        private Long categoryId;
        
        /**
         * 标签ID列表
         */
        private java.util.List<Long> tagIds;
        
        /**
         * 验证标签值是否有效
         */
        public boolean isValid() {
            return tagIds != null && !tagIds.isEmpty();
        }
    }
    
    /**
     * 日期范围值内部类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DateRangeValue {
        /**
         * 开始日期
         */
        private String startDate;
        
        /**
         * 结束日期
         */
        private String endDate;
        
        /**
         * 验证日期范围是否有效
         */
        public boolean isValid() {
            return startDate != null && endDate != null;
        }
    }
    
    /**
     * 验证筛选条件是否完整
     */
    public boolean isValid() {
        if (fieldKey == null || fieldKey.trim().isEmpty()) {
            return false;
        }
        if (fieldType == null || operator == null) {
            return false;
        }
        
        // 检查操作符是否需要值参数
        if (operator.requiresValue() && value == null) {
            return false;
        }
        
        // 特殊字段类型的值验证
        if (fieldType == FieldType.TAG_CASCADE && value instanceof TagCascadeValue) {
            return ((TagCascadeValue) value).isValid();
        }
        
        return true;
    }
    
    /**
     * 获取字符串形式的值（用于日志记录）
     */
    public String getValueAsString() {
        if (value == null) {
            return "null";
        }
        if (value instanceof TagCascadeValue) {
            TagCascadeValue tagValue = (TagCascadeValue) value;
            return String.format("TagCascade{categoryId=%s, tagIds=%s}", 
                    tagValue.getCategoryId(), tagValue.getTagIds());
        }
        if (value instanceof DateRangeValue) {
            DateRangeValue dateRange = (DateRangeValue) value;
            return String.format("DateRange{%s ~ %s}", 
                    dateRange.getStartDate(), dateRange.getEndDate());
        }
        return value.toString();
    }
}