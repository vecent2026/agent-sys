import request from '@/utils/request';
import type { Role, RoleForm, RoleQuery } from '@/types/role';
import type { PageResult } from '@/types/api';

export const getRoleList = (params: RoleQuery) => {
  return request.get<PageResult<Role>>('/api/roles', { params });
};

export const getAllRoles = () => {
  return request.get<Role[]>('/api/roles/all');
};

export const createRole = (data: RoleForm) => {
  return request.post('/api/roles', data);
};

export const updateRole = (id: number, data: RoleForm) => {
  return request.put(`/api/roles/${id}`, data);
};

export const deleteRole = (ids: number[]) => {
  return request.delete(`/api/roles/${ids.join(',')}`);
};

export const getRolePermissions = (id: number) => {
  return request.get<number[]>(`/api/roles/${id}/permissions`);
};

export const assignRolePermissions = (id: number, permissionIds: number[]) => {
  return request.post(`/api/roles/${id}/permissions`, { permissionIds });
};
