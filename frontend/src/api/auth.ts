import request from '@/utils/request';
import type { LoginResult, UserInfo } from '@/types/user';
import type { Permission } from '@/types/permission';

export const login = (data: any) => {
  return request.post<LoginResult>('/api/auth/login', data);
};

export const getUserInfo = () => {
  return request.get<UserInfo>('/api/auth/me');
};

export const getMenus = () => {
  return request.get<Permission[]>('/api/auth/menus');
};

export const logout = () => {
  return request.post('/api/auth/logout');
};
