package com.trae.user.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 筛选操作符枚举 - 统一筛选系统
 * 
 * 定义所有支持的筛选操作符，支持中英文操作符的转换
 * 
 * @author Unified Filter System
 * @version 2.0
 */
public enum FilterOperator {
    // ==================== 相等性操作 ====================
    /**
     * 等于操作符
     */
    EQUALS("equals", "等于", "="),
    
    /**
     * 不等于操作符
     */
    NOT_EQUALS("not_equals", "不等于", "!="),
    
    // ==================== 包含性操作 ====================
    /**
     * 包含操作符 (模糊查询)
     */
    CONTAINS("contains", "包含", "LIKE"),
    
    /**
     * 不包含操作符
     */
    NOT_CONTAINS("not_contains", "不包含", "NOT LIKE"),
    
    // ==================== 比较操作 ====================
    /**
     * 大于操作符 (数字/日期)
     */
    GREATER_THAN("gt", "大于", ">"),
    
    /**
     * 小于操作符 (数字/日期)
     */
    LESS_THAN("lt", "小于", "<"),
    
    /**
     * 大于等于操作符
     */
    GREATER_THAN_OR_EQUAL("gte", "大于等于", ">="),
    
    /**
     * 小于等于操作符
     */
    LESS_THAN_OR_EQUAL("lte", "小于等于", "<="),
    
    /**
     * 区间操作符 (日期/数字范围)
     */
    BETWEEN("between", "介于", "BETWEEN"),
    
    // ==================== 日期特殊操作 ====================
    /**
     * 晚于操作符 (专用于日期)
     */
    AFTER("after", "晚于", ">"),
    
    /**
     * 早于操作符 (专用于日期)
     */
    BEFORE("before", "早于", "<"),
    
    // ==================== 空值操作 ====================
    /**
     * 为空操作符
     */
    IS_EMPTY("is_empty", "为空", "IS NULL OR = ''"),
    
    /**
     * 不为空操作符
     */
    IS_NOT_EMPTY("is_not_empty", "不为空", "IS NOT NULL AND != ''"),
    
    // ==================== 集合操作 ====================
    /**
     * 在集合中操作符
     */
    IN("in", "在列表中", "IN"),
    
    /**
     * 不在集合中操作符
     */
    NOT_IN("not_in", "不在列表中", "NOT IN"),
    
    // ==================== 文本特殊操作 ====================
    /**
     * 以...开始
     */
    STARTS_WITH("starts_with", "以此开始", "LIKE 'value%'"),
    
    /**
     * 以...结束
     */
    ENDS_WITH("ends_with", "以此结束", "LIKE '%value'"),
    
    // ==================== 正则表达式操作 ====================
    /**
     * 正则表达式匹配
     */
    REGEX("regex", "正则匹配", "REGEXP"),
    
    // ==================== 多选集合操作 ====================
    /**
     * 全部包含操作符 (多选字段必须包含所有选项)
     */
    CONTAINS_ALL("contains_all", "全部包含", "FIND_IN_SET ALL");
    
    private final String code;
    private final String label;
    private final String sqlOperator;
    
    FilterOperator(String code, String label, String sqlOperator) {
        this.code = code;
        this.label = label;
        this.sqlOperator = sqlOperator;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getLabel() {
        return label;
    }
    
    public String getSqlOperator() {
        return sqlOperator;
    }
    
    /**
     * 根据代码获取操作符
     */
    public static FilterOperator fromCode(String code) {
        for (FilterOperator operator : values()) {
            if (operator.code.equals(code)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("Unknown operator code: " + code);
    }
    
    /**
     * 从中文标签获取操作符 (兼容旧系统)
     */
    public static FilterOperator fromLabel(String label) {
        for (FilterOperator operator : values()) {
            if (operator.label.equals(label)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("Unknown operator label: " + label);
    }
    
    /**
     * 获取适用于字符串类型的操作符
     */
    public static List<FilterOperator> getStringOperators() {
        return Arrays.asList(
            EQUALS, NOT_EQUALS,
            CONTAINS, NOT_CONTAINS,
            STARTS_WITH, ENDS_WITH,
            IS_EMPTY, IS_NOT_EMPTY,
            IN, NOT_IN,
            REGEX
        );
    }
    
    /**
     * 获取适用于数字类型的操作符
     */
    public static List<FilterOperator> getNumberOperators() {
        return Arrays.asList(
            EQUALS, NOT_EQUALS,
            GREATER_THAN, LESS_THAN,
            GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL,
            BETWEEN,
            IS_EMPTY, IS_NOT_EMPTY,
            IN, NOT_IN
        );
    }
    
    /**
     * 获取适用于日期类型的操作符
     */
    public static List<FilterOperator> getDateOperators() {
        return Arrays.asList(
            EQUALS, NOT_EQUALS,
            AFTER, BEFORE,
            GREATER_THAN, LESS_THAN,
            BETWEEN,
            IS_EMPTY, IS_NOT_EMPTY
        );
    }
    
    /**
     * 获取适用于枚举类型的操作符
     */
    public static List<FilterOperator> getEnumOperators() {
        return Arrays.asList(
            EQUALS, NOT_EQUALS,
            IS_EMPTY, IS_NOT_EMPTY,
            IN, NOT_IN
        );
    }
    
    /**
     * 获取适用于标签类型的操作符
     */
    public static List<FilterOperator> getTagOperators() {
        return Arrays.asList(
            CONTAINS, NOT_CONTAINS,
            IS_EMPTY, IS_NOT_EMPTY
        );
    }
    
    /**
     * 判断操作符是否需要值参数
     */
    public boolean requiresValue() {
        return this != IS_EMPTY && this != IS_NOT_EMPTY;
    }
    
    /**
     * 判断操作符是否支持多值
     */
    public boolean supportsMultipleValues() {
        return this == IN || this == NOT_IN || this == BETWEEN;
    }
}