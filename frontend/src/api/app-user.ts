import request from '@/utils/request';
import type { PageResult } from '@/types/api';
import type {
  AppUser,
  AppUserQuery,
  AppUserTagDetail,
  AppUserTagCategory,
  AppUserTagQuery,
  AppUserField,
  AppUserFieldQuery,
  UserFieldValue,
  UserFieldValuesForm,
} from '@/types/app-user';

export const getAppUserList = (params: AppUserQuery) => {
  return request.get<PageResult<AppUser>>('/api/v1/app-users', { params });
};

export const getAppUserDetail = (id: number) => {
  return request.get<AppUser>(`/api/v1/app-users/${id}`);
};

export const updateAppUserStatus = (id: number, status: number) => {
  return request.put(`/api/v1/app-users/${id}/status`, { status });
};

export const assignAppUserTags = (userId: number, tagIds: number[]) => {
  return request.post(`/api/v1/app-users/${userId}/tags`, tagIds);
};

export const batchAddAppUserTags = (userIds: number[], tagIds: number[]) => {
  return request.post('/api/v1/app-users/batch-tags', { userIds, tagIds });
};

export const batchRemoveAppUserTags = (userIds: number[], tagIds: number[]) => {
  return request.delete('/api/v1/app-users/batch-tags', { data: { userIds, tagIds } });
};

export const exportAppUsers = (params: AppUserQuery) => {
  return request.post('/api/v1/app-users/export', params, { responseType: 'blob' });
};

export const getAppUserFieldValues = (userId: number) => {
  return request.get<UserFieldValue[]>(`/api/v1/app-users/${userId}/field-values`);
};

export const updateAppUserFieldValues = (userId: number, data: UserFieldValuesForm) => {
  return request.put(`/api/v1/app-users/${userId}/field-values`, data);
};

export const getTagCategoryList = () => {
  return request.get<AppUserTagCategory[]>('/api/v1/tag-categories');
};

export const createTagCategory = (data: { name: string; color?: string; description?: string; sort?: number }) => {
  return request.post('/api/v1/tag-categories', data);
};

export const updateTagCategory = (
  id: number,
  data: { name?: string; color?: string; description?: string; sort?: number }
) => {
  return request.put(`/api/v1/tag-categories/${id}`, data);
};

export const deleteTagCategory = (id: number) => {
  return request.delete(`/api/v1/tag-categories/${id}`);
};

export const getAppTagList = (params: AppUserTagQuery) => {
  return request.get<PageResult<AppUserTagDetail>>('/api/v1/user-tags', { params });
};

export const createAppTag = (data: {
  categoryId: number;
  name: string;
  color?: string;
  description?: string;
}) => {
  return request.post('/api/v1/user-tags', data);
};

export const updateAppTag = (
  id: number,
  data: {
    categoryId?: number;
    name?: string;
    color?: string;
    description?: string;
  }
) => {
  return request.put(`/api/v1/user-tags/${id}`, data);
};

export const deleteAppTag = (id: number) => {
  return request.delete(`/api/v1/user-tags/${id}`);
};

export const updateAppTagStatus = (id: number, status: number) => {
  return request.put(`/api/v1/user-tags/${id}/status`, null, { params: { status } });
};

export const getAppTagUsers = (tagId: number, page: number, size: number) => {
  return request.get<PageResult<AppUser>>(`/api/v1/user-tags/${tagId}/users`, {
    params: { page, size },
  });
};

export const getAppFieldList = (params: AppUserFieldQuery) => {
  return request.get<PageResult<AppUserField>>('/api/v1/user-fields', { params });
};

export const getEnabledAppFields = () => {
  return request.get<AppUserField[]>('/api/v1/user-fields/enabled');
};

export const createAppField = (data: {
  fieldName: string;
  fieldKey: string;
  fieldType: string;
  config?: Record<string, unknown>;
  isRequired?: number;
  sort?: number;
}) => {
  return request.post('/api/v1/user-fields', data);
};

export const updateAppField = (
  id: number,
  data: {
    fieldName?: string;
    config?: Record<string, unknown>;
    isRequired?: number;
    sort?: number;
    status?: number;
  }
) => {
  return request.put(`/api/v1/user-fields/${id}`, data);
};

export const deleteAppField = (id: number) => {
  return request.delete(`/api/v1/user-fields/${id}`);
};

export const updateAppFieldStatus = (id: number, status: number) => {
  return request.put(`/api/v1/user-fields/${id}/status`, null, { params: { status } });
};

export const sortAppFields = (fieldIds: number[]) => {
  return request.put('/api/v1/user-fields/sort', { fieldIds });
};

export const downloadImportTemplate = () => {
  return request.get('/api/v1/app-users/import-template', { responseType: 'blob' });
};

export const validateImportData = (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<ImportValidateResult>('/api/v1/app-users/import-validate', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export const downloadValidateResult = (taskId: string) => {
  return request.get(`/api/v1/app-users/import-validate-result/${taskId}`, { responseType: 'blob' });
};

export const executeImport = (taskId: string) => {
  return request.post<string>('/api/v1/app-users/import-execute', { taskId });
};

export const getImportProgress = (importTaskId: string) => {
  return request.get<ImportProgress>(`/api/v1/app-users/import-progress/${importTaskId}`);
};

export interface ImportValidateResult {
  taskId: string;
  total: number;
  validCount: number;
  invalidCount: number;
  canProceed: boolean;
}

export interface ImportProgress {
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  total: number;
  processed: number;
  success: number;
  failed: number;
  progress: number;
  errors: ImportError[];
}

export interface ImportError {
  row: number;
  mobile: string;
  reason: string;
}
