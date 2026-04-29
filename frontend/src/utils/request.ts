import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import { ElLoading, ElMessage } from 'element-plus';

import type { ApiResponse } from '@/types/api';
import { pinia } from '@/stores';
import { useUserStore } from '@/stores/user';
import { getAccessToken } from '@/utils/auth';

export interface RequestConfig extends AxiosRequestConfig {
  showError?: boolean;
  showLoading?: boolean;
}

interface InternalRequestConfig extends InternalAxiosRequestConfig {
  showError?: boolean;
  showLoading?: boolean;
}

let loadingInstance: ReturnType<typeof ElLoading.service> | null = null;
let pendingRequestCount = 0;

const openLoading = () => {
  if (pendingRequestCount === 0) {
    loadingInstance = ElLoading.service({
      lock: true,
      text: '加载中...',
      background: 'rgba(2, 6, 23, 0.45)',
    });
  }

  pendingRequestCount += 1;
};

const closeLoading = () => {
  if (pendingRequestCount > 0) {
    pendingRequestCount -= 1;
  }

  if (pendingRequestCount === 0 && loadingInstance) {
    loadingInstance.close();
    loadingInstance = null;
  }
};

const getResponseMessage = (payload?: ApiResponse<unknown> | null) => {
  return payload?.msg || payload?.message || '';
};

const handleUnauthorized = () => {
  const userStore = useUserStore(pinia);
  userStore.clearAuth();
  window.location.href = '/login';
};

const handleHttpError = (status?: number, fallback = '网络异常，请稍后重试') => {
  if (status === 401) {
    ElMessage.error('登录已过期，请重新登录');
    handleUnauthorized();
    return;
  }

  if (status === 403) {
    ElMessage.error('当前账号无权限访问该资源');
    return;
  }

  if (status === 500) {
    ElMessage.error('服务器异常，请稍后重试');
    return;
  }

  ElMessage.error(fallback);
};

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 60000,
  withCredentials: false,
  headers: {
    'X-Requested-With': 'XMLHttpRequest',
  },
});

service.interceptors.request.use(
  (config: InternalRequestConfig) => {
    const token = getAccessToken();

    config.showLoading ??= true;
    config.showError ??= true;

    if (config.showLoading) {
      openLoading();
    }

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    if (config.data instanceof FormData) {
      delete config.headers['Content-Type'];
    } else if (!config.headers['Content-Type']) {
      config.headers['Content-Type'] = 'application/json;charset=UTF-8';
    }

    config.headers['X-Trace-Id'] = `${Date.now()}`;
    return config;
  },
  (error) => {
    closeLoading();
    return Promise.reject(error);
  },
);

service.interceptors.response.use(
  (response) => {
    closeLoading();
    const payload = response.data as ApiResponse<unknown>;

    if (typeof payload?.code !== 'number') {
      return response.data;
    }

    if (payload.code === 0 || payload.code === 200) {
      return payload.data;
    }

    const message = getResponseMessage(payload) || '请求失败';

    if (payload.code === 401) {
      ElMessage.error(message || '登录已过期，请重新登录');
      handleUnauthorized();
      return Promise.reject({ code: payload.code, message });
    }

    if (payload.code === 403) {
      ElMessage.error(message || '当前账号无权限访问该资源');
      return Promise.reject({ code: payload.code, message });
    }

    if (payload.code === 500) {
      ElMessage.error(message || '服务器异常，请稍后重试');
      return Promise.reject({ code: payload.code, message });
    }

    ElMessage.error(message);
    return Promise.reject({ code: payload.code, message });
  },
  (error: AxiosError<ApiResponse<never>>) => {
    closeLoading();

    const config = error.config as InternalRequestConfig | undefined;
    const status = error.response?.status;
    const message = getResponseMessage(error.response?.data) || error.message || '网络异常，请稍后重试';

    if (config?.showError !== false) {
      handleHttpError(status, message);
    }

    return Promise.reject({
      code: status ?? -1,
      message,
    });
  },
);

export const request = <T = unknown>(config: RequestConfig) => {
  return service.request<T, T>(config);
};

export const httpClient = service;

export default service;
