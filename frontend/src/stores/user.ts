import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

import { clearAccessToken, getAccessToken, getStoredProfile, setStoredProfile, type StoredUserProfile } from '@/utils/auth';

export type UserRole = 'admin' | 'engineer' | 'operator';

const roleTextMap: Record<UserRole, string> = {
  admin: '系统管理员',
  engineer: '工艺工程师',
  operator: '操作员',
};

export const useUserStore = defineStore('user', () => {
  const token = ref(getAccessToken());
  const profile = ref<StoredUserProfile | null>(getStoredProfile());

  const isAuthenticated = computed(() => Boolean(token.value));
  const role = computed<UserRole | ''>(() => profile.value?.role ?? '');
  const roleLabel = computed(() => (role.value ? roleTextMap[role.value] : '未登录'));
  const displayName = computed(() => profile.value?.name ?? '访客');

  const setAuth = (payload: { token: string; profile: StoredUserProfile }) => {
    token.value = payload.token;
    profile.value = payload.profile;
    setStoredProfile(payload.profile);
  };

  const clearAuth = () => {
    token.value = '';
    profile.value = null;
    clearAccessToken();
    setStoredProfile(null);
  };

  return {
    clearAuth,
    displayName,
    isAuthenticated,
    profile,
    role,
    roleLabel,
    setAuth,
    token,
  };
});
