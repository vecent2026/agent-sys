import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios';
import { message } from 'antd';
import { storage } from '@/utils/storage';
import type { Result } from '@/types/api';

// 平台端独立 axios 实例（使用 platform token）
export const platformInstance = axios.create({
  timeout: 10000,
});

platformInstance.interceptors.request.use(
  (config) => {
    const token = storage.getPlatformToken().access;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

let isRefreshing = false;
let failedQueue: any[] = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) prom.reject(error);
    else prom.resolve(token);
  });
  failedQueue = [];
};

const isSilentAuthBootstrapRequest = (url?: string) =>
  url === '/api/platform/auth/me' || url === '/api/platform/auth/permissions';

platformInstance.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    const res = response.data;
    if (response.config.responseType === 'blob') return res as any;

    if (res && typeof res.code !== 'undefined') {
      if (res.code === 200) return res.data as any;
      const errorMessage = res.message || `Error code: ${res.code}`;
      message.error(errorMessage);
      return Promise.reject(new Error(errorMessage));
    }
    return res as any;
  },
  async (error) => {
    const originalRequest = error.config;
    const requestUrl = originalRequest?.url;

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return platformInstance(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const refreshToken = storage.getPlatformToken().refresh;
        if (!refreshToken) throw new Error('No refresh token');

        const { data } = await axios.post<Result<{ accessToken: string; refreshToken: string }>>(
          `/api/platform/auth/refresh?refreshToken=${encodeURIComponent(refreshToken)}`
        );

        if (data.code === 200) {
          const { accessToken, refreshToken: newRefreshToken } = data.data;
          storage.setPlatformToken(accessToken, newRefreshToken);
          platformInstance.defaults.headers.common.Authorization = `Bearer ${accessToken}`;
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;
          processQueue(null, accessToken);
          return platformInstance(originalRequest);
        } else {
          throw new Error(data.message);
        }
      } catch (err) {
        processQueue(err, null);
        storage.clearPlatformToken();
        window.location.href = '/platform.html';
        return Promise.reject(err);
      } finally {
        isRefreshing = false;
      }
    }

    if (error.response?.status === 403) {
      if (isSilentAuthBootstrapRequest(requestUrl)) {
        return Promise.reject(new Error('Access denied'));
      }
      message.warning('当前账号暂无此操作权限');
      return Promise.reject(new Error('Access denied'));
    }

    const msg = error.response?.data?.message || error.message || 'Network Error';
    message.error(msg);
    return Promise.reject(error);
  }
);

export const platformRequest = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return platformInstance.get(url, config);
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return platformInstance.post(url, data, config);
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return platformInstance.put(url, data, config);
  },
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return platformInstance.delete(url, config);
  },
};

export default platformRequest;
