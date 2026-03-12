package com.trae.user.filter.handlers;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.user.entity.AppUser;
import com.trae.user.entity.AppUserField;
import com.trae.user.entity.AppUserFieldValue;
import com.trae.user.enums.FilterOperator;
import com.trae.user.filter.FieldFilterHandler;
import com.trae.user.filter.FilterValidationResult;
import com.trae.user.filter.ValidatedFilterCondition;
import com.trae.user.service.AppUserFieldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 动态字段处理器基类 - 解决原有的N+1查询问题和"为空"逻辑错误
 * 
 * 主要改进：
 * 1. 正确的"为空"逻辑：包括没有记录的用户和有记录但值为空的用户
 * 2. 使用EXISTS/NOT EXISTS子查询优化性能
 * 3. 提供不同字段类型的专用处理逻辑
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Slf4j
@RequiredArgsConstructor
public abstract class CustomFieldHandlerBase implements FieldFilterHandler {

    protected final AppUserFieldService fieldService;

    /**
     * 通用操作符支持列表 - 子类可重写以提供特定操作符
     */
    protected static final List<FilterOperator> COMMON_OPERATORS = Arrays.asList(
            FilterOperator.EQUALS,
            FilterOperator.NOT_EQUALS,
            FilterOperator.CONTAINS,
            FilterOperator.NOT_CONTAINS,
            FilterOperator.IS_EMPTY,
            FilterOperator.IS_NOT_EMPTY,
            FilterOperator.IN,
            FilterOperator.NOT_IN
    );

    @Override
    public void applyFilter(LambdaQueryWrapper<AppUser> wrapper, ValidatedFilterCondition condition) {
        FilterOperator operator = condition.getOperator();
        Object value = condition.getNormalizedValue();
        String fieldKey = condition.getFieldKey();
        
        log.debug("Applying custom field filter - field: {}, operator: {}, value: {}", 
                fieldKey, operator, value);

        // 获取字段定义
        AppUserField field = getFieldDefinition(fieldKey);
        if (field == null) {
            log.warn("Custom field not found: {}", fieldKey);
            return;
        }

        try {
            switch (operator) {
                case EQUALS:
                    applyEqualsFilter(wrapper, field.getId(), value);
                    break;
                    
                case NOT_EQUALS:
                    applyNotEqualsFilter(wrapper, field.getId(), value);
                    break;
                    
                case CONTAINS:
                    applyContainsFilter(wrapper, field.getId(), value);
                    break;
                    
                case NOT_CONTAINS:
                    applyNotContainsFilter(wrapper, field.getId(), value);
                    break;
                    
                case IS_EMPTY:
                    applyIsEmptyFilter(wrapper, field.getId());
                    break;
                    
                case IS_NOT_EMPTY:
                    applyIsNotEmptyFilter(wrapper, field.getId());
                    break;
                    
                case IN:
                    applyInFilter(wrapper, field.getId(), value);
                    break;
                    
                case NOT_IN:
                    applyNotInFilter(wrapper, field.getId(), value);
                    break;
                    
                default:
                    // 委托给子类处理特殊操作符
                    applySpecialOperator(wrapper, field.getId(), operator, value);
            }
            
        } catch (Exception e) {
            log.error("Failed to apply custom field filter: field={}, operator={}, value={}", 
                    fieldKey, operator, value, e);
            // 不抛出异常，避免影响其他筛选条件
        }
    }

    @Override
    public FilterValidationResult validateValue(FilterOperator operator, Object value) {
        // 空值检查操作符不需要值
        if (operator == FilterOperator.IS_EMPTY || operator == FilterOperator.IS_NOT_EMPTY) {
            return FilterValidationResult.success();
        }

        // 其他操作符需要值
        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
            return FilterValidationResult.failure(
                    "操作符 " + operator.getLabel() + " 需要输入筛选值",
                    FilterValidationResult.ErrorCodes.VALUE_REQUIRED
            );
        }

        // 委托给子类进行特定验证
        return validateFieldSpecificValue(operator, value);
    }

    @Override
    public int estimatePerformance(ValidatedFilterCondition condition) {
        FilterOperator operator = condition.getOperator();
        
        // 基于操作符的性能评分
        switch (operator) {
            case IS_EMPTY:
            case IS_NOT_EMPTY:
                return 7; // EXISTS子查询，性能良好
                
            case EQUALS:
            case NOT_EQUALS:
                return 8; // 精确匹配，性能最好
                
            case CONTAINS:
            case NOT_CONTAINS:
                return 5; // LIKE查询，性能中等
                
            case IN:
            case NOT_IN:
                Object value = condition.getNormalizedValue();
                if (value instanceof Collection) {
                    int size = ((Collection<?>) value).size();
                    if (size > 50) return 3; // 大量值，性能较差
                    if (size > 10) return 5; // 中等数量，性能中等
                }
                return 7; // 少量值，性能良好
                
            default:
                return 6; // 默认中等性能
        }
    }

    // ==================== 核心筛选方法 ====================

    /**
     * 等于筛选 - 使用EXISTS子查询
     */
    protected void applyEqualsFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, Object value) {
        String normalizedValue = normalizeValue(value);
        String existsSql = "SELECT 1 FROM app_user_field_value fv " +
                          "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                          " AND fv.field_value = '" + escapeSqlValue(normalizedValue) + "'";
        wrapper.exists(existsSql);
        log.debug("Applied EQUALS filter for field {} with value {}", fieldId, normalizedValue);
    }

    /**
     * 不等于筛选 - 使用NOT EXISTS子查询
     */
    protected void applyNotEqualsFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, Object value) {
        String normalizedValue = normalizeValue(value);
        
        // 不等于逻辑：没有该值的记录 OR 有其他值的记录
        wrapper.and(w -> w
            // 没有该值的记录
            .notExists("SELECT 1 FROM app_user_field_value fv " +
                      "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                      " AND fv.field_value = '" + escapeSqlValue(normalizedValue) + "'")
            .and(
                // 但必须有该字段的其他记录（避免包含完全没有该字段的用户）
                w2 -> w2.exists("SELECT 1 FROM app_user_field_value fv " +
                               "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId)
            )
        );
        log.debug("Applied NOT_EQUALS filter for field {} with value {}", fieldId, normalizedValue);
    }

    /**
     * 包含筛选 - 使用LIKE查询
     */
    protected void applyContainsFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, Object value) {
        String normalizedValue = normalizeValue(value);
        String existsSql = "SELECT 1 FROM app_user_field_value fv " +
                          "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                          " AND fv.field_value LIKE '%" + escapeSqlValue(normalizedValue) + "%'";
        wrapper.exists(existsSql);
        log.debug("Applied CONTAINS filter for field {} with value {}", fieldId, normalizedValue);
    }

    /**
     * 不包含筛选
     */
    protected void applyNotContainsFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, Object value) {
        String normalizedValue = normalizeValue(value);
        String notExistsSql = "SELECT 1 FROM app_user_field_value fv " +
                             "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                             " AND fv.field_value LIKE '%" + escapeSqlValue(normalizedValue) + "%'";
        wrapper.notExists(notExistsSql);
        log.debug("Applied NOT_CONTAINS filter for field {} with value {}", fieldId, normalizedValue);
    }

    /**
     * 为空筛选 - 修复原有逻辑错误
     * 正确逻辑：没有记录 OR 有记录但值为空/null
     */
    protected void applyIsEmptyFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId) {
        wrapper.and(w -> w
            // 情况1：完全没有该字段的记录
            .notExists("SELECT 1 FROM app_user_field_value fv " +
                      "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId)
            .or(
                // 情况2：有记录但值为空或null
                w2 -> w2.exists("SELECT 1 FROM app_user_field_value fv " +
                               "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                               " AND (fv.field_value IS NULL OR fv.field_value = '')")
            )
        );
        log.debug("Applied IS_EMPTY filter for field {}", fieldId);
    }

    /**
     * 不为空筛选
     */
    protected void applyIsNotEmptyFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId) {
        String existsSql = "SELECT 1 FROM app_user_field_value fv " +
                          "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                          " AND fv.field_value IS NOT NULL AND fv.field_value <> ''";
        wrapper.exists(existsSql);
        log.debug("Applied IS_NOT_EMPTY filter for field {}", fieldId);
    }

    /**
     * IN筛选 - 支持多个值
     */
    protected void applyInFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, Object value) {
        Collection<String> values = parseMultipleValues(value);
        if (values.isEmpty()) {
            log.warn("IN filter with empty values for field {}", fieldId);
            return;
        }
        
        String valuesStr = values.stream()
                .map(this::escapeSqlValue)
                .map(v -> "'" + v + "'")
                .collect(Collectors.joining(","));
        
        String existsSql = "SELECT 1 FROM app_user_field_value fv " +
                          "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                          " AND fv.field_value IN (" + valuesStr + ")";
        wrapper.exists(existsSql);
        log.debug("Applied IN filter for field {} with {} values", fieldId, values.size());
    }

    /**
     * NOT IN筛选
     */
    protected void applyNotInFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, Object value) {
        Collection<String> values = parseMultipleValues(value);
        if (values.isEmpty()) {
            log.warn("NOT_IN filter with empty values for field {}", fieldId);
            return;
        }
        
        String valuesStr = values.stream()
                .map(this::escapeSqlValue)
                .map(v -> "'" + v + "'")
                .collect(Collectors.joining(","));
        
        String notExistsSql = "SELECT 1 FROM app_user_field_value fv " +
                             "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                             " AND fv.field_value IN (" + valuesStr + ")";
        wrapper.notExists(notExistsSql);
        log.debug("Applied NOT_IN filter for field {} with {} values", fieldId, values.size());
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取字段定义（带缓存）
     */
    protected AppUserField getFieldDefinition(String fieldKey) {
        // TODO: 添加缓存机制以提高性能
        return fieldService.lambdaQuery()
                .eq(AppUserField::getFieldKey, fieldKey)
                .eq(AppUserField::getStatus, 1) // 只查询启用的字段
                .one();
    }

    /**
     * 规范化值 - 子类可重写以提供特定转换
     */
    protected String normalizeValue(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }

    /**
     * 解析多个值（用于IN/NOT_IN操作）
     */
    protected Collection<String> parseMultipleValues(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        
        if (value instanceof Collection) {
            return ((Collection<?>) value).stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        
        if (value instanceof String) {
            String str = (String) value;
            return Arrays.stream(str.split("[,;，；]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        
        return Collections.singletonList(value.toString().trim());
    }

    /**
     * SQL值转义，防止SQL注入
     */
    protected String escapeSqlValue(String value) {
        if (value == null) {
            return "";
        }
        // 基础的SQL转义，实际应用中应使用更完善的转义机制
        return value.replace("'", "''").replace("\\", "\\\\");
    }

    // ==================== 抽象方法 - 子类必须实现 ====================

    /**
     * 子类特定的值验证
     */
    protected abstract FilterValidationResult validateFieldSpecificValue(FilterOperator operator, Object value);

    /**
     * 处理特殊操作符（子类特有的操作符）
     */
    protected void applySpecialOperator(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, 
                                      FilterOperator operator, Object value) {
        log.warn("Unsupported operator {} for custom field {}", operator, fieldId);
    }

    // ==================== 性能优化支持 ====================

    /**
     * 检查是否可以批量优化
     */
    public boolean supportsBatchOptimization() {
        return true;
    }

    /**
     * 获取批量查询的SQL片段
     */
    public String getBatchQueryFragment(Long fieldId, FilterOperator operator, Object value) {
        // 为批量查询优化提供SQL片段
        // 在批量优化器中使用
        return null;
    }
}