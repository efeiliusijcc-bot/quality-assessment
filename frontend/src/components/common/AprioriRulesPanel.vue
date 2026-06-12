<template>
  <div class="space-y-3">
    <div
      v-for="(rule, index) in topRules"
      :key="`${index}-${rule.antecedentLabel}-${rule.consequentLabel}`"
      class="rounded-2xl border border-amber-200 bg-amber-50 p-4"
    >
      <div class="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
        <div class="text-sm font-semibold text-amber-950">
          {{ rule.antecedentLabel || rule.antecedents?.join(' + ') }} → {{ rule.consequentLabel || rule.consequents?.join(' + ') }}
        </div>
        <div class="flex flex-wrap gap-2">
          <el-tag size="small" type="warning" effect="plain" round>支持度 {{ format(rule.support) }}</el-tag>
          <el-tag size="small" type="warning" effect="plain" round>置信度 {{ format(rule.confidence) }}</el-tag>
          <el-tag size="small" type="warning" effect="plain" round>提升度 {{ format(rule.lift) }}</el-tag>
        </div>
      </div>
      <div v-if="rule.reason" class="mt-2 text-xs leading-5 text-amber-800">{{ rule.reason }}</div>
    </div>
    <EmptyState
      v-if="topRules.length === 0"
      title="暂无 Apriori 规则"
      description="请先执行关联规则挖掘，或检查当前批次是否包含足够的参数与缺陷记录。"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import EmptyState from '@/components/common/EmptyState.vue';
import type { AprioriRule } from '@/api/graph';

const props = withDefaults(
  defineProps<{
    rules: AprioriRule[];
    limit?: number;
  }>(),
  {
    limit: 8,
  },
);

const topRules = computed(() => [...props.rules].sort((a, b) => (b.lift || 0) - (a.lift || 0)).slice(0, props.limit));
const format = (value?: number) => (value === undefined ? '--' : value.toFixed(3));
</script>
