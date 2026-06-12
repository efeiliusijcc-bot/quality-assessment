<template>
  <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
    <div class="mb-2 text-sm font-medium text-slate-500">优化算法</div>
    <el-segmented :model-value="modelValue" :options="options" @update:model-value="handleChange" />
    <div class="mt-3 text-xs leading-5 text-slate-500">
      MANSGA-III 适合超多目标 Pareto 搜索；PUSH_PULL 适合约束条件较强的参数空间。
    </div>
  </div>
</template>

<script setup lang="ts">
export type ManyObjectiveAlgorithm = 'MANSGA_III' | 'PUSH_PULL';

const emit = defineEmits<{
  (e: 'update:modelValue', value: ManyObjectiveAlgorithm): void;
}>();

defineProps<{
  modelValue: ManyObjectiveAlgorithm;
}>();

const options = [
  { label: 'MANSGA-III', value: 'MANSGA_III' },
  { label: 'PUSH-PULL', value: 'PUSH_PULL' },
];

const handleChange = (value: string | number | boolean) => {
  emit('update:modelValue', value as ManyObjectiveAlgorithm);
};
</script>
