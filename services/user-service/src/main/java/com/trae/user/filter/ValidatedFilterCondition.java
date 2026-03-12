package com.trae.user.filter;

import com.trae.user.dto.UnifiedFilterCondition;
import com.trae.user.enums.FieldType;
import com.trae.user.enums.FilterOperator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * 已验证的筛选条件 - 经过验证和预处理的筛选条件
 * 
 * 包含原始条件信息和验证元数据
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidatedFilterCondition {
    
    /**
     * 原始筛选条件
     */
    private UnifiedFilterCondition originalCondition;
    
    /**
     * 字段键名
     */
    private String fieldKey;
    
    /**
     * 字段类型
     */
    private FieldType fieldType;
    
    /**
     * 筛选操作符
     */
    private FilterOperator operator;
    
    /**
     * 已验证和标准化的值
     */
    private Object normalizedValue;
    
    /**
     * 验证结果
     */
    private FilterValidationResult validationResult;
    
    /**
     * 字段元数据 (如数据库字段ID、字段配置等)
     */
    private Map<String, Object> fieldMetadata;
    
    /**
     * 性能评估结果
     */
    private PerformanceAssessment performance;
    
    /**
     * 从原始条件创建已验证条件
     */
    public static ValidatedFilterCondition from(UnifiedFilterCondition condition, 
                                               FilterValidationResult validationResult) {
        return ValidatedFilterCondition.builder()
                .originalCondition(condition)
                .fieldKey(condition.getFieldKey())
                .fieldType(condition.getFieldType())
                .operator(condition.getOperator())
                .normalizedValue(condition.getValue())
                .validationResult(validationResult)
                .build();
    }
    
    /**
     * 检查条件是否有效
     */
    public boolean isValid() {
        return validationResult != null && validationResult.isValid();
    }
    
    /**
     * 获取验证错误信息
     */
    public String getValidationError() {
        return validationResult != null ? validationResult.getErrorMessage() : null;
    }
    
    /**
     * 获取字段元数据中的特定值
     */
    @SuppressWarnings("unchecked")
    public <T> T getFieldMetadata(String key, Class<T> type) {
        if (fieldMetadata == null) {
            return null;
        }
        Object value = fieldMetadata.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * 设置字段元数据
     */
    public ValidatedFilterCondition withFieldMetadata(String key, Object value) {
        if (this.fieldMetadata == null) {
            this.fieldMetadata = new java.util.HashMap<>();
        }
        this.fieldMetadata.put(key, value);
        return this;
    }
    
    /**
     * 性能评估内部类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PerformanceAssessment {
        /**
         * 性能评分 (1-10，10为最佳)
         */
        private int score;
        
        /**
         * 预估影响的记录数
         */
        private Long estimatedRecordCount;
        
        /**
         * 查询复杂度级别
         */
        private ComplexityLevel complexity;
        
        /**
         * 性能建议
         */
        private String suggestion;
        
        /**
         * 复杂度级别枚举
         */
        public enum ComplexityLevel {
            LOW("低", "简单的主表查询"),
            MEDIUM("中", "涉及子查询或关联"),
            HIGH("高", "复杂的多表关联查询"),
            VERY_HIGH("极高", "可能导致性能问题的查询");
            
            private final String label;
            private final String description;
            
            ComplexityLevel(String label, String description) {
                this.label = label;
                this.description = description;
            }
            
            public String getLabel() { return label; }
            public String getDescription() { return description; }
        }
    }
}