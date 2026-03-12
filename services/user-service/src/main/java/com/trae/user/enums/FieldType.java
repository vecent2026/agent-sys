package com.trae.user.enums;

/**
 * 字段类型枚举 - 统一筛选系统
 * 
 * 支持内置字段、动态字段和特殊字段的统一类型定义
 * 
 * @author Unified Filter System
 * @version 2.0
 */
public enum FieldType {
    // ==================== 内置字段 ====================
    /**
     * 内置字符串字段 (如: nickname, mobile, email)
     */
    BUILTIN_STRING("builtin_string", "内置字符串"),
    
    /**
     * 内置数字字段 (如: age, loginCount)
     */
    BUILTIN_NUMBER("builtin_number", "内置数字"),
    
    /**
     * 内置日期字段 (如: registerTime, lastLoginTime, birthday)
     */
    BUILTIN_DATE("builtin_date", "内置日期"),
    
    /**
     * 内置枚举字段 (如: status, gender, registerSource)
     */
    BUILTIN_ENUM("builtin_enum", "内置枚举"),
    
    // ==================== 动态字段 ====================
    /**
     * 动态文本字段 (对应 AppUserField.fieldType = TEXT)
     */
    CUSTOM_TEXT("custom_text", "动态文本"),
    
    /**
     * 动态数字字段 (对应 AppUserField.fieldType = NUMBER)
     */
    CUSTOM_NUMBER("custom_number", "动态数字"),
    
    /**
     * 动态日期字段 (对应 AppUserField.fieldType = DATE)
     */
    CUSTOM_DATE("custom_date", "动态日期"),
    
    /**
     * 动态单选字段 (对应 AppUserField.fieldType = RADIO)
     */
    CUSTOM_RADIO("custom_radio", "动态单选"),
    
    /**
     * 动态复选字段 (对应 AppUserField.fieldType = CHECKBOX)
     */
    CUSTOM_CHECKBOX("custom_checkbox", "动态复选"),
    
    /**
     * 动态链接字段 (对应 AppUserField.fieldType = LINK)
     */
    CUSTOM_LINK("custom_link", "动态链接"),
    
    // ==================== 特殊字段 ====================
    /**
     * 标签级联字段 (标签分类 + 标签选择)
     */
    TAG_CASCADE("tag_cascade", "标签级联"),
    
    /**
     * 关联字段 (用于未来扩展，如关联其他实体)
     */
    RELATION("relation", "关联字段");
    
    private final String code;
    private final String description;
    
    FieldType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据代码获取字段类型
     */
    public static FieldType fromCode(String code) {
        for (FieldType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown field type code: " + code);
    }
    
    /**
     * 判断是否为内置字段类型
     */
    public boolean isBuiltin() {
        return this.name().startsWith("BUILTIN_");
    }
    
    /**
     * 判断是否为动态字段类型
     */
    public boolean isCustom() {
        return this.name().startsWith("CUSTOM_");
    }
    
    /**
     * 判断是否为特殊字段类型
     */
    public boolean isSpecial() {
        return this == TAG_CASCADE || this == RELATION;
    }
    
    /**
     * 从传统的 AppUserField.fieldType 映射到新的 FieldType
     */
    public static FieldType fromLegacyFieldType(String legacyType) {
        if (legacyType == null) return CUSTOM_TEXT;
        
        switch (legacyType.toUpperCase()) {
            case "TEXT":
                return CUSTOM_TEXT;
            case "NUMBER":
                return CUSTOM_NUMBER;
            case "DATE":
                return CUSTOM_DATE;
            case "RADIO":
                return CUSTOM_RADIO;
            case "CHECKBOX":
                return CUSTOM_CHECKBOX;
            case "LINK":
                return CUSTOM_LINK;
            default:
                return CUSTOM_TEXT; // 默认为文本类型
        }
    }
}