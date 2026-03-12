/**
 * 统一筛选系统类型定义
 *
 * 与后端 UnifiedFilterCondition / UnifiedFilterQuery 保持一致。
 */

import { FieldType } from '../enums/FieldType';
import { FilterOperator } from '../enums/FilterOperator';

// ==================== 基础筛选类型 ====================

export interface FilterCondition {
  id: string;
  fieldKey: string;
  fieldType: FieldType;
  operator: FilterOperator;
  value: FilterValue;
}

export type FilterValue =
  | string
  | number
  | Date
  | string[]
  | number[]
  | DateRange
  | NumberRange
  | TagCascadeValue
  | null
  | undefined;

export interface DateRange {
  start: Date | string | null;
  end: Date | string | null;
}

export interface NumberRange {
  start: number | null;
  end: number | null;
}

export interface TagCascadeValue {
  categoryId: number | null;
  tagIds: number[];
}

// ==================== 查询相关类型 ====================

export const FilterLogic = {
  AND: 'AND',
  OR: 'OR',
} as const;
export type FilterLogic = (typeof FilterLogic)[keyof typeof FilterLogic];

export const SortDirection = {
  ASC: 'ASC',
  DESC: 'DESC',
} as const;
export type SortDirection = (typeof SortDirection)[keyof typeof SortDirection];

export interface PaginationInfo {
  page: number;
  size: number;
}

export interface SortInfo {
  field: string;
  direction: SortDirection;
}

export interface UnifiedFilterQuery {
  conditions: FilterCondition[];
  logic: FilterLogic;
  pagination: PaginationInfo;
  sort?: SortInfo;
}

// ==================== 字段定义类型 ====================

export interface EnumOption {
  label: string;
  value: string | number;
  color?: string;
}

export interface FieldDefinition {
  fieldKey: string;
  fieldName: string;
  fieldType: FieldType;
  isDefault: boolean;
  operators: FilterOperator[];
  options?: EnumOption[];
  validator?: (operator: FilterOperator, value: FilterValue) => FilterValidationResult;
  config?: any;
}

/** FieldRegistry 接口（供 FieldRegistry 类实现） */
export interface FieldRegistry {
  get(fieldKey: string): FieldDefinition | undefined;
  getAll(): FieldDefinition[];
  register(definition: FieldDefinition): void;
  isSupported(fieldKey: string): boolean;
  getSupportedOperators(fieldKey: string): FilterOperator[];
  getFieldsByType(fieldType: FieldType): FieldDefinition[];
}

// ==================== 验证类型 ====================

export interface FilterValidationResult {
  valid: boolean;
  errorMessage?: string;
  errorCode?: string;
  warnings?: string[];
  suggestions?: string[];
  normalizedValue?: FilterValue;
}

// ==================== 默认值工厂 ====================

export const createDefaultFilterCondition = (
  fieldKey: string,
  fieldType: FieldType,
): FilterCondition => ({
  id: `filter_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
  fieldKey,
  fieldType,
  operator: FilterOperator.EQUALS,
  value: null,
});

export const createDefaultFilterQuery = (): UnifiedFilterQuery => ({
  conditions: [],
  logic: FilterLogic.AND,
  pagination: { page: 1, size: 20 },
});
