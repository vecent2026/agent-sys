package com.trae.user.filter.handlers;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.user.entity.AppUser;
import com.trae.user.enums.FieldType;
import com.trae.user.enums.FilterOperator;
import com.trae.user.filter.FieldFilterHandler;
import com.trae.user.filter.FilterValidationResult;
import com.trae.user.filter.ValidatedFilterCondition;
import com.trae.user.service.AppUserTagRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 标签级联字段处理器
 * 
 * 处理标签筛选，支持分类+标签的级联选择
 * 使用优化的EXISTS子查询替代传统的IN查询，解决性能问题
 * 
 * @author Unified Filter System
 * @version 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagCascadeFieldHandler implements FieldFilterHandler {

    private final AppUserTagRelationService tagRelationService;

    /**
     * 支持的操作符
     */
    private static final List<FilterOperator> SUPPORTED_OPERATORS = Arrays.asList(
            FilterOperator.CONTAINS,        // 包含任一标签
            FilterOperator.NOT_CONTAINS,    // 不包含任一标签
            FilterOperator.IS_EMPTY,        // 没有任何标签
            FilterOperator.IS_NOT_EMPTY     // 有标签
    );

    @Override
    public FieldType getSupportedFieldType() {
        return FieldType.TAG_CASCADE;
    }

    @Override
    public List<FilterOperator> getSupportedOperators() {
        return SUPPORTED_OPERATORS;
    }

    @Override
    public void applyFilter(LambdaQueryWrapper<AppUser> wrapper, ValidatedFilterCondition condition) {
        FilterOperator operator = condition.getOperator();
        Object value = condition.getNormalizedValue();

        log.debug("Applying tag filter - operator: {}, value: {}", operator, value);

        switch (operator) {
            case CONTAINS:
                applyContainsFilter(wrapper, value);
                break;
                
            case NOT_CONTAINS:
                applyNotContainsFilter(wrapper, value);
                break;
                
            case IS_EMPTY:
                applyIsEmptyFilter(wrapper);
                break;
                
            case IS_NOT_EMPTY:
                applyIsNotEmptyFilter(wrapper);
                break;
                
            default:
                throw new UnsupportedOperationException("标签字段不支持操作符: " + operator);
        }
    }

    @Override
    public FilterValidationResult validateValue(FilterOperator operator, Object value) {
        // 空值检查操作符不需要值
        if (operator == FilterOperator.IS_EMPTY || operator == FilterOperator.IS_NOT_EMPTY) {
            return FilterValidationResult.success();
        }

        // 其他操作符需要标签值
        if (value == null) {
            return FilterValidationResult.failure(
                    "操作符 " + operator.getLabel() + " 需要选择标签",
                    FilterValidationResult.ErrorCodes.VALUE_REQUIRED
            );
        }

        // 验证标签级联值格式
        TagCascadeValue tagValue = parseTagCascadeValue(value);
        if (tagValue == null) {
            return FilterValidationResult.failure(
                    "标签值格式错误，需要包含 tagIds 数组",
                    FilterValidationResult.ErrorCodes.VALUE_INVALID
            );
        }

        if (tagValue.getTagIds() == null || tagValue.getTagIds().isEmpty()) {
            return FilterValidationResult.failure(
                    "请至少选择一个标签",
                    FilterValidationResult.ErrorCodes.VALUE_INVALID
            );
        }

        // 验证标签ID的有效性
        for (Long tagId : tagValue.getTagIds()) {
            if (tagId == null || tagId <= 0) {
                return FilterValidationResult.failure(
                        "无效的标签ID: " + tagId,
                        FilterValidationResult.ErrorCodes.VALUE_INVALID
                );
            }
        }

        // 检查标签数量限制
        if (tagValue.getTagIds().size() > 50) {
            return FilterValidationResult.failure(
                    "选择的标签数量过多，最多支持50个标签",
                    FilterValidationResult.ErrorCodes.VALUE_OUT_OF_RANGE
            ).addSuggestion("建议减少标签数量或使用更精确的分类筛选");
        }

        FilterValidationResult result = FilterValidationResult.success();
        
        // 性能建议
        if (tagValue.getTagIds().size() > 10) {
            result.addWarning("选择的标签较多，可能影响查询性能");
            result.addSuggestion("建议使用分类筛选缩小范围");
        }

        return result;
    }

    @Override
    public int estimatePerformance(ValidatedFilterCondition condition) {
        FilterOperator operator = condition.getOperator();
        
        // 基于操作符的性能评分
        switch (operator) {
            case IS_EMPTY:
            case IS_NOT_EMPTY:
                return 8; // 空值检查，性能良好
                
            case CONTAINS:
            case NOT_CONTAINS:
                // 性能取决于标签数量
                TagCascadeValue tagValue = parseTagCascadeValue(condition.getNormalizedValue());
                if (tagValue != null && tagValue.getTagIds() != null) {
                    int tagCount = tagValue.getTagIds().size();
                    if (tagCount <= 3) {
                        return 8; // 少量标签，性能良好
                    } else if (tagCount <= 10) {
                        return 6; // 中等数量标签，性能中等
                    } else {
                        return 4; // 大量标签，性能较低
                    }
                }
                return 6; // 默认中等性能
                
            default:
                return 5;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 应用包含筛选（用户拥有任一指定标签）
     */
    private void applyContainsFilter(LambdaQueryWrapper<AppUser> wrapper, Object value) {
        TagCascadeValue tagValue = parseTagCascadeValue(value);
        if (tagValue == null || tagValue.getTagIds().isEmpty()) {
            log.warn("标签包含筛选值为空，跳过筛选");
            return;
        }

        List<Long> tagIds = tagValue.getTagIds();
        String tagIdStr = tagIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        
        // 使用EXISTS子查询，性能优于IN查询
        String existsSql = "SELECT 1 FROM tenant_user_tag r " +
                          "WHERE r.user_id = app_user.id AND r.tag_id IN (" + tagIdStr + ")";
        
        wrapper.exists(existsSql);
        
        log.debug("Applied tag CONTAINS filter for {} tags", tagIds.size());
    }

    /**
     * 应用不包含筛选（用户不拥有任一指定标签）
     */
    private void applyNotContainsFilter(LambdaQueryWrapper<AppUser> wrapper, Object value) {
        TagCascadeValue tagValue = parseTagCascadeValue(value);
        if (tagValue == null || tagValue.getTagIds().isEmpty()) {
            log.warn("标签不包含筛选值为空，跳过筛选");
            return;
        }

        List<Long> tagIds = tagValue.getTagIds();
        String tagIdStr = tagIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        
        // 使用NOT EXISTS子查询
        String notExistsSql = "SELECT 1 FROM tenant_user_tag r " +
                             "WHERE r.user_id = app_user.id AND r.tag_id IN (" + tagIdStr + ")";
        
        wrapper.notExists(notExistsSql);
        
        log.debug("Applied tag NOT_CONTAINS filter for {} tags", tagIds.size());
    }

    /**
     * 应用为空筛选（用户没有任何标签）
     */
    private void applyIsEmptyFilter(LambdaQueryWrapper<AppUser> wrapper) {
        String notExistsSql = "SELECT 1 FROM tenant_user_tag r WHERE r.user_id = app_user.id";
        wrapper.notExists(notExistsSql);
        
        log.debug("Applied tag IS_EMPTY filter");
    }

    /**
     * 应用不为空筛选（用户有至少一个标签）
     */
    private void applyIsNotEmptyFilter(LambdaQueryWrapper<AppUser> wrapper) {
        String existsSql = "SELECT 1 FROM tenant_user_tag r WHERE r.user_id = app_user.id";
        wrapper.exists(existsSql);
        
        log.debug("Applied tag IS_NOT_EMPTY filter");
    }

    /**
     * 解析标签级联值
     */
    private TagCascadeValue parseTagCascadeValue(Object value) {
        if (value == null) {
            return null;
        }

        try {
            if (value instanceof TagCascadeValue) {
                return (TagCascadeValue) value;
            }

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                
                TagCascadeValue tagValue = new TagCascadeValue();
                
                // 解析categoryId
                Object categoryIdObj = map.get("categoryId");
                if (categoryIdObj instanceof Number) {
                    tagValue.setCategoryId(((Number) categoryIdObj).longValue());
                }
                
                // 解析tagIds
                Object tagIdsObj = map.get("tagIds");
                if (tagIdsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> tagIdsList = (List<Object>) tagIdsObj;
                    List<Long> tagIds = tagIdsList.stream()
                            .filter(Objects::nonNull)
                            .map(obj -> {
                                if (obj instanceof Number) {
                                    return ((Number) obj).longValue();
                                } else if (obj instanceof String) {
                                    try {
                                        return Long.parseLong((String) obj);
                                    } catch (NumberFormatException e) {
                                        log.warn("Invalid tag ID format: {}", obj);
                                        return null;
                                    }
                                }
                                return null;
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    
                    tagValue.setTagIds(tagIds);
                }
                
                return tagValue;
            }

            log.warn("Unsupported tag value type: {}", value.getClass().getName());
            return null;
            
        } catch (Exception e) {
            log.error("Failed to parse tag cascade value: {}", value, e);
            return null;
        }
    }

    /**
     * 标签级联值内部类
     */
    public static class TagCascadeValue {
        private Long categoryId;
        private List<Long> tagIds;

        public TagCascadeValue() {
            this.tagIds = new ArrayList<>();
        }

        public Long getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(Long categoryId) {
            this.categoryId = categoryId;
        }

        public List<Long> getTagIds() {
            return tagIds;
        }

        public void setTagIds(List<Long> tagIds) {
            this.tagIds = tagIds != null ? tagIds : new ArrayList<>();
        }

        @Override
        public String toString() {
            return String.format("TagCascadeValue{categoryId=%s, tagIds=%s}", categoryId, tagIds);
        }
    }

    /**
     * 检查是否支持指定字段
     */
    public static boolean isSupported(String fieldKey) {
        return "tags".equals(fieldKey);
    }

    /**
     * 获取支持的字段列表
     */
    public static List<String> getSupportedFields() {
        return Collections.singletonList("tags");
    }
}