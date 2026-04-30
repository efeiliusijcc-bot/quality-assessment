import { request } from '@/utils/request';
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

interface LoginApiResponse {
  token: string;
  user: {
    id: string;
    name: string;
    role: string;
  };
}

export const getLoginCaptcha = async (): Promise<LoginCaptcha> => {
  return request<LoginCaptcha>({
    url: '/user/captcha',
    method: 'GET',
  });
};

export const loginByPassword = async (payload: LoginPayload): Promise<LoginResult> => {
  const res = await request<LoginApiResponse>({
    url: '/user/login',
    method: 'POST',
    data: payload,
  });

  const roleLower = res.user.role.toLowerCase();
  const validRoles = ['admin', 'engineer', 'operator'] as const;
  const role = validRoles.includes(roleLower as any) ? roleLower : 'operator';

  const result: LoginResult = {
    token: res.token,
    user: {
      id: res.user.id,
      name: res.user.name,
      role: role as UserProfile['role'],
    },
  };

  setAccessToken(result.token);
  return result;
};

export const logoutRequest = async () => {
  return request<boolean>({
    url: '/user/logout',
    method: 'POST',
  });
};
