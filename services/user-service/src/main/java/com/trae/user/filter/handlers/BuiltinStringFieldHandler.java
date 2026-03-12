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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置字符串字段处理器
 * 
 * 处理 AppUser 实体中的字符串类型字段，如 nickname, mobile, email 等
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Component
public class BuiltinStringFieldHandler implements FieldFilterHandler {
    
    /**
     * 支持的字段映射 (fieldKey -> Lambda函数)
     */
    private static final Map<String, SFunction<AppUser, String>> FIELD_MAPPINGS = new HashMap<>();
    
    static {
        FIELD_MAPPINGS.put("nickname", AppUser::getNickname);
        FIELD_MAPPINGS.put("mobile", AppUser::getMobile);
        FIELD_MAPPINGS.put("email", AppUser::getEmail);
        FIELD_MAPPINGS.put("registerSource", AppUser::getRegisterSource);
        FIELD_MAPPINGS.put("lastLoginIp", AppUser::getLastLoginIp);
    }
    
    /**
     * 支持的操作符
     */
    private static final List<FilterOperator> SUPPORTED_OPERATORS = Arrays.asList(
            FilterOperator.EQUALS,
            FilterOperator.NOT_EQUALS,
            FilterOperator.CONTAINS,
            FilterOperator.NOT_CONTAINS,
            FilterOperator.STARTS_WITH,
            FilterOperator.ENDS_WITH,
            FilterOperator.IS_EMPTY,
            FilterOperator.IS_NOT_EMPTY,
            FilterOperator.IN,
            FilterOperator.NOT_IN,
            FilterOperator.REGEX
    );
    
    @Override
    public FieldType getSupportedFieldType() {
        return FieldType.BUILTIN_STRING;
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
        SFunction<AppUser, String> columnFunction = FIELD_MAPPINGS.get(fieldKey);
        if (columnFunction == null) {
            throw new IllegalArgumentException("不支持的内置字符串字段: " + fieldKey);
        }
        
        // 根据操作符应用筛选条件
        switch (operator) {
            case EQUALS:
                if (value != null) {
                    wrapper.eq(columnFunction, value.toString());
                }
                break;
                
            case NOT_EQUALS:
                if (value != null) {
                    wrapper.ne(columnFunction, value.toString());
                }
                break;
                
            case CONTAINS:
                if (value != null && !value.toString().isEmpty()) {
                    wrapper.like(columnFunction, value.toString());
                }
                break;
                
            case NOT_CONTAINS:
                if (value != null && !value.toString().isEmpty()) {
                    wrapper.notLike(columnFunction, value.toString());
                }
                break;
                
            case STARTS_WITH:
                if (value != null && !value.toString().isEmpty()) {
                    wrapper.likeRight(columnFunction, value.toString());
                }
                break;
                
            case ENDS_WITH:
                if (value != null && !value.toString().isEmpty()) {
                    wrapper.likeLeft(columnFunction, value.toString());
                }
                break;
                
            case IS_EMPTY:
                wrapper.and(w -> w.isNull(columnFunction).or().eq(columnFunction, ""));
                break;
                
            case IS_NOT_EMPTY:
                wrapper.and(w -> w.isNotNull(columnFunction).ne(columnFunction, ""));
                break;
                
            case IN:
                if (value instanceof List) {
                    List<?> values = (List<?>) value;
                    if (!values.isEmpty()) {
                        wrapper.in(columnFunction, values);
                    }
                } else if (value != null) {
                    // 单个值也支持IN操作
                    wrapper.eq(columnFunction, value.toString());
                }
                break;
                
            case NOT_IN:
                if (value instanceof List) {
                    List<?> values = (List<?>) value;
                    if (!values.isEmpty()) {
                        wrapper.notIn(columnFunction, values);
                    }
                } else if (value != null) {
                    wrapper.ne(columnFunction, value.toString());
                }
                break;
                
            case REGEX:
                if (value != null && !value.toString().isEmpty()) {
                    // MySQL的正则表达式
                    wrapper.apply("REGEXP({0}, {1})", columnFunction, value.toString());
                }
                break;
                
            default:
                throw new UnsupportedOperationException("内置字符串字段不支持操作符: " + operator);
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
                
                // 验证列表中的每个值都是字符串
                for (Object v : values) {
                    if (v != null && !(v instanceof String)) {
                        return FilterValidationResult.failure(
                                "IN/NOT_IN 操作符的值必须是字符串列表",
                                FilterValidationResult.ErrorCodes.VALUE_TYPE_MISMATCH
                        );
                    }
                }
            } else {
                // 单个值会被转换为字符串
                if (!(value instanceof String)) {
                    return FilterValidationResult.success(value.toString());
                }
            }
        } else {
            // 其他操作符只接受字符串值
            if (!(value instanceof String)) {
                return FilterValidationResult.success(value.toString());
            }
            
            String stringValue = (String) value;
            
            // 验证字符串长度
            if (stringValue.length() > 500) {
                return FilterValidationResult.failure(
                        "字符串值长度不能超过500字符",
                        FilterValidationResult.ErrorCodes.VALUE_OUT_OF_RANGE
                ).addWarning("长字符串可能影响查询性能");
            }
            
            // 正则表达式验证
            if (operator == FilterOperator.REGEX) {
                try {
                    java.util.regex.Pattern.compile(stringValue);
                } catch (java.util.regex.PatternSyntaxException e) {
                    return FilterValidationResult.failure(
                            "无效的正则表达式: " + e.getMessage(),
                            FilterValidationResult.ErrorCodes.VALUE_INVALID
                    );
                }
            }
        }
        
        FilterValidationResult result = FilterValidationResult.success();
        
        // 添加性能建议
        if (operator == FilterOperator.CONTAINS || operator == FilterOperator.NOT_CONTAINS) {
            result.addSuggestion("模糊查询可能影响性能，建议在数据量大时添加其他精确条件");
        }
        
        if (operator == FilterOperator.REGEX) {
            result.addWarning("正则表达式查询性能较低，建议谨慎使用");
        }
        
        return result;
    }
    
    @Override
    public int estimatePerformance(ValidatedFilterCondition condition) {
        FilterOperator operator = condition.getOperator();
        String fieldKey = condition.getFieldKey();
        
        // 基于操作符的性能评分
        switch (operator) {
            case EQUALS:
            case NOT_EQUALS:
                // 精确匹配，性能最好
                return 9;
                
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
                
            case STARTS_WITH:
                // 前缀匹配，性能较好（可以使用索引）
                return 8;
                
            case IS_EMPTY:
            case IS_NOT_EMPTY:
                // 空值检查，性能良好
                return 8;
                
            case ENDS_WITH:
            case CONTAINS:
            case NOT_CONTAINS:
                // 模糊匹配，性能中等
                return 6;
                
            case REGEX:
                // 正则表达式，性能最低
                return 3;
                
            default:
                return 5;
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
        return Arrays.asList("nickname", "mobile", "email", "registerSource", "lastLoginIp");
    }
}