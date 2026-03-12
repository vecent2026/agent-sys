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
 * 内置数字字段处理器
 * 
 * 处理 AppUser 实体中的数字类型字段，如 age, loginCount 等
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Component
public class BuiltinNumberFieldHandler implements FieldFilterHandler {
    
    /**
     * 支持的整数字段映射 (fieldKey -> Lambda函数)
     */
    private static final Map<String, SFunction<AppUser, Integer>> INTEGER_FIELD_MAPPINGS = new HashMap<>();
    
    /**
     * 支持的长整数字段映射 (fieldKey -> Lambda函数)
     */
    private static final Map<String, SFunction<AppUser, Long>> LONG_FIELD_MAPPINGS = new HashMap<>();
    
    static {
        // 整数字段 (AppUser中现有的整数字段)
        INTEGER_FIELD_MAPPINGS.put("gender", AppUser::getGender);
        INTEGER_FIELD_MAPPINGS.put("status", AppUser::getStatus);
        
        // 长整数字段
        LONG_FIELD_MAPPINGS.put("id", AppUser::getId);
    }
    
    /**
     * 支持的操作符
     */
    private static final List<FilterOperator> SUPPORTED_OPERATORS = Arrays.asList(
            FilterOperator.EQUALS,
            FilterOperator.NOT_EQUALS,
            FilterOperator.GREATER_THAN,
            FilterOperator.LESS_THAN,
            FilterOperator.GREATER_THAN_OR_EQUAL,
            FilterOperator.LESS_THAN_OR_EQUAL,
            FilterOperator.BETWEEN,
            FilterOperator.IS_EMPTY,
            FilterOperator.IS_NOT_EMPTY,
            FilterOperator.IN,
            FilterOperator.NOT_IN
    );
    
    @Override
    public FieldType getSupportedFieldType() {
        return FieldType.BUILTIN_NUMBER;
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
        
        // 判断是整数字段还是长整数字段
        if (INTEGER_FIELD_MAPPINGS.containsKey(fieldKey)) {
            applyIntegerFilter(wrapper, fieldKey, operator, value);
        } else if (LONG_FIELD_MAPPINGS.containsKey(fieldKey)) {
            applyLongFilter(wrapper, fieldKey, operator, value);
        } else {
            throw new IllegalArgumentException("不支持的内置数字字段: " + fieldKey);
        }
    }
    
    /**
     * 应用整数字段筛选
     */
    private void applyIntegerFilter(LambdaQueryWrapper<AppUser> wrapper, String fieldKey, 
                                  FilterOperator operator, Object value) {
        SFunction<AppUser, Integer> columnFunction = INTEGER_FIELD_MAPPINGS.get(fieldKey);
        
        switch (operator) {
            case EQUALS:
                if (value != null) {
                    Integer intValue = parseInteger(value);
                    if (intValue != null) {
                        wrapper.eq(columnFunction, intValue);
                    }
                }
                break;
                
            case NOT_EQUALS:
                if (value != null) {
                    Integer intValue = parseInteger(value);
                    if (intValue != null) {
                        wrapper.ne(columnFunction, intValue);
                    }
                }
                break;
                
            case GREATER_THAN:
                if (value != null) {
                    Integer intValue = parseInteger(value);
                    if (intValue != null) {
                        wrapper.gt(columnFunction, intValue);
                    }
                }
                break;
                
            case LESS_THAN:
                if (value != null) {
                    Integer intValue = parseInteger(value);
                    if (intValue != null) {
                        wrapper.lt(columnFunction, intValue);
                    }
                }
                break;
                
            case GREATER_THAN_OR_EQUAL:
                if (value != null) {
                    Integer intValue = parseInteger(value);
                    if (intValue != null) {
                        wrapper.ge(columnFunction, intValue);
                    }
                }
                break;
                
            case LESS_THAN_OR_EQUAL:
                if (value != null) {
                    Integer intValue = parseInteger(value);
                    if (intValue != null) {
                        wrapper.le(columnFunction, intValue);
                    }
                }
                break;
                
            case BETWEEN:
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rangeValue = (Map<String, Object>) value;
                    Object startObj = rangeValue.get("start");
                    Object endObj = rangeValue.get("end");
                    
                    if (startObj != null && endObj != null) {
                        Integer start = parseInteger(startObj);
                        Integer end = parseInteger(endObj);
                        if (start != null && end != null) {
                            wrapper.between(columnFunction, start, end);
                        }
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
                            Integer intValue = parseInteger(v);
                            if (intValue != null) {
                                intValues.add(intValue);
                            }
                        }
                        if (!intValues.isEmpty()) {
                            wrapper.in(columnFunction, intValues);
                        }
                    }
                } else if (value != null) {
                    Integer intValue = parseInteger(value);
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
                            Integer intValue = parseInteger(v);
                            if (intValue != null) {
                                intValues.add(intValue);
                            }
                        }
                        if (!intValues.isEmpty()) {
                            wrapper.notIn(columnFunction, intValues);
                        }
                    }
                } else if (value != null) {
                    Integer intValue = parseInteger(value);
                    if (intValue != null) {
                        wrapper.ne(columnFunction, intValue);
                    }
                }
                break;
                
            default:
                throw new UnsupportedOperationException("数字字段不支持操作符: " + operator);
        }
    }
    
    /**
     * 应用长整数字段筛选
     */
    private void applyLongFilter(LambdaQueryWrapper<AppUser> wrapper, String fieldKey, 
                               FilterOperator operator, Object value) {
        SFunction<AppUser, Long> columnFunction = LONG_FIELD_MAPPINGS.get(fieldKey);
        
        switch (operator) {
            case EQUALS:
                if (value != null) {
                    Long longValue = parseLong(value);
                    if (longValue != null) {
                        wrapper.eq(columnFunction, longValue);
                    }
                }
                break;
                
            case NOT_EQUALS:
                if (value != null) {
                    Long longValue = parseLong(value);
                    if (longValue != null) {
                        wrapper.ne(columnFunction, longValue);
                    }
                }
                break;
                
            case GREATER_THAN:
                if (value != null) {
                    Long longValue = parseLong(value);
                    if (longValue != null) {
                        wrapper.gt(columnFunction, longValue);
                    }
                }
                break;
                
            case LESS_THAN:
                if (value != null) {
                    Long longValue = parseLong(value);
                    if (longValue != null) {
                        wrapper.lt(columnFunction, longValue);
                    }
                }
                break;
                
            case GREATER_THAN_OR_EQUAL:
                if (value != null) {
                    Long longValue = parseLong(value);
                    if (longValue != null) {
                        wrapper.ge(columnFunction, longValue);
                    }
                }
                break;
                
            case LESS_THAN_OR_EQUAL:
                if (value != null) {
                    Long longValue = parseLong(value);
                    if (longValue != null) {
                        wrapper.le(columnFunction, longValue);
                    }
                }
                break;
                
            case BETWEEN:
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rangeValue = (Map<String, Object>) value;
                    Object startObj = rangeValue.get("start");
                    Object endObj = rangeValue.get("end");
                    
                    if (startObj != null && endObj != null) {
                        Long start = parseLong(startObj);
                        Long end = parseLong(endObj);
                        if (start != null && end != null) {
                            wrapper.between(columnFunction, start, end);
                        }
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
                        List<Long> longValues = new ArrayList<>();
                        for (Object v : values) {
                            Long longValue = parseLong(v);
                            if (longValue != null) {
                                longValues.add(longValue);
                            }
                        }
                        if (!longValues.isEmpty()) {
                            wrapper.in(columnFunction, longValues);
                        }
                    }
                } else if (value != null) {
                    Long longValue = parseLong(value);
                    if (longValue != null) {
                        wrapper.eq(columnFunction, longValue);
                    }
                }
                break;
                
            case NOT_IN:
                if (value instanceof List) {
                    List<?> values = (List<?>) value;
                    if (!values.isEmpty()) {
                        List<Long> longValues = new ArrayList<>();
                        for (Object v : values) {
                            Long longValue = parseLong(v);
                            if (longValue != null) {
                                longValues.add(longValue);
                            }
                        }
                        if (!longValues.isEmpty()) {
                            wrapper.notIn(columnFunction, longValues);
                        }
                    }
                } else if (value != null) {
                    Long longValue = parseLong(value);
                    if (longValue != null) {
                        wrapper.ne(columnFunction, longValue);
                    }
                }
                break;
                
            default:
                throw new UnsupportedOperationException("数字字段不支持操作符: " + operator);
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
                    "操作符 " + operator.getLabel() + " 需要提供数字值",
                    FilterValidationResult.ErrorCodes.VALUE_REQUIRED
            );
        }
        
        // BETWEEN操作符需要特殊验证
        if (operator == FilterOperator.BETWEEN) {
            if (!(value instanceof Map)) {
                return FilterValidationResult.failure(
                        "BETWEEN 操作符需要提供包含 start 和 end 的数字范围对象",
                        FilterValidationResult.ErrorCodes.VALUE_INVALID
                );
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> rangeValue = (Map<String, Object>) value;
            Object start = rangeValue.get("start");
            Object end = rangeValue.get("end");
            
            if (start == null || end == null) {
                return FilterValidationResult.failure(
                        "数字范围必须包含 start 和 end 值",
                        FilterValidationResult.ErrorCodes.VALUE_INVALID
                );
            }
            
            // 验证开始和结束数字格式
            if (!isValidNumber(start)) {
                return FilterValidationResult.failure(
                        "无效的开始数字: " + start,
                        FilterValidationResult.ErrorCodes.VALUE_INVALID
                );
            }
            
            if (!isValidNumber(end)) {
                return FilterValidationResult.failure(
                        "无效的结束数字: " + end,
                        FilterValidationResult.ErrorCodes.VALUE_INVALID
                );
            }
            
        } else if (operator == FilterOperator.IN || operator == FilterOperator.NOT_IN) {
            // IN/NOT_IN操作符支持列表值
            if (value instanceof List) {
                List<?> values = (List<?>) value;
                if (values.isEmpty()) {
                    return FilterValidationResult.failure(
                            "IN/NOT_IN 操作符至少需要一个值",
                            FilterValidationResult.ErrorCodes.VALUE_INVALID
                    );
                }
                
                // 验证列表中的每个值都是数字
                for (Object v : values) {
                    if (v != null && !isValidNumber(v)) {
                        return FilterValidationResult.failure(
                                "IN/NOT_IN 操作符的值必须是数字列表",
                                FilterValidationResult.ErrorCodes.VALUE_TYPE_MISMATCH
                        );
                    }
                }
            } else {
                // 单个值验证
                if (!isValidNumber(value)) {
                    return FilterValidationResult.failure(
                            "无效的数字值: " + value,
                            FilterValidationResult.ErrorCodes.VALUE_INVALID
                    );
                }
            }
        } else {
            // 其他操作符验证单个数字值
            if (!isValidNumber(value)) {
                return FilterValidationResult.failure(
                        "无效的数字值: " + value,
                        FilterValidationResult.ErrorCodes.VALUE_INVALID
                );
            }
        }
        
        return FilterValidationResult.success();
    }
    
    @Override
    public int estimatePerformance(ValidatedFilterCondition condition) {
        FilterOperator operator = condition.getOperator();
        
        // 数字字段通常有索引，性能都比较好
        switch (operator) {
            case EQUALS:
            case NOT_EQUALS:
            case GREATER_THAN:
            case LESS_THAN:
            case GREATER_THAN_OR_EQUAL:
            case LESS_THAN_OR_EQUAL:
                return 9; // 数字比较操作，性能优秀
                
            case BETWEEN:
                return 8; // 范围查询，性能良好
                
            case IS_EMPTY:
            case IS_NOT_EMPTY:
                return 8; // 空值检查，性能良好
                
            case IN:
            case NOT_IN:
                // IN查询性能中等，取决于列表大小
                if (condition.getNormalizedValue() instanceof List) {
                    List<?> values = (List<?>) condition.getNormalizedValue();
                    if (values.size() > 100) {
                        return 6; // 大列表性能较低
                    } else if (values.size() > 10) {
                        return 7; // 中等列表
                    }
                }
                return 8; // 小列表性能良好
                
            default:
                return 7;
        }
    }
    
    /**
     * 解析整数值
     */
    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Integer) {
            return (Integer) value;
        }
        
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 解析长整数值
     */
    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Long) {
            return (Long) value;
        }
        
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 检查值是否为有效数字
     */
    private boolean isValidNumber(Object value) {
        if (value == null) {
            return false;
        }
        
        if (value instanceof Number) {
            return true;
        }
        
        try {
            Double.parseDouble(value.toString());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 检查字段是否被支持
     */
    public static boolean isSupported(String fieldKey) {
        return INTEGER_FIELD_MAPPINGS.containsKey(fieldKey) || LONG_FIELD_MAPPINGS.containsKey(fieldKey);
    }
    
    /**
     * 获取支持的字段列表
     */
    public static List<String> getSupportedFields() {
        List<String> fields = new ArrayList<>();
        fields.addAll(INTEGER_FIELD_MAPPINGS.keySet());
        fields.addAll(LONG_FIELD_MAPPINGS.keySet());
        return fields;
    }
}