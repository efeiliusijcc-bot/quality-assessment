<template>
  <header
    class="sticky top-3 z-20 flex min-h-20 flex-wrap items-center justify-between gap-4 rounded-[24px] border border-slate-200/10 bg-slate-900/80 px-4 py-4 text-slate-100 shadow-[0_18px_40px_rgba(15,23,42,0.25)] backdrop-blur sm:px-6"
  >
    <div class="flex items-center gap-4">
      <button
        type="button"
        class="flex h-11 w-11 items-center justify-center rounded-2xl border border-slate-700 bg-slate-900/90 text-slate-200 transition hover:border-cyan-400 hover:text-cyan-300 lg:hidden"
        @click="mobileMenuVisible = true"
      >
        <el-icon :size="18">
          <Menu />
        </el-icon>
      </button>

      <button
        type="button"
        class="hidden h-11 w-11 items-center justify-center rounded-2xl border border-slate-700 bg-slate-900/90 text-slate-200 transition hover:border-cyan-400 hover:text-cyan-300 lg:flex"
        @click="appStore.toggleSidebar"
      >
        <el-icon :size="18">
          <Fold v-if="!sidebarCollapsed" />
          <Expand v-else />
        </el-icon>
      </button>

      <div>
        <div class="text-base font-semibold tracking-[0.08em] text-slate-50 sm:text-lg">电子元器件装配生产线质量评估系统</div>
        <el-breadcrumb separator="/" class="mt-2">
          <el-breadcrumb-item
            v-for="item in breadcrumbs"
            :key="item.path"
            :to="item.path"
          >
            {{ item.label }}
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>
    </div>

    <div class="flex flex-wrap items-center justify-end gap-3">
      <div class="rounded-2xl border border-cyan-400/20 bg-cyan-400/10 px-4 py-2 text-right">
        <div class="text-sm text-cyan-200">{{ roleLabel }}</div>
        <div class="mt-1 font-medium text-slate-100">{{ displayName }}</div>
      </div>

      <button
        type="button"
        class="flex h-11 w-11 items-center justify-center rounded-2xl border border-slate-700 bg-slate-900/90 text-slate-200 transition hover:border-cyan-400 hover:text-cyan-300"
        @click="toggleFullScreen"
      >
        <el-icon :size="18">
          <FullScreen />
        </el-icon>
      </button>

      <button
        type="button"
        class="flex h-11 items-center gap-2 rounded-2xl border border-red-500/25 bg-red-500/10 px-4 text-sm text-red-100 transition hover:bg-red-500/20"
        @click="handleLogout"
      >
        <el-icon :size="16">
          <SwitchButton />
        </el-icon>
        退出登录
      </button>
    </div>

    <el-drawer
      v-model="mobileMenuVisible"
      title="功能导航"
      direction="ltr"
      size="82%"
      class="mobile-nav-drawer"
    >
      <el-menu
        :default-active="route.path"
        router
        unique-opened
        class="border-none"
        @select="mobileMenuVisible = false"
      >
        <template v-for="menu in menus" :key="menu.path">
          <el-sub-menu v-if="menu.children?.length" :index="menu.path">
            <template #title>
              <el-icon><component :is="resolveIcon(menu.icon)" /></el-icon>
              <span>{{ menu.title }}</span>
            </template>
            <el-menu-item v-for="child in menu.children" :key="child.path" :index="child.path">
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="menu.path">
            <el-icon><component :is="resolveIcon(menu.icon)" /></el-icon>
            <span>{{ menu.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-drawer>
  </header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as icons from '@element-plus/icons-vue';
import { Expand, Fold, FullScreen, Menu, SwitchButton } from '@element-plus/icons-vue';
import { storeToRefs } from 'pinia';
import { useRoute, useRouter } from 'vue-router';

import { logoutRequest } from '@/api/user';
import { menus } from '@/constants/menu';
import { useAppStore } from '@/stores/app';
import { useUserStore } from '@/stores/user';

const route = useRoute();
const router = useRouter();
const appStore = useAppStore();
const userStore = useUserStore();
const { sidebarCollapsed } = storeToRefs(appStore);
const { displayName, roleLabel } = storeToRefs(userStore);
const mobileMenuVisible = ref(false);

const breadcrumbs = computed(() => {
  return route.matched
    .filter((item) => item.meta?.title && item.path !== '/')
    .map((item) => ({
      path: item.path,
      label: String(item.meta.title),
    }));
});

const toggleFullScreen = async () => {
  if (!document.fullscreenElement) {
    await document.documentElement.requestFullscreen();
    return;
  }

  await document.exitFullscreen();
};

const resolveIcon = (iconName?: string) => {
  if (!iconName) {
    return icons.Menu;
  }
  return icons[iconName as keyof typeof icons] ?? icons.Menu;
};

const handleLogout = async () => {
  await logoutRequest().catch(() => undefined);
  userStore.clearAuth();
  ElMessage.success('已退出登录');
  router.push('/login');
};
</script>

<style scoped>
:deep(.el-breadcrumb__inner),
:deep(.el-breadcrumb__separator) {
  color: #cbd5e1;
}

:deep(.mobile-nav-drawer) {
  background: #f8fafc;
}
</style>
