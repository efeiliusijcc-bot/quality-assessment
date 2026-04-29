<template>
  <aside
    class="h-screen border-r border-slate-800/80 bg-slate-950/95 px-4 py-6 backdrop-blur"
    :style="{ width: sidebarWidth }"
  >
    <div class="mb-8 flex items-center gap-3 overflow-hidden px-2">
      <div
        class="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-cyan-400/30 bg-cyan-500/10 text-cyan-300 shadow-[0_0_24px_rgba(6,182,212,0.22)]"
      >
        QA
      </div>
      <div v-show="!collapsed" class="min-w-0">
        <div class="truncate text-sm tracking-[0.28em] text-cyan-300/80">MIL-INDUSTRY</div>
        <div class="mt-1 truncate text-base font-semibold text-slate-100">质量评估系统</div>
      </div>
    </div>

    <el-menu
      :default-active="activeMenu"
      :collapse="collapsed"
      :collapse-transition="false"
      background-color="transparent"
      text-color="#94a3b8"
      active-text-color="#f8fafc"
      unique-opened
      router
      class="border-none !bg-transparent"
    >
      <template v-for="menu in menus" :key="menu.path">
        <el-sub-menu v-if="menu.children?.length" :index="menu.path">
          <template #title>
            <el-icon>
              <component :is="resolveIcon(menu.icon)" />
            </el-icon>
            <span>{{ menu.title }}</span>
          </template>
          <el-menu-item
            v-for="child in menu.children"
            :key="child.path"
            :index="child.path"
          >
            {{ child.title }}
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item v-else :index="menu.path">
          <el-icon>
            <component :is="resolveIcon(menu.icon)" />
          </el-icon>
          <span>{{ menu.title }}</span>
        </el-menu-item>
      </template>
    </el-menu>
  </aside>
</template>

<script setup lang="ts">
import * as icons from '@element-plus/icons-vue';
import { storeToRefs } from 'pinia';
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import { menus } from '@/constants/menu';
import { useAppStore } from '@/stores/app';

const route = useRoute();
const appStore = useAppStore();
const { sidebarCollapsed: collapsed, sidebarWidth } = storeToRefs(appStore);

const activeMenu = computed(() => route.path);

const resolveIcon = (iconName?: string) => {
  if (!iconName) {
    return icons.Menu;
  }

  return icons[iconName as keyof typeof icons] ?? icons.Menu;
};
</script>

<style scoped>
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 48px;
  margin-bottom: 8px;
  border-radius: 14px;
}

:deep(.el-menu-item.is-active),
:deep(.el-sub-menu__title:hover),
:deep(.el-menu-item:hover) {
  background: linear-gradient(90deg, rgba(14, 165, 233, 0.18), rgba(37, 99, 235, 0.3)) !important;
}

:deep(.el-sub-menu .el-menu-item) {
  min-width: auto;
  padding-left: 52px !important;
  background-color: rgba(15, 23, 42, 0.38) !important;
}
</style>
