<template>
  <div class="overflow-hidden rounded-3xl border border-slate-200 bg-slate-50">
    <el-table :data="rows" stripe max-height="360">
      <el-table-column type="index" label="#" width="56" />
      <el-table-column label="推荐参数" min-width="220">
        <template #default="{ row }">
          <div class="flex flex-wrap gap-2">
            <el-tag v-for="item in row.parameterPairs" :key="item" type="primary" effect="plain" round>
              {{ item }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="七目标结果" min-width="260">
        <template #default="{ row }">
          <div class="grid gap-1 text-xs text-slate-600 md:grid-cols-2">
            <span v-for="item in row.objectivePairs" :key="item">{{ item }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="拥挤距离" width="110">
        <template #default="{ row }">{{ row.crowdingDistance }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ParetoSolution } from '@/api/optimization';

const props = defineProps<{
  solutions: ParetoSolution[];
}>();

const toPairs = (data: Record<string, number>, limit = 6) =>
  Object.entries(data || {})
    .slice(0, limit)
    .map(([key, value]) => `${key}: ${Number.isFinite(value) ? value.toFixed(Math.abs(value) < 1 ? 4 : 2) : value}`);

const rows = computed(() =>
  props.solutions.map((solution) => ({
    ...solution,
    parameterPairs: toPairs(solution.parameters, 5),
    objectivePairs: toPairs(solution.objectiveValues, 8),
    crowdingDistance: Number.isFinite(solution.crowdingDistance) ? solution.crowdingDistance.toFixed(3) : '--',
  })),
);
</script>
