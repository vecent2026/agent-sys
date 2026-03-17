import { platformRequest } from '../utils/request';
import type { PageResult } from '@/types/api';
import type { UserInfo, UserForm, UserQuery } from '@/types/user';

export type PlatformUserInfo = UserInfo;
export type PlatformUserQuery = UserQuery;
export type PlatformUserForm = UserForm;

export const getPlatformUserPage = async (params: PlatformUserQuery) => {
  const res = await platformRequest.get<any>('/api/platform/users', { params });
  if (res?.content != null) {
    return {
      records: res.content,
      total: res.totalElements ?? 0,
      size: res.size ?? params.size,
      current: (res.number ?? 0) + 1,
      pages: res.totalPages ?? 0,
    } as PageResult<PlatformUserInfo>;
  }
  if (res?.records != null) {
    return {
      records: res.records,
      total: res.total ?? 0,
      size: res.size ?? params.size,
      current: res.current ?? params.page,
      pages: res.pages ?? 0,
    } as PageResult<PlatformUserInfo>;
  }
  return res as PageResult<PlatformUserInfo>;
};

export const createPlatformUser = (data: PlatformUserForm) => {
  return platformRequest.post('/api/platform/users', data);
};

export const updatePlatformUser = (id: number, data: PlatformUserForm) => {
  return platformRequest.put(`/api/platform/users/${id}`, data);
};

export const updatePlatformUserStatus = (id: number, status: 0 | 1) => {
  return platformRequest.put(`/api/platform/users/${id}/status`, { status });
};

export const resetPlatformUserPassword = (id: number, password?: string) => {
  return platformRequest.put(`/api/platform/users/${id}/password`, { password });
};

export const deletePlatformUser = (ids: number[]) => {
  return platformRequest.delete(`/api/platform/users/${ids.join(',')}`);
};

