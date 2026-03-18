import request from '@/utils/request';
import type { Role, RoleForm, RoleQuery } from '@/types/role';
import type { PageResult } from '@/types/api';

export const getRoleList = (params: RoleQuery) => {
  return request.get<PageResult<Role>>('/api/rbac/roles', { params });
};

export const getAllRoles = () => {
  return request.get<Role[]>('/api/rbac/roles/all');
};

export const createRole = (data: RoleForm) => {
  return request.post('/api/rbac/roles', data);
};

export const updateRole = (id: number, data: RoleForm) => {
  return request.put(`/api/rbac/roles/${id}`, data);
};

export const deleteRole = (ids: number[]) => {
  return request.delete(`/api/rbac/roles/${ids.join(',')}`);
};

export const getRolePermissions = (id: number) => {
  return request.get<number[]>(`/api/rbac/roles/${id}/permissions`);
};

export const assignRolePermissions = (id: number, permissionIds: number[]) => {
  return request.post(`/api/rbac/roles/${id}/permissions`, { permissionIds });
};
