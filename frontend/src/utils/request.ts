import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios';
import { message } from 'antd';
import { useUserStore } from '@/store/userStore';
import { storage } from '@/utils/storage';
import type { Result } from '@/types/api';

export const instance = axios.create({
  timeout: 10000,
});

// Request interceptor
instance.interceptors.request.use(
  (config) => {
    const token = storage.getToken().access;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

let isRefreshing = false;
let failedQueue: any[] = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

// Response interceptor
instance.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    const res = response.data;
    if (res.code === 200) {
      return res.data as any;
    }
    // Handle business errors
    message.error(res.message || 'Error');
    if (res.traceId) {
      console.error(`TraceId: ${res.traceId}`);
    }
    return Promise.reject(new Error(res.message || 'Error'));
  },
  async (error) => {
    const originalRequest = error.config;
    
    // Handle 401 Unauthorized
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return instance(originalRequest);
          })
          .catch((err) => {
            return Promise.reject(err);
          });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const refreshToken = storage.getToken().refresh;
        if (!refreshToken) {
          throw new Error('No refresh token');
        }

        // Call refresh API directly using axios to avoid interceptor loop
        const { data } = await axios.post<Result<{ accessToken: string; refreshToken: string }>>(
          '/api/auth/refresh',
          { refreshToken }
        );

        if (data.code === 200) {
          const { accessToken, refreshToken: newRefreshToken } = data.data;
          useUserStore.getState().setToken(accessToken, newRefreshToken);
          
          instance.defaults.headers.common.Authorization = `Bearer ${accessToken}`;
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;
          
          processQueue(null, accessToken);
          return instance(originalRequest);
        } else {
          throw new Error(data.message);
        }
      } catch (err) {
        processQueue(err, null);
        useUserStore.getState().logout();
        window.location.href = '/login';
        return Promise.reject(err);
      } finally {
        isRefreshing = false;
      }
    }
    
    // Handle 403 Forbidden - Permission denied (authenticated but no permission)
    if (error.response?.status === 403) {
      message.warning("当前账号暂无此操作权限");
      // For 403, we don't clear authentication info since user is still authenticated
      // Just show the warning and let user continue using the system
      return Promise.reject(new Error('Access denied'));
    }

    // Handle other errors
    const msg = error.response?.data?.message || error.message || 'Network Error';
    message.error(msg);
    const traceId = error.response?.headers?.['x-trace-id'] || error.response?.data?.traceId;
    if (traceId) {
      console.error(`TraceId: ${traceId}`);
      message.error(`TraceId: ${traceId}`);
    }
    
    return Promise.reject(error);
  }
);

export const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.get(url, config);
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return instance.post(url, data, config);
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return instance.put(url, data, config);
  },
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.delete(url, config);
  },
};

export default request;
