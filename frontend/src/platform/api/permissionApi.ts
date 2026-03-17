import platformRequest from '../utils/request';

export interface PermissionVo {
  id: number;
  parentId: number;
  name: string;
  type: string; // 'directory' | 'menu' | 'button'
  permissionKey: string;
  path?: string;
  component?: string;
  sort: number;
  logEnabled: boolean;
  createTime: string;
  children?: PermissionVo[];
}

export interface PermissionDto {
  id?: number;
  parentId?: number;
  name: string;
  type: string;
  permissionKey: string;
  path?: string;
  component?: string;
  sort?: number;
  logEnabled?: boolean;
}

export const getPermissionTree = () =>
  platformRequest.get<PermissionVo[]>('/api/platform/permissions/tree');

export const getPermissionList = (name?: string) =>
  platformRequest.get<PermissionVo[]>('/api/platform/permissions', { params: name ? { name } : undefined });

export const createPermission = (data: PermissionDto) =>
  platformRequest.post('/api/platform/permissions', data);

export const updatePermission = (id: number, data: PermissionDto) =>
  platformRequest.put(`/api/platform/permissions/${id}`, data);

export const deletePermission = (id: number) =>
  platformRequest.delete(`/api/platform/permissions/${id}`);
