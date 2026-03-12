/**
 * 统一筛选系统 API 层
 *
 * 提供筛选查询和字段元数据的接口调用。
 */

import { request } from '@/utils/request';
import type { FilterCondition, UnifiedFilterQuery } from '@/types/unified-filter';
import { FilterLogic, SortDirection } from '@/types/unified-filter';

// ==================== 请求/响应类型 ====================

export interface UnifiedFilterRequest {
  conditions: FilterCondition[];
  logic: 'AND' | 'OR';
  page?: number;
  size?: number;
  sortBy?: string;
  sortOrder?: 'ASC' | 'DESC';
}

export interface UnifiedFilterResponse {
  records: any[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface FieldOption {
  label: string;
  value: any;
  color?: string;
}

export interface FieldMetadata {
  fieldKey: string;
  fieldName: string;
  fieldType: string;
  operators: string[];
  options?: FieldOption[];
}

export interface FieldMetadataResponse {
  builtinFields: FieldMetadata[];
  customFields: FieldMetadata[];
  specialFields: FieldMetadata[];
}

// ==================== API 函数 ====================

/**
 * 执行统一筛选查询
 */
export const executeUnifiedFilter = async (
  payload: UnifiedFilterRequest,
): Promise<UnifiedFilterResponse> => {
  return request.post<UnifiedFilterResponse>('/api/app-users/filter', payload);
};

/**
 * 获取可筛选字段元数据（内置字段 + 动态字段 + 特殊字段）
 */
export const getFilterFieldMetadata = async (): Promise<FieldMetadataResponse> => {
  return request.get<FieldMetadataResponse>('/api/app-users/filter/fields');
};

// ==================== 工具函数 ====================

/**
 * 从筛选条件列表构建标准查询对象
 */
export const buildFilterQuery = (
  conditions: FilterCondition[],
  options: {
    logic?: 'AND' | 'OR';
    page?: number;
    size?: number;
    sortBy?: string;
    sortOrder?: 'ASC' | 'DESC';
  } = {},
): UnifiedFilterQuery => {
  const logic: FilterLogic =
    options.logic === 'OR' ? FilterLogic.OR : FilterLogic.AND;

  return {
    conditions,
    logic,
    pagination: {
      page: options.page ?? 1,
      size: options.size ?? 20,
    },
    ...(options.sortBy && {
      sort: {
        field: options.sortBy,
        direction: options.sortOrder === 'ASC' ? SortDirection.ASC : SortDirection.DESC,
      },
    }),
  };
};
