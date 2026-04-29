<template>
  <section class="content-card p-8">
    <div class="flex items-start justify-between gap-6">
      <div>
        <h1 class="panel-title">{{ title }}</h1>
        <p class="panel-subtitle">{{ description }}</p>
      </div>
      <div
        class="rounded-2xl border border-cyan-500/20 bg-slate-950 px-4 py-3 text-right text-sm text-slate-300 shadow-[0_12px_30px_rgba(2,132,199,0.18)]"
      >
        <div class="tracking-[0.16em] text-cyan-300/80">CURRENT MODULE</div>
        <div class="mt-2 text-base font-semibold text-white">{{ badge }}</div>
      </div>
    </div>

    <div class="mt-8 grid gap-4 md:grid-cols-3">
      <div
        v-for="metric in metrics"
        :key="metric.label"
        class="rounded-2xl border border-slate-200 bg-slate-50 p-5"
      >
        <div class="text-sm text-slate-500">{{ metric.label }}</div>
        <div class="mt-3 text-3xl font-bold text-slate-900">{{ metric.value }}</div>
        <div class="mt-2 text-sm text-slate-500">{{ metric.extra }}</div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
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
