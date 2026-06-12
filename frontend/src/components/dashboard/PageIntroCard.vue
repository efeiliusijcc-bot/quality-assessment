<template>
  <section class="content-card p-5 md:p-6">
    <div class="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
      <div class="max-w-4xl">
        <div class="flex items-center gap-3">
          <div class="hidden h-10 w-10 items-center justify-center rounded-2xl border border-cyan-200 bg-cyan-50 text-cyan-500 md:flex">
            <span class="text-sm font-bold">QA</span>
          </div>
          <div>
            <h1 class="panel-title">{{ title }}</h1>
            <p class="panel-subtitle">{{ description }}</p>
          </div>
        </div>
      </div>
      <div
        class="w-fit rounded-2xl border border-cyan-500/20 bg-slate-950 px-4 py-3 text-left text-sm text-slate-300 shadow-[0_12px_30px_rgba(2,132,199,0.14)] xl:text-right"
      >
        <div class="tracking-[0.16em] text-cyan-300/80">CURRENT MODULE</div>
        <div class="mt-1 text-base font-semibold text-white">{{ badge }}</div>
      </div>
    </div>

    <div class="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      <MetricTile
        v-for="metric in metrics"
        :key="metric.label"
        :label="metric.label"
        :value="metric.value"
        :extra="metric.extra"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import MetricTile from '@/components/common/MetricTile.vue';

defineProps<{
  title: string;
  description: string;
  badge: string;
  metrics: Array<{
    label: string;
    value: string;
    extra: string;
  }>;
}>();
</script>
