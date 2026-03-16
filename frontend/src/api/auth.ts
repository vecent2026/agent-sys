import request from '@/utils/request';
import type { UserInfo } from '@/types/user';

// ── 租户端认证 ──────────────────────────────────

/** 租户端登录（手机号+密码）*/
export const tenantLogin = (data: { mobile: string; password: string }) => {
  return request.post<any>('/api/tenant/auth/login', data);
};

/** 选择租户（preToken 换正式 JWT）*/
export const tenantSelectTenant = (data: { preToken: string; tenantId: number }) => {
  return request.post<any>('/api/tenant/auth/select', data);
};

/** 切换租户 */
export const tenantSwitchTenant = (data: { tenantId: number }) => {
  return request.post<any>('/api/tenant/auth/switch', data);
};

/** 获取当前租户用户信息 */
export const getTenantUserInfo = () => {
  return request.get<UserInfo>('/api/tenant/auth/me');
};

/** 获取当前租户权限列表 */
export const getTenantPermissions = () => {
  return request.get<string[]>('/api/tenant/auth/permissions');
};

/** 退出登录（租户端）*/
export const tenantLogout = () => {
  return request.post('/api/tenant/auth/logout');
};

// ── 平台端认证 ──────────────────────────────────

/** 平台端登录（用户名+密码）*/
export const platformLogin = (data: { username: string; password: string }) => {
  return request.post<any>('/api/platform/auth/login', data);
};

/** 获取当前平台用户信息 */
export const getPlatformUserInfo = () => {
  return request.get<any>('/api/platform/auth/me');
};

/** 获取当前平台权限列表 */
export const getPlatformPermissions = () => {
  return request.get<string[]>('/api/platform/auth/permissions');
};

/** 退出登录（平台端）*/
export const platformLogout = () => {
  return request.post('/api/platform/auth/logout');
};

// ── 向后兼容（旧代码引用） ─────────────────────

/** @deprecated 使用 tenantLogin */
export const login = tenantLogin as any;

/** @deprecated 使用 getTenantUserInfo */
export const getUserInfo = getTenantUserInfo;

/** @deprecated */
export const getMenus = () => request.get<any[]>('/api/tenant/auth/permissions');

/** @deprecated 使用 tenantLogout */
export const logout = tenantLogout;
