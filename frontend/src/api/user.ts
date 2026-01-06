import request from '@/utils/request';
import type { UserInfo, UserForm, UserQuery } from '@/types/user';
import type { PageResult } from '@/types/api';

export const getUserList = (params: UserQuery) => {
  return request.get<PageResult<UserInfo>>('/api/users', { params });
};

export const createUser = (data: UserForm) => {
  console.log('createUser data:', data);
  return request.post('/api/users', data);
};

export const updateUser = (id: number, data: UserForm) => {
  return request.put(`/api/users/${id}`, data);
};

export const deleteUser = (ids: number[]) => {
  return request.delete(`/api/users/${ids.join(',')}`);
};

export const resetUserPassword = (id: number, password?: string) => {
  return request.put(`/api/users/${id}/password`, { password });
};

export const updateUserStatus = (id: number, status: 0 | 1) => {
  return request.put(`/api/users/${id}/status`, { status });
};

export const getUserRoles = (id: number) => {
  return request.get<number[]>(`/api/users/${id}/roles`);
};

export const assignUserRoles = (id: number, roleIds: number[]) => {
  return request.post(`/api/users/${id}/roles`, { roleIds });
};
