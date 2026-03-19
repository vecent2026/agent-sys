import request from '@/utils/request';
import type { Permission, PermissionForm } from '@/types/permission';

export const getPermissionTree = () => {
  return request.get<Permission[]>('/api/rbac/roles/available-permissions');
};

export const createPermission = (data: PermissionForm) => {
  return request.post('/api/permissions', data);
};

export const updatePermission = (id: number, data: PermissionForm) => {
  return request.put(`/api/permissions/${id}`, data);
};

export const deletePermission = (id: number) => {
  return request.delete(`/api/permissions/${id}`);
};
