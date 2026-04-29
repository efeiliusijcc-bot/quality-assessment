import type { ApiResponse } from '@/types/api';

export const mockApiSuccess = <T>(data: T, message = 'success'): Promise<ApiResponse<T>> =>
  new Promise((resolve) => {
    window.setTimeout(() => {
      resolve({
        code: 0,
        data,
        message,
      });
    }, 300 + Math.round(Math.random() * 400));
  });

export const mockApiFailure = (message: string, code = 500): Promise<never> =>
  new Promise((_, reject) => {
    window.setTimeout(() => {
      reject({
        code,
        message,
      });
    }, 300);
  });
