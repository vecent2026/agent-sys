package com.trae.user.dto;

import com.trae.user.enums.FilterOperator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 标签筛选请求DTO - 专用于标签筛选功能测试
 * 
 * 提供简化的标签筛选接口，用于验证新的标签筛选逻辑
 * 
 * @author Unified Filter System  
 * @version 2.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagFilterRequest {
    
    /**
     * 标签分类ID（可选，用于按分类筛选）
     */
    private Long categoryId;
    
    /**
     * 标签ID列表
     */
    @NotEmpty(message = "标签ID列表不能为空")
    @Size(max = 50, message = "最多支持50个标签")
    private List<@NotNull @Positive Long> tagIds;
    
    /**
     * 筛选操作符
     */
    @NotNull(message = "操作符不能为空")
    @Builder.Default
    private FilterOperator operator = FilterOperator.CONTAINS;
    
    /**
     * 页码
     */
    @Min(value = 1, message = "页码必须大于0")
    @Builder.Default
    private Integer page = 1;
    
    /**
     * 每页大小  
     */
    @Min(value = 1, message = "每页大小必须大于0")
    @Max(value = 100, message = "每页大小不能超过100")
    @Builder.Default
    private Integer size = 10;
    
    /**
     * 是否包含标签信息
     */
    @Builder.Default
    private Boolean includeTagInfo = true;
    
    /**
     * 是否启用性能监控
     */
    @Builder.Default
    private Boolean enablePerformanceMonitoring = false;
    
    /**
     * 验证请求是否有效
     */
    public boolean isValid() {
        if (tagIds == null || tagIds.isEmpty()) {
            return false;
        }
        
        if (operator == null) {
            return false;
        }
        
        // 检查标签ID有效性
        for (Long tagId : tagIds) {
            if (tagId == null || tagId <= 0) {
                return false;
            }
        }
        
        // 检查分页参数
        if (page == null || page < 1 || size == null || size < 1 || size > 100) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 获取摘要信息
     */
    public String getSummary() {
        return String.format("TagFilter{categoryId=%s, tagIds=%d, operator=%s, page=%d/%d}", 
                categoryId, 
                tagIds != null ? tagIds.size() : 0, 
                operator, 
                page, 
                size);
    }
    
    /**
     * 创建简单的标签筛选请求
     */
    public static TagFilterRequest simple(List<Long> tagIds) {
        return TagFilterRequest.builder()
                .tagIds(tagIds)
                .operator(FilterOperator.CONTAINS)
                .page(1)
                .size(10)
                .build();
    }
    
    /**
     * 创建分类筛选请求
     */
    public static TagFilterRequest withCategory(Long categoryId, List<Long> tagIds) {
        return TagFilterRequest.builder()
                .categoryId(categoryId)
                .tagIds(tagIds)
                .operator(FilterOperator.CONTAINS)
                .page(1)
                .size(10)
                .build();
    }
    
    /**
     * 创建排除标签的筛选请求
     */
    public static TagFilterRequest exclude(List<Long> tagIds) {
        return TagFilterRequest.builder()
                .tagIds(tagIds)
                .operator(FilterOperator.NOT_CONTAINS)
                .page(1)
                .size(10)
                .build();
    }
}