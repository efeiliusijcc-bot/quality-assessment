<template>
  <div
    class="rounded-2xl border border-slate-200 bg-slate-50/95 p-4 transition hover:-translate-y-0.5 hover:border-cyan-200 hover:bg-white"
  >
    <div class="flex items-center justify-between gap-3">
      <div class="text-sm text-slate-500">{{ label }}</div>
      <el-tag v-if="tag" :type="tagType" effect="plain" round>{{ tag }}</el-tag>
    </div>
    <div class="mt-2 text-2xl font-bold tracking-tight text-slate-900">{{ value }}</div>
    <div v-if="extra" class="mt-1 text-sm text-slate-500">{{ extra }}</div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label: string;
  value: string | number;
  extra?: string;
  tag?: string;
  tagType?: 'success' | 'warning' | 'info' | 'primary' | 'danger';
}>();
</script>
