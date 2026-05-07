<template>
  <div class="space-y-6">
    <PageIntroCard
      title="工艺参数预测评估与优化"
      description="展示当前工艺组合的预测合格概率，并在低于阈值时给出优化方案。"
      badge="PREDICTION"
      :metrics="dashboard.metrics"
    />

    <section class="content-card p-6">
      <div class="grid gap-4 md:grid-cols-[1fr_220px_220px]">
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">评估上下文</div>
          <div class="mt-2 text-lg font-semibold text-slate-900">{{ currentContextLabel }}</div>
          <div class="mt-2 text-sm text-slate-500">预警阈值 {{ config.warningThreshold }}%</div>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">通过阈值</div>
          <div class="mt-2 flex items-center gap-3">
            <el-input-number
              :model-value="config.passThreshold"
              :min="70"
              :max="99"
              :controls="false"
              class="!w-full"
              @update:model-value="handleThresholdChange"
            />
          </div>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">刷新周期</div>
          <div class="mt-2 text-lg font-semibold text-slate-900">{{ config.refreshIntervalMs }} ms</div>
        </div>
      </div>
    </section>

    <section class="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
      <div class="space-y-6">
        <section class="content-card p-8">
          <div class="flex items-center justify-between gap-4">
            <div>
              <h2 class="panel-title">仿真预测结果</h2>
              <p class="panel-subtitle">基于当前工艺组合预测批次达到合格标准的概率。</p>
            </div>
            <el-tag :type="predictedProbability >= threshold ? 'success' : 'danger'" effect="dark" round>
              阈值 {{ threshold }}%
            </el-tag>
          </div>

          <div class="mt-8 rounded-[28px] border border-slate-200 bg-slate-950 p-6">
            <div class="mx-auto h-[280px] max-w-[360px]">
              <BaseChart :option="gaugeOption" />
            </div>
            <el-progress
              class="mt-6"
              :percentage="predictedProbability"
              :status="predictedProbability >= threshold ? 'success' : 'exception'"
              :stroke-width="16"
            />
          </div>
        </section>

        <section class="content-card p-8">
          <div>
            <h2 class="panel-title">仿真参数趋势</h2>
            <p class="panel-subtitle">仿真参数与预测概率随时间变化趋势。</p>
          </div>
          <div class="mt-6 h-[360px] rounded-[28px] border border-slate-200 bg-slate-950 p-5">
            <BaseChart :option="simulationChartOption" />
          </div>
        </section>

        <section class="content-card p-8">
          <h2 class="panel-title">优化触发条件</h2>
          <div class="mt-5 grid gap-4">
            <div
              v-for="item in dashboard.triggerCards"
              :key="item.label"
              class="rounded-2xl border border-slate-200 bg-slate-50 p-4"
            >
              <div class="text-sm text-slate-500">{{ item.label }}</div>
              <div class="mt-2 text-xl font-semibold text-slate-900">{{ item.value }}</div>
              <div class="mt-2 text-sm text-slate-500">{{ item.tip }}</div>
            </div>
          </div>
        </section>
      </div>

      <div class="space-y-6">
        <section class="content-card p-8">
          <div class="flex items-center justify-between gap-4">
            <div>
              <h2 class="panel-title">工艺参数优化方案</h2>
              <p class="panel-subtitle">当预测概率低于阈值时，展示推荐参数方案。</p>
            </div>
            <el-tag :type="showOptimization ? 'danger' : 'success'" effect="dark" round>
              {{ showOptimization ? '已触发优化' : '无需优化' }}
            </el-tag>
          </div>

          <div v-if="showOptimization" class="mt-6 rounded-3xl border border-rose-200 bg-rose-50 p-5">
            <div class="text-sm text-rose-700">优化提示</div>
            <div class="mt-3 text-base leading-7 text-rose-900">
              当前合格概率低于阈值，建议根据多目标搜索结果调整工艺参数，优先抑制主要缺陷链路。
            </div>
          </div>

          <div class="mt-6 rounded-3xl border border-slate-200 bg-slate-50 p-4">
            <el-table :data="dashboard.optimizationTable" stripe>
              <el-table-column prop="parameter" label="工艺参数" min-width="140" />
              <el-table-column prop="current" label="当前参数" min-width="120" />
              <el-table-column prop="recommended" label="建议参数" min-width="120" />
              <el-table-column prop="effect" label="优化效果" min-width="180" />
            </el-table>
          </div>

          <div class="mt-6 rounded-3xl border border-slate-200 bg-slate-950 p-5 text-slate-100">
            <div class="text-sm tracking-[0.18em] text-cyan-300/75">OPTIMIZATION SUMMARY</div>
            <div class="mt-4 grid gap-4 md:grid-cols-3">
              <div
                v-for="item in dashboard.optimizationSummary"
                :key="item.label"
                class="rounded-2xl border border-white/10 bg-white/5 p-4"
              >
                <div class="text-sm text-slate-400">{{ item.label }}</div>
                <div class="mt-2 text-xl font-semibold text-white">{{ item.value }}</div>
              </div>
            </div>
          </div>
        </section>

        <section v-if="dashboard.graphReasoning" class="content-card p-8">
          <div class="flex items-center justify-between gap-4">
            <div>
              <h2 class="panel-title">知识图谱推理摘要</h2>
              <p class="panel-subtitle">识别主要缺陷类型和参数影响链路。</p>
            </div>
            <el-tag :type="dashboard.graphReasoning.riskScore > 60 ? 'danger' : 'success'" effect="dark" round>
              风险评分 {{ dashboard.graphReasoning.riskScore.toFixed(1) }}
            </el-tag>
          </div>

          <div class="mt-5 grid gap-4 md:grid-cols-2">
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm text-slate-500">主要缺陷</div>
              <div class="mt-2 text-xl font-semibold text-slate-900">{{ dashboard.graphReasoning.mainDefect }}</div>
            </div>
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm text-slate-500">推理结论</div>
              <div class="mt-2 text-sm font-medium text-slate-700">{{ dashboard.graphReasoning.reasoningSummary }}</div>
            </div>
          </div>

          <div class="mt-4 grid gap-4 md:grid-cols-3">
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm text-slate-500">图谱节点数</div>
              <div class="mt-1 text-lg font-semibold text-slate-900">{{ dashboard.graphReasoning.statistics.nodeCount }}</div>
            </div>
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm text-slate-500">关系总数</div>
              <div class="mt-1 text-lg font-semibold text-slate-900">{{ dashboard.graphReasoning.statistics.relationCount }}</div>
            </div>
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm text-slate-500">工序链路</div>
              <div class="mt-1 text-sm text-slate-700">{{ stepChainLabel }}</div>
            </div>
          </div>

          <div class="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4">
            <div class="mb-3 text-sm font-medium text-amber-700">图谱优化依据 optimizationHints</div>
            <div v-if="uniqueOptimizationHints.length > 0" class="grid gap-2">
              <div
                v-for="(hint, index) in uniqueOptimizationHints"
                :key="`${index}-${hint}`"
                class="rounded-xl border border-amber-100 bg-white/70 px-3 py-2 text-sm leading-6 text-amber-950"
              >
                {{ index + 1 }}. {{ hint }}
              </div>
            </div>
            <div v-else class="text-sm text-amber-700">
              暂无图谱优化建议。
            </div>
          </div>

          <div class="mt-6 h-[400px] rounded-[28px] border border-slate-200 bg-slate-950 p-5">
            <GraphVisualization :nodes="graphNodes" :edges="graphEdges" />
          </div>
        </section>
      </div>
    </section>

    <section class="content-card p-8">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h2 class="panel-title">Pareto 前沿解集分布</h2>
          <p class="panel-subtitle">展示合格率、代价和可靠性的权衡结果。</p>
        </div>
        <div class="flex items-center gap-3">
          <el-button type="primary" plain :loading="gatLoading" @click="handleRunGat">执行 GAT 图优化</el-button>
          <el-tag v-if="optimizationResult" type="info" effect="plain" round>
            {{ optimizationResult.algorithm }} / {{ optimizationResult.generations }} 代
          </el-tag>
        </div>
      </div>

      <div v-if="gatResult" class="mt-4 rounded-2xl border border-emerald-200 bg-emerald-50 p-4">
        <div class="text-sm text-emerald-700">GAT 优化结果</div>
        <div class="mt-2 text-sm text-emerald-900">{{ gatResult.summary }}</div>
        <div class="mt-2 grid gap-2 md:grid-cols-4">
          <div class="text-xs text-emerald-600">节点数: {{ gatResult.nodeCount }}</div>
          <div class="text-xs text-emerald-600">边数: {{ gatResult.edgeCount }}</div>
          <div class="text-xs text-emerald-600">注意力头: {{ gatResult.attentionHeads }}</div>
          <div class="text-xs text-emerald-600">嵌入维度: {{ gatResult.embeddingDim }}</div>
        </div>
      </div>

      <div class="mt-6 h-[380px] rounded-3xl border border-slate-200 bg-slate-950 p-4">
        <BaseChart :option="paretoScatterOption" />
      </div>

      <div v-if="optimizationResult" class="mt-4 grid gap-3 md:grid-cols-3">
        <div
          v-for="stat in optimizationStats"
          :key="stat.label"
          class="rounded-2xl border border-slate-200 bg-slate-50 p-4"
        >
          <div class="text-sm text-slate-500">{{ stat.label }}</div>
          <div class="mt-1 text-lg font-semibold text-slate-900">{{ stat.value }}</div>
        </div>
      </div>
    </section>

    <section class="content-card p-8">
      <h2 class="panel-title">评估历史记录</h2>
      <p class="panel-subtitle">展示预测评估的历史数据记录。</p>

      <div class="mt-6 overflow-hidden rounded-[24px] border border-slate-200">
        <el-table :data="historyRecords" border stripe v-loading="historyLoading" class="export-table">
          <el-table-column prop="sampledAt" label="采样时间" min-width="180" />
          <el-table-column prop="batchId" label="批次号" min-width="140" />
          <el-table-column prop="station" label="工位" min-width="140" />
          <el-table-column prop="temperature" label="温度" min-width="100">
            <template #default="{ row }">{{ row.temperature.toFixed(1) }}</template>
          </el-table-column>
          <el-table-column prop="pressure" label="压力" min-width="100">
            <template #default="{ row }">{{ row.pressure.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="currentValue" label="电流" min-width="100">
            <template #default="{ row }">{{ row.currentValue.toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div class="mt-6 flex justify-end">
        <el-pagination
          v-model:current-page="historyPage"
          v-model:page-size="historyPageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="historyTotal"
          @size-change="loadHistory"
          @current-change="loadHistory"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type { EChartsCoreOption } from 'echarts/core';
import { storeToRefs } from 'pinia';

import {
  fetchAssessmentHistory,
  fetchPredictionDashboard,
  fetchSimulationStream,
  type AssessmentHistoryItem,
  type PredictionDashboardData,
  type SimulationStreamData,
} from '@/api/assessment';
import {
  fetchGraphVisualization,
  runGatOptimization,
  type GatOptimizationResponse,
  type GraphVisualizationEdge,
  type GraphVisualizationNode,
} from '@/api/graph';
import { runOptimization, type OptimizationResponse } from '@/api/optimization';
import BaseChart from '@/components/charts/BaseChart.vue';
import GraphVisualization from '@/components/charts/GraphVisualization.vue';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';
import { useAssessmentStore } from '@/stores/assessment';
import { axisLabelColor, createTooltip, splitLineColor } from '@/utils/chart';

const assessmentStore = useAssessmentStore();
const { config, currentBatchId, currentContextLabel } = storeToRefs(assessmentStore);

const historyRecords = ref<AssessmentHistoryItem[]>([]);
const historyTotal = ref(0);
const historyPage = ref(1);
const historyPageSize = ref(10);
const historyLoading = ref(false);

const dashboard = ref<PredictionDashboardData>({
  metrics: [],
  predictedProbability: 0,
  threshold: 0,
  triggerCards: [],
  optimizationTable: [],
  optimizationSummary: [],
  graphReasoning: {
    riskScore: 0,
    mainDefect: '',
    defectChain: [],
    parameterChain: [],
    stepChain: [],
    reasoningSummary: '',
    optimizationHints: [],
    statistics: {
      nodeCount: 0,
      relationCount: 0,
      ruleRelationCount: 0,
      aprioriRelationCount: 0,
      defectCount: 0,
      parameterCount: 0,
    },
  },
});

const optimizationResult = ref<OptimizationResponse | null>(null);
const gatResult = ref<GatOptimizationResponse | null>(null);
const gatLoading = ref(false);
const graphNodes = ref<GraphVisualizationNode[]>([]);
const graphEdges = ref<GraphVisualizationEdge[]>([]);
const simulationData = ref<SimulationStreamData>({ points: [] });

const predictedProbability = computed(() => dashboard.value.predictedProbability);
const threshold = computed(() => dashboard.value.threshold);
const showOptimization = computed(() => predictedProbability.value < threshold.value);
const stepChainLabel = computed(() => dashboard.value.graphReasoning.stepChain.join(' -> ') || '暂无');
const uniqueOptimizationHints = computed(() => [...new Set(dashboard.value.graphReasoning.optimizationHints)]);

const loadHistory = async () => {
  historyLoading.value = true;
  try {
    const result = await fetchAssessmentHistory(currentBatchId.value, historyPage.value, historyPageSize.value);
    historyRecords.value = result.records;
    historyTotal.value = result.total;
  } finally {
    historyLoading.value = false;
  }
};

const loadGraphVisualization = async () => {
  try {
    const result = await fetchGraphVisualization(currentBatchId.value);
    graphNodes.value = result.nodes;
    graphEdges.value = result.edges;
  } catch {
    graphNodes.value = [];
    graphEdges.value = [];
  }
};

const loadSimulationData = async () => {
  try {
    simulationData.value = await fetchSimulationStream(currentBatchId.value);
  } catch {
    simulationData.value = { points: [] };
  }
};

const optimizationStats = computed(() => {
  if (!optimizationResult.value) return [];
  const statistics = optimizationResult.value.statistics;
  return [
    { label: '计算耗时', value: `${(statistics.elapsedTimeMs / 1000).toFixed(1)}s` },
    { label: '总评估次数', value: String(statistics.totalEvaluations) },
    { label: 'Pareto 解集大小', value: String(statistics.paretoFrontSize) },
  ];
});

const gaugeOption = computed<EChartsCoreOption>(() => ({
  series: [
    {
      type: 'gauge',
      startAngle: 210,
      endAngle: -30,
      min: 0,
      max: 100,
      progress: {
        show: true,
        width: 18,
        roundCap: true,
        itemStyle: { color: showOptimization.value ? '#ef4444' : '#10b981' },
      },
      pointer: {
        show: true,
        itemStyle: { color: '#e2e8f0' },
      },
      axisLine: {
        lineStyle: {
          width: 18,
          color: [[1, 'rgba(148, 163, 184, 0.18)']],
        },
      },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { color: '#94a3b8', distance: 18 },
      detail: {
        valueAnimation: false,
        formatter: '{value}%',
        color: '#f8fafc',
        fontSize: 34,
        offsetCenter: [0, '50%'],
      },
      title: {
        offsetCenter: [0, '80%'],
        color: '#94a3b8',
      },
      data: [{ value: predictedProbability.value, name: '预测合格率' }],
    },
  ],
}));

const simulationChartOption = computed<EChartsCoreOption>(() => {
  const points = simulationData.value.points;
  return {
    tooltip: createTooltip(),
    legend: {
      top: 10,
      textStyle: { color: '#cbd5e1' },
    },
    grid: {
      left: 24,
      right: 20,
      top: 64,
      bottom: 24,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: points.map((point) => point.time),
      axisLine: { lineStyle: { color: splitLineColor } },
      axisLabel: { color: axisLabelColor },
    },
    yAxis: [
      {
        type: 'value',
        name: '温度/压力/链速',
        axisLabel: { color: axisLabelColor },
        splitLine: { lineStyle: { color: splitLineColor } },
      },
      {
        type: 'value',
        name: '概率(%)',
        axisLabel: { color: axisLabelColor },
        splitLine: { show: false },
      },
    ],
    series: [
      { name: '仿真温度', type: 'line', smooth: true, data: points.map((point) => point.temperature), lineStyle: { color: '#22d3ee', width: 2 }, itemStyle: { color: '#22d3ee' } },
      { name: '仿真压力', type: 'line', smooth: true, data: points.map((point) => point.pressure), lineStyle: { color: '#60a5fa', width: 2 }, itemStyle: { color: '#60a5fa' } },
      { name: '仿真链速', type: 'line', smooth: true, data: points.map((point) => point.beltSpeed), lineStyle: { color: '#10b981', width: 2 }, itemStyle: { color: '#10b981' } },
      { name: '预测概率', type: 'line', yAxisIndex: 1, smooth: true, data: points.map((point) => point.probability), lineStyle: { color: '#f59e0b', width: 3 }, itemStyle: { color: '#f59e0b' }, areaStyle: { color: 'rgba(245, 158, 11, 0.12)' } },
    ],
  };
});

const paretoScatterOption = computed<EChartsCoreOption>(() => {
  const front = optimizationResult.value?.paretoFront ?? [];
  const recommended = optimizationResult.value?.recommendedSolution;

  const normalPoints = front
    .filter((solution) => solution !== recommended)
    .map((solution) => ({
      value: [
        (solution.objectiveValues.pass_rate ?? 0) * 100,
        solution.objectiveValues.cost ?? 0,
        (solution.objectiveValues.reliability ?? 0) * 100,
      ],
    }));

  const highlightPoint = recommended
    ? [{
        value: [
          (recommended.objectiveValues.pass_rate ?? 0) * 100,
          recommended.objectiveValues.cost ?? 0,
          (recommended.objectiveValues.reliability ?? 0) * 100,
        ],
      }]
    : [];

  return {
    backgroundColor: 'transparent',
    tooltip: {
      backgroundColor: 'rgba(2, 6, 23, 0.9)',
      borderColor: 'rgba(34, 211, 238, 0.2)',
      textStyle: { color: '#e2e8f0' },
      formatter: (params: { value: number[] }) => {
        const [passRate, cost, reliability] = params.value;
        return `合格率 ${(passRate ?? 0).toFixed(1)}%<br/>代价 ${(cost ?? 0).toFixed(3)}<br/>可靠性 ${(reliability ?? 0).toFixed(1)}%`;
      },
    },
    grid: {
      left: 60,
      right: 30,
      top: 30,
      bottom: 50,
    },
    xAxis: {
      type: 'value',
      name: '合格率(%)',
      nameTextStyle: { color: '#94a3b8' },
      axisLabel: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.15)' } },
    },
    yAxis: {
      type: 'value',
      name: '代价',
      nameTextStyle: { color: '#94a3b8' },
      axisLabel: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.15)' } },
    },
    series: [
      {
        name: 'Pareto 解',
        type: 'scatter',
        data: normalPoints,
        symbolSize: 12,
        itemStyle: { color: 'rgba(56, 189, 248, 0.7)' },
      },
      {
        name: '推荐方案',
        type: 'scatter',
        data: highlightPoint,
        symbolSize: 22,
        itemStyle: {
          color: '#f59e0b',
          borderColor: '#fbbf24',
          borderWidth: 2,
          shadowBlur: 10,
          shadowColor: 'rgba(245, 158, 11, 0.5)',
        },
        label: {
          show: true,
          formatter: '推荐',
          color: '#fbbf24',
          position: 'top',
          fontSize: 12,
          fontWeight: 'bold',
        },
      },
    ],
  };
});

const loadDashboard = async () => {
  dashboard.value = await fetchPredictionDashboard(currentBatchId.value);
  assessmentStore.updateConfig({
    passThreshold: dashboard.value.threshold,
  });

  try {
    optimizationResult.value = await runOptimization(currentBatchId.value);
  } catch {
    optimizationResult.value = null;
  }
};

const handleRunGat = async () => {
  gatLoading.value = true;
  try {
    gatResult.value = await runGatOptimization(currentBatchId.value);
  } catch {
    gatResult.value = null;
  } finally {
    gatLoading.value = false;
  }
};

const handleThresholdChange = (value: number | null | undefined) => {
  if (typeof value !== 'number') return;
  assessmentStore.updateConfig({ passThreshold: value });
  dashboard.value.threshold = value;
};

onMounted(() => {
  // 先显示页面，异步加载数据
  Promise.all([
    assessmentStore.loadBatches(),
    assessmentStore.loadStations(),
  ]).then(() => {
    void loadDashboard();
    void loadHistory();
    void loadSimulationData();
    void loadGraphVisualization();
  }).catch((error) => {
    console.error('Failed to load initial data:', error);
  });
});
</script>
