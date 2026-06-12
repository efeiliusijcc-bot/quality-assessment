<template>
  <div class="space-y-5">
    <PageIntroCard
      title="工艺参数研判评估"
      description="融合实时工艺参数、质量缺陷链路、Apriori 规则和知识图谱关系，对异常工位进行原因研判。"
      badge="JUDGMENT"
      :metrics="dashboard.metrics"
    />

    <section class="grid gap-5 xl:grid-cols-[1.08fr_0.92fr]">
      <SectionCard title="工艺参数实时流" description="展示关键工艺参数的时间曲线，用于定位异常波动区间。" compact>
        <template #extra>
          <el-button type="primary" plain @click="switchSample">切换样本</el-button>
        </template>
        <div class="h-[330px] rounded-[24px] border border-slate-200 bg-slate-950 p-4">
          <BaseChart :option="streamChartOption" />
        </div>
      </SectionCard>

      <SectionCard title="研判结论" description="汇总异常产品、主要缺陷与工艺参数异常链路。" compact>
        <template #extra>
          <el-tag :type="dashboard.graphReasoning.riskScore > 60 ? 'danger' : 'success'" effect="dark" round>
            风险评分 {{ dashboard.graphReasoning.riskScore.toFixed(1) }}
          </el-tag>
        </template>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">核心结论</div>
          <div class="mt-2 text-xl font-bold text-slate-900">{{ dashboard.coreConclusion || '暂无研判结论' }}</div>
          <div class="mt-2 text-sm leading-6 text-slate-600">{{ dashboard.coreDescription }}</div>
        </div>
        <div class="mt-4 grid gap-3 md:grid-cols-2">
          <MetricTile label="主要缺陷" :value="dashboard.graphReasoning.mainDefect || '暂无'" extra="缺陷类型" />
          <MetricTile label="缺陷链路" :value="defectChainLabel" extra="参数-缺陷关联" />
          <MetricTile label="参数异常链" :value="parameterChainLabel" extra="GAT / Apriori 证据" />
          <MetricTile label="工序诊断链" :value="stepChainLabel" extra="上下游影响" />
        </div>
      </SectionCard>
    </section>

    <section class="grid gap-5 xl:grid-cols-[0.9fr_1.1fr]">
      <SectionCard title="参数偏差雷达" description="对比异常样本与目标工艺的多指标偏差。" compact>
        <div class="h-[320px] rounded-[24px] border border-slate-200 bg-slate-950 p-4">
          <BaseChart :option="radarOption" />
        </div>
      </SectionCard>

      <SectionCard title="参数优化对比" description="展示当前参数与目标参数的对比结果。" compact>
        <div class="h-[320px] rounded-[24px] border border-slate-200 bg-slate-950 p-4">
          <BaseChart :option="barOption" />
        </div>
      </SectionCard>
    </section>

    <section class="grid gap-5 xl:grid-cols-[0.86fr_1.14fr]">
      <SectionCard title="Apriori 关联规则" description="展示参数、缺陷、工序之间的频繁共现关系，用于解释潜在质量问题。" compact>
        <template #extra>
          <div class="flex gap-2">
            <el-button plain :loading="aprioriLoading" @click="handleMineApriori">挖掘规则</el-button>
            <el-button type="primary" plain :loading="aprioriSaving" @click="handleSaveApriori">挖掘并入库</el-button>
          </div>
        </template>
        <AprioriRulesPanel :rules="aprioriRules" :limit="6" />
      </SectionCard>

      <SectionCard title="异常诊断与处置建议" description="根据规则挖掘、图谱推理和工艺参数偏差生成处置建议。" compact>
        <div class="grid gap-4 lg:grid-cols-2">
          <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
            <div class="mb-3 text-sm font-semibold text-slate-500">诊断项</div>
            <div class="space-y-3">
              <div v-for="item in dashboard.diagnosisItems" :key="item.title" class="rounded-xl bg-white px-3 py-3 text-sm leading-6 text-slate-700">
                <div class="font-semibold text-slate-900">{{ item.title }}</div>
                <div class="mt-1">{{ item.content }}</div>
              </div>
              <EmptyState v-if="dashboard.diagnosisItems.length === 0" title="暂无诊断项" />
            </div>
          </div>
          <div class="rounded-2xl border border-slate-200 bg-slate-950 p-4 text-slate-100">
            <div class="mb-3 text-sm tracking-[0.18em] text-cyan-300/75">ACTION ITEMS</div>
            <div class="space-y-3">
              <div v-for="item in dashboard.actionItems" :key="item.label" class="rounded-xl border border-white/10 bg-white/5 px-3 py-3">
                <div class="text-xs text-slate-400">{{ item.label }}</div>
                <div class="mt-1 font-semibold text-white">{{ item.value }}</div>
              </div>
              <div v-if="dashboard.actionItems.length === 0" class="rounded-xl border border-white/10 bg-white/5 p-4 text-sm text-slate-400">
                暂无处置建议
              </div>
            </div>
          </div>
        </div>
      </SectionCard>
    </section>

    <SectionCard title="知识图谱研判视图" description="展示当前批次下工序、参数、缺陷、质量指标之间的关联网络。" compact>
      <div class="h-[360px] rounded-[24px] border border-slate-200 bg-slate-950 p-4">
        <GraphVisualization :nodes="graphNodes" :edges="graphEdges" />
      </div>
    </SectionCard>

    <SectionCard title="评估历史记录" description="展示研判评估历史数据。" compact>
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
  fetchJudgmentDashboard,
  fetchJudgmentStream,
  type AssessmentHistoryItem,
  type JudgmentDashboardData,
  type JudgmentStreamData,
} from '@/api/assessment';
import {
  fetchGraphVisualization,
  mineAndSaveAprioriRules,
  mineAprioriRules,
  type AprioriRule,
  type GraphVisualizationEdge,
  type GraphVisualizationNode,
} from '@/api/graph';
import AprioriRulesPanel from '@/components/common/AprioriRulesPanel.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import MetricTile from '@/components/common/MetricTile.vue';
import SectionCard from '@/components/common/SectionCard.vue';
import BaseChart from '@/components/charts/BaseChart.vue';
import GraphVisualization from '@/components/charts/GraphVisualization.vue';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';
import { useAssessmentStore } from '@/stores/assessment';
import { axisLabelColor, createTooltip, splitLineColor } from '@/utils/chart';

const assessmentStore = useAssessmentStore();
const { activeSampleId, currentBatchId } = storeToRefs(assessmentStore);

const historyRecords = ref<AssessmentHistoryItem[]>([]);
const historyTotal = ref(0);
const historyPage = ref(1);
const historyPageSize = ref(10);
const historyLoading = ref(false);
const aprioriLoading = ref(false);
const aprioriSaving = ref(false);
const aprioriRules = ref<AprioriRule[]>([]);

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

const streamData = ref<JudgmentStreamData>({ timeAxis: [], temperature: [], beltSpeed: [], o2Ppm: [], humidity: [], current: [] });
const graphNodes = ref<GraphVisualizationNode[]>([]);
const graphEdges = ref<GraphVisualizationEdge[]>([]);

const defectChainLabel = computed(() => dashboard.value.graphReasoning.defectChain.join(' → ') || '暂无');
const parameterChainLabel = computed(() => dashboard.value.graphReasoning.parameterChain.join(' → ') || '暂无');
const stepChainLabel = computed(() => dashboard.value.graphReasoning.stepChain.join(' → ') || '暂无');

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
    streamData.value = { timeAxis: [], temperature: [], beltSpeed: [], o2Ppm: [], humidity: [], current: [] };
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
  assessmentStore.setBatchContext({ sampleId: activeSampleId.value === 'SAMPLE-07' ? 'SAMPLE-09' : 'SAMPLE-07' });
  void loadDashboard();
};

const handleMineApriori = async () => {
  aprioriLoading.value = true;
  try {
    const result = await mineAprioriRules(currentBatchId.value);
    aprioriRules.value = result.rules ?? [];
    ElMessage.success(`Apriori 挖掘完成：${aprioriRules.value.length} 条规则`);
  } finally {
    aprioriLoading.value = false;
  }
};

const handleSaveApriori = async () => {
  aprioriSaving.value = true;
  try {
    const result = await mineAndSaveAprioriRules(currentBatchId.value);
    aprioriRules.value = result.rules ?? [];
    ElMessage.success(`Apriori 规则已入库：${result.savedRelationCount ?? result.insertedRelationCount ?? aprioriRules.value.length} 条`);
    await loadGraphVisualization();
  } finally {
    aprioriSaving.value = false;
  }
};

const streamChartOption = computed<EChartsCoreOption>(() => ({
  tooltip: createTooltip(),
  legend: { top: 8, textStyle: { color: '#cbd5e1' } },
  grid: { left: 24, right: 20, top: 58, bottom: 22, containLabel: true },
  xAxis: { type: 'category', boundaryGap: false, data: streamData.value.timeAxis, axisLine: { lineStyle: { color: splitLineColor } }, axisLabel: { color: axisLabelColor } },
  yAxis: { type: 'value', axisLabel: { color: axisLabelColor }, splitLine: { lineStyle: { color: splitLineColor } } },
  series: [
    { name: '回流温度', type: 'line', smooth: true, data: streamData.value.temperature },
    { name: '链速', type: 'line', smooth: true, data: streamData.value.beltSpeed },
    { name: 'O2浓度', type: 'line', smooth: true, data: streamData.value.o2Ppm },
    { name: '湿度', type: 'line', smooth: true, data: streamData.value.humidity },
    { name: '电流', type: 'line', smooth: true, data: streamData.value.current },
  ],
}));

const radarOption = computed<EChartsCoreOption>(() => ({
  tooltip: createTooltip(),
  legend: { top: 8, textStyle: { color: '#cbd5e1' } },
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
      : [{ type: 'radar', data: [{ value: dashboard.value.abnormalSampleValues, name: '异常样本' }, { value: dashboard.value.targetValues, name: '目标工艺' }] }],
}));

const barOption = computed<EChartsCoreOption>(() => ({
  tooltip: createTooltip(),
  legend: { top: 8, textStyle: { color: '#cbd5e1' } },
  grid: { left: 24, right: 20, top: 58, bottom: 22, containLabel: true },
  xAxis: { type: 'category', data: dashboard.value.compareCategories, axisLine: { lineStyle: { color: splitLineColor } }, axisLabel: { color: axisLabelColor } },
  yAxis: { type: 'value', axisLabel: { color: axisLabelColor }, splitLine: { lineStyle: { color: splitLineColor } } },
  series: [
    { name: '当前参数', type: 'bar', data: dashboard.value.currentParameters },
    { name: '目标参数', type: 'bar', data: dashboard.value.targetParameters },
  ],
}));

onMounted(async () => {
  await Promise.all([loadDashboard(), loadStreamData(), loadHistory(), loadGraphVisualization(), handleMineApriori()]);
});
</script>
