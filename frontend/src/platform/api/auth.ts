import platformRequest from '../utils/request';

export const platformLogin = (data: { username: string; password: string }) => {
  return platformRequest.post<any>('/api/platform/auth/login', data);
};

export const getPlatformUserInfo = () => {
  return platformRequest.get<any>('/api/platform/auth/me');
};

export const getPlatformPermissions = () => {
  return platformRequest.get<string[]>('/api/platform/auth/permissions');
};

export const platformRefresh = (refreshToken: string) => {
  return platformRequest.post<any>(`/api/platform/auth/refresh?refreshToken=${encodeURIComponent(refreshToken)}`);
};

export const platformLogout = () => {
  return platformRequest.post('/api/platform/auth/logout');
};
