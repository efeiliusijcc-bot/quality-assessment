<template>
  <div class="space-y-5">
    <PageIntroCard
      title="工艺参数预测评估与优化"
      description="围绕当前批次预测合格概率、七目标 Pareto 解集与知识图谱推理结果，形成可执行的工艺参数优化建议。"
      badge="PREDICTION"
      :metrics="dashboard.metrics"
    />

    <section class="grid gap-5 xl:grid-cols-[0.76fr_1.24fr]">
      <div class="space-y-5">
        <SectionCard title="预测合格概率" description="当预测概率低于阈值时，自动触发参数优化建议。" compact>
          <template #extra>
            <el-tag :type="showOptimization ? 'danger' : 'success'" effect="dark" round>
              {{ showOptimization ? '风险预警' : '状态正常' }}
            </el-tag>
          </template>
          <div class="rounded-[24px] border border-slate-200 bg-slate-950 p-5">
            <div class="mx-auto h-[230px] max-w-[320px]"><BaseChart :option="gaugeOption" /></div>
            <el-progress
              class="mt-4"
              :percentage="predictedProbability"
              :status="showOptimization ? 'exception' : 'success'"
              :stroke-width="14"
            />
          </div>
        </SectionCard>

        <SectionCard title="评估上下文" description="用于确认本次预测评估的批次、阈值和刷新配置。" compact>
          <div class="grid gap-3">
            <MetricTile label="当前批次/工位" :value="currentContextLabel" extra="评估对象" />
            <MetricTile label="通过阈值" :value="`${threshold}%`" extra="可在系统配置中调整" />
            <MetricTile label="预警阈值" :value="`${config.warningThreshold}%`" extra="低于阈值触发优化" />
          </div>
        </SectionCard>

        <SectionCard title="算法控制台" description="可在两种超多目标优化方法之间切换。" compact>
          <AlgorithmSelector v-model="algorithm" />
          <div class="mt-4 grid gap-3 md:grid-cols-2">
            <el-button type="primary" :loading="optimizationLoading" @click="handleRunOptimization">
              执行七目标优化
            </el-button>
            <el-button plain :loading="gatLoading" @click="handleRunGat">执行 GAT 图优化</el-button>
          </div>
        </SectionCard>
      </div>

      <div class="space-y-5">
        <SectionCard title="七目标优化矩阵" description="展示缺陷、质量、成本、效率和可靠性的联合优化目标。" compact>
          <ObjectiveMatrix :objectives="objectives" :values="recommendedObjectiveValues" />
        </SectionCard>

        <SectionCard title="推荐参数组合" description="展示 Pareto 前沿中系统推荐的工艺参数组合。" compact>
          <template #extra>
            <el-tag v-if="optimizationResult" type="info" effect="plain" round>
              {{ optimizationResult.algorithm }} / {{ optimizationResult.generations }} 代
            </el-tag>
          </template>
          <div v-if="optimizationResult?.recommendedSolution" class="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            <MetricTile
              v-for="item in recommendedParameterTiles"
              :key="item.label"
              :label="item.label"
              :value="item.value"
              extra="推荐参数"
            />
          </div>
          <EmptyState
            v-else
            title="暂无优化结果"
            description="点击“执行七目标优化”后，系统将返回 Pareto 解集和推荐参数组合。"
          />
        </SectionCard>
      </div>
    </section>

    <section class="grid gap-5 xl:grid-cols-[1fr_1fr]">
      <SectionCard title="仿真参数趋势" description="展示仿真温度、压力、链速与预测概率的动态变化。" compact>
        <div class="h-[320px] rounded-[24px] border border-slate-200 bg-slate-950 p-4">
          <BaseChart :option="simulationChartOption" />
        </div>
      </SectionCard>

      <SectionCard title="Pareto 前沿解集分布" description="横轴为合格率损失，纵轴为综合成本/缺陷代价，推荐解会高亮显示。" compact>
        <div class="h-[320px] rounded-[24px] border border-slate-200 bg-slate-950 p-4">
          <BaseChart :option="paretoScatterOption" />
        </div>
      </SectionCard>
    </section>

    <SectionCard title="Pareto 解集明细" description="展示不同参数组合在七个优化目标上的综合表现。" compact>
      <ParetoSolutionTable :solutions="optimizationResult?.paretoFront ?? []" />
    </SectionCard>

    <section class="grid gap-5 xl:grid-cols-[0.9fr_1.1fr]">
      <SectionCard v-if="dashboard.graphReasoning" title="知识图谱推理摘要" description="结合 Apriori 与 GAT 关系权重解释主要风险来源。" compact>
        <template #extra>
          <el-tag :type="dashboard.graphReasoning.riskScore > 60 ? 'danger' : 'success'" effect="dark" round>
            风险评分 {{ dashboard.graphReasoning.riskScore.toFixed(1) }}
          </el-tag>
        </template>
        <div class="grid gap-3 md:grid-cols-2">
          <MetricTile label="主要缺陷" :value="dashboard.graphReasoning.mainDefect || '暂无'" extra="图谱推理结果" />
          <MetricTile label="图谱节点" :value="dashboard.graphReasoning.statistics.nodeCount" extra="知识图谱规模" />
          <MetricTile label="关系总数" :value="dashboard.graphReasoning.statistics.relationCount" extra="规则 + Apriori + GAT" />
          <MetricTile label="工序链路" :value="stepChainLabel" extra="诊断链路" />
        </div>
        <div class="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950">
          {{ dashboard.graphReasoning.reasoningSummary || '暂无图谱推理结论。' }}
        </div>
        <div v-if="uniqueOptimizationHints.length > 0" class="mt-3 grid gap-2">
          <div
            v-for="(hint, index) in uniqueOptimizationHints"
            :key="`${index}-${hint}`"
            class="rounded-xl border border-amber-100 bg-white/70 px-3 py-2 text-sm leading-6 text-amber-950"
          >
            {{ index + 1 }}. {{ hint }}
          </div>
        </div>
      </SectionCard>

      <SectionCard title="GAT 注意力权重" description="展示权重最高的节点关联，用于解释参数与缺陷之间的潜在影响关系。" compact>
        <div v-if="gatResult" class="mb-4 rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-950">
          {{ gatResult.explanationSummary || gatResult.summary }}
        </div>
        <el-table :data="topGatAttentionEdges" stripe max-height="320">
          <el-table-column prop="from" label="来源节点" min-width="160" />
          <el-table-column prop="to" label="目标节点" min-width="160" />
          <el-table-column prop="relationType" label="关系类型" min-width="140" />
          <el-table-column label="注意力权重" width="120">
            <template #default="{ row }">{{ row.attentionWeight.toFixed(3) }}</template>
          </el-table-column>
        </el-table>
      </SectionCard>
    </section>

    <SectionCard title="知识图谱局部视图" description="展示当前批次下工序、参数、缺陷和质量指标之间的核心关系。" compact>
      <div class="h-[360px] rounded-[24px] border border-slate-200 bg-slate-950 p-4">
        <GraphVisualization :nodes="graphNodes" :edges="graphEdges" />
      </div>
    </SectionCard>

    <SectionCard title="评估历史记录" description="展示预测评估的历史数据记录。" compact>
      <div class="overflow-hidden rounded-[24px] border border-slate-200">
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
      <div class="mt-5 flex justify-end">
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
    </SectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type { EChartsCoreOption } from 'echarts/core';
import { storeToRefs } from 'pinia';
import { ElMessage } from 'element-plus';

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
import {
  fetchOptimizationObjectives,
  runManyObjectiveOptimization,
  sevenObjectiveFallback,
  type ManyObjectiveAlgorithm,
  type OptimizationObjective,
  type OptimizationResponse,
} from '@/api/optimization';
import AlgorithmSelector from '@/components/common/AlgorithmSelector.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import MetricTile from '@/components/common/MetricTile.vue';
import ObjectiveMatrix from '@/components/common/ObjectiveMatrix.vue';
import ParetoSolutionTable from '@/components/common/ParetoSolutionTable.vue';
import SectionCard from '@/components/common/SectionCard.vue';
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
const optimizationLoading = ref(false);
const gatLoading = ref(false);
const algorithm = ref<ManyObjectiveAlgorithm>('MANSGA_III');
const objectives = ref<OptimizationObjective[]>(sevenObjectiveFallback);

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
const graphNodes = ref<GraphVisualizationNode[]>([]);
const graphEdges = ref<GraphVisualizationEdge[]>([]);
const simulationData = ref<SimulationStreamData>({ points: [] });

const predictedProbability = computed(() => dashboard.value.predictedProbability);
const threshold = computed(() => dashboard.value.threshold || config.value.passThreshold || 85);
const showOptimization = computed(() => predictedProbability.value < threshold.value);
const stepChainLabel = computed(() => dashboard.value.graphReasoning.stepChain.join(' → ') || '暂无');
const uniqueOptimizationHints = computed(() => [...new Set(dashboard.value.graphReasoning.optimizationHints)]);
const recommendedObjectiveValues = computed(() => optimizationResult.value?.recommendedSolution?.objectiveValues ?? {});
const recommendedParameterTiles = computed(() =>
  Object.entries(optimizationResult.value?.recommendedSolution?.parameters ?? {})
    .slice(0, 6)
    .map(([label, value]) => ({ label, value: Number.isFinite(value) ? value.toFixed(3) : String(value) })),
);
const topGatAttentionEdges = computed(() =>
  gatResult.value ? [...gatResult.value.attentionEdges].sort((a, b) => b.attentionWeight - a.attentionWeight).slice(0, 8) : [],
);

const loadDashboard = async () => {
  dashboard.value = await fetchPredictionDashboard(currentBatchId.value);
};

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

const handleRunOptimization = async () => {
  if (!currentBatchId.value) {
    ElMessage.warning('请先选择批次');
    return;
  }
  optimizationLoading.value = true;
  try {
    optimizationResult.value = await runManyObjectiveOptimization(currentBatchId.value, {
      algorithm: algorithm.value,
      populationSize: 96,
      generations: 120,
    });
    ElMessage.success('七目标优化已完成');
  } finally {
    optimizationLoading.value = false;
  }
};

const handleRunGat = async () => {
  if (!currentBatchId.value) {
    ElMessage.warning('请先选择批次');
    return;
  }
  gatLoading.value = true;
  try {
    gatResult.value = await runGatOptimization(currentBatchId.value);
    ElMessage.success('GAT 图优化已完成');
  } finally {
    gatLoading.value = false;
  }
};

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
        width: 16,
        roundCap: true,
        itemStyle: { color: showOptimization.value ? '#ef4444' : '#10b981' },
      },
      pointer: { itemStyle: { color: '#e2e8f0' } },
      axisLine: { lineStyle: { width: 16, color: [[1, 'rgba(148, 163, 184, 0.18)']] } },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { color: '#94a3b8', distance: 16 },
      detail: { valueAnimation: false, formatter: '{value}%', color: '#f8fafc', fontSize: 32, offsetCenter: [0, '48%'] },
      title: { offsetCenter: [0, '78%'], color: '#94a3b8' },
      data: [{ value: predictedProbability.value, name: '预测合格率' }],
    },
  ],
}));

const simulationChartOption = computed<EChartsCoreOption>(() => {
  const points = simulationData.value.points;
  return {
    tooltip: createTooltip(),
    legend: { top: 8, textStyle: { color: '#cbd5e1' } },
    grid: { left: 24, right: 20, top: 58, bottom: 22, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: points.map((point) => point.time), axisLine: { lineStyle: { color: splitLineColor } }, axisLabel: { color: axisLabelColor } },
    yAxis: [
      { type: 'value', name: '参数值', axisLabel: { color: axisLabelColor }, splitLine: { lineStyle: { color: splitLineColor } } },
      { type: 'value', name: '概率(%)', axisLabel: { color: axisLabelColor }, splitLine: { show: false } },
    ],
    series: [
      { name: '仿真温度', type: 'line', smooth: true, data: points.map((point) => point.temperature) },
      { name: '仿真压力', type: 'line', smooth: true, data: points.map((point) => point.pressure) },
      { name: '仿真链速', type: 'line', smooth: true, data: points.map((point) => point.beltSpeed) },
      { name: '预测概率', type: 'line', yAxisIndex: 1, smooth: true, data: points.map((point) => point.probability) },
    ],
  };
});

const paretoScatterOption = computed<EChartsCoreOption>(() => {
  const front = optimizationResult.value?.paretoFront ?? [];
  const recommended = optimizationResult.value?.recommendedSolution;
  const mapPoint = (solution: NonNullable<OptimizationResponse['recommendedSolution']>) => [
    solution.objectiveValues.negative_pass_rate ?? solution.objectiveValues.pass_rate ?? 0,
    solution.objectiveValues.cost ?? 0,
    solution.objectiveValues.negative_reliability ?? solution.objectiveValues.reliability ?? 0,
  ];
  return {
    tooltip: createTooltip(),
    grid: { left: 36, right: 18, top: 30, bottom: 34, containLabel: true },
    xAxis: { type: 'value', name: '合格率损失', axisLabel: { color: axisLabelColor }, splitLine: { lineStyle: { color: splitLineColor } } },
    yAxis: { type: 'value', name: '成本', axisLabel: { color: axisLabelColor }, splitLine: { lineStyle: { color: splitLineColor } } },
    series: [
      { name: 'Pareto 解', type: 'scatter', symbolSize: 10, data: front.filter((item) => item !== recommended).map(mapPoint) },
      { name: '推荐解', type: 'scatter', symbolSize: 18, data: recommended ? [mapPoint(recommended)] : [] },
    ],
  };
});

onMounted(async () => {
  objectives.value = await fetchOptimizationObjectives();
  await Promise.all([loadDashboard(), loadHistory(), loadGraphVisualization(), loadSimulationData()]);
});
</script>
