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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 内置日期字段处理器
 * 
 * 处理 AppUser 实体中的日期类型字段，如 registerTime, lastLoginTime, birthday 等
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Component
public class BuiltinDateFieldHandler implements FieldFilterHandler {
    
    /**
     * 日期时间字段映射 (fieldKey -> Lambda函数)
     */
    private static final Map<String, SFunction<AppUser, LocalDateTime>> DATETIME_FIELD_MAPPINGS = new HashMap<>();
    
    /**
     * 日期字段映射 (fieldKey -> Lambda函数)
     */
    private static final Map<String, SFunction<AppUser, LocalDate>> DATE_FIELD_MAPPINGS = new HashMap<>();
    
    /**
     * 支持的日期时间格式
     */
    private static final List<DateTimeFormatter> DATETIME_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );
    
    /**
     * 支持的日期格式
     */
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ISO_LOCAL_DATE
    );
    
    static {
        // 日期时间字段
        DATETIME_FIELD_MAPPINGS.put("registerTime", AppUser::getRegisterTime);
        DATETIME_FIELD_MAPPINGS.put("lastLoginTime", AppUser::getLastLoginTime);
        
        // 日期字段
        DATE_FIELD_MAPPINGS.put("birthday", AppUser::getBirthday);
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
            FilterOperator.AFTER,
            FilterOperator.BEFORE,
            FilterOperator.IS_EMPTY,
            FilterOperator.IS_NOT_EMPTY
    );
    
    @Override
    public FieldType getSupportedFieldType() {
        return FieldType.BUILTIN_DATE;
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
        
        // 判断是日期时间字段还是日期字段
        if (DATETIME_FIELD_MAPPINGS.containsKey(fieldKey)) {
            applyDateTimeFilter(wrapper, fieldKey, operator, value);
        } else if (DATE_FIELD_MAPPINGS.containsKey(fieldKey)) {
            applyDateFilter(wrapper, fieldKey, operator, value);
        } else {
            throw new IllegalArgumentException("不支持的内置日期字段: " + fieldKey);
        }
    }
    
    /**
     * 应用日期时间字段筛选
     */
    private void applyDateTimeFilter(LambdaQueryWrapper<AppUser> wrapper, String fieldKey, 
                                   FilterOperator operator, Object value) {
        SFunction<AppUser, LocalDateTime> columnFunction = DATETIME_FIELD_MAPPINGS.get(fieldKey);
        
        switch (operator) {
            case EQUALS:
                if (value != null) {
                    LocalDateTime dateTime = parseDateTime(value);
                    if (dateTime != null) {
                        // 等于某个日期：当天的00:00:00到23:59:59
                        LocalDateTime startOfDay = dateTime.toLocalDate().atStartOfDay();
                        LocalDateTime endOfDay = dateTime.toLocalDate().atTime(23, 59, 59, 999999999);
                        wrapper.between(columnFunction, startOfDay, endOfDay);
                    }
                }
                break;
                
            case NOT_EQUALS:
                if (value != null) {
                    LocalDateTime dateTime = parseDateTime(value);
                    if (dateTime != null) {
                        LocalDateTime startOfDay = dateTime.toLocalDate().atStartOfDay();
                        LocalDateTime endOfDay = dateTime.toLocalDate().atTime(23, 59, 59, 999999999);
                        wrapper.and(w -> w.lt(columnFunction, startOfDay).or().gt(columnFunction, endOfDay));
                    }
                }
                break;
                
            case GREATER_THAN:
            case AFTER:
                if (value != null) {
                    LocalDateTime dateTime = parseDateTime(value);
                    if (dateTime != null) {
                        wrapper.gt(columnFunction, dateTime);
                    }
                }
                break;
                
            case LESS_THAN:
            case BEFORE:
                if (value != null) {
                    LocalDateTime dateTime = parseDateTime(value);
                    if (dateTime != null) {
                        wrapper.lt(columnFunction, dateTime);
                    }
                }
                break;
                
            case GREATER_THAN_OR_EQUAL:
                if (value != null) {
                    LocalDateTime dateTime = parseDateTime(value);
                    if (dateTime != null) {
                        wrapper.ge(columnFunction, dateTime);
                    }
                }
                break;
                
            case LESS_THAN_OR_EQUAL:
                if (value != null) {
                    LocalDateTime dateTime = parseDateTime(value);
                    if (dateTime != null) {
                        wrapper.le(columnFunction, dateTime);
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
                        LocalDateTime start = parseDateTime(startObj);
                        LocalDateTime end = parseDateTime(endObj);
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
                
            default:
                throw new UnsupportedOperationException("日期时间字段不支持操作符: " + operator);
        }
    }
    
    /**
     * 应用日期字段筛选
     */
    private void applyDateFilter(LambdaQueryWrapper<AppUser> wrapper, String fieldKey, 
                               FilterOperator operator, Object value) {
        SFunction<AppUser, LocalDate> columnFunction = DATE_FIELD_MAPPINGS.get(fieldKey);
        
        switch (operator) {
            case EQUALS:
                if (value != null) {
                    LocalDate date = parseDate(value);
                    if (date != null) {
                        wrapper.eq(columnFunction, date);
                    }
                }
                break;
                
            case NOT_EQUALS:
                if (value != null) {
                    LocalDate date = parseDate(value);
                    if (date != null) {
                        wrapper.ne(columnFunction, date);
                    }
                }
                break;
                
            case GREATER_THAN:
            case AFTER:
                if (value != null) {
                    LocalDate date = parseDate(value);
                    if (date != null) {
                        wrapper.gt(columnFunction, date);
                    }
                }
                break;
                
            case LESS_THAN:
            case BEFORE:
                if (value != null) {
                    LocalDate date = parseDate(value);
                    if (date != null) {
                        wrapper.lt(columnFunction, date);
                    }
                }
                break;
                
            case GREATER_THAN_OR_EQUAL:
                if (value != null) {
                    LocalDate date = parseDate(value);
                    if (date != null) {
                        wrapper.ge(columnFunction, date);
                    }
                }
                break;
                
            case LESS_THAN_OR_EQUAL:
                if (value != null) {
                    LocalDate date = parseDate(value);
                    if (date != null) {
                        wrapper.le(columnFunction, date);
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
                        LocalDate start = parseDate(startObj);
                        LocalDate end = parseDate(endObj);
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
                
            default:
                throw new UnsupportedOperationException("日期字段不支持操作符: " + operator);
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
                    "操作符 " + operator.getLabel() + " 需要提供日期值",
                    FilterValidationResult.ErrorCodes.VALUE_REQUIRED
            );
        }
        
        // BETWEEN操作符需要特殊验证
        if (operator == FilterOperator.BETWEEN) {
            if (!(value instanceof Map)) {
                return FilterValidationResult.failure(
                        "BETWEEN 操作符需要提供包含 start 和 end 的日期范围对象",
                        FilterValidationResult.ErrorCodes.VALUE_INVALID
                );
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> rangeValue = (Map<String, Object>) value;
            Object start = rangeValue.get("start");
            Object end = rangeValue.get("end");
            
            if (start == null || end == null) {
                return FilterValidationResult.failure(
                        "日期范围必须包含 start 和 end 值",
                        FilterValidationResult.ErrorCodes.VALUE_INVALID
                );
            }
            
            // 验证开始和结束日期格式
            if (parseDateTime(start) == null && parseDate(start) == null) {
                return FilterValidationResult.failure(
                        "无效的开始日期格式: " + start,
                        FilterValidationResult.ErrorCodes.VALUE_INVALID
                );
            }
            
            if (parseDateTime(end) == null && parseDate(end) == null) {
                return FilterValidationResult.failure(
                        "无效的结束日期格式: " + end,
                        FilterValidationResult.ErrorCodes.VALUE_INVALID
                );
            }
            
        } else {
            // 其他操作符验证单个日期值
            if (parseDateTime(value) == null && parseDate(value) == null) {
                return FilterValidationResult.failure(
                        "无效的日期格式: " + value + "。支持格式: yyyy-MM-dd, yyyy-MM-dd HH:mm:ss 等",
                        FilterValidationResult.ErrorCodes.VALUE_INVALID
                );
            }
        }
        
        return FilterValidationResult.success();
    }
    
    @Override
    public int estimatePerformance(ValidatedFilterCondition condition) {
        FilterOperator operator = condition.getOperator();
        
        // 日期字段通常有索引，性能都比较好
        switch (operator) {
            case EQUALS:
            case NOT_EQUALS:
            case GREATER_THAN:
            case LESS_THAN:
            case GREATER_THAN_OR_EQUAL:
            case LESS_THAN_OR_EQUAL:
            case AFTER:
            case BEFORE:
                return 9; // 日期比较操作，性能优秀
                
            case BETWEEN:
                return 8; // 范围查询，性能良好
                
            case IS_EMPTY:
            case IS_NOT_EMPTY:
                return 8; // 空值检查，性能良好
                
            default:
                return 7;
        }
    }
    
    /**
     * 解析日期时间字符串
     */
    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        
        String dateStr = value.toString();
        
        // 尝试各种日期时间格式
        for (DateTimeFormatter formatter : DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        
        // 尝试解析为日期，然后转为日期时间
        LocalDate date = parseDate(value);
        if (date != null) {
            return date.atStartOfDay();
        }
        
        return null;
    }
    
    /**
     * 解析日期字符串
     */
    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toLocalDate();
        }
        
        String dateStr = value.toString();
        
        // 尝试各种日期格式
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        
        return null;
    }
    
    /**
     * 检查字段是否被支持
     */
    public static boolean isSupported(String fieldKey) {
        return DATETIME_FIELD_MAPPINGS.containsKey(fieldKey) || DATE_FIELD_MAPPINGS.containsKey(fieldKey);
    }
    
    /**
     * 获取支持的字段列表
     */
    public static List<String> getSupportedFields() {
        List<String> fields = new ArrayList<>();
        fields.addAll(DATETIME_FIELD_MAPPINGS.keySet());
        fields.addAll(DATE_FIELD_MAPPINGS.keySet());
        return fields;
    }
    
    /**
     * 获取支持的日期格式说明
     */
    public static List<String> getSupportedDateFormats() {
        return Arrays.asList(
                "yyyy-MM-dd (如: 2024-01-15)",
                "yyyy-MM-dd HH:mm:ss (如: 2024-01-15 14:30:00)",
                "yyyy-MM-dd HH:mm (如: 2024-01-15 14:30)",
                "yyyy-MM-ddTHH:mm:ss (ISO格式)"
        );
    }
}