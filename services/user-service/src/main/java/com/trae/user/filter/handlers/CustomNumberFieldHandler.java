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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 自定义数字字段处理器 - 处理动态数字字段的筛选
 * 
 * 支持的字段类型：CUSTOM_NUMBER
 * 支持整数和小数，提供数值范围筛选功能
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Slf4j
@Component
public class CustomNumberFieldHandler extends CustomFieldHandlerBase {

    // 支持的操作符
    private static final List<FilterOperator> SUPPORTED_OPERATORS = Arrays.asList(
            FilterOperator.EQUALS,
            FilterOperator.NOT_EQUALS,
            FilterOperator.GREATER_THAN,
            FilterOperator.GREATER_THAN_OR_EQUAL,
            FilterOperator.LESS_THAN,
            FilterOperator.LESS_THAN_OR_EQUAL,
            FilterOperator.BETWEEN,
            FilterOperator.IS_EMPTY,
            FilterOperator.IS_NOT_EMPTY,
            FilterOperator.IN,
            FilterOperator.NOT_IN
    );

    public CustomNumberFieldHandler(AppUserFieldService fieldService) {
        super(fieldService);
    }

    @Override
    public FieldType getSupportedFieldType() {
        return FieldType.CUSTOM_NUMBER;
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

        try {
            if (operator == FilterOperator.BETWEEN) {
                // 范围查询需要验证范围格式
                NumberRange range = parseNumberRange(value);
                if (range.getMin() != null && range.getMax() != null && 
                    range.getMin().compareTo(range.getMax()) > 0) {
                    return FilterValidationResult.failure(
                            "数值范围无效，最小值不能大于最大值",
                            FilterValidationResult.ErrorCodes.VALUE_INVALID
                    );
                }
            } else if (operator == FilterOperator.IN || operator == FilterOperator.NOT_IN) {
                // 多值验证
                List<BigDecimal> numbers = parseMultipleNumbers(value);
                if (numbers.isEmpty()) {
                    return FilterValidationResult.failure(
                            "请输入有效的数字列表",
                            FilterValidationResult.ErrorCodes.VALUE_INVALID
                    );
                }
                if (numbers.size() > 50) {
                    result.addWarning("数字列表过长可能影响查询性能");
                    result.addSuggestion("建议减少数字数量或使用范围查询");
                }
            } else {
                // 单值验证
                parseNumber(value);
            }
        } catch (NumberFormatException e) {
            return FilterValidationResult.failure(
                    "无效的数字格式: " + value,
                    FilterValidationResult.ErrorCodes.VALUE_INVALID
            );
        }

        return result;
    }

    @Override
    protected void applySpecialOperator(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, 
                                      FilterOperator operator, Object value) {
        try {
            switch (operator) {
                case GREATER_THAN:
                    applyGreaterThanFilter(wrapper, fieldId, parseNumber(value));
                    break;
                    
                case GREATER_THAN_OR_EQUAL:
                    applyGreaterEqualFilter(wrapper, fieldId, parseNumber(value));
                    break;
                    
                case LESS_THAN:
                    applyLessThanFilter(wrapper, fieldId, parseNumber(value));
                    break;
                    
                case LESS_THAN_OR_EQUAL:
                    applyLessEqualFilter(wrapper, fieldId, parseNumber(value));
                    break;
                    
                case BETWEEN:
                    NumberRange range = parseNumberRange(value);
                    applyBetweenFilter(wrapper, fieldId, range);
                    break;
                    
                default:
                    super.applySpecialOperator(wrapper, fieldId, operator, value);
            }
        } catch (Exception e) {
            log.error("Failed to apply number filter: fieldId={}, operator={}, value={}", 
                    fieldId, operator, value, e);
        }
    }

    @Override
    public int estimatePerformance(ValidatedFilterCondition condition) {
        FilterOperator operator = condition.getOperator();
        Object value = condition.getNormalizedValue();
        switch (operator) {
            case EQUALS:
            case NOT_EQUALS:
            case GREATER_THAN:
            case GREATER_THAN_OR_EQUAL:
            case LESS_THAN:
            case LESS_THAN_OR_EQUAL:
                return 8; // 数字比较，性能优秀
                
            case BETWEEN:
                return 7; // 范围查询，性能良好
                
            case IN:
            case NOT_IN:
                if (value != null) {
                    List<java.math.BigDecimal> numbers = parseMultipleNumbers(value);
                    if (numbers.size() > 20) return 4; // 大量值，性能较差
                    if (numbers.size() > 5) return 6; // 中等数量，性能中等
                }
                return 7; // 少量值，性能良好
                
            default:
                return 6; // 默认中等性能
        }
    }

    // ==================== 专用筛选方法 ====================

    /**
     * 大于筛选
     */
    private void applyGreaterThanFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, BigDecimal value) {
        String existsSql = "SELECT 1 FROM tenant_field_value fv " +
                          "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                          " AND CAST(fv.field_value AS DECIMAL(20,6)) > " + value;
        wrapper.exists(existsSql);
        log.debug("Applied GREATER_THAN filter for field {} with value {}", fieldId, value);
    }

    /**
     * 大于等于筛选
     */
    private void applyGreaterEqualFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, BigDecimal value) {
        String existsSql = "SELECT 1 FROM tenant_field_value fv " +
                          "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                          " AND CAST(fv.field_value AS DECIMAL(20,6)) >= " + value;
        wrapper.exists(existsSql);
        log.debug("Applied GREATER_EQUAL filter for field {} with value {}", fieldId, value);
    }

    /**
     * 小于筛选
     */
    private void applyLessThanFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, BigDecimal value) {
        String existsSql = "SELECT 1 FROM tenant_field_value fv " +
                          "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                          " AND CAST(fv.field_value AS DECIMAL(20,6)) < " + value;
        wrapper.exists(existsSql);
        log.debug("Applied LESS_THAN filter for field {} with value {}", fieldId, value);
    }

    /**
     * 小于等于筛选
     */
    private void applyLessEqualFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, BigDecimal value) {
        String existsSql = "SELECT 1 FROM tenant_field_value fv " +
                          "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                          " AND CAST(fv.field_value AS DECIMAL(20,6)) <= " + value;
        wrapper.exists(existsSql);
        log.debug("Applied LESS_EQUAL filter for field {} with value {}", fieldId, value);
    }

    /**
     * 范围筛选
     */
    private void applyBetweenFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, NumberRange range) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT 1 FROM tenant_field_value fv ")
                 .append("WHERE fv.user_id = app_user.id AND fv.field_id = ").append(fieldId);

        if (range.getMin() != null && range.getMax() != null) {
            sqlBuilder.append(" AND CAST(fv.field_value AS DECIMAL(20,6)) BETWEEN ")
                     .append(range.getMin()).append(" AND ").append(range.getMax());
        } else if (range.getMin() != null) {
            sqlBuilder.append(" AND CAST(fv.field_value AS DECIMAL(20,6)) >= ").append(range.getMin());
        } else if (range.getMax() != null) {
            sqlBuilder.append(" AND CAST(fv.field_value AS DECIMAL(20,6)) <= ").append(range.getMax());
        }

        wrapper.exists(sqlBuilder.toString());
        log.debug("Applied BETWEEN filter for field {} with range [{}, {}]", 
                fieldId, range.getMin(), range.getMax());
    }

    // ==================== 数值解析方法 ====================

    /**
     * 解析单个数字
     */
    private BigDecimal parseNumber(Object value) {
        if (value == null) {
            throw new NumberFormatException("数值不能为空");
        }
        
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        
        if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.isEmpty()) {
                throw new NumberFormatException("数值不能为空");
            }
            return new BigDecimal(str);
        }
        
        throw new NumberFormatException("无法解析数值: " + value);
    }

    /**
     * 解析数字列表
     */
    private List<BigDecimal> parseMultipleNumbers(Object value) {
        return parseMultipleValues(value).stream()
                .map(str -> {
                    try {
                        return new BigDecimal(str);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid number format: {}", str);
                        return null;
                    }
                })
                .filter(num -> num != null)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 解析数字范围
     */
    private NumberRange parseNumberRange(Object value) {
        if (value == null) {
            return new NumberRange(null, null);
        }
        
        if (value instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) value;
            BigDecimal min = null;
            BigDecimal max = null;
            
            if (map.containsKey("min") && map.get("min") != null) {
                min = parseNumber(map.get("min"));
            }
            if (map.containsKey("max") && map.get("max") != null) {
                max = parseNumber(map.get("max"));
            }
            
            return new NumberRange(min, max);
        }
        
        if (value instanceof String) {
            String str = (String) value;
            // 支持格式：min,max 或 min-max 或 min~max
            String[] parts = str.split("[,-~]");
            if (parts.length == 2) {
                BigDecimal min = parts[0].trim().isEmpty() ? null : parseNumber(parts[0].trim());
                BigDecimal max = parts[1].trim().isEmpty() ? null : parseNumber(parts[1].trim());
                return new NumberRange(min, max);
            }
        }
        
        throw new NumberFormatException("无效的数字范围格式: " + value);
    }

    // ==================== 批量优化支持 ====================

    @Override
    public String getBatchQueryFragment(Long fieldId, FilterOperator operator, Object value) {
        try {
            switch (operator) {
                case EQUALS:
                    BigDecimal number = parseNumber(value);
                    return String.format("CAST(fv%d.field_value AS DECIMAL(20,6)) = %s", fieldId, number);
                    
                case GREATER_THAN:
                    return String.format("CAST(fv%d.field_value AS DECIMAL(20,6)) > %s", 
                            fieldId, parseNumber(value));
                    
                case LESS_THAN:
                    return String.format("CAST(fv%d.field_value AS DECIMAL(20,6)) < %s", 
                            fieldId, parseNumber(value));
                    
                case BETWEEN:
                    NumberRange range = parseNumberRange(value);
                    if (range.getMin() != null && range.getMax() != null) {
                        return String.format("CAST(fv%d.field_value AS DECIMAL(20,6)) BETWEEN %s AND %s", 
                                fieldId, range.getMin(), range.getMax());
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

    // ==================== 内部类 ====================

    /**
     * 数字范围类
     */
    public static class NumberRange {
        private final BigDecimal min;
        private final BigDecimal max;

        public NumberRange(BigDecimal min, BigDecimal max) {
            this.min = min;
            this.max = max;
        }

        public BigDecimal getMin() { return min; }
        public BigDecimal getMax() { return max; }

        @Override
        public String toString() {
            return String.format("[%s, %s]", min, max);
        }
    }
}