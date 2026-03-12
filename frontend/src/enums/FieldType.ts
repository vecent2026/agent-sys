/**
 * 字段类型枚举 - 统一筛选系统
 * 
 * 与后端 FieldType 枚举保持一致
 * 支持内置字段、动态字段和特殊字段的统一类型定义
 * 
 * @version 2.0
 */

// ==================== 内置字段 ====================
export const FieldType = {
  /** 内置字符串字段 (如: nickname, mobile, email) */
  BUILTIN_STRING: 'builtin_string',
  /** 内置数字字段 (如: age, loginCount) */
  BUILTIN_NUMBER: 'builtin_number',
  /** 内置日期字段 (如: registerTime, lastLoginTime, birthday) */
  BUILTIN_DATE: 'builtin_date',
  /** 内置枚举字段 (如: status, gender, registerSource) */
  BUILTIN_ENUM: 'builtin_enum',
  // ==================== 动态字段 ====================
  /** 动态文本字段 (对应 AppUserField.fieldType = TEXT) */
  CUSTOM_TEXT: 'custom_text',
  /** 动态数字字段 (对应 AppUserField.fieldType = NUMBER) */
  CUSTOM_NUMBER: 'custom_number',
  /** 动态日期字段 (对应 AppUserField.fieldType = DATE) */
  CUSTOM_DATE: 'custom_date',
  /** 动态单选字段 (对应 AppUserField.fieldType = RADIO) */
  CUSTOM_RADIO: 'custom_radio',
  /** 动态复选字段 (对应 AppUserField.fieldType = CHECKBOX) */
  CUSTOM_CHECKBOX: 'custom_checkbox',
  /** 动态链接字段 (对应 AppUserField.fieldType = LINK) */
  CUSTOM_LINK: 'custom_link',
  // ==================== 特殊字段 ====================
  /** 标签级联字段 (标签分类 + 标签选择) */
  TAG_CASCADE: 'tag_cascade',
  /** 关联字段 (用于未来扩展，如关联其他实体) */
  RELATION: 'relation',
} as const;

export type FieldType = (typeof FieldType)[keyof typeof FieldType];

/**
 * 字段类型描述映射
 */
export const FieldTypeDescriptions: Record<FieldType, string> = {
  [FieldType.BUILTIN_STRING]: '内置字符串',
  [FieldType.BUILTIN_NUMBER]: '内置数字',
  [FieldType.BUILTIN_DATE]: '内置日期',
  [FieldType.BUILTIN_ENUM]: '内置枚举',
  [FieldType.CUSTOM_TEXT]: '动态文本',
  [FieldType.CUSTOM_NUMBER]: '动态数字',
  [FieldType.CUSTOM_DATE]: '动态日期',
  [FieldType.CUSTOM_RADIO]: '动态单选',
  [FieldType.CUSTOM_CHECKBOX]: '动态复选',
  [FieldType.CUSTOM_LINK]: '动态链接',
  [FieldType.TAG_CASCADE]: '标签级联',
  [FieldType.RELATION]: '关联字段',
};

/**
 * 字段类型分组
 */
export const FieldTypeGroups = {
  BUILTIN: [
    FieldType.BUILTIN_STRING,
    FieldType.BUILTIN_NUMBER,
    FieldType.BUILTIN_DATE,
    FieldType.BUILTIN_ENUM,
  ],
  CUSTOM: [
    FieldType.CUSTOM_TEXT,
    FieldType.CUSTOM_NUMBER,
    FieldType.CUSTOM_DATE,
    FieldType.CUSTOM_RADIO,
    FieldType.CUSTOM_CHECKBOX,
    FieldType.CUSTOM_LINK,
  ],
  SPECIAL: [
    FieldType.TAG_CASCADE,
    FieldType.RELATION,
  ],
} as const;

/**
 * 工具函数类
 */
export class FieldTypeUtils {
  /**
   * 根据代码获取字段类型
   */
  static fromCode(code: string): FieldType | null {
    return Object.values(FieldType).find(type => type === code) || null;
  }
  
  /**
   * 获取字段类型描述
   */
  static getDescription(fieldType: FieldType): string {
    return FieldTypeDescriptions[fieldType] || fieldType;
  }
  
  /**
   * 判断是否为内置字段类型
   */
  static isBuiltin(fieldType: FieldType): boolean {
    return FieldTypeGroups.BUILTIN.includes(fieldType as any);
  }
  
  /**
   * 判断是否为动态字段类型
   */
  static isCustom(fieldType: FieldType): boolean {
    return FieldTypeGroups.CUSTOM.includes(fieldType as any);
  }
  
  /**
   * 判断是否为特殊字段类型
   */
  static isSpecial(fieldType: FieldType): boolean {
    return FieldTypeGroups.SPECIAL.includes(fieldType as any);
  }
  
  /**
   * 从传统的 AppUserField.fieldType 映射到新的 FieldType
   */
  static fromLegacyFieldType(legacyType: string): FieldType {
    if (!legacyType) return FieldType.CUSTOM_TEXT;
    
    const upperType = legacyType.toUpperCase();
    switch (upperType) {
      case 'TEXT':
        return FieldType.CUSTOM_TEXT;
      case 'NUMBER':
        return FieldType.CUSTOM_NUMBER;
      case 'DATE':
        return FieldType.CUSTOM_DATE;
      case 'RADIO':
        return FieldType.CUSTOM_RADIO;
      case 'CHECKBOX':
        return FieldType.CUSTOM_CHECKBOX;
      case 'LINK':
        return FieldType.CUSTOM_LINK;
      default:
        return FieldType.CUSTOM_TEXT; // 默认为文本类型
    }
  }
  
  /**
   * 获取字段类型的默认值
   */
  static getDefaultValue(fieldType: FieldType): any {
    switch (fieldType) {
      case FieldType.BUILTIN_STRING:
      case FieldType.CUSTOM_TEXT:
      case FieldType.CUSTOM_LINK:
        return '';
      case FieldType.BUILTIN_NUMBER:
      case FieldType.CUSTOM_NUMBER:
        return 0;
      case FieldType.BUILTIN_DATE:
      case FieldType.CUSTOM_DATE:
        return null;
      case FieldType.BUILTIN_ENUM:
      case FieldType.CUSTOM_RADIO:
        return null;
      case FieldType.CUSTOM_CHECKBOX:
        return [];
      case FieldType.TAG_CASCADE:
        return { categoryId: null, tagIds: [] };
      case FieldType.RELATION:
        return null;
      default:
        return null;
    }
  }
  
  /**
   * 判断字段类型是否支持多值
   */
  static supportsMultipleValues(fieldType: FieldType): boolean {
    return ([FieldType.CUSTOM_CHECKBOX, FieldType.TAG_CASCADE] as FieldType[]).includes(fieldType);
  }
  
  /**
   * 获取字段类型的输入组件类型
   */
  static getInputType(fieldType: FieldType): string {
    switch (fieldType) {
      case FieldType.BUILTIN_STRING:
      case FieldType.CUSTOM_TEXT:
        return 'text';
      case FieldType.BUILTIN_NUMBER:
      case FieldType.CUSTOM_NUMBER:
        return 'number';
      case FieldType.BUILTIN_DATE:
      case FieldType.CUSTOM_DATE:
        return 'date';
      case FieldType.BUILTIN_ENUM:
      case FieldType.CUSTOM_RADIO:
        return 'select';
      case FieldType.CUSTOM_CHECKBOX:
        return 'checkbox';
      case FieldType.CUSTOM_LINK:
        return 'url';
      case FieldType.TAG_CASCADE:
        return 'tag-cascade';
      case FieldType.RELATION:
        return 'relation';
      default:
        return 'text';
    }
  }
  
  /**
   * 获取所有字段类型选项 (用于下拉选择)
   */
  static getAllOptions(): Array<{ label: string; value: FieldType; group: string }> {
    const options: Array<{ label: string; value: FieldType; group: string }> = [];
    
    // 内置字段
    FieldTypeGroups.BUILTIN.forEach(type => {
      options.push({
        label: FieldTypeDescriptions[type],
        value: type,
        group: '内置字段',
      });
    });
    
    // 动态字段
    FieldTypeGroups.CUSTOM.forEach(type => {
      options.push({
        label: FieldTypeDescriptions[type],
        value: type,
        group: '动态字段',
      });
    });
    
    // 特殊字段
    FieldTypeGroups.SPECIAL.forEach(type => {
      options.push({
        label: FieldTypeDescriptions[type],
        value: type,
        group: '特殊字段',
      });
    });
    
    return options;
  }
}

/**
 * 字段类型验证器
 */
export class FieldTypeValidator {
  /**
   * 验证字段类型是否有效
   */
  static isValid(fieldType: string): fieldType is FieldType {
    return Object.values(FieldType).includes(fieldType as FieldType);
  }
  
  /**
   * 验证字段类型是否支持指定的操作
   */
  static supportsOperation(_fieldType: FieldType, _operation: string): boolean {
    return true;
  }
}

export default FieldType;