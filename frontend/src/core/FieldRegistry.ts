/**
 * 字段注册表 - 统一管理所有字段的元信息
 * 
 * 负责注册和管理内置字段、动态字段和特殊字段
 * 
 * @version 2.0
 */

import { FieldType, FieldTypeUtils } from '@/enums/FieldType';
import { FilterOperator, FilterOperatorUtils } from '@/enums/FilterOperator';
import type { 
  FieldDefinition, 
  FilterCondition,
  EnumOption,
  FieldRegistry as IFieldRegistry
} from '@/types/unified-filter';
import { createDefaultFilterCondition } from '@/types/unified-filter';

/**
 * 字段注册表实现
 */
export class FieldRegistry implements IFieldRegistry {
  private fields = new Map<string, FieldDefinition>();
  private fieldsByType = new Map<FieldType, FieldDefinition[]>();

  constructor() {
    this.initializeBuiltinFields();
  }

  /**
   * 初始化内置字段定义
   */
  private initializeBuiltinFields(): void {
    // 内置字符串字段
    this.registerBuiltinStringFields();
    
    // 内置枚举字段
    this.registerBuiltinEnumFields();
    
    // 内置日期字段
    this.registerBuiltinDateFields();
    
    // 内置数字字段
    this.registerBuiltinNumberFields();
    
    // 特殊字段
    this.registerSpecialFields();
    
    console.log(`FieldRegistry initialized with ${this.fields.size} builtin fields`);
  }

  /**
   * 注册内置字符串字段
   */
  private registerBuiltinStringFields(): void {
    const stringFields = [
      { key: 'nickname', name: '昵称' },
      { key: 'mobile', name: '手机号' },
      { key: 'email', name: '邮箱' },
      { key: 'registerSource', name: '注册来源' },
      { key: 'lastLoginIp', name: '最后登录IP' },
    ];

    stringFields.forEach(({ key, name }) => {
      this.register({
        fieldKey: key,
        fieldName: name,
        fieldType: FieldType.BUILTIN_STRING,
        isDefault: true,
        operators: FilterOperatorUtils.getStringOperators(),
        validator: this.createStringValidator(),
      });
    });
  }

  /**
   * 注册内置枚举字段
   */
  private registerBuiltinEnumFields(): void {
    // 状态字段
    this.register({
      fieldKey: 'status',
      fieldName: '状态',
      fieldType: FieldType.BUILTIN_ENUM,
      isDefault: true,
      operators: FilterOperatorUtils.getEnumOperators(),
      options: [
        { label: '正常', value: 1, color: '#52c41a' },
        { label: '禁用', value: 0, color: '#faad14' },
        { label: '注销', value: 2, color: '#f5222d' },
      ],
      validator: this.createEnumValidator(['正常', '禁用', '注销']),
    });

    // 性别字段
    this.register({
      fieldKey: 'gender',
      fieldName: '性别',
      fieldType: FieldType.BUILTIN_ENUM,
      isDefault: true,
      operators: FilterOperatorUtils.getEnumOperators(),
      options: [
        { label: '未知', value: 0 },
        { label: '男', value: 1 },
        { label: '女', value: 2 },
      ],
      validator: this.createEnumValidator(['未知', '男', '女']),
    });
  }

  /**
   * 注册内置日期字段
   */
  private registerBuiltinDateFields(): void {
    const dateFields = [
      { key: 'registerTime', name: '注册时间' },
      { key: 'lastLoginTime', name: '最后登录时间' },
      { key: 'birthday', name: '生日' },
    ];

    dateFields.forEach(({ key, name }) => {
      this.register({
        fieldKey: key,
        fieldName: name,
        fieldType: FieldType.BUILTIN_DATE,
        isDefault: true,
        operators: FilterOperatorUtils.getDateOperators(),
        validator: this.createDateValidator(),
      });
    });
  }

  /**
   * 注册内置数字字段
   */
  private registerBuiltinNumberFields(): void {
    const numberFields = [
      { key: 'age', name: '年龄' },
      { key: 'loginCount', name: '登录次数' },
      { key: 'id', name: 'ID' },
    ];

    numberFields.forEach(({ key, name }) => {
      this.register({
        fieldKey: key,
        fieldName: name,
        fieldType: FieldType.BUILTIN_NUMBER,
        isDefault: true,
        operators: FilterOperatorUtils.getNumberOperators(),
        validator: this.createNumberValidator(),
      });
    });
  }

  /**
   * 注册特殊字段
   */
  private registerSpecialFields(): void {
    // 标签字段
    this.register({
      fieldKey: 'tags',
      fieldName: '标签',
      fieldType: FieldType.TAG_CASCADE,
      isDefault: true,
      operators: FilterOperatorUtils.getTagOperators(),
      validator: this.createTagValidator(),
    });
  }

  /**
   * 注册字段定义
   */
  register(definition: FieldDefinition): void {
    this.validateFieldDefinition(definition);
    
    this.fields.set(definition.fieldKey, definition);
    
    // 按类型分组
    const typeFields = this.fieldsByType.get(definition.fieldType) || [];
    typeFields.push(definition);
    this.fieldsByType.set(definition.fieldType, typeFields);
    
    console.debug(`Registered field: ${definition.fieldKey} (${definition.fieldType})`);
  }

  /**
   * 注册动态字段列表
   */
  registerCustomFields(customFields: Array<{
    fieldKey: string;
    fieldName: string;
    fieldType: string;
    config?: any;
  }>): void {
    customFields.forEach(field => {
      const fieldType = FieldTypeUtils.fromLegacyFieldType(field.fieldType);
      
      let options: EnumOption[] | undefined;
      if ((fieldType === FieldType.CUSTOM_RADIO || fieldType === FieldType.CUSTOM_CHECKBOX) 
          && field.config?.options) {
        try {
          const configOptions = typeof field.config.options === 'string' 
            ? JSON.parse(field.config.options) 
            : field.config.options;
          options = configOptions.map((opt: any) => ({
            label: opt.label || opt.value,
            value: opt.value,
            color: opt.color,
          }));
        } catch (e) {
          console.warn(`Failed to parse options for field ${field.fieldKey}:`, e);
          options = [];
        }
      }

      const definition: FieldDefinition = {
        fieldKey: field.fieldKey,
        fieldName: field.fieldName,
        fieldType,
        isDefault: false,
        operators: this.getOperatorsForFieldType(fieldType),
        options,
        validator: this.createValidatorForFieldType(fieldType, options),
        config: field.config,
      };

      this.register(definition);
    });

    console.log(`Registered ${customFields.length} custom fields`);
  }

  /**
   * 获取字段定义
   */
  get(fieldKey: string): FieldDefinition | undefined {
    return this.fields.get(fieldKey);
  }

  /**
   * 获取所有字段定义
   */
  getAll(): FieldDefinition[] {
    return Array.from(this.fields.values());
  }

  /**
   * 检查字段是否被支持
   */
  isSupported(fieldKey: string): boolean {
    return this.fields.has(fieldKey);
  }

  /**
   * 获取字段支持的操作符
   */
  getSupportedOperators(fieldKey: string): FilterOperator[] {
    const field = this.get(fieldKey);
    return field ? field.operators : [];
  }

  /**
   * 根据字段类型获取字段列表
   */
  getFieldsByType(fieldType: FieldType): FieldDefinition[] {
    return this.fieldsByType.get(fieldType) || [];
  }

  /**
   * 获取内置字段列表
   */
  getBuiltinFields(): FieldDefinition[] {
    return this.getAll().filter(field => field.isDefault);
  }

  /**
   * 获取动态字段列表
   */
  getCustomFields(): FieldDefinition[] {
    return this.getAll().filter(field => !field.isDefault);
  }

  /**
   * 清空动态字段（保留内置字段）
   */
  clearCustomFields(): void {
    const builtinFields = this.getBuiltinFields();
    this.fields.clear();
    this.fieldsByType.clear();
    
    builtinFields.forEach(field => this.register(field));
    console.log('Cleared custom fields, kept builtin fields');
  }

  /**
   * 创建默认筛选条件
   */
  createDefaultCondition(fieldKey: string): FilterCondition {
    const field = this.get(fieldKey);
    if (!field) {
      throw new Error(`Unknown field: ${fieldKey}`);
    }

    return createDefaultFilterCondition(fieldKey, field.fieldType);
  }

  /**
   * 获取字段选项列表（用于下拉选择）
   */
  getFieldOptions(): Array<{
    label: string;
    value: string;
    type: FieldType;
    group: string;
  }> {
    const options: Array<{
      label: string;
      value: string;
      type: FieldType;
      group: string;
    }> = [];

    // 内置字段
    this.getBuiltinFields().forEach(field => {
      options.push({
        label: field.fieldName,
        value: field.fieldKey,
        type: field.fieldType,
        group: '内置字段',
      });
    });

    // 动态字段
    this.getCustomFields().forEach(field => {
      options.push({
        label: field.fieldName,
        value: field.fieldKey,
        type: field.fieldType,
        group: '动态字段',
      });
    });

    return options.sort((a, b) => {
      // 内置字段排在前面
      if (a.group !== b.group) {
        return a.group === '内置字段' ? -1 : 1;
      }
      return a.label.localeCompare(b.label);
    });
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 验证字段定义
   */
  private validateFieldDefinition(definition: FieldDefinition): void {
    if (!definition.fieldKey) {
      throw new Error('Field key is required');
    }
    if (!definition.fieldName) {
      throw new Error('Field name is required');
    }
    if (!definition.fieldType) {
      throw new Error('Field type is required');
    }
    if (!definition.operators || definition.operators.length === 0) {
      throw new Error('At least one operator is required');
    }
  }

  /**
   * 根据字段类型获取操作符
   */
  private getOperatorsForFieldType(fieldType: FieldType): FilterOperator[] {
    switch (fieldType) {
      case FieldType.BUILTIN_STRING:
      case FieldType.CUSTOM_TEXT:
      case FieldType.CUSTOM_LINK:
        return FilterOperatorUtils.getStringOperators();
      case FieldType.BUILTIN_NUMBER:
      case FieldType.CUSTOM_NUMBER:
        return FilterOperatorUtils.getNumberOperators();
      case FieldType.BUILTIN_DATE:
      case FieldType.CUSTOM_DATE:
        return FilterOperatorUtils.getDateOperators();
      case FieldType.BUILTIN_ENUM:
      case FieldType.CUSTOM_RADIO:
        return FilterOperatorUtils.getEnumOperators();
      case FieldType.CUSTOM_CHECKBOX:
        return [
          FilterOperator.CONTAINS,
          FilterOperator.NOT_CONTAINS,
          FilterOperator.IS_EMPTY,
          FilterOperator.IS_NOT_EMPTY,
        ];
      case FieldType.TAG_CASCADE:
        return FilterOperatorUtils.getTagOperators();
      default:
        return [FilterOperator.EQUALS, FilterOperator.NOT_EQUALS];
    }
  }

  /**
   * 根据字段类型创建验证器
   */
  private createValidatorForFieldType(fieldType: FieldType, options?: EnumOption[]) {
    switch (fieldType) {
      case FieldType.BUILTIN_STRING:
      case FieldType.CUSTOM_TEXT:
      case FieldType.CUSTOM_LINK:
        return this.createStringValidator();
      case FieldType.BUILTIN_NUMBER:
      case FieldType.CUSTOM_NUMBER:
        return this.createNumberValidator();
      case FieldType.BUILTIN_DATE:
      case FieldType.CUSTOM_DATE:
        return this.createDateValidator();
      case FieldType.BUILTIN_ENUM:
      case FieldType.CUSTOM_RADIO:
      case FieldType.CUSTOM_CHECKBOX:
        return this.createEnumValidator(options?.map(opt => opt.label) || []);
      case FieldType.TAG_CASCADE:
        return this.createTagValidator();
      default:
        return this.createGenericValidator();
    }
  }

  // ==================== 验证器工厂方法 ====================

  private createStringValidator() {
    return (operator: FilterOperator, value: any) => {
      if (!FilterOperatorUtils.requiresValue(operator)) {
        return { valid: true };
      }
      
      if (value == null || value === '') {
        return { valid: false, errorMessage: '请输入筛选值' };
      }
      
      if (typeof value !== 'string') {
        return { valid: false, errorMessage: '筛选值必须是字符串' };
      }
      
      if (value.length > 500) {
        return { 
          valid: false, 
          errorMessage: '筛选值长度不能超过500字符',
          suggestions: ['请缩短输入内容']
        };
      }
      
      return { valid: true };
    };
  }

  private createNumberValidator() {
    return (operator: FilterOperator, value: any) => {
      if (!FilterOperatorUtils.requiresValue(operator)) {
        return { valid: true };
      }
      
      if (value == null) {
        return { valid: false, errorMessage: '请输入数字值' };
      }
      
      const numValue = Number(value);
      if (isNaN(numValue)) {
        return { valid: false, errorMessage: '请输入有效的数字' };
      }
      
      return { valid: true, normalizedValue: numValue };
    };
  }

  private createDateValidator() {
    return (operator: FilterOperator, value: any) => {
      if (!FilterOperatorUtils.requiresValue(operator)) {
        return { valid: true };
      }
      
      if (value == null) {
        return { valid: false, errorMessage: '请选择日期' };
      }
      
      // 简单的日期验证
      const date = new Date(value);
      if (isNaN(date.getTime())) {
        return { valid: false, errorMessage: '请输入有效的日期' };
      }
      
      return { valid: true };
    };
  }

  private createEnumValidator(validValues: string[]) {
    return (operator: FilterOperator, value: any) => {
      if (!FilterOperatorUtils.requiresValue(operator)) {
        return { valid: true };
      }
      
      if (value == null) {
        return { valid: false, errorMessage: '请选择值' };
      }
      
      if (Array.isArray(value)) {
        // 多值验证
        for (const v of value) {
          if (!validValues.includes(v)) {
            return { valid: false, errorMessage: `无效的选项: ${v}` };
          }
        }
      } else {
        // 单值验证
        if (!validValues.includes(value)) {
          return { valid: false, errorMessage: `无效的选项: ${value}` };
        }
      }
      
      return { valid: true };
    };
  }

  private createTagValidator() {
    return (operator: FilterOperator, value: any) => {
      if (!FilterOperatorUtils.requiresValue(operator)) {
        return { valid: true };
      }
      
      if (!value || typeof value !== 'object') {
        return { valid: false, errorMessage: '请选择标签' };
      }
      
      const { tagIds } = value;
      if (!Array.isArray(tagIds) || tagIds.length === 0) {
        return { valid: false, errorMessage: '请至少选择一个标签' };
      }
      
      return { valid: true };
    };
  }

  private createGenericValidator() {
    return (operator: FilterOperator, value: any) => {
      if (FilterOperatorUtils.requiresValue(operator) && value == null) {
        return { valid: false, errorMessage: '请输入筛选值' };
      }
      return { valid: true };
    };
  }
}

/**
 * 全局字段注册表实例
 */
export const fieldRegistry = new FieldRegistry();

export default FieldRegistry;