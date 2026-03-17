import platformRequest from '../utils/request';

export interface TenantVo {
  id: number;
  tenantCode: string;
  tenantName: string;
  description?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  status: number;
  expireTime?: string;
  maxUsers?: number;
  dataVersion: number;
  createTime: string;
  updateTime: string;
}

export interface TenantQueryParams {
  page?: number;
  size?: number;
  tenantName?: string;
  tenantCode?: string;
  status?: number;
}

export interface TenantDto {
  id?: number;
  tenantCode: string;
  tenantName: string;
  description?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  status?: number;
  expireTime?: string;
  maxUsers?: number;
  adminUser?: { mobile: string; nickname?: string };
}

export const getTenantPage = (params: TenantQueryParams) =>
  platformRequest.get<any>('/api/platform/tenants', { params });

export const getTenantList = () =>
  platformRequest.get<TenantVo[]>('/api/platform/tenants/all');

export const getTenant = (id: number) =>
  platformRequest.get<TenantVo>(`/api/platform/tenants/${id}`);

export const createTenant = (data: TenantDto) =>
  platformRequest.post('/api/platform/tenants', data);

export const updateTenant = (id: number, data: TenantDto) =>
  platformRequest.put(`/api/platform/tenants/${id}`, data);

export const deleteTenant = (id: number) =>
  platformRequest.delete(`/api/platform/tenants/${id}`);

export const changeTenantStatus = (id: number, status: number) =>
  platformRequest.put(`/api/platform/tenants/${id}/status`, { status });

export const getTenantPermissions = (id: number) =>
  platformRequest.get<number[]>(`/api/platform/tenants/${id}/permissions`);

export const updateTenantPermissions = (id: number, permissionIds: number[]) =>
  platformRequest.put(`/api/platform/tenants/${id}/permissions`, { permissionIds });
