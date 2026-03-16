package com.trae.user.filter.handlers;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.user.entity.AppUser;
import com.trae.user.enums.FieldType;
import com.trae.user.enums.FilterOperator;
import com.trae.user.filter.FilterValidationResult;
import com.trae.user.filter.ValidatedFilterCondition;
import com.trae.user.service.AppUserFieldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 自定义多选字段处理器 - 处理动态多选字段的筛选
 * 
 * 支持的字段类型：CUSTOM_CHECKBOX
 * 多选字段值为逗号分隔的多个选项，支持包含、全部包含、任一包含等复杂逻辑
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Slf4j
@Component
public class CustomCheckboxFieldHandler extends CustomFieldHandlerBase {

    // 支持的操作符
    private static final List<FilterOperator> SUPPORTED_OPERATORS = Arrays.asList(
            FilterOperator.CONTAINS,        // 包含任一选项
            FilterOperator.NOT_CONTAINS,    // 不包含任一选项
            FilterOperator.CONTAINS_ALL,    // 包含所有选项
            FilterOperator.IS_EMPTY,        // 没有选择任何选项
            FilterOperator.IS_NOT_EMPTY     // 有选择选项
    );

    public CustomCheckboxFieldHandler(AppUserFieldService fieldService) {
        super(fieldService);
    }

    @Override
    public FieldType getSupportedFieldType() {
        return FieldType.CUSTOM_CHECKBOX;
    }

    @Override
    public List<FilterOperator> getSupportedOperators() {
        return SUPPORTED_OPERATORS;
    }

    @Override
    protected FilterValidationResult validateFieldSpecificValue(FilterOperator operator, Object value) {
        // 空值检查操作符不需要值
        if (operator == FilterOperator.IS_EMPTY || operator == FilterOperator.IS_NOT_EMPTY) {
            return FilterValidationResult.success();
        }

        FilterValidationResult result = FilterValidationResult.success();
        
        if (value == null) {
            return FilterValidationResult.failure(
                    "操作符 " + operator.getLabel() + " 需要选择选项",
                    FilterValidationResult.ErrorCodes.VALUE_REQUIRED
            );
        }

        // 解析选项值
        Collection<String> options = parseCheckboxValues(value);
        if (options.isEmpty()) {
            return FilterValidationResult.failure(
                    "请选择至少一个选项",
                    FilterValidationResult.ErrorCodes.VALUE_REQUIRED
            );
        }

        if (options.size() > 20) {
            result.addWarning("选择的选项过多可能影响查询性能");
            result.addSuggestion("建议减少选择的选项数量");
        }

        // 性能建议
        if (operator == FilterOperator.CONTAINS_ALL && options.size() > 5) {
            result.addWarning("包含所有选项的查询复杂度较高");
            result.addSuggestion("考虑使用包含任一选项或减少选项数量");
        }

        return result;
    }

    @Override
    public int estimatePerformance(ValidatedFilterCondition condition) {
        FilterOperator operator = condition.getOperator();
        Object value = condition.getNormalizedValue();
        
        switch (operator) {
            case IS_EMPTY:
            case IS_NOT_EMPTY:
                return 7; // EXISTS查询，性能良好
                
            case CONTAINS:
            case NOT_CONTAINS:
                if (value != null) {
                    Collection<String> options = parseCheckboxValues(value);
                    if (options.size() > 5) return 4; // 大量选项，性能较差
                    if (options.size() > 2) return 6; // 中等数量，性能中等
                }
                return 7; // 少量选项，性能良好
                
            case CONTAINS_ALL:
                if (value != null) {
                    Collection<String> options = parseCheckboxValues(value);
                    // CONTAINS_ALL查询复杂度较高
                    if (options.size() > 3) return 3; // 大量选项，性能差
                    if (options.size() > 1) return 5; // 中等数量，性能中等
                }
                return 6; // 单选项，性能良好
                
            default:
                return 5; // 默认中等性能
        }
    }

    @Override
    protected void applySpecialOperator(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, 
                                      FilterOperator operator, Object value) {
        try {
            switch (operator) {
                case CONTAINS:
                    applyContainsAnyFilter(wrapper, fieldId, value);
                    break;
                    
                case NOT_CONTAINS:
                    applyNotContainsAnyFilter(wrapper, fieldId, value);
                    break;
                    
                case CONTAINS_ALL:
                    applyContainsAllFilter(wrapper, fieldId, value);
                    break;
                    
                default:
                    super.applySpecialOperator(wrapper, fieldId, operator, value);
            }
        } catch (Exception e) {
            log.error("Failed to apply checkbox filter: fieldId={}, operator={}, value={}", 
                    fieldId, operator, value, e);
        }
    }

    // ==================== 专用筛选方法 ====================

    /**
     * 包含任一选项筛选
     */
    private void applyContainsAnyFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, Object value) {
        Collection<String> options = parseCheckboxValues(value);
        if (options.isEmpty()) {
            log.warn("CONTAINS filter with empty options for field {}", fieldId);
            return;
        }
        
        // 构建OR条件：任一选项匹配即可
        StringBuilder conditionBuilder = new StringBuilder();
        conditionBuilder.append("SELECT 1 FROM tenant_field_value fv ")
                       .append("WHERE fv.user_id = app_user.id AND fv.field_id = ").append(fieldId)
                       .append(" AND (");
        
        List<String> conditions = options.stream()
                .map(option -> String.format(
                    "FIND_IN_SET('%s', fv.field_value) > 0", 
                    escapeSqlValue(option)
                ))
                .collect(Collectors.toList());
        
        conditionBuilder.append(String.join(" OR ", conditions));
        conditionBuilder.append(")");
        
        wrapper.exists(conditionBuilder.toString());
        log.debug("Applied CONTAINS_ANY filter for field {} with {} options", fieldId, options.size());
    }

    /**
     * 不包含任一选项筛选
     */
    private void applyNotContainsAnyFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, Object value) {
        Collection<String> options = parseCheckboxValues(value);
        if (options.isEmpty()) {
            log.warn("NOT_CONTAINS filter with empty options for field {}", fieldId);
            return;
        }
        
        // 构建AND条件：所有选项都不匹配
        StringBuilder conditionBuilder = new StringBuilder();
        conditionBuilder.append("SELECT 1 FROM tenant_field_value fv ")
                       .append("WHERE fv.user_id = app_user.id AND fv.field_id = ").append(fieldId)
                       .append(" AND (");
        
        List<String> conditions = options.stream()
                .map(option -> String.format(
                    "FIND_IN_SET('%s', fv.field_value) > 0", 
                    escapeSqlValue(option)
                ))
                .collect(Collectors.toList());
        
        conditionBuilder.append(String.join(" OR ", conditions));
        conditionBuilder.append(")");
        
        wrapper.notExists(conditionBuilder.toString());
        log.debug("Applied NOT_CONTAINS_ANY filter for field {} with {} options", fieldId, options.size());
    }

    /**
     * 包含所有选项筛选
     */
    private void applyContainsAllFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, Object value) {
        Collection<String> options = parseCheckboxValues(value);
        if (options.isEmpty()) {
            log.warn("CONTAINS_ALL filter with empty options for field {}", fieldId);
            return;
        }
        
        // 构建AND条件：所有选项都必须匹配
        StringBuilder conditionBuilder = new StringBuilder();
        conditionBuilder.append("SELECT 1 FROM tenant_field_value fv ")
                       .append("WHERE fv.user_id = app_user.id AND fv.field_id = ").append(fieldId)
                       .append(" AND ");
        
        List<String> conditions = options.stream()
                .map(option -> String.format(
                    "FIND_IN_SET('%s', fv.field_value) > 0", 
                    escapeSqlValue(option)
                ))
                .collect(Collectors.toList());
        
        conditionBuilder.append(String.join(" AND ", conditions));
        
        wrapper.exists(conditionBuilder.toString());
        log.debug("Applied CONTAINS_ALL filter for field {} with {} options", fieldId, options.size());
    }

    // ==================== 多选值解析方法 ====================

    /**
     * 解析多选框值 - 支持多种格式
     */
    private Collection<String> parseCheckboxValues(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        
        Set<String> options = new LinkedHashSet<>();
        
        if (value instanceof Collection) {
            // 值为集合类型
            Collection<?> collection = (Collection<?>) value;
            for (Object item : collection) {
                if (item != null) {
                    String option = normalizeCheckboxOption(item.toString());
                    if (!option.isEmpty()) {
                        options.add(option);
                    }
                }
            }
        } else if (value instanceof String) {
            // 值为字符串类型，按逗号分隔
            String str = (String) value;
            String[] parts = str.split("[,;，；]");
            for (String part : parts) {
                String option = normalizeCheckboxOption(part);
                if (!option.isEmpty()) {
                    options.add(option);
                }
            }
        } else {
            // 单个值
            String option = normalizeCheckboxOption(value.toString());
            if (!option.isEmpty()) {
                options.add(option);
            }
        }
        
        return options;
    }

    /**
     * 规范化多选框选项值
     */
    private String normalizeCheckboxOption(String option) {
        if (option == null) {
            return "";
        }
        
        String normalized = option.trim();
        
        // TODO: 实际实现中应该查询字段配置来进行标签到值的转换
        // 这里应该：
        // 1. 查询字段配置获取选项列表
        // 2. 如果输入是选项标签，转换为选项值
        // 3. 如果输入已经是选项值，直接返回
        
        return normalized;
    }

    // ==================== 批量优化支持 ====================

    @Override
    public String getBatchQueryFragment(Long fieldId, FilterOperator operator, Object value) {
        try {
            Collection<String> options = parseCheckboxValues(value);
            if (options.isEmpty()) {
                return null;
            }
            
            switch (operator) {
                case CONTAINS:
                    // 包含任一选项
                    List<String> anyConditions = options.stream()
                            .map(option -> String.format(
                                "FIND_IN_SET('%s', fv%d.field_value) > 0", 
                                escapeSqlValue(option), fieldId
                            ))
                            .collect(Collectors.toList());
                    return "(" + String.join(" OR ", anyConditions) + ")";
                    
                case NOT_CONTAINS:
                    // 不包含任一选项
                    List<String> notAnyConditions = options.stream()
                            .map(option -> String.format(
                                "FIND_IN_SET('%s', fv%d.field_value) = 0", 
                                escapeSqlValue(option), fieldId
                            ))
                            .collect(Collectors.toList());
                    return "(" + String.join(" AND ", notAnyConditions) + ")";
                    
                case CONTAINS_ALL:
                    // 包含所有选项
                    List<String> allConditions = options.stream()
                            .map(option -> String.format(
                                "FIND_IN_SET('%s', fv%d.field_value) > 0", 
                                escapeSqlValue(option), fieldId
                            ))
                            .collect(Collectors.toList());
                    return "(" + String.join(" AND ", allConditions) + ")";
                    
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
     */
    public List<CheckboxOptionInfo> getFieldOptions(Long fieldId) {
        // TODO: 实际实现中应该从字段配置中读取选项列表
        return Collections.emptyList();
    }

    /**
     * 验证选项值是否有效
     */
    public boolean areValidOptions(Long fieldId, Collection<String> optionValues) {
        if (optionValues == null || optionValues.isEmpty()) {
            return false;
        }
        
        // TODO: 实际实现中应该检查选项值是否在字段配置的选项列表中
        // List<CheckboxOptionInfo> options = getFieldOptions(fieldId);
        // Set<String> validValues = options.stream().map(CheckboxOptionInfo::getValue).collect(Collectors.toSet());
        // return optionValues.stream().allMatch(validValues::contains);
        
        // 临时实现：认为所有非空值都有效
        return optionValues.stream().allMatch(v -> v != null && !v.trim().isEmpty());
    }

    // ==================== 内部类 ====================

    /**
     * 多选框选项信息类
     */
    public static class CheckboxOptionInfo {
        private final String label;
        private final String value;
        private final String color;

        public CheckboxOptionInfo(String label, String value, String color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }

        public String getLabel() { return label; }
        public String getValue() { return value; }
        public String getColor() { return color; }

        @Override
        public String toString() {
            return String.format("CheckboxOption{label='%s', value='%s', color='%s'}", label, value, color);
        }
    }
}