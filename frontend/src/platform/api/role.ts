import { platformRequest } from '../utils/request';
import type { PageResult } from '@/types/api';
import type { Role, RoleForm, RoleQuery } from '@/types/role';

export type PlatformRole = Role;
export type PlatformRoleForm = RoleForm;
export type PlatformRoleQuery = RoleQuery;

export const getPlatformRolePage = async (params: PlatformRoleQuery) => {
  const res = await platformRequest.get<any>('/api/platform/roles', { params });
  if (res?.content != null) {
    return {
      records: res.content,
      total: res.totalElements ?? 0,
      size: res.size ?? params.size,
      current: (res.number ?? 0) + 1,
      pages: res.totalPages ?? 0,
    } as PageResult<PlatformRole>;
  }
  if (res?.records != null) {
    return {
      records: res.records,
      total: res.total ?? 0,
      size: res.size ?? params.size,
      current: res.current ?? params.page,
      pages: res.pages ?? 0,
    } as PageResult<PlatformRole>;
  }
  return res as PageResult<PlatformRole>;
};

export const getAllPlatformRoles = () => {
  return platformRequest.get<PlatformRole[]>('/api/platform/roles/all');
};

export const createPlatformRole = (data: PlatformRoleForm) => {
  return platformRequest.post('/api/platform/roles', data);
};

export const updatePlatformRole = (id: number, data: PlatformRoleForm) => {
  return platformRequest.put(`/api/platform/roles/${id}`, data);
};

export const deletePlatformRole = (ids: number[]) => {
  return platformRequest.delete(`/api/platform/roles/${ids.join(',')}`);
};

export const getPlatformRolePermissionIds = (id: number) => {
  return platformRequest.get<number[]>(`/api/platform/roles/${id}/permissions`);
};

export const assignPlatformRolePermissionIds = (
  id: number,
  permissionIds: number[],
) => {
  return platformRequest.put(`/api/platform/roles/${id}/permissions`, {
    permissionIds,
  });
};

