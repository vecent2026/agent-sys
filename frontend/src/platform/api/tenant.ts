import { platformRequest } from '../utils/request';
import type { PageResult } from '@/types/api';

export interface PlatformTenant {
  id: number;
  tenantCode: string;
  tenantName: string;
  logo?: string;
  description?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  status: 0 | 1;
  expireTime?: string | null;
  maxUsers?: number | null;
  dataVersion: number;
  createTime: string;
  updateTime: string;
}

export interface PlatformTenantQuery {
  page: number;
  size: number;
  keyword?: string;
  status?: number;
}

export interface CreateTenantAdminUser {
  mobile: string;
  nickname: string;
}

export interface CreateTenantForm {
  tenantCode: string;
  tenantName: string;
  logo?: string;
  description?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  expireTime?: string | null;
  maxUsers?: number | null;
  adminUser: CreateTenantAdminUser;
}

export interface UpdateTenantForm
  extends Omit<CreateTenantForm, 'tenantCode' | 'adminUser'> {}

export interface TenantStats {
  memberCount: number;
  roleCount: number;
  todayActiveCount: number;
}

export const getPlatformTenantPage = async (params: PlatformTenantQuery) => {
  const res = await platformRequest.get<any>('/api/platform/tenants', { params });
  if (res?.content != null) {
    return {
      records: res.content,
      total: res.totalElements ?? 0,
      size: res.size ?? params.size,
      current: (res.number ?? 0) + 1,
      pages: res.totalPages ?? 0,
    } as PageResult<PlatformTenant>;
  }
  if (res?.records != null) {
    return {
      records: res.records,
      total: res.total ?? 0,
      size: res.size ?? params.size,
      current: res.current ?? params.page,
      pages: res.pages ?? 0,
    } as PageResult<PlatformTenant>;
  }
  return res as PageResult<PlatformTenant>;
};

export const getPlatformTenantDetail = (id: number) => {
  return platformRequest.get<PlatformTenant>(`/api/platform/tenants/${id}`);
};

export const createPlatformTenant = (data: CreateTenantForm) => {
  return platformRequest.post('/api/platform/tenants', data);
};

export const updatePlatformTenant = (id: number, data: UpdateTenantForm) => {
  return platformRequest.put(`/api/platform/tenants/${id}`, data);
};

export const updatePlatformTenantStatus = (id: number, status: 0 | 1) => {
  return platformRequest.put(`/api/platform/tenants/${id}/status`, { status });
};

export const getPlatformTenantPermissionIds = (id: number) => {
  return platformRequest.get<number[]>(`/api/platform/tenants/${id}/permissions`);
};

export const updatePlatformTenantPermissionIds = (
  id: number,
  permissionIds: number[],
) => {
  return platformRequest.put(`/api/platform/tenants/${id}/permissions`, {
    permissionIds,
  });
};

export const getPlatformTenantStats = (id: number) => {
  return platformRequest.get<TenantStats>(`/api/platform/tenants/${id}/stats`);
};

