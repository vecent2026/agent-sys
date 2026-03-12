package com.trae.user.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trae.user.entity.AppUser;
import com.trae.user.enums.FieldType;
import com.trae.user.enums.FilterOperator;

import java.util.List;

/**
 * 字段筛选处理器接口 - 统一筛选系统核心接口
 * 
 * 每个字段类型都有对应的处理器实现，负责将筛选条件转换为SQL查询
 * 
 * @author Unified Filter System
 * @version 2.0
 */
public interface FieldFilterHandler {
    
    /**
     * 获取支持的字段类型
     */
    FieldType getSupportedFieldType();
    
    /**
     * 获取支持的操作符列表
     */
    List<FilterOperator> getSupportedOperators();
    
    /**
     * 应用筛选条件到查询构建器
     * 
     * @param wrapper 查询构建器
     * @param condition 已验证的筛选条件
     */
    void applyFilter(LambdaQueryWrapper<AppUser> wrapper, ValidatedFilterCondition condition);
    
    /**
     * 验证筛选条件的值是否有效
     * 
     * @param operator 操作符
     * @param value 筛选值
     * @return 验证结果
     */
    FilterValidationResult validateValue(FilterOperator operator, Object value);
    
    /**
     * 估算筛选条件的性能影响 (1-10分，10分为最高性能)
     * 
     * @param condition 筛选条件
     * @return 性能评分
     */
    default int estimatePerformance(ValidatedFilterCondition condition) {
        return 5; // 默认中等性能
    }
    
    /**
     * 检查操作符是否被支持
     */
    default boolean supportsOperator(FilterOperator operator) {
        return getSupportedOperators().contains(operator);
    }
    
    /**
     * 获取处理器的描述信息 (用于调试)
     */
    default String getDescription() {
        return String.format("%s Handler for %s", 
                getSupportedFieldType().getDescription(), 
                getSupportedFieldType().getCode());
    }
}