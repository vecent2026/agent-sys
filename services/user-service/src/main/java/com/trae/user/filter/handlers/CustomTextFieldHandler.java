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

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 自定义文本字段处理器 - 处理动态文本字段的筛选
 * 
 * 支持的字段类型：CUSTOM_TEXT、CUSTOM_LINK
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Slf4j
@Component
public class CustomTextFieldHandler extends CustomFieldHandlerBase {

    // 支持的操作符
    private static final List<FilterOperator> SUPPORTED_OPERATORS = Arrays.asList(
            FilterOperator.EQUALS,
            FilterOperator.NOT_EQUALS,
            FilterOperator.CONTAINS,
            FilterOperator.NOT_CONTAINS,
            FilterOperator.STARTS_WITH,
            FilterOperator.ENDS_WITH,
            FilterOperator.REGEX,
            FilterOperator.IS_EMPTY,
            FilterOperator.IS_NOT_EMPTY,
            FilterOperator.IN,
            FilterOperator.NOT_IN
    );

    // URL正则表达式
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?|ftp)://[^\s/$.?#].[^\s]*$",
            Pattern.CASE_INSENSITIVE
    );

    public CustomTextFieldHandler(AppUserFieldService fieldService) {
        super(fieldService);
    }

    @Override
    public FieldType getSupportedFieldType() {
        return FieldType.CUSTOM_TEXT;
    }

    @Override
    public List<FilterOperator> getSupportedOperators() {
        return SUPPORTED_OPERATORS;
    }

    @Override
    protected FilterValidationResult validateFieldSpecificValue(FilterOperator operator, Object value) {
        FilterValidationResult result = FilterValidationResult.success();
        
        if (value == null || value.toString().trim().isEmpty()) {
            return result;
        }

        String stringValue = value.toString().trim();

        // 长度验证
        if (stringValue.length() > 1000) {
            return FilterValidationResult.failure(
                    "文本值过长，最多支持1000个字符",
                    FilterValidationResult.ErrorCodes.VALUE_OUT_OF_RANGE
            );
        }

        // 正则表达式验证
        if (operator == FilterOperator.REGEX) {
            try {
                Pattern.compile(stringValue);
            } catch (Exception e) {
                return FilterValidationResult.failure(
                        "无效的正则表达式: " + e.getMessage(),
                        FilterValidationResult.ErrorCodes.VALUE_INVALID
                );
            }
        }

        // 性能警告
        if (stringValue.length() > 100) {
            result.addWarning("长文本筛选可能影响查询性能");
        }

        if (operator == FilterOperator.CONTAINS && stringValue.length() < 3) {
            result.addWarning("短文本的包含查询可能返回大量结果");
            result.addSuggestion("建议使用更长的搜索词或精确匹配");
        }

        return result;
    }

    @Override
    protected void applySpecialOperator(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, 
                                      FilterOperator operator, Object value) {
        String normalizedValue = normalizeValue(value);
        
        switch (operator) {
            case STARTS_WITH:
                applyStartsWithFilter(wrapper, fieldId, normalizedValue);
                break;
                
            case ENDS_WITH:
                applyEndsWithFilter(wrapper, fieldId, normalizedValue);
                break;
                
            case REGEX:
                applyRegexFilter(wrapper, fieldId, normalizedValue);
                break;
                
            default:
                super.applySpecialOperator(wrapper, fieldId, operator, value);
        }
    }

    @Override
    public int estimatePerformance(ValidatedFilterCondition condition) {
        FilterOperator operator = condition.getOperator();
        Object value = condition.getNormalizedValue();
        
        if (value == null) {
            return 5;
        }
        
        String stringValue = value.toString();
        
        switch (operator) {
            case EQUALS:
            case NOT_EQUALS:
                return 8; // 精确匹配，性能最好
                
            case STARTS_WITH:
                return 7; // 前缀匹配，性能良好
                
            case ENDS_WITH:
                return 4; // 后缀匹配，性能较差
                
            case CONTAINS:
            case NOT_CONTAINS:
                if (stringValue.length() < 3) {
                    return 3; // 短文本包含查询，性能差
                }
                return 6; // 长文本包含查询，性能中等
                
            case REGEX:
                return 2; // 正则表达式，性能最差
                
            default:
                return 6;
        }
    }

    // ==================== 专用筛选方法 ====================

    /**
     * 前缀匹配筛选
     */
    private void applyStartsWithFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, String value) {
        String existsSql = "SELECT 1 FROM app_user_field_value fv " +
                          "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                          " AND fv.field_value LIKE '" + escapeSqlValue(value) + "%'";
        wrapper.exists(existsSql);
        log.debug("Applied STARTS_WITH filter for field {} with value {}", fieldId, value);
    }

    /**
     * 后缀匹配筛选
     */
    private void applyEndsWithFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, String value) {
        String existsSql = "SELECT 1 FROM app_user_field_value fv " +
                          "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                          " AND fv.field_value LIKE '%" + escapeSqlValue(value) + "'";
        wrapper.exists(existsSql);
        log.debug("Applied ENDS_WITH filter for field {} with value {}", fieldId, value);
    }

    /**
     * 正则表达式筛选
     */
    private void applyRegexFilter(LambdaQueryWrapper<AppUser> wrapper, Long fieldId, String pattern) {
        // 使用MySQL的REGEXP函数
        String existsSql = "SELECT 1 FROM app_user_field_value fv " +
                          "WHERE fv.user_id = app_user.id AND fv.field_id = " + fieldId + 
                          " AND fv.field_value REGEXP '" + escapeSqlValue(pattern) + "'";
        wrapper.exists(existsSql);
        log.debug("Applied REGEX filter for field {} with pattern {}", fieldId, pattern);
    }

    // ==================== 链接字段特殊处理 ====================

    /**
     * 检查是否为有效的URL
     */
    public boolean isValidUrl(String url) {
        return url != null && URL_PATTERN.matcher(url).matches();
    }

    /**
     * 链接字段特殊验证
     */
    public FilterValidationResult validateLinkField(FilterOperator operator, Object value) {
        FilterValidationResult result = validateFieldSpecificValue(operator, value);
        
        if (value != null && operator == FilterOperator.EQUALS) {
            String stringValue = value.toString().trim();
            if (!stringValue.isEmpty() && !isValidUrl(stringValue)) {
                result.addWarning("输入的值不是有效的URL格式");
                result.addSuggestion("链接应以 http:// 或 https:// 开头");
            }
        }
        
        return result;
    }

    // ==================== 批量优化支持 ====================

    @Override
    public String getBatchQueryFragment(Long fieldId, FilterOperator operator, Object value) {
        String normalizedValue = normalizeValue(value);
        
        switch (operator) {
            case EQUALS:
                return String.format("fv%d.field_value = '%s'", fieldId, escapeSqlValue(normalizedValue));
                
            case CONTAINS:
                return String.format("fv%d.field_value LIKE '%%%s%%'", fieldId, escapeSqlValue(normalizedValue));
                
            case STARTS_WITH:
                return String.format("fv%d.field_value LIKE '%s%%'", fieldId, escapeSqlValue(normalizedValue));
                
            case ENDS_WITH:
                return String.format("fv%d.field_value LIKE '%%%s'", fieldId, escapeSqlValue(normalizedValue));
                
            default:
                return null; // 不支持批量优化的操作符
        }
    }
    
    /**
     * 检查字段类型是否匹配
     */
    public boolean supportsFieldType(FieldType fieldType) {
        return fieldType == FieldType.CUSTOM_TEXT || fieldType == FieldType.CUSTOM_LINK;
    }
}