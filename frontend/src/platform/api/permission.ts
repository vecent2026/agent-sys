import { platformRequest } from '../utils/request';
import type { Permission, PermissionForm } from '@/types/permission';

export type PlatformPermission = Permission & {
  scope?: 'platform' | 'tenant';
};

export interface PlatformPermissionForm extends PermissionForm {
  scope?: 'platform' | 'tenant';
}

export const getPlatformPermissionTree = () => {
  return platformRequest.get<PlatformPermission[]>('/api/platform/permissions');
};

export const createPlatformPermission = (data: PlatformPermissionForm) => {
  return platformRequest.post('/api/platform/permissions', data);
};

export const updatePlatformPermission = (id: number, data: PlatformPermissionForm) => {
  return platformRequest.put(`/api/platform/permissions/${id}`, data);
};

export const deletePlatformPermission = (id: number) => {
  return platformRequest.delete(`/api/platform/permissions/${id}`);
};

