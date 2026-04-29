import { mockApiFailure, mockApiSuccess } from '@/api/_mock';
import { request } from '@/utils/request';
import { isMockEnabled } from '@/constants/env';
import { setAccessToken } from '@/utils/auth';

export interface LoginPayload {
  username: string;
  password: string;
  captchaId: string;
  captchaCode: string;
}

export interface LoginCaptcha {
  captchaId: string;
  captchaImage: string;
}

export interface UserProfile {
  id: string;
  name: string;
  role: 'admin' | 'engineer' | 'operator';
}

export interface LoginResult {
  token: string;
  user: UserProfile;
}

const captchaPool = new Map<string, string>();

const createCaptcha = () => Math.random().toString(36).slice(2, 6).toUpperCase();

export const getLoginCaptcha = async (): Promise<LoginCaptcha> => {
  if (!isMockEnabled) {
    return request<LoginCaptcha>({
      url: '/user/captcha',
      method: 'GET',
    });
  }

  const captchaId = `captcha_${Date.now()}`;
  const captchaCode = createCaptcha();
  captchaPool.set(captchaId, captchaCode);

  const response = await mockApiSuccess({
    captchaId,
    captchaCode,
  });

  return response.data;
};

export const loginByPassword = async (payload: LoginPayload): Promise<LoginResult> => {
  if (!isMockEnabled) {
    const result = await request<LoginResult>({
      url: '/user/login',
      method: 'POST',
      data: payload,
    });
    setAccessToken(result.token);
    return result;
  }

  const currentCaptcha = captchaPool.get(payload.captchaId);

  if (!currentCaptcha || payload.captchaCode.toUpperCase() !== currentCaptcha) {
    return mockApiFailure('验证码错误', 400);
  }

  const allowedUsers = ['admin', 'engineer', 'operator'];
  if (!(allowedUsers.includes(payload.username) && payload.password === '123456')) {
    return mockApiFailure('账号或密码错误', 401);
  }

  captchaPool.delete(payload.captchaId);

  const role: UserProfile['role'] =
    payload.username === 'engineer'
      ? 'engineer'
      : payload.username === 'operator'
        ? 'operator'
        : 'admin';

  const result: LoginResult = {
    token: `mock-token-${Date.now()}`,
    user: {
      id:
        payload.username === 'engineer'
          ? 'U-002'
          : payload.username === 'operator'
            ? 'U-003'
            : 'U-001',
      name:
        payload.username === 'engineer'
          ? '工艺工程师'
          : payload.username === 'operator'
            ? '产线操作员'
            : '系统管理员',
      role,
    },
  };

  const response = await mockApiSuccess(result, '登录成功');
  setAccessToken(response.data.token);
  return response.data;
};

export const logoutRequest = async () => {
  if (!isMockEnabled) {
    return request<boolean>({
      url: '/user/logout',
      method: 'POST',
    });
  }

  const response = await mockApiSuccess(true, '退出成功');
  return response.data;
};
