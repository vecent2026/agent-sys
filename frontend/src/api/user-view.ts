import { request } from '@/utils/request';
import type { FilterCondition } from '@/store/userManagementStore';

export interface ViewConfig {
  id: string;
  name: string;
  filters: FilterCondition[];
  hiddenFields: string[];
  filterLogic: 'AND' | 'OR';
  isDefault?: boolean;
  createTime?: string;
  updateTime?: string;
  orderNo?: number;
  viewConfig?: {
    columnOrder?: string[];
    columnWidths?: Record<string, number>;
  };
}

export interface CreateViewParams {
  name: string;
  filters: FilterCondition[];
  hiddenFields: string[];
  filterLogic: 'AND' | 'OR';
  orderNo?: number;
  viewConfig?: ViewConfig['viewConfig'];
}

export interface UpdateViewParams {
  name?: string;
  filters?: FilterCondition[];
  hiddenFields?: string[];
  filterLogic?: 'AND' | 'OR';
  isDefault?: boolean;
  orderNo?: number;
  viewConfig?: ViewConfig['viewConfig'];
}

export const userViewApi = {
  // Get view list
  getViews: () => request.get<ViewConfig[]>('/api/user/views'),
  
  // Create view
  createView: (data: CreateViewParams) => request.post<ViewConfig>('/api/user/views', data),
  
  // Update view
  updateView: (id: string, data: UpdateViewParams) => 
    request.put<ViewConfig>(`/api/user/views/${id}`, data),
  
  // Delete view
  deleteView: (id: string) => 
    request.delete(`/api/user/views/${id}`),
};
