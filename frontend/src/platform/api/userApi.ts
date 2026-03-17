import platformRequest from '../utils/request';

export interface UserVo {
  id: number;
  username: string;
  nickname: string;
  mobile: string;
  email: string;
  status: number;
  isBuiltin?: number | boolean;
  isSuper?: number | boolean;
  lastLoginTime?: string;
  createTime: string;
  roleNames: string[];
  roleIds: number[];
}

export interface UserQueryParams {
  page?: number;
  size?: number;
  username?: string;
  mobile?: string;
  status?: number;
}

export interface UserDto {
  id?: number;
  username: string;
  nickname?: string;
  mobile?: string;
  email?: string;
  password?: string;
  status?: number;
  roleIds?: number[];
}

export const getUserPage = (params: UserQueryParams) =>
  platformRequest.get<any>('/api/platform/users', { params });

export const getUser = (id: number) =>
  platformRequest.get<UserVo>(`/api/platform/users/${id}`);

export const createUser = (data: UserDto) =>
  platformRequest.post('/api/platform/users', data);

export const updateUser = (id: number, data: UserDto) =>
  platformRequest.put(`/api/platform/users/${id}`, data);

export const deleteUsers = (ids: number[]) =>
  platformRequest.delete(`/api/platform/users/${ids.join(',')}`);

export const changeUserStatus = (id: number, status: number) =>
  platformRequest.put(`/api/platform/users/${id}/status`, { status });

export const resetUserPassword = (id: number, password: string) =>
  platformRequest.put(`/api/platform/users/${id}/password`, { password });

export const getUserRoles = (id: number) =>
  platformRequest.get<number[]>(`/api/platform/users/${id}/roles`);

export const assignUserRoles = (id: number, roleIds: number[]) =>
  platformRequest.post(`/api/platform/users/${id}/roles`, { roleIds });
