import platformRequest from '../utils/request';

export interface RoleVo {
  id: number;
  roleName: string;
  roleKey: string;
  description: string;
  createTime: string;
  userCount: number;
  isBuiltin?: number;
}

export interface RoleDto {
  id?: number;
  roleName: string;
  roleKey: string;
  description?: string;
}

export const getRolePage = (params: { page?: number; size?: number; roleName?: string }) =>
  platformRequest.get<any>('/api/platform/roles', { params });

export const getRoleList = () =>
  platformRequest.get<RoleVo[]>('/api/platform/roles/all');

export const getRole = (id: number) =>
  platformRequest.get<RoleVo>(`/api/platform/roles/${id}`);

export const getRolePermissions = (id: number) =>
  platformRequest.get<number[]>(`/api/platform/roles/${id}/permissions`);

export const assignRolePermissions = (id: number, permissionIds: number[]) =>
  platformRequest.put(`/api/platform/roles/${id}/permissions`, { permissionIds });

export const createRole = (data: RoleDto) =>
  platformRequest.post('/api/platform/roles', data);

export const updateRole = (id: number, data: RoleDto) =>
  platformRequest.put(`/api/platform/roles/${id}`, data);

export const deleteRoles = (ids: number[]) =>
  platformRequest.delete(`/api/platform/roles/${ids.join(',')}`);
