const TOKEN_KEY = 'qa_access_token';
const PROFILE_KEY = 'qa_user_profile';

export interface StoredUserProfile {
  id: string;
  name: string;
  role: 'admin' | 'engineer' | 'operator';
}

const normalizeRole = (role: unknown): StoredUserProfile['role'] => {
  const normalized = String(role ?? '').toLowerCase();
  if (normalized === 'admin' || normalized === 'engineer' || normalized === 'operator') {
    return normalized;
  }
  return 'operator';
};

export const getAccessToken = () => localStorage.getItem(TOKEN_KEY) ?? '';

export const setAccessToken = (token: string) => {
  localStorage.setItem(TOKEN_KEY, token);
};

export const clearAccessToken = () => {
  localStorage.removeItem(TOKEN_KEY);
};

export const getStoredProfile = (): StoredUserProfile | null => {
  const raw = localStorage.getItem(PROFILE_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as StoredUserProfile;
    return {
      ...parsed,
      role: normalizeRole(parsed.role),
    };
  } catch {
    localStorage.removeItem(PROFILE_KEY);
    return null;
  }
};

export const setStoredProfile = (profile: StoredUserProfile | null) => {
  if (!profile) {
    localStorage.removeItem(PROFILE_KEY);
    return;
  }

  localStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
};
