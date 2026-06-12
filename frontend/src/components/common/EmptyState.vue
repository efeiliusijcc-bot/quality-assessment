<template>
  <div class="flex min-h-[180px] flex-col items-center justify-center rounded-3xl border border-dashed border-slate-300 bg-slate-50 p-8 text-center">
    <div class="flex h-12 w-12 items-center justify-center rounded-2xl bg-cyan-50 text-cyan-500">
      <el-icon :size="24"><InfoFilled /></el-icon>
    </div>
    <div class="mt-4 text-base font-semibold text-slate-800">{{ title }}</div>
    <div v-if="description" class="mt-2 max-w-md text-sm leading-6 text-slate-500">{{ description }}</div>
    <div v-if="$slots.default" class="mt-4">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue';

defineProps<{
  title: string;
  description?: string;
}>();
</script>
