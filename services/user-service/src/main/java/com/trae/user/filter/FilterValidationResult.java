package com.trae.user.filter;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.ArrayList;

/**
 * 筛选验证结果 - 筛选条件验证的结果信息
 * 
 * 包含验证状态、错误信息、警告和建议
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterValidationResult {
    
    /**
     * 验证是否通过
     */
    private boolean valid;
    
    /**
     * 错误信息 (验证失败时)
     */
    private String errorMessage;
    
    /**
     * 错误代码 (用于国际化)
     */
    private String errorCode;
    
    /**
     * 警告信息列表 (验证通过但有注意事项)
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    
    /**
     * 建议信息列表 (优化建议)
     */
    @Builder.Default
    private List<String> suggestions = new ArrayList<>();
    
    /**
     * 标准化后的值 (如果需要转换)
     */
    private Object normalizedValue;
    
    /**
     * 创建成功的验证结果
     */
    public static FilterValidationResult success() {
        return FilterValidationResult.builder()
                .valid(true)
                .build();
    }
    
    /**
     * 创建成功的验证结果 (带标准化值)
     */
    public static FilterValidationResult success(Object normalizedValue) {
        return FilterValidationResult.builder()
                .valid(true)
                .normalizedValue(normalizedValue)
                .build();
    }
    
    /**
     * 创建失败的验证结果
     */
    public static FilterValidationResult failure(String errorMessage) {
        return FilterValidationResult.builder()
                .valid(false)
                .errorMessage(errorMessage)
                .build();
    }
    
    /**
     * 创建失败的验证结果 (带错误代码)
     */
    public static FilterValidationResult failure(String errorMessage, String errorCode) {
        return FilterValidationResult.builder()
                .valid(false)
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .build();
    }
    
    /**
     * 添加警告信息
     */
    public FilterValidationResult addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new ArrayList<>();
        }
        this.warnings.add(warning);
        return this;
    }
    
    /**
     * 添加建议信息
     */
    public FilterValidationResult addSuggestion(String suggestion) {
        if (this.suggestions == null) {
            this.suggestions = new ArrayList<>();
        }
        this.suggestions.add(suggestion);
        return this;
    }
    
    /**
     * 检查是否有警告
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
    
    /**
     * 检查是否有建议
     */
    public boolean hasSuggestions() {
        return suggestions != null && !suggestions.isEmpty();
    }
    
    /**
     * 获取完整的消息摘要
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        
        if (!valid) {
            summary.append("验证失败: ").append(errorMessage);
            if (errorCode != null) {
                summary.append(" (").append(errorCode).append(")");
            }
        } else {
            summary.append("验证通过");
        }
        
        if (hasWarnings()) {
            summary.append("; 警告: ").append(String.join(", ", warnings));
        }
        
        if (hasSuggestions()) {
            summary.append("; 建议: ").append(String.join(", ", suggestions));
        }
        
        return summary.toString();
    }
    
    /**
     * 常用错误代码
     */
    public static class ErrorCodes {
        public static final String FIELD_NOT_FOUND = "FIELD_NOT_FOUND";
        public static final String OPERATOR_NOT_SUPPORTED = "OPERATOR_NOT_SUPPORTED";
        public static final String VALUE_INVALID = "VALUE_INVALID";
        public static final String VALUE_REQUIRED = "VALUE_REQUIRED";
        public static final String VALUE_TYPE_MISMATCH = "VALUE_TYPE_MISMATCH";
        public static final String VALUE_OUT_OF_RANGE = "VALUE_OUT_OF_RANGE";
        public static final String COMPLEX_QUERY_WARNING = "COMPLEX_QUERY_WARNING";
    }
    
    /**
     * 常用验证方法
     */
    public static class Validations {
        
        /**
         * 验证值不为空
         */
        public static FilterValidationResult requireValue(Object value, String fieldName) {
            if (value == null) {
                return failure(String.format("字段 %s 的值不能为空", fieldName), 
                             ErrorCodes.VALUE_REQUIRED);
            }
            return success();
        }
        
        /**
         * 验证字符串值
         */
        public static FilterValidationResult validateString(Object value, String fieldName) {
            if (value == null) {
                return success(); // 空值在其他地方处理
            }
            if (!(value instanceof String)) {
                return failure(String.format("字段 %s 的值必须是字符串类型", fieldName), 
                             ErrorCodes.VALUE_TYPE_MISMATCH);
            }
            String str = (String) value;
            if (str.length() > 1000) {
                return failure(String.format("字段 %s 的值长度不能超过1000字符", fieldName), 
                             ErrorCodes.VALUE_OUT_OF_RANGE)
                      .addWarning("长字符串可能影响查询性能");
            }
            return success();
        }
        
        /**
         * 验证数字值
         */
        public static FilterValidationResult validateNumber(Object value, String fieldName) {
            if (value == null) {
                return success();
            }
            if (!(value instanceof Number)) {
                // 尝试转换字符串为数字
                if (value instanceof String) {
                    try {
                        Double.parseDouble((String) value);
                        return success(Double.parseDouble((String) value));
                    } catch (NumberFormatException e) {
                        return failure(String.format("字段 %s 的值 '%s' 不是有效数字", fieldName, value), 
                                     ErrorCodes.VALUE_TYPE_MISMATCH);
                    }
                } else {
                    return failure(String.format("字段 %s 的值必须是数字类型", fieldName), 
                                 ErrorCodes.VALUE_TYPE_MISMATCH);
                }
            }
            return success();
        }
    }
}