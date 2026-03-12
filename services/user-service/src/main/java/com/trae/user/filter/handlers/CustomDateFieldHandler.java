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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * 自定义日期字段处理器 - 处理动态日期字段的筛选
 * 
 * 支持的字段类型：CUSTOM_DATE
 * 支持日期和日期时间，提供灵活的日期范围筛选
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Slf4j
@Component
public class CustomDateFieldHandler extends CustomFieldHandlerBase {

    // 支持的操作符
    private static final List<FilterOperator> SUPPORTED_OPERATORS = Arrays.asList(
            FilterOperator.EQUALS,
            FilterOperator.NOT_EQUALS,
            FilterOperator.AFTER,
            FilterOperator.BEFORE,
            FilterOperator.BETWEEN,
            FilterOperator.IS_EMPTY,
            FilterOperator.IS_NOT_EMPTY
    );

    // 支持的日期格式
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    );

    public CustomDateFieldHandler(AppUserFieldService fieldService) {
        super(fieldService);
    }

    @Override
    public FieldType getSupportedFieldType() {
        return FieldType.CUSTOM_DATE;
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
                // 日期范围验证
                DateRange range = parseDateRange(value);
                if (range.getStart() != null && range.getEnd() != null && 
                    range.getStart().isAfter(range.getEnd())) {
                    return FilterValidationResult.failure(
                            "日期范围无效，开始日期不能晚于结束日期",
                            FilterValidationResult.ErrorCodes.VALUE_INVALID
                    );
                }
            } else {
                // 单日期验证
                parseDateTime(value);
            }
        } catch (DateTimeParseException e) {
            return FilterValidationResult.failure(
                    "无效的日期格式，支持的格式：yyyy-MM-dd, yyyy/MM/dd, yyyy-MM-dd HH:mm:ss 等",
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
                case AFTER:
                    LocalDateTime afterDate = parseDateTime(value);
                    applyAfterFilter(wrapper, fieldId, afterDate);
                    break;
                    
                case BEFORE:
                    LocalDateTime beforeDate = parseDateTime(value);
                    applyBeforeFilter(wrapper, fieldId, beforeDate);
                    break;
                    
                case BETWEEN:
                    DateRange range = parseDateRange(value);
                    applyBetweenFilter(wrapper, fieldId, range);
                    break;
                    
                default:
                    super.applySpecialOperator(wrapper, fieldId, operator, value);
            }
        } catch (Exception e) {
            log.error("Failed to apply date filter: fieldId={}, operator={}, value={}", 
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
                return 8; // 日期精确匹配，性能优秀
                
            case AFTER:
            case BEFORE:
                return 7; // 日期比较，性能良好
                
            case BETWEEN:
                return 6; // 日期范围，性能中等
                
            default:
                return 6; // 默认中等性能
        }
    }

    // ==================== 专用筛选方法 ====================

    /**
     * 晚于指定日期筛选
     */
    private void applyAfterFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, LocalDateTime dateTime) {
        String dateStr = formatDateTimeForSql(dateTime);
        String existsSql = "SELECT 1 FROM app_user_field_value fv " +
                          "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                          " AND STR_TO_DATE(fv.field_value, '%Y-%m-%d %H:%i:%s') > '" + dateStr + "'";
        wrapper.exists(existsSql);
        log.debug("Applied AFTER filter for field {} with date {}", fieldId, dateTime);
    }

    /**
     * 早于指定日期筛选
     */
    private void applyBeforeFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, LocalDateTime dateTime) {
        String dateStr = formatDateTimeForSql(dateTime);
        String existsSql = "SELECT 1 FROM app_user_field_value fv " +
                          "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                          " AND STR_TO_DATE(fv.field_value, '%Y-%m-%d %H:%i:%s') < '" + dateStr + "'";
        wrapper.exists(existsSql);
        log.debug("Applied BEFORE filter for field {} with date {}", fieldId, dateTime);
    }

    /**
     * 日期范围筛选
     */
    private void applyBetweenFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, DateRange range) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT 1 FROM app_user_field_value fv ")
                 .append("WHERE fv.user_id = app_user.id AND fv.field_id = ").append(fieldId);

        if (range.getStart() != null && range.getEnd() != null) {
            String startStr = formatDateTimeForSql(range.getStart());
            String endStr = formatDateTimeForSql(range.getEnd());
            sqlBuilder.append(" AND STR_TO_DATE(fv.field_value, '%Y-%m-%d %H:%i:%s') BETWEEN '")
                     .append(startStr).append("' AND '").append(endStr).append("'");
        } else if (range.getStart() != null) {
            String startStr = formatDateTimeForSql(range.getStart());
            sqlBuilder.append(" AND STR_TO_DATE(fv.field_value, '%Y-%m-%d %H:%i:%s') >= '").append(startStr).append("'");
        } else if (range.getEnd() != null) {
            String endStr = formatDateTimeForSql(range.getEnd());
            sqlBuilder.append(" AND STR_TO_DATE(fv.field_value, '%Y-%m-%d %H:%i:%s') <= '").append(endStr).append("'");
        }

        wrapper.exists(sqlBuilder.toString());
        log.debug("Applied BETWEEN filter for field {} with range [{}, {}]", 
                fieldId, range.getStart(), range.getEnd());
    }

    // ==================== 日期解析方法 ====================

    /**
     * 解析日期时间 - 支持多种格式
     */
    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            throw new DateTimeParseException("日期不能为空", "", 0);
        }

        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }

        if (value instanceof LocalDate) {
            return ((LocalDate) value).atStartOfDay();
        }

        String dateStr = value.toString().trim();
        if (dateStr.isEmpty()) {
            throw new DateTimeParseException("日期不能为空", dateStr, 0);
        }

        // 尝试各种日期格式
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                if (dateStr.contains(":")) {
                    // 包含时间部分
                    return LocalDateTime.parse(dateStr, formatter);
                } else {
                    // 只有日期部分
                    return LocalDate.parse(dateStr, formatter).atStartOfDay();
                }
            } catch (DateTimeParseException e) {
                // 继续尝试下一种格式
            }
        }

        throw new DateTimeParseException("无法解析日期格式: " + dateStr, dateStr, 0);
    }

    /**
     * 解析日期范围
     */
    private DateRange parseDateRange(Object value) {
        if (value == null) {
            return new DateRange(null, null);
        }
        
        if (value instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) value;
            LocalDateTime start = null;
            LocalDateTime end = null;
            
            if (map.containsKey("start") && map.get("start") != null) {
                start = parseDateTime(map.get("start"));
            }
            if (map.containsKey("end") && map.get("end") != null) {
                end = parseDateTime(map.get("end"));
            }
            
            return new DateRange(start, end);
        }
        
        if (value instanceof String) {
            String str = (String) value;
            // 支持格式：start,end 或 start~end
            String[] parts = str.split("[,~]");
            if (parts.length == 2) {
                LocalDateTime start = parts[0].trim().isEmpty() ? null : parseDateTime(parts[0].trim());
                LocalDateTime end = parts[1].trim().isEmpty() ? null : parseDateTime(parts[1].trim());
                return new DateRange(start, end);
            }
        }
        
        throw new DateTimeParseException("无效的日期范围格式: " + value, value.toString(), 0);
    }

    /**
     * 格式化日期时间为SQL格式
     */
    private String formatDateTimeForSql(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 日期值规范化
     */
    @Override
    protected String normalizeValue(Object value) {
        if (value == null) {
            return "";
        }
        
        try {
            LocalDateTime dateTime = parseDateTime(value);
            return formatDateTimeForSql(dateTime);
        } catch (Exception e) {
            log.warn("Failed to normalize date value: {}", value, e);
            return value.toString();
        }
    }

    // ==================== 批量优化支持 ====================

    @Override
    public String getBatchQueryFragment(Long fieldId, FilterOperator operator, Object value) {
        try {
            switch (operator) {
                case EQUALS:
                    LocalDateTime dateTime = parseDateTime(value);
                    String dateStr = formatDateTimeForSql(dateTime);
                    return String.format("STR_TO_DATE(fv%d.field_value, '%%Y-%%m-%%d %%H:%%i:%%s') = '%s'", 
                            fieldId, dateStr);
                    
                case AFTER:
                    String afterStr = formatDateTimeForSql(parseDateTime(value));
                    return String.format("STR_TO_DATE(fv%d.field_value, '%%Y-%%m-%%d %%H:%%i:%%s') > '%s'", 
                            fieldId, afterStr);
                    
                case BEFORE:
                    String beforeStr = formatDateTimeForSql(parseDateTime(value));
                    return String.format("STR_TO_DATE(fv%d.field_value, '%%Y-%%m-%%d %%H:%%i:%%s') < '%s'", 
                            fieldId, beforeStr);
                    
                case BETWEEN:
                    DateRange range = parseDateRange(value);
                    if (range.getStart() != null && range.getEnd() != null) {
                        String startStr = formatDateTimeForSql(range.getStart());
                        String endStr = formatDateTimeForSql(range.getEnd());
                        return String.format("STR_TO_DATE(fv%d.field_value, '%%Y-%%m-%%d %%H:%%i:%%s') BETWEEN '%s' AND '%s'", 
                                fieldId, startStr, endStr);
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
     * 日期范围类
     */
    public static class DateRange {
        private final LocalDateTime start;
        private final LocalDateTime end;

        public DateRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

        public LocalDateTime getStart() { return start; }
        public LocalDateTime getEnd() { return end; }

        @Override
        public String toString() {
            return String.format("[%s, %s]", start, end);
        }
    }
}