<template>
  <div class="space-y-6">
    <PageIntroCard
      title="工艺参数研判评估"
      description="针对异常批次进行参数对比、诊断分析和知识图谱关联研判。"
      badge="JUDGMENT"
      :metrics="dashboard.metrics"
    />

    <section class="content-card p-6">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div class="text-sm text-slate-500">当前评估上下文</div>
          <div class="mt-2 text-lg font-semibold text-slate-900">{{ currentContextLabel }}</div>
        </div>
        <div class="flex items-center gap-3">
          <span class="text-sm text-slate-500">异常样本</span>
          <el-tag type="danger" round>{{ activeSampleId || 'SAMPLE-07' }}</el-tag>
          <el-button type="primary" plain @click="switchSample">切换样本</el-button>
        </div>
      </div>
    </section>

    <section class="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
      <div class="space-y-6">
        <section class="content-card p-8">
          <div>
            <h2 class="panel-title">工艺参数数据流</h2>
            <p class="panel-subtitle">设备运行参数的时间序列趋势。</p>
          </div>
          <div class="mt-6 h-[360px] rounded-[28px] border border-slate-200 bg-slate-950 p-5">
            <BaseChart :option="streamChartOption" />
          </div>
        </section>

        <section class="content-card p-8">
          <div class="flex items-center justify-between gap-4">
            <div>
              <h2 class="panel-title">工艺参数雷达评估</h2>
              <p class="panel-subtitle">异常样本与目标工艺窗口对比。</p>
            </div>
            <el-tag type="danger" effect="dark" round>异常样本</el-tag>
          </div>
          <div class="mt-6 h-[360px] rounded-[28px] border border-slate-200 bg-slate-950 p-5">
            <BaseChart :option="radarOption" />
          </div>
        </section>

        <section class="content-card p-8">
          <div>
            <h2 class="panel-title">关键参数偏差对比</h2>
            <p class="panel-subtitle">当前工艺值和目标工艺值的并列比较。</p>
          </div>
          <div class="mt-6 h-[320px] rounded-[28px] border border-slate-200 bg-slate-950 p-5">
            <BaseChart :option="barOption" />
          </div>
        </section>
      </div>

      <div class="space-y-6">
        <section class="content-card p-8">
          <h2 class="panel-title">研判结果报告</h2>
          <p class="panel-subtitle">结合参数偏差与图谱推理输出诊断意见。</p>

          <div class="mt-6 rounded-3xl border border-amber-200 bg-amber-50 p-5">
            <div class="text-sm text-amber-700">核心结论</div>
            <div class="mt-3 text-lg font-semibold text-amber-900">{{ dashboard.coreConclusion }}</div>
            <div class="mt-2 text-sm leading-7 text-amber-800">{{ dashboard.coreDescription }}</div>
          </div>

          <div class="mt-6 space-y-4">
            <div
              v-for="item in dashboard.diagnosisItems"
              :key="item.title"
              class="rounded-3xl border border-slate-200 bg-slate-50 p-5"
            >
              <div class="text-base font-semibold text-slate-900">{{ item.title }}</div>
              <div class="mt-3 text-sm leading-7 text-slate-600">{{ item.content }}</div>
            </div>
          </div>

          <div class="mt-6 rounded-3xl border border-slate-200 bg-slate-950 p-5 text-slate-100">
            <div class="text-sm tracking-[0.18em] text-cyan-300/75">DIAGNOSIS ACTION</div>
            <div class="mt-4 grid gap-4">
              <div
                v-for="item in dashboard.actionItems"
                :key="item.label"
                class="rounded-2xl border border-white/10 bg-white/5 p-4"
              >
                <div class="text-sm text-slate-400">{{ item.label }}</div>
                <div class="mt-2 font-semibold text-white">{{ item.value }}</div>
              </div>
            </div>
          </div>
        </section>

        <section class="content-card p-8">
          <div class="flex items-center justify-between gap-4">
            <div>
              <h2 class="panel-title">图谱推理摘要</h2>
              <p class="panel-subtitle">批次缺陷链路和规则关联结果。</p>
            </div>
            <el-tag :type="dashboard.graphReasoning?.riskScore > 60 ? 'danger' : 'success'" effect="dark" round>
              风险评分 {{ (dashboard.graphReasoning?.riskScore ?? 0).toFixed(1) }}
            </el-tag>
          </div>

          <div class="mt-5 grid gap-4 md:grid-cols-2">
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm text-slate-500">主要缺陷</div>
              <div class="mt-2 text-xl font-semibold text-slate-900">{{ dashboard.graphReasoning?.mainDefect || '暂无' }}</div>
            </div>
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm text-slate-500">推理结论</div>
              <div class="mt-2 text-sm font-medium text-slate-700">{{ dashboard.graphReasoning?.reasoningSummary || '暂无图谱推理结果' }}</div>
            </div>
          </div>

          <div class="mt-4 grid gap-4 md:grid-cols-3">
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm text-slate-500">图谱节点数</div>
              <div class="mt-1 text-lg font-semibold text-slate-900">{{ dashboard.graphReasoning?.statistics?.nodeCount ?? 0 }}</div>
            </div>
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm text-slate-500">关系总数</div>
              <div class="mt-1 text-lg font-semibold text-slate-900">{{ dashboard.graphReasoning?.statistics?.relationCount ?? 0 }}</div>
            </div>
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm text-slate-500">缺陷链路</div>
              <div class="mt-1 text-sm text-slate-700">{{ defectChainLabel }}</div>
            </div>
          </div>
        </section>
      </div>
    </section>

    <section class="content-card p-8">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h2 class="panel-title">知识图谱关联可视化</h2>
          <p class="panel-subtitle">展示参数、缺陷和工序之间的关系网络。</p>
        </div>
      </div>
      <div class="mt-6 h-[400px] rounded-[28px] border border-slate-200 bg-slate-950 p-5">
        <GraphVisualization :nodes="graphNodes" :edges="graphEdges" />
      </div>
    </section>

    <section class="content-card p-8">
      <h2 class="panel-title">评估历史记录</h2>
      <p class="panel-subtitle">展示研判评估的历史数据记录。</p>

      <div class="mt-6 overflow-hidden rounded-[24px] border border-slate-200">
        <el-table :data="historyRecords" border stripe v-loading="historyLoading" class="export-table">
          <el-table-column prop="sampledAt" label="采样时间" min-width="180" />
          <el-table-column prop="batchId" label="批次号" min-width="160" />
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
  fetchJudgmentDashboard,
  fetchJudgmentStream,
  type AssessmentHistoryItem,
  type JudgmentDashboardData,
  type JudgmentStreamData,
} from '@/api/assessment';
import { fetchGraphVisualization, type GraphVisualizationEdge, type GraphVisualizationNode } from '@/api/graph';
import BaseChart from '@/components/charts/BaseChart.vue';
import GraphVisualization from '@/components/charts/GraphVisualization.vue';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';
import { useAssessmentStore } from '@/stores/assessment';
import { axisLabelColor, createTooltip, splitLineColor } from '@/utils/chart';

const assessmentStore = useAssessmentStore();
const { activeSampleId, currentBatchId, currentContextLabel } = storeToRefs(assessmentStore);

const historyRecords = ref<AssessmentHistoryItem[]>([]);
const historyTotal = ref(0);
const historyPage = ref(1);
const historyPageSize = ref(10);
const historyLoading = ref(false);

const dashboard = ref<JudgmentDashboardData>({
  metrics: [],
  radarIndicators: [],
  abnormalSampleValues: [],
  targetValues: [],
  compareCategories: [],
  currentParameters: [],
  targetParameters: [],
  coreConclusion: '',
  coreDescription: '',
  diagnosisItems: [],
  actionItems: [],
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

const streamData = ref<JudgmentStreamData>({
  timeAxis: [],
  temperature: [],
  beltSpeed: [],
  o2Ppm: [],
  humidity: [],
  current: [],
});

const graphNodes = ref<GraphVisualizationNode[]>([]);
const graphEdges = ref<GraphVisualizationEdge[]>([]);

const defectChainLabel = computed(() => {
  const items = dashboard.value.graphReasoning?.defectChain ?? [];
  return items.length > 0 ? items.join(' -> ') : '暂无';
});

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

const loadDashboard = async () => {
  dashboard.value = await fetchJudgmentDashboard(currentBatchId.value);
};

const loadStreamData = async () => {
  try {
    streamData.value = await fetchJudgmentStream(currentBatchId.value);
  } catch {
    streamData.value = {
      timeAxis: [],
      temperature: [],
      beltSpeed: [],
      o2Ppm: [],
      humidity: [],
      current: [],
    };
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

const switchSample = () => {
  assessmentStore.setBatchContext({
    sampleId: activeSampleId.value === 'SAMPLE-07' ? 'SAMPLE-09' : 'SAMPLE-07',
  });
};

const streamChartOption = computed<EChartsCoreOption>(() => ({
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
    data: streamData.value.timeAxis,
    axisLine: { lineStyle: { color: splitLineColor } },
    axisLabel: { color: axisLabelColor },
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: axisLabelColor },
    splitLine: { lineStyle: { color: splitLineColor } },
  },
  series: [
    { name: '回流温度', type: 'line', smooth: true, data: streamData.value.temperature, lineStyle: { color: '#22d3ee', width: 2 }, itemStyle: { color: '#22d3ee' } },
    { name: '链速', type: 'line', smooth: true, data: streamData.value.beltSpeed, lineStyle: { color: '#60a5fa', width: 2 }, itemStyle: { color: '#60a5fa' } },
    { name: 'O2浓度', type: 'line', smooth: true, data: streamData.value.o2Ppm, lineStyle: { color: '#f59e0b', width: 2 }, itemStyle: { color: '#f59e0b' } },
    { name: '湿度', type: 'line', smooth: true, data: streamData.value.humidity, lineStyle: { color: '#10b981', width: 2 }, itemStyle: { color: '#10b981' } },
    { name: '电流', type: 'line', smooth: true, data: streamData.value.current, lineStyle: { color: '#ef4444', width: 2 }, itemStyle: { color: '#ef4444' } },
  ],
}));

const radarOption = computed<EChartsCoreOption>(() => ({
  tooltip: createTooltip(),
  legend: {
    top: 8,
    textStyle: { color: '#cbd5e1' },
  },
  radar: {
    radius: '64%',
    splitNumber: 4,
    axisName: { color: '#cbd5e1' },
    splitLine: { lineStyle: { color: ['rgba(148, 163, 184, 0.08)'] } },
    splitArea: { areaStyle: { color: ['rgba(15, 23, 42, 0.28)', 'rgba(15, 23, 42, 0.14)'] } },
    indicator: dashboard.value.radarIndicators,
  },
  series:
    dashboard.value.radarIndicators.length === 0
      ? []
      : [
          {
            type: 'radar',
            data: [
              {
                value: dashboard.value.abnormalSampleValues,
                name: '异常样本',
                areaStyle: { color: 'rgba(239, 68, 68, 0.22)' },
                lineStyle: { color: '#ef4444', width: 2 },
                itemStyle: { color: '#ef4444' },
              },
              {
                value: dashboard.value.targetValues,
                name: '目标工艺',
                areaStyle: { color: 'rgba(34, 211, 238, 0.16)' },
                lineStyle: { color: '#22d3ee', width: 2 },
                itemStyle: { color: '#22d3ee' },
              },
            ],
          },
        ],
}));

const barOption = computed<EChartsCoreOption>(() => ({
  tooltip: createTooltip(),
  legend: {
    top: 10,
    textStyle: { color: '#cbd5e1' },
  },
  grid: {
    left: 24,
    right: 16,
    top: 60,
    bottom: 24,
    containLabel: true,
  },
  xAxis: {
    type: 'category',
    data: dashboard.value.compareCategories,
    axisLine: { lineStyle: { color: splitLineColor } },
    axisLabel: { color: axisLabelColor },
  },
  yAxis: {
    type: 'value',
    axisLabel: { color: axisLabelColor },
    splitLine: { lineStyle: { color: splitLineColor } },
  },
  series: [
    {
      name: '当前参数',
      type: 'bar',
      data: dashboard.value.currentParameters,
      itemStyle: { color: '#f97316', borderRadius: [8, 8, 0, 0] },
    },
    {
      name: '目标参数',
      type: 'bar',
      data: dashboard.value.targetParameters,
      itemStyle: { color: '#38bdf8', borderRadius: [8, 8, 0, 0] },
    },
  ],
}));

onMounted(() => {
  // 先显示页面，异步加载数据
  Promise.all([
    assessmentStore.loadBatches(),
    assessmentStore.loadStations(),
  ]).then(() => {
    void loadDashboard();
    void loadHistory();
    void loadStreamData();
    void loadGraphVisualization();
  }).catch((error) => {
    console.error('Failed to load initial data:', error);
  });
});
</script>
