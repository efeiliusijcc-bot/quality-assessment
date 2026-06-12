<template>
  <section :class="['content-card', paddingClass]">
    <div v-if="title || description || $slots.extra" class="mb-5 flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
      <div>
        <h2 v-if="title" class="panel-title">{{ title }}</h2>
        <p v-if="description" class="panel-subtitle">{{ description }}</p>
      </div>
      <div v-if="$slots.extra" class="shrink-0">
        <slot name="extra" />
      </div>
    </div>
    <slot />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{
    title?: string;
    description?: string;
    compact?: boolean;
  }>(),
  {
    compact: false,
  },
);

const paddingClass = computed(() => (props.compact ? 'p-5' : 'p-6'));
</script>
