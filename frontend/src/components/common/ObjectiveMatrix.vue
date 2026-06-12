<template>
  <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
    <div
      v-for="objective in objectives"
      :key="objective.code"
      class="rounded-2xl border border-slate-200 bg-slate-50 p-4"
    >
      <div class="flex items-start justify-between gap-3">
        <div>
          <div class="text-sm font-semibold text-slate-900">{{ objective.name }}</div>
          <div class="mt-1 text-xs text-slate-500">{{ objective.code }}</div>
        </div>
        <el-tag type="info" effect="plain" round>{{ objective.direction === 'MAX' ? '最大化' : '最小化' }}</el-tag>
      </div>
      <div class="mt-3 text-xl font-bold text-slate-900">
        {{ formatValue(values?.[objective.code]) }}
      </div>
      <div class="mt-2 line-clamp-2 text-xs leading-5 text-slate-500">{{ objective.description || '用于工艺参数组合优选。' }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { OptimizationObjective } from '@/api/optimization';

defineProps<{
  objectives: OptimizationObjective[];
  values?: Record<string, number>;
}>();

const formatValue = (value?: number) => {
  if (value === undefined || Number.isNaN(value)) return '--';
  if (Math.abs(value) < 1) return value.toFixed(4);
  return value.toFixed(2);
};
</script>
