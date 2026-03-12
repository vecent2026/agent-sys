package com.trae.user.filter.handlers;

import com.trae.user.enums.FieldType;
import com.trae.user.enums.FilterOperator;
import com.trae.user.filter.FilterValidationResult;
import com.trae.user.filter.ValidatedFilterCondition;
import com.trae.user.service.AppUserFieldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 自定义单选字段处理器 - 处理动态单选字段的筛选
 * 
 * 支持的字段类型：CUSTOM_RADIO
 * 单选字段值为单一选项，支持精确匹配和多选匹配
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Slf4j
@Component
public class CustomRadioFieldHandler extends CustomFieldHandlerBase {

    // 支持的操作符
    private static final List<FilterOperator> SUPPORTED_OPERATORS = Arrays.asList(
            FilterOperator.EQUALS,
            FilterOperator.NOT_EQUALS,
            FilterOperator.IS_EMPTY,
            FilterOperator.IS_NOT_EMPTY,
            FilterOperator.IN,
            FilterOperator.NOT_IN
    );

    public CustomRadioFieldHandler(AppUserFieldService fieldService) {
        super(fieldService);
    }

    @Override
    public FieldType getSupportedFieldType() {
        return FieldType.CUSTOM_RADIO;
    }

    @Override
    public List<FilterOperator> getSupportedOperators() {
        return SUPPORTED_OPERATORS;
    }

    @Override
    protected FilterValidationResult validateFieldSpecificValue(FilterOperator operator, Object value) {
        FilterValidationResult result = FilterValidationResult.success();
        
        if (value == null) {
            return result;
        }

        if (operator == FilterOperator.IN || operator == FilterOperator.NOT_IN) {
            // 多值验证
            java.util.Collection<String> values = parseMultipleValues(value);
            if (values.isEmpty()) {
                return FilterValidationResult.failure(
                        "请选择至少一个选项",
                        FilterValidationResult.ErrorCodes.VALUE_REQUIRED
                );
            }
            
            if (values.size() > 20) {
                result.addWarning("选择的选项过多可能影响查询性能");
                result.addSuggestion("建议减少选择的选项数量");
            }
        } else {
            // 单值验证
            String stringValue = normalizeValue(value);
            if (stringValue.isEmpty()) {
                return FilterValidationResult.failure(
                        "请选择一个选项",
                        FilterValidationResult.ErrorCodes.VALUE_REQUIRED
                );
            }
        }

        return result;
    }

    @Override
    public int estimatePerformance(ValidatedFilterCondition condition) {
        FilterOperator operator = condition.getOperator();
        Object value = condition.getNormalizedValue();
        switch (operator) {
            case EQUALS:
            case NOT_EQUALS:
                return 8; // 精确匹配，性能优秀
                
            case IN:
            case NOT_IN:
                if (value != null) {
                    java.util.Collection<String> values = parseMultipleValues(value);
                    if (values.size() > 10) return 5; // 大量值，性能中等
                    if (values.size() > 3) return 7; // 中等数量，性能良好
                }
                return 8; // 少量值，性能优秀
                
            case IS_EMPTY:
            case IS_NOT_EMPTY:
                return 7; // EXISTS查询，性能良好
                
            default:
                return 6; // 默认中等性能
        }
    }

    @Override
    protected String normalizeValue(Object value) {
        if (value == null) {
            return "";
        }
        
        String stringValue = value.toString().trim();
        
        // 尝试解析为选项值或选项标签
        return normalizeOptionValue(stringValue);
    }

    /**
     * 规范化选项值 - 支持选项标签到选项值的转换
     * TODO: 实际实现中应该查询字段配置来进行标签到值的转换
     */
    private String normalizeOptionValue(String input) {
        // 基础实现：直接返回输入值
        // 在实际应用中，这里应该：
        // 1. 查询字段配置获取选项列表
        // 2. 如果输入是选项标签，转换为选项值
        // 3. 如果输入已经是选项值，直接返回
        return input;
    }

    // ==================== 批量优化支持 ====================

    @Override
    public String getBatchQueryFragment(Long fieldId, FilterOperator operator, Object value) {
        try {
            switch (operator) {
                case EQUALS:
                    String normalizedValue = normalizeValue(value);
                    return String.format("fv%d.field_value = '%s'", fieldId, escapeSqlValue(normalizedValue));
                    
                case NOT_EQUALS:
                    String notEqualValue = normalizeValue(value);
                    return String.format("(fv%d.field_value IS NULL OR fv%d.field_value <> '%s')", 
                            fieldId, fieldId, escapeSqlValue(notEqualValue));
                    
                case IN:
                    java.util.Collection<String> inValues = parseMultipleValues(value);
                    if (!inValues.isEmpty()) {
                        String valuesStr = inValues.stream()
                                .map(this::normalizeValue)
                                .map(this::escapeSqlValue)
                                .map(v -> "'" + v + "'")
                                .collect(java.util.stream.Collectors.joining(","));
                        return String.format("fv%d.field_value IN (%s)", fieldId, valuesStr);
                    }
                    break;
                    
                case NOT_IN:
                    java.util.Collection<String> notInValues = parseMultipleValues(value);
                    if (!notInValues.isEmpty()) {
                        String valuesStr = notInValues.stream()
                                .map(this::normalizeValue)
                                .map(this::escapeSqlValue)
                                .map(v -> "'" + v + "'")
                                .collect(java.util.stream.Collectors.joining(","));
                        return String.format("(fv%d.field_value IS NULL OR fv%d.field_value NOT IN (%s))", 
                                fieldId, fieldId, valuesStr);
                    }
                    break;
                    
                default:
                    return null;
            }
        } catch (Exception e) {
            log.warn("Failed to generate batch query fragment: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取字段选项列表（用于验证和转换）
     * TODO: 实际实现中应该从字段配置中读取选项列表
     */
    public List<OptionInfo> getFieldOptions(Long fieldId) {
        // 这里应该查询 app_user_field 表获取字段配置
        // 解析配置中的选项列表
        // 返回选项信息列表
        
        // 临时实现：返回空列表
        return java.util.Collections.emptyList();
    }

    /**
     * 验证选项值是否有效
     */
    public boolean isValidOption(Long fieldId, String optionValue) {
        if (optionValue == null || optionValue.trim().isEmpty()) {
            return false;
        }
        
        // TODO: 实际实现中应该检查选项值是否在字段配置的选项列表中
        // List<OptionInfo> options = getFieldOptions(fieldId);
        // return options.stream().anyMatch(opt -> opt.getValue().equals(optionValue));
        
        // 临时实现：认为所有非空值都有效
        return true;
    }

    // ==================== 内部类 ====================

    /**
     * 选项信息类
     */
    public static class OptionInfo {
        private final String label;
        private final String value;
        private final String color;

        public OptionInfo(String label, String value, String color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }

        public String getLabel() { return label; }
        public String getValue() { return value; }
        public String getColor() { return color; }

        @Override
        public String toString() {
            return String.format("Option{label='%s', value='%s', color='%s'}", label, value, color);
        }
    }
}