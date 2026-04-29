<template>
  <div class="space-y-6">
    <PageIntroCard
      title="产品质量合格评估"
      description="展示当前批次质量参数的实时变化趋势，并给出即时合格判定。"
      badge="QUALIFIED"
      :metrics="dashboard.metrics"
    />

    <section class="content-card p-6">
      <div class="grid gap-4 md:grid-cols-5">
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">选择批次</div>
          <el-select
            v-model="selectedBatchId"
            placeholder="请选择批次"
            class="mt-2 w-full"
            @change="onBatchChange"
            filterable
          >
            <el-option
              v-for="batch in availableBatches"
              :key="batch"
              :label="batch"
              :value="batch"
            />
          </el-select>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">选中工位</div>
          <el-select
            v-model="selectedStation"
            placeholder="请选择工位"
            class="mt-2 w-full"
            @change="onStationChange"
          >
            <el-option
              v-for="station in availableStations"
              :key="station"
              :label="station"
              :value="station"
            />
          </el-select>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">通过阈值</div>
          <div class="mt-2 text-lg font-semibold text-slate-900">{{ config.passThreshold }}%</div>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">最近评估</div>
          <div class="mt-2 text-lg font-semibold text-slate-900">{{ lastAssessmentAt || '--' }}</div>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">批次状态</div>
          <div class="mt-2">
            <el-tag :type="batchStatusType" effect="dark" round>{{ batchStatusText }}</el-tag>
          </div>
        </div>
      </div>
    </section>

    <section class="grid gap-6 xl:grid-cols-[1.3fr_0.7fr]">
      <div class="content-card p-8">
        <div class="flex items-center justify-between gap-4">
          <div>
            <h2 class="panel-title">当前批次质量参数流</h2>
            <p class="panel-subtitle">实时监控温度、压力和电流曲线。</p>
          </div>
          <div class="flex items-center gap-3">
            <el-tag :type="connectionTagType" effect="dark" round>{{ connectionLabel }}</el-tag>
            <el-tag type="info" effect="dark" round>窗口 {{ maxPoints }} 点</el-tag>
          </div>
        </div>

        <div class="mt-6 h-[420px] rounded-[28px] border border-slate-200 bg-slate-950 p-5">
          <div class="relative h-full w-full">
            <div ref="chartRef" class="h-full w-full"></div>
            <div
              v-if="!chartReady"
              class="pointer-events-none absolute inset-0 rounded-[20px] bg-[linear-gradient(110deg,rgba(15,23,42,0.88),rgba(30,41,59,0.72),rgba(15,23,42,0.88))] bg-[length:200%_100%] animate-[chartShimmer_1.6s_linear_infinite]"
            />
          </div>
        </div>
      </div>

      <div class="space-y-6">
        <section class="content-card p-8">
          <h2 class="panel-title">评估结果</h2>
          <p class="panel-subtitle">根据当前滚动窗口进行合格判定。</p>

          <div class="mt-6 grid gap-4">
            <div
              v-for="item in dashboard.resultCards"
              :key="item.label"
              class="rounded-3xl border p-5"
              :class="item.pass ? 'border-emerald-200 bg-emerald-50' : 'border-rose-200 bg-rose-50'"
            >
              <div class="text-sm text-slate-500">{{ item.label }}</div>
              <div class="mt-3 text-2xl font-semibold" :class="item.pass ? 'text-emerald-700' : 'text-rose-700'">
                {{ item.value }}
              </div>
              <div class="mt-2 text-sm text-slate-500">{{ item.tip }}</div>
            </div>
          </div>
        </section>

        <section class="content-card p-8">
          <h2 class="panel-title">流式指标快照</h2>
          <div class="mt-5 space-y-4">
            <div
              v-for="item in dashboard.streamMetrics"
              :key="item.label"
              class="rounded-2xl border border-slate-200 bg-slate-50 p-4"
            >
              <div class="flex items-center justify-between gap-4">
                <div class="text-sm text-slate-500">{{ item.label }}</div>
                <div class="text-sm font-semibold text-slate-900">{{ item.value }}</div>
              </div>
              <el-progress class="mt-3" :percentage="item.percentage" :color="item.color" :stroke-width="10" />
            </div>
          </div>
        </section>

        <section class="content-card p-8">
          <div class="flex items-center justify-between gap-4">
            <div>
              <h2 class="panel-title">图谱推理摘要</h2>
              <p class="panel-subtitle">展示批次质量风险和图谱规模。</p>
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
              <div class="mt-2 text-sm font-medium text-slate-700">{{ dashboard.graphReasoning?.reasoningSummary || '暂无图谱结果' }}</div>
            </div>
          </div>
        </section>
      </div>
    </section>

    <section class="content-card p-8">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h2 class="panel-title">评估历史记录</h2>
          <p class="panel-subtitle">展示当前批次及历史的合格评估数据记录。</p>
        </div>
        <div class="flex items-center gap-3">
          <el-select
            v-model="historyFilterBatchId"
            placeholder="筛选批次"
            class="w-48"
            clearable
            @change="onHistoryFilterChange"
          >
            <el-option label="全部批次" value="" />
            <el-option
              v-for="batch in availableBatches"
              :key="batch"
              :label="batch"
              :value="batch"
            />
          </el-select>
          <el-button type="primary" @click="loadHistory" :loading="historyLoading">
            <el-icon class="mr-1"><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>

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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { EChartsCoreOption } from 'echarts/core';
import { storeToRefs } from 'pinia';
import { Refresh } from '@element-plus/icons-vue';

import { fetchAssessmentHistory, fetchQualifiedDashboard, type AssessmentHistoryItem, type QualifiedDashboardData } from '@/api/assessment';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';
import { isMockEnabled } from '@/constants/env';
import { useECharts } from '@/hooks/useECharts';
import { useWebSocket } from '@/hooks/useWebSocket';
import { useAssessmentStore } from '@/stores/assessment';
import { axisLabelColor, createTooltip, splitLineColor } from '@/utils/chart';

const assessmentStore = useAssessmentStore();
const { config, currentBatchId, lastAssessmentAt, selectedStation, availableBatches, availableStations } = storeToRefs(assessmentStore);
const maxPoints = 24;

const selectedBatchId = ref('');
const historyFilterBatchId = ref<string>('');

const historyRecords = ref<AssessmentHistoryItem[]>([]);
const historyTotal = ref(0);
const historyPage = ref(1);
const historyPageSize = ref(10);
const historyLoading = ref(false);

const dashboard = ref<QualifiedDashboardData>({
  metrics: [],
  timeAxis: [],
  temperatureData: [],
  pressureData: [],
  currentData: [],
  resultCards: [],
  streamMetrics: [],
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

interface QualityStreamMessage {
  timestamp: string;
  batchId: string;
  station: string;
  temperature: number;
  pressure: number;
  current: number;
}

const loadHistory = async () => {
  historyLoading.value = true;
  try {
    const batchId = historyFilterBatchId.value || selectedBatchId.value || undefined;
    const result = await fetchAssessmentHistory(batchId, historyPage.value, historyPageSize.value);
    historyRecords.value = result.records;
    historyTotal.value = result.total;
  } finally {
    historyLoading.value = false;
  }
};

const batchStatusType = computed(() => {
  if (!selectedBatchId.value) return 'info';
  return 'success';
});

const batchStatusText = computed(() => {
  if (!selectedBatchId.value) return '未选择';
  return '已加载';
});

const onBatchChange = async (batchId: string) => {
  if (batchId) {
    currentBatchId.value = batchId;
    await loadDashboard();
    await loadHistory();
  }
};

const onStationChange = async () => {
  await loadDashboard();
};

const onHistoryFilterChange = () => {
  historyPage.value = 1;
  loadHistory();
};

const chartRef = ref<HTMLDivElement | null>(null);
const chartReady = computed(() => Boolean(chartInstance.value));

const lineChartOption = computed<EChartsCoreOption>(() => ({
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
    data: dashboard.value.timeAxis,
    axisLine: { lineStyle: { color: splitLineColor } },
    axisLabel: { color: axisLabelColor },
  },
  yAxis: [
    {
      type: 'value',
      name: '温度/压力',
      axisLabel: { color: axisLabelColor },
      splitLine: { lineStyle: { color: splitLineColor } },
    },
    {
      type: 'value',
      name: '电流',
      axisLabel: { color: axisLabelColor },
      splitLine: { show: false },
    },
  ],
  series: [
    { name: '焊接温度', type: 'line', smooth: true, data: dashboard.value.temperatureData, showSymbol: false, lineStyle: { width: 3, color: '#22d3ee' }, itemStyle: { color: '#22d3ee' }, areaStyle: { color: 'rgba(34, 211, 238, 0.12)' } },
    { name: '贴装压力', type: 'line', smooth: true, data: dashboard.value.pressureData, showSymbol: false, lineStyle: { width: 3, color: '#60a5fa' }, itemStyle: { color: '#60a5fa' } },
    { name: '工作电流', type: 'line', yAxisIndex: 1, smooth: true, data: dashboard.value.currentData, showSymbol: false, lineStyle: { width: 3, color: '#f59e0b' }, itemStyle: { color: '#f59e0b' } },
  ],
}));

const { chartInstance } = useECharts(chartRef, lineChartOption);

const createWebSocketUrl = () => {
  const { protocol, host } = window.location;
  const wsProtocol = protocol === 'https:' ? 'wss:' : 'ws:';
  return `${wsProtocol}//${host}/ws/quality-stream`;
};

const updateRollingSeries = (message: QualityStreamMessage) => {
  dashboard.value.timeAxis = [...dashboard.value.timeAxis, message.timestamp].slice(-maxPoints);
  dashboard.value.temperatureData = [...dashboard.value.temperatureData, message.temperature].slice(-maxPoints);
  dashboard.value.pressureData = [...dashboard.value.pressureData, message.pressure].slice(-maxPoints);
  dashboard.value.currentData = [...dashboard.value.currentData, message.current].slice(-maxPoints);

  const passThreshold = config.value.passThreshold;
  const temperatureScore = Math.max(0, 100 - Math.abs(message.temperature - 234) * 6);
  const pressureScore = Math.max(0, 100 - Math.abs(message.pressure - 4.2) * 30);
  const currentScore = Math.max(0, 100 - Math.abs(message.current - 1.16) * 120);
  const compositeScore = (temperatureScore + pressureScore + currentScore) / 3;
  const pass = compositeScore >= passThreshold;

  dashboard.value.resultCards = [
    { label: '批次评估', value: pass ? '通过' : '待复核', tip: `实时评分 ${compositeScore.toFixed(1)} / 100`, pass },
    { label: '关键参数报警', value: pass ? '0 项' : '1 项', tip: pass ? '当前窗口未触发越界' : '温度或压力接近阈值边界', pass },
    { label: '边缘风险提示', value: pressureScore < 92 ? '压力波动偏高' : '运行稳定', tip: `${message.station} 实时流判定`, pass: pressureScore >= 92 },
  ];

  dashboard.value.streamMetrics = [
    { label: '焊接温度稳定度', value: `${temperatureScore.toFixed(1)}%`, percentage: Number(temperatureScore.toFixed(1)), color: temperatureScore >= 95 ? '#10b981' : '#f59e0b' },
    { label: '贴装压力稳定度', value: `${pressureScore.toFixed(1)}%`, percentage: Number(pressureScore.toFixed(1)), color: pressureScore >= 92 ? '#10b981' : '#ef4444' },
    { label: '电流波动健康度', value: `${currentScore.toFixed(1)}%`, percentage: Number(currentScore.toFixed(1)), color: currentScore >= 94 ? '#0ea5e9' : '#f59e0b' },
  ];

  assessmentStore.setBatchContext({
    batchId: message.batchId,
    station: message.station,
  });
};

const normalizeStreamMessage = (payload: Partial<QualityStreamMessage>): QualityStreamMessage => ({
  timestamp: payload.timestamp ?? new Date().toLocaleTimeString('zh-CN', { hour12: false }),
  batchId: payload.batchId ?? currentBatchId.value,
  station: payload.station ?? selectedStation.value,
  temperature: Number((payload.temperature ?? 234).toFixed(2)),
  pressure: Number((payload.pressure ?? 4.2).toFixed(2)),
  current: Number((payload.current ?? 1.16).toFixed(2)),
});

const handleSocketMessage = (event: MessageEvent) => {
  try {
    const payload = JSON.parse(String(event.data)) as Partial<QualityStreamMessage>;
    updateRollingSeries(normalizeStreamMessage(payload));
  } catch {
    // ignore malformed messages
  }
};

const { status: socketStatus } = useWebSocket(createWebSocketUrl(), {
  autoConnect: !isMockEnabled,
  heartbeatInterval: 12000,
  pongTimeout: 6000,
  reconnectDelay: 3000,
  onMessage: handleSocketMessage,
});

let mockStreamTimer: number | null = null;

const startMockStream = () => {
  if (!isMockEnabled) {
    return;
  }

  mockStreamTimer = window.setInterval(() => {
    updateRollingSeries(
      normalizeStreamMessage({
        timestamp: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
        batchId: currentBatchId.value,
        station: selectedStation.value,
        temperature: 234 + (Math.random() - 0.5) * 4,
        pressure: 4.2 + (Math.random() - 0.5) * 0.4,
        current: 1.16 + (Math.random() - 0.5) * 0.12,
      }),
    );
  }, config.value.refreshIntervalMs);
};

const stopMockStream = () => {
  if (mockStreamTimer !== null) {
    window.clearInterval(mockStreamTimer);
    mockStreamTimer = null;
  }
};

const connectionLabel = computed(() => {
  if (isMockEnabled) return 'Mock 实时流';
  if (socketStatus.value === 'open') return 'WebSocket 已连接';
  if (socketStatus.value === 'reconnecting') return 'WebSocket 重连中';
  if (socketStatus.value === 'error') return 'WebSocket 异常';
  return 'WebSocket 连接中';
});

const connectionTagType = computed(() => {
  if (isMockEnabled || socketStatus.value === 'open') return 'success';
  if (socketStatus.value === 'reconnecting') return 'warning';
  return 'danger';
});

const loadDashboard = async () => {
  const result = await fetchQualifiedDashboard(selectedBatchId.value || undefined);
  dashboard.value = result;
  assessmentStore.setBatchContext({
    batchId: result.metrics[0]?.value ?? selectedBatchId.value,
    station: result.metrics[0]?.extra ?? selectedStation.value,
  });
  if (!selectedBatchId.value && result.metrics[0]?.value) {
    selectedBatchId.value = result.metrics[0]?.value;
  }
};

watch(
  () => config.value.refreshIntervalMs,
  () => {
    if (!isMockEnabled) return;
    stopMockStream();
    startMockStream();
  },
);

onMounted(() => {
  // 先显示页面，异步加载数据
  startMockStream();
  
  // 异步加载，不阻塞页面渲染
  Promise.all([
    assessmentStore.loadBatches(),
    assessmentStore.loadStations(),
  ]).then(() => {
    if (availableBatches.value.length > 0 && !selectedBatchId.value) {
      selectedBatchId.value = availableBatches.value[0];
      currentBatchId.value = availableBatches.value[0];
    }
    if (selectedBatchId.value) {
      void loadDashboard();
    }
    void loadHistory();
  }).catch((error) => {
    console.error('Failed to load initial data:', error);
  });
});

onBeforeUnmount(() => {
  stopMockStream();
});
</script>

<style scoped>
@keyframes chartShimmer {
  0% {
    background-position: 200% 0;
  }

  100% {
    background-position: -200% 0;
  }
}
</style>
