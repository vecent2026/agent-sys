/**
 * 筛选操作符枚举 - 统一筛选系统
 * 
 * 与后端 FilterOperator 枚举保持一致
 * 定义所有支持的筛选操作符，支持中英文操作符的转换
 * 
 * @version 2.0
 */

export const FilterOperator = {
  // ==================== 相等性操作 ====================
  /** 等于操作符 */
  EQUALS: 'equals',
  /** 不等于操作符 */
  NOT_EQUALS: 'not_equals',
  // ==================== 包含性操作 ====================
  /** 包含操作符 (模糊查询) */
  CONTAINS: 'contains',
  /** 不包含操作符 */
  NOT_CONTAINS: 'not_contains',
  // ==================== 比较操作 ====================
  /** 大于操作符 (数字/日期) */
  GREATER_THAN: 'gt',
  /** 小于操作符 (数字/日期) */
  LESS_THAN: 'lt',
  /** 大于等于操作符 */
  GREATER_THAN_OR_EQUAL: 'gte',
  /** 小于等于操作符 */
  LESS_THAN_OR_EQUAL: 'lte',
  /** 区间操作符 (日期/数字范围) */
  BETWEEN: 'between',
  // ==================== 日期特殊操作 ====================
  /** 晚于操作符 (专用于日期) */
  AFTER: 'after',
  /** 早于操作符 (专用于日期) */
  BEFORE: 'before',
  // ==================== 空值操作 ====================
  /** 为空操作符 */
  IS_EMPTY: 'is_empty',
  /** 不为空操作符 */
  IS_NOT_EMPTY: 'is_not_empty',
  // ==================== 集合操作 ====================
  /** 在集合中操作符 */
  IN: 'in',
  /** 不在集合中操作符 */
  NOT_IN: 'not_in',
  // ==================== 文本特殊操作 ====================
  /** 以...开始 */
  STARTS_WITH: 'starts_with',
  /** 以...结束 */
  ENDS_WITH: 'ends_with',
  // ==================== 正则表达式操作 ====================
  /** 正则表达式匹配 */
  REGEX: 'regex',
} as const;

export type FilterOperator = (typeof FilterOperator)[keyof typeof FilterOperator];

/**
 * 操作符标签映射 (中文显示)
 */
export const FilterOperatorLabels: Record<FilterOperator, string> = {
  [FilterOperator.EQUALS]: '等于',
  [FilterOperator.NOT_EQUALS]: '不等于',
  [FilterOperator.CONTAINS]: '包含',
  [FilterOperator.NOT_CONTAINS]: '不包含',
  [FilterOperator.GREATER_THAN]: '大于',
  [FilterOperator.LESS_THAN]: '小于',
  [FilterOperator.GREATER_THAN_OR_EQUAL]: '大于等于',
  [FilterOperator.LESS_THAN_OR_EQUAL]: '小于等于',
  [FilterOperator.BETWEEN]: '介于',
  [FilterOperator.AFTER]: '晚于',
  [FilterOperator.BEFORE]: '早于',
  [FilterOperator.IS_EMPTY]: '为空',
  [FilterOperator.IS_NOT_EMPTY]: '不为空',
  [FilterOperator.IN]: '在列表中',
  [FilterOperator.NOT_IN]: '不在列表中',
  [FilterOperator.STARTS_WITH]: '以此开始',
  [FilterOperator.ENDS_WITH]: '以此结束',
  [FilterOperator.REGEX]: '正则匹配',
};

/**
 * 操作符 SQL 映射 (用于调试和文档)
 */
export const FilterOperatorSqlMapping: Record<FilterOperator, string> = {
  [FilterOperator.EQUALS]: '=',
  [FilterOperator.NOT_EQUALS]: '!=',
  [FilterOperator.CONTAINS]: 'LIKE',
  [FilterOperator.NOT_CONTAINS]: 'NOT LIKE',
  [FilterOperator.GREATER_THAN]: '>',
  [FilterOperator.LESS_THAN]: '<',
  [FilterOperator.GREATER_THAN_OR_EQUAL]: '>=',
  [FilterOperator.LESS_THAN_OR_EQUAL]: '<=',
  [FilterOperator.BETWEEN]: 'BETWEEN',
  [FilterOperator.AFTER]: '>',
  [FilterOperator.BEFORE]: '<',
  [FilterOperator.IS_EMPTY]: 'IS NULL OR = \'\'',
  [FilterOperator.IS_NOT_EMPTY]: 'IS NOT NULL AND != \'\'',
  [FilterOperator.IN]: 'IN',
  [FilterOperator.NOT_IN]: 'NOT IN',
  [FilterOperator.STARTS_WITH]: 'LIKE \'value%\'',
  [FilterOperator.ENDS_WITH]: 'LIKE \'%value\'',
  [FilterOperator.REGEX]: 'REGEXP',
};

/**
 * 按字段类型分组的操作符
 */
export const OperatorsByFieldType = {
  STRING: [
    FilterOperator.EQUALS,
    FilterOperator.NOT_EQUALS,
    FilterOperator.CONTAINS,
    FilterOperator.NOT_CONTAINS,
    FilterOperator.STARTS_WITH,
    FilterOperator.ENDS_WITH,
    FilterOperator.IS_EMPTY,
    FilterOperator.IS_NOT_EMPTY,
    FilterOperator.IN,
    FilterOperator.NOT_IN,
    FilterOperator.REGEX,
  ],
  NUMBER: [
    FilterOperator.EQUALS,
    FilterOperator.NOT_EQUALS,
    FilterOperator.GREATER_THAN,
    FilterOperator.LESS_THAN,
    FilterOperator.GREATER_THAN_OR_EQUAL,
    FilterOperator.LESS_THAN_OR_EQUAL,
    FilterOperator.BETWEEN,
    FilterOperator.IS_EMPTY,
    FilterOperator.IS_NOT_EMPTY,
    FilterOperator.IN,
    FilterOperator.NOT_IN,
  ],
  DATE: [
    FilterOperator.EQUALS,
    FilterOperator.NOT_EQUALS,
    FilterOperator.AFTER,
    FilterOperator.BEFORE,
    FilterOperator.GREATER_THAN,
    FilterOperator.LESS_THAN,
    FilterOperator.BETWEEN,
    FilterOperator.IS_EMPTY,
    FilterOperator.IS_NOT_EMPTY,
  ],
  ENUM: [
    FilterOperator.EQUALS,
    FilterOperator.NOT_EQUALS,
    FilterOperator.IS_EMPTY,
    FilterOperator.IS_NOT_EMPTY,
    FilterOperator.IN,
    FilterOperator.NOT_IN,
  ],
  TAG: [
    FilterOperator.CONTAINS,
    FilterOperator.NOT_CONTAINS,
    FilterOperator.IS_EMPTY,
    FilterOperator.IS_NOT_EMPTY,
  ],
} as const;

/**
 * 工具函数类
 */
export class FilterOperatorUtils {
  /**
   * 根据代码获取操作符
   */
  static fromCode(code: string): FilterOperator | null {
    return Object.values(FilterOperator).find(op => op === code) || null;
  }
  
  /**
   * 从中文标签获取操作符 (兼容旧系统)
   */
  static fromLabel(label: string): FilterOperator | null {
    const entry = Object.entries(FilterOperatorLabels).find(([_, l]) => l === label);
    return entry ? (entry[0] as FilterOperator) : null;
  }
  
  /**
   * 获取操作符的中文标签
   */
  static getLabel(operator: FilterOperator): string {
    return FilterOperatorLabels[operator] || operator;
  }
  
  /**
   * 获取操作符的 SQL 表示
   */
  static getSql(operator: FilterOperator): string {
    return FilterOperatorSqlMapping[operator] || operator;
  }
  
  /**
   * 获取适用于字符串类型的操作符
   */
  static getStringOperators(): FilterOperator[] {
    return [...OperatorsByFieldType.STRING];
  }
  
  /**
   * 获取适用于数字类型的操作符
   */
  static getNumberOperators(): FilterOperator[] {
    return [...OperatorsByFieldType.NUMBER];
  }
  
  /**
   * 获取适用于日期类型的操作符
   */
  static getDateOperators(): FilterOperator[] {
    return [...OperatorsByFieldType.DATE];
  }
  
  /**
   * 获取适用于枚举类型的操作符
   */
  static getEnumOperators(): FilterOperator[] {
    return [...OperatorsByFieldType.ENUM];
  }
  
  /**
   * 获取适用于标签类型的操作符
   */
  static getTagOperators(): FilterOperator[] {
    return [...OperatorsByFieldType.TAG];
  }
  
  /**
   * 判断操作符是否需要值参数
   */
  static requiresValue(operator: FilterOperator): boolean {
    return !([FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY] as FilterOperator[]).includes(operator);
  }
  
  /**
   * 判断操作符是否支持多值
   */
  static supportsMultipleValues(operator: FilterOperator): boolean {
    return ([FilterOperator.IN, FilterOperator.NOT_IN, FilterOperator.BETWEEN] as FilterOperator[]).includes(operator);
  }
  
  /**
   * 判断操作符是否为比较操作符
   */
  static isComparisonOperator(operator: FilterOperator): boolean {
    return ([
      FilterOperator.GREATER_THAN,
      FilterOperator.LESS_THAN,
      FilterOperator.GREATER_THAN_OR_EQUAL,
      FilterOperator.LESS_THAN_OR_EQUAL,
      FilterOperator.AFTER,
      FilterOperator.BEFORE,
    ] as FilterOperator[]).includes(operator);
  }
  
  /**
   * 判断操作符是否为包含操作符
   */
  static isContainmentOperator(operator: FilterOperator): boolean {
    return ([
      FilterOperator.CONTAINS,
      FilterOperator.NOT_CONTAINS,
      FilterOperator.STARTS_WITH,
      FilterOperator.ENDS_WITH,
      FilterOperator.IN,
      FilterOperator.NOT_IN,
    ] as FilterOperator[]).includes(operator);
  }
  
  /**
   * 判断操作符是否为空值操作符
   */
  static isEmptyOperator(operator: FilterOperator): boolean {
    return ([FilterOperator.IS_EMPTY, FilterOperator.IS_NOT_EMPTY] as FilterOperator[]).includes(operator);
  }
  
  /**
   * 获取操作符的性能影响级别 (1-5, 5为最高性能)
   */
  static getPerformanceLevel(operator: FilterOperator): number {
    switch (operator) {
      case FilterOperator.EQUALS:
      case FilterOperator.NOT_EQUALS:
        return 5; // 最高性能
      case FilterOperator.GREATER_THAN:
      case FilterOperator.LESS_THAN:
      case FilterOperator.GREATER_THAN_OR_EQUAL:
      case FilterOperator.LESS_THAN_OR_EQUAL:
      case FilterOperator.AFTER:
      case FilterOperator.BEFORE:
      case FilterOperator.IS_EMPTY:
      case FilterOperator.IS_NOT_EMPTY:
        return 4; // 高性能
      case FilterOperator.IN:
      case FilterOperator.NOT_IN:
      case FilterOperator.STARTS_WITH:
        return 3; // 中等性能
      case FilterOperator.CONTAINS:
      case FilterOperator.NOT_CONTAINS:
      case FilterOperator.ENDS_WITH:
      case FilterOperator.BETWEEN:
        return 2; // 较低性能
      case FilterOperator.REGEX:
        return 1; // 最低性能
      default:
        return 3;
    }
  }
  
  /**
   * 获取所有操作符选项 (用于下拉选择)
   */
  static getAllOptions(): Array<{ label: string; value: FilterOperator; performance: number }> {
    return Object.values(FilterOperator).map(operator => ({
      label: FilterOperatorLabels[operator],
      value: operator,
      performance: FilterOperatorUtils.getPerformanceLevel(operator),
    }));
  }
  
  /**
   * 根据字段类型获取操作符选项
   */
  static getOperatorsByType(type: 'STRING' | 'NUMBER' | 'DATE' | 'ENUM' | 'TAG'): Array<{ label: string; value: FilterOperator }> {
    const operators = OperatorsByFieldType[type] || [];
    return operators.map(operator => ({
      label: FilterOperatorLabels[operator],
      value: operator,
    }));
  }
}

/**
 * 操作符验证器
 */
export class FilterOperatorValidator {
  /**
   * 验证操作符是否有效
   */
  static isValid(operator: string): operator is FilterOperator {
    return Object.values(FilterOperator).includes(operator as FilterOperator);
  }
  
  /**
   * 验证操作符是否适用于指定的字段类型
   */
  static isValidForFieldType(operator: FilterOperator, fieldType: 'STRING' | 'NUMBER' | 'DATE' | 'ENUM' | 'TAG'): boolean {
    const validOperators = OperatorsByFieldType[fieldType];
    return validOperators ? (validOperators as unknown as FilterOperator[]).includes(operator) : false;
  }
  
  /**
   * 获取操作符的验证规则
   */
  static getValidationRules(operator: FilterOperator): {
    requiresValue: boolean;
    supportsMultiple: boolean;
    expectedValueType?: string;
  } {
    return {
      requiresValue: FilterOperatorUtils.requiresValue(operator),
      supportsMultiple: FilterOperatorUtils.supportsMultipleValues(operator),
      expectedValueType: this.getExpectedValueType(operator),
    };
  }
  
  private static getExpectedValueType(operator: FilterOperator): string | undefined {
    if (FilterOperatorUtils.supportsMultipleValues(operator)) {
      return operator === FilterOperator.BETWEEN ? 'range' : 'array';
    }
    return FilterOperatorUtils.requiresValue(operator) ? 'single' : undefined;
  }
}

export default FilterOperator;