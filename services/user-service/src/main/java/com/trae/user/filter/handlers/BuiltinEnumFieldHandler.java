package com.trae.user.filter.handlers;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.trae.user.entity.AppUser;
import com.trae.user.enums.FieldType;
import com.trae.user.enums.FilterOperator;
import com.trae.user.filter.FieldFilterHandler;
import com.trae.user.filter.FilterValidationResult;
import com.trae.user.filter.ValidatedFilterCondition;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 内置枚举字段处理器
 * 
 * 处理 AppUser 实体中的枚举类型字段，如 status, gender 等
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Component
public class BuiltinEnumFieldHandler implements FieldFilterHandler {
    
    /**
     * 支持的字段映射 (fieldKey -> Lambda函数)
     */
    private static final Map<String, SFunction<AppUser, Integer>> FIELD_MAPPINGS = new HashMap<>();
    
    /**
     * 字段值映射 (fieldKey -> {label -> value})
     */
    private static final Map<String, Map<String, Integer>> FIELD_VALUE_MAPPINGS = new HashMap<>();
    
    static {
        // 字段映射
        FIELD_MAPPINGS.put("status", AppUser::getStatus);
        FIELD_MAPPINGS.put("gender", AppUser::getGender);
        
        // 状态字段值映射
        Map<String, Integer> statusValues = new HashMap<>();
        statusValues.put("正常", 1);
        statusValues.put("禁用", 0);
        statusValues.put("注销", 2);
        FIELD_VALUE_MAPPINGS.put("status", statusValues);
        
        // 性别字段值映射
        Map<String, Integer> genderValues = new HashMap<>();
        genderValues.put("未知", 0);
        genderValues.put("男", 1);
        genderValues.put("女", 2);
        FIELD_VALUE_MAPPINGS.put("gender", genderValues);
    }
    
    /**
     * 支持的操作符
     */
    private static final List<FilterOperator> SUPPORTED_OPERATORS = Arrays.asList(
            FilterOperator.EQUALS,
            FilterOperator.NOT_EQUALS,
            FilterOperator.IS_EMPTY,
            FilterOperator.IS_NOT_EMPTY,
            FilterOperator.IN,
            FilterOperator.NOT_IN
    );
    
    @Override
    public FieldType getSupportedFieldType() {
        return FieldType.BUILTIN_ENUM;
    }
    
    @Override
    public List<FilterOperator> getSupportedOperators() {
        return SUPPORTED_OPERATORS;
    }
    
    @Override
    public void applyFilter(LambdaQueryWrapper<AppUser> wrapper, ValidatedFilterCondition condition) {
        String fieldKey = condition.getFieldKey();
        FilterOperator operator = condition.getOperator();
        Object value = condition.getNormalizedValue();
        
        // 获取字段对应的Lambda函数
        SFunction<AppUser, Integer> columnFunction = FIELD_MAPPINGS.get(fieldKey);
        if (columnFunction == null) {
            throw new IllegalArgumentException("不支持的内置枚举字段: " + fieldKey);
        }
        
        // 根据操作符应用筛选条件
        switch (operator) {
            case EQUALS:
                if (value != null) {
                    Integer intValue = normalizeValue(fieldKey, value);
                    if (intValue != null) {
                        wrapper.eq(columnFunction, intValue);
                    }
                }
                break;
                
            case NOT_EQUALS:
                if (value != null) {
                    Integer intValue = normalizeValue(fieldKey, value);
                    if (intValue != null) {
                        wrapper.ne(columnFunction, intValue);
                    }
                }
                break;
                
            case IS_EMPTY:
                wrapper.isNull(columnFunction);
                break;
                
            case IS_NOT_EMPTY:
                wrapper.isNotNull(columnFunction);
                break;
                
            case IN:
                if (value instanceof List) {
                    List<?> values = (List<?>) value;
                    if (!values.isEmpty()) {
                        List<Integer> intValues = new ArrayList<>();
                        for (Object v : values) {
                            Integer intValue = normalizeValue(fieldKey, v);
                            if (intValue != null) {
                                intValues.add(intValue);
                            }
                        }
                        if (!intValues.isEmpty()) {
                            wrapper.in(columnFunction, intValues);
                        }
                    }
                } else if (value != null) {
                    Integer intValue = normalizeValue(fieldKey, value);
                    if (intValue != null) {
                        wrapper.eq(columnFunction, intValue);
                    }
                }
                break;
                
            case NOT_IN:
                if (value instanceof List) {
                    List<?> values = (List<?>) value;
                    if (!values.isEmpty()) {
                        List<Integer> intValues = new ArrayList<>();
                        for (Object v : values) {
                            Integer intValue = normalizeValue(fieldKey, v);
                            if (intValue != null) {
                                intValues.add(intValue);
                            }
                        }
                        if (!intValues.isEmpty()) {
                            wrapper.notIn(columnFunction, intValues);
                        }
                    }
                } else if (value != null) {
                    Integer intValue = normalizeValue(fieldKey, value);
                    if (intValue != null) {
                        wrapper.ne(columnFunction, intValue);
                    }
                }
                break;
                
            default:
                throw new UnsupportedOperationException("内置枚举字段不支持操作符: " + operator);
        }
    }
    
    @Override
    public FilterValidationResult validateValue(FilterOperator operator, Object value) {
        // 空值检查操作符不需要值
        if (operator == FilterOperator.IS_EMPTY || operator == FilterOperator.IS_NOT_EMPTY) {
            return FilterValidationResult.success();
        }
        
        // 其他操作符需要值
        if (value == null) {
            return FilterValidationResult.failure(
                    "操作符 " + operator.getLabel() + " 需要提供值",
                    FilterValidationResult.ErrorCodes.VALUE_REQUIRED
            );
        }
        
        // 验证值类型和内容
        if (operator == FilterOperator.IN || operator == FilterOperator.NOT_IN) {
            // IN/NOT_IN操作符支持列表值
            if (value instanceof List) {
                List<?> values = (List<?>) value;
                if (values.isEmpty()) {
                    return FilterValidationResult.failure(
                            "IN/NOT_IN 操作符至少需要一个值",
                            FilterValidationResult.ErrorCodes.VALUE_INVALID
                    );
                }
                
                // 验证列表中的每个值
                for (Object v : values) {
                    if (v == null) continue;
                    if (!(v instanceof Integer) && !(v instanceof String)) {
                        return FilterValidationResult.failure(
                                "枚举字段的值必须是整数或字符串",
                                FilterValidationResult.ErrorCodes.VALUE_TYPE_MISMATCH
                        );
                    }
                }
            }
        } else {
            // 其他操作符只接受单个值
            if (!(value instanceof Integer) && !(value instanceof String)) {
                return FilterValidationResult.failure(
                        "枚举字段的值必须是整数或字符串",
                        FilterValidationResult.ErrorCodes.VALUE_TYPE_MISMATCH
                );
            }
        }
        
        return FilterValidationResult.success();
    }
    
    @Override
    public int estimatePerformance(ValidatedFilterCondition condition) {
        FilterOperator operator = condition.getOperator();
        
        // 枚举字段通常有索引，性能都比较好
        switch (operator) {
            case EQUALS:
            case NOT_EQUALS:
                return 9; // 精确匹配，性能最好
                
            case IS_EMPTY:
            case IS_NOT_EMPTY:
                return 8; // 空值检查，性能良好
                
            case IN:
            case NOT_IN:
                // IN查询性能中等，取决于列表大小
                if (condition.getNormalizedValue() instanceof List) {
                    List<?> values = (List<?>) condition.getNormalizedValue();
                    if (values.size() > 10) {
                        return 7; // 大列表性能稍低
                    }
                }
                return 8; // 小列表性能良好
                
            default:
                return 7;
        }
    }
    
    /**
     * 标准化枚举值 (将字符串标签转换为对应的整数值)
     */
    private Integer normalizeValue(String fieldKey, Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Integer) {
            return (Integer) value;
        }
        
        if (value instanceof String) {
            Map<String, Integer> valueMapping = FIELD_VALUE_MAPPINGS.get(fieldKey);
            if (valueMapping != null) {
                return valueMapping.get(value.toString());
            }
            
            // 尝试解析为整数
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        // 其他类型尝试转换为整数
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 检查字段是否被支持
     */
    public static boolean isSupported(String fieldKey) {
        return FIELD_MAPPINGS.containsKey(fieldKey);
    }
    
    /**
     * 获取支持的字段列表
     */
    public static List<String> getSupportedFields() {
        return Arrays.asList("status", "gender");
    }
    
    /**
     * 获取字段的可选值
     */
    public static Map<String, Integer> getFieldValues(String fieldKey) {
        Map<String, Integer> values = FIELD_VALUE_MAPPINGS.get(fieldKey);
        return values != null ? new HashMap<>(values) : new HashMap<>();
    }
    
    /**
     * 获取所有字段的值映射
     */
    public static Map<String, Map<String, Integer>> getAllFieldValues() {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : FIELD_VALUE_MAPPINGS.entrySet()) {
            result.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return result;
    }
}