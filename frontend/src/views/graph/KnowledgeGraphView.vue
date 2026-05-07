<template>
  <div class="space-y-6">
    <PageIntroCard
      title="知识图谱可视化"
      description="按生产批次查看 PostgreSQL 同步到 Neo4j 后形成的工序、设备、参数、检测与缺陷关联网络。"
      badge="KNOWLEDGE GRAPH"
      :metrics="metrics"
    />

    <section class="content-card p-6">
      <div class="grid gap-4 lg:grid-cols-[1fr_auto_auto]">
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">选择生产批次</div>
          <el-select
            v-model="selectedBatchId"
            placeholder="请选择已同步的生产批次"
            class="mt-2 w-full"
            filterable
            clearable
            @change="onBatchChange"
          >
            <el-option
              v-for="batch in availableBatches"
              :key="batch"
              :label="batch"
              :value="batch"
            />
          </el-select>
        </div>

        <div class="flex items-end">
          <el-button type="primary" :loading="loading" :disabled="!selectedBatchId" @click="loadGraphData">
            <el-icon class="mr-1"><Refresh /></el-icon>
            加载图谱
          </el-button>
        </div>

        <div class="flex items-end">
          <el-button :loading="gatLoading" :disabled="!canRunGat" @click="onRunGat">
            <el-icon class="mr-1"><MagicStick /></el-icon>
            GAT 分析
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="selectedBatchId && !canRunGat"
        class="mt-4"
        type="info"
        show-icon
        :closable="false"
        title="当前可视化按业务批次号查询；GAT 接口仍预留给图谱版本 UUID，暂不作为一期测试重点。"
      />
    </section>

    <section class="grid gap-6 xl:grid-cols-[1.35fr_0.65fr]">
      <div class="content-card p-8">
        <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 class="panel-title">图谱网络</h2>
            <p class="panel-subtitle">节点可拖拽，支持缩放。连线表示批次、工序、参数、检测与缺陷之间的关系。</p>
          </div>
          <div class="flex items-center gap-3">
            <el-tag type="info" effect="dark" round>节点 {{ graphData.nodes.length }}</el-tag>
            <el-tag type="success" effect="dark" round>关系 {{ graphData.edges.length }}</el-tag>
          </div>
        </div>

        <div class="mt-6 h-[560px] rounded-[28px] border border-slate-200 bg-slate-950 p-5">
          <div v-if="graphData.nodes.length > 0" class="h-full w-full">
            <GraphVisualization :nodes="graphData.nodes" :edges="graphData.edges" />
          </div>
          <div v-else class="flex h-full flex-col items-center justify-center text-slate-400">
            <el-icon :size="48" class="text-cyan-300"><Connection /></el-icon>
            <div class="mt-4 text-lg font-semibold">暂无图谱数据</div>
            <div class="mt-2 text-sm">请选择已同步成功的批次，或先执行知识图谱同步。</div>
          </div>
        </div>
      </div>

      <div class="space-y-6">
        <section class="content-card p-8">
          <h2 class="panel-title">图谱统计</h2>
          <p class="panel-subtitle">当前批次子图的实体与关系概览。</p>

          <div class="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-1">
            <div
              v-for="item in statsCards"
              :key="item.label"
              class="rounded-2xl border border-slate-200 bg-slate-50 p-4"
            >
              <div class="text-sm text-slate-500">{{ item.label }}</div>
              <div class="mt-2 text-2xl font-bold text-slate-900">{{ item.value }}</div>
            </div>
          </div>
        </section>

        <section class="content-card p-8">
          <h2 class="panel-title">实体列表</h2>
          <p class="panel-subtitle">当前展示的前 80 个节点。</p>

          <div class="mt-6 overflow-hidden rounded-[16px] border border-slate-200">
            <el-table :data="entityTable" stripe max-height="360" size="small">
              <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="label" label="类型" min-width="120">
                <template #default="{ row }">
                  <el-tag size="small" :color="labelColorMap[row.label] || '#94a3b8'" effect="dark" round style="border:none">
                    {{ displayLabel(row.label) }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <section class="content-card p-8">
          <h2 class="panel-title">GAT 分析</h2>
          <p class="panel-subtitle">预留的图注意力分析能力，当前不影响图谱可视化主流程。</p>

          <div v-if="gatResult" class="mt-6 space-y-4">
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-700">
              {{ gatResult.summary }}
            </div>
            <div
              v-for="(edge, idx) in topAttentionEdges"
              :key="idx"
              class="flex items-center gap-3 rounded-xl border border-slate-100 bg-white p-3"
            >
              <span class="truncate text-sm font-medium text-slate-700">{{ edge.from }}</span>
              <el-icon class="text-slate-400"><Right /></el-icon>
              <span class="truncate text-sm font-medium text-slate-700">{{ edge.to }}</span>
              <el-tag class="ml-auto" size="small" :type="edge.attentionWeight > 0.7 ? 'danger' : 'info'" round>
                {{ (edge.attentionWeight * 100).toFixed(1) }}%
              </el-tag>
            </div>
          </div>

          <div v-else class="mt-6 rounded-2xl border border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-400">
            先完成批次图谱可视化；GAT 后续可接入模型训练和注意力权重展示。
          </div>
        </section>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { Connection, MagicStick, Refresh, Right } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

import { fetchBatches } from '@/api/assessment';
import {
  fetchGraphVisualization,
  runGatOptimization,
  type GatOptimizationResponse,
  type GraphVisualizationEdge,
  type GraphVisualizationNode,
} from '@/api/graph';
import GraphVisualization from '@/components/charts/GraphVisualization.vue';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';

const selectedBatchId = ref('');
const route = useRoute();
const availableBatches = ref<string[]>([]);
const loading = ref(false);
const gatLoading = ref(false);
const graphData = ref<{ nodes: GraphVisualizationNode[]; edges: GraphVisualizationEdge[] }>({
  nodes: [],
  edges: [],
});
const gatResult = ref<GatOptimizationResponse | null>(null);

const labelColorMap: Record<string, string> = {
  Batch: '#3b82f6',
  ProductionBatch: '#3b82f6',
  ProcessStep: '#8b5cf6',
  ProcessParameter: '#10b981',
  ParameterDef: '#10b981',
  ParameterValue: '#22c55e',
  QualityParameter: '#f59e0b',
  QualityMeasurement: '#f59e0b',
  Defect: '#ef4444',
  DefectType: '#ef4444',
  DefectRecord: '#f97316',
  InspectionTask: '#06b6d4',
  ProductUnit: '#6366f1',
  ProcessRun: '#a855f7',
  Equipment: '#64748b',
  Workstation: '#14b8a6',
};

const labelNameMap: Record<string, string> = {
  Batch: '批次',
  ProductionBatch: '生产批次',
  ProcessStep: '工序',
  ProcessParameter: '工艺参数',
  ParameterDef: '参数定义',
  ParameterValue: '参数值',
  QualityParameter: '质量参数',
  QualityMeasurement: '质量测量',
  Defect: '缺陷',
  DefectType: '缺陷类型',
  DefectRecord: '缺陷记录',
  InspectionTask: '检测任务',
  ProductUnit: '产品单元',
  ProcessRun: '生产运行',
  Equipment: '设备',
  Workstation: '工位',
};

const canRunGat = computed(() =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(selectedBatchId.value),
);

const metrics = computed(() => [
  { label: '节点总数', value: String(graphData.value.nodes.length), extra: '当前批次子图' },
  { label: '关系总数', value: String(graphData.value.edges.length), extra: '关系连线数量' },
  { label: '实体类型', value: String(new Set(graphData.value.nodes.map((n) => n.label)).size), extra: '节点分类数量' },
]);

const statsCards = computed(() => {
  const nodes = graphData.value.nodes;
  const labels = [...new Set(nodes.map((n) => n.label))];
  return [
    { label: '实体节点', value: nodes.length },
    { label: '关系边', value: graphData.value.edges.length },
    ...labels.slice(0, 6).map((label) => ({
      label: displayLabel(label),
      value: nodes.filter((node) => node.label === label).length,
    })),
  ];
});

const topAttentionEdges = computed(() => {
  if (!gatResult.value) return [];
  return [...gatResult.value.attentionEdges]
    .sort((a, b) => b.attentionWeight - a.attentionWeight)
    .slice(0, 8);
});

const entityTable = computed(() =>
  graphData.value.nodes.slice(0, 80).map((node) => ({
    name: node.name || node.graphId,
    label: node.label,
  })),
);

const displayLabel = (label: string) => labelNameMap[label] || label;

const onBatchChange = () => {
  gatResult.value = null;
  void loadGraphData();
};

const loadGraphData = async () => {
  if (!selectedBatchId.value) {
    graphData.value = { nodes: [], edges: [] };
    return;
  }

  loading.value = true;
  try {
    const result = await fetchGraphVisualization(selectedBatchId.value);
    graphData.value = {
      nodes: result.nodes ?? [],
      edges: result.edges ?? [],
    };
    if (graphData.value.nodes.length === 0) {
      ElMessage.info('该批次暂无图谱数据，请先执行知识图谱同步。');
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String((error as { message?: string })?.message ?? '未知错误');
    ElMessage.error(`加载图谱失败：${message}`);
    graphData.value = { nodes: [], edges: [] };
  } finally {
    loading.value = false;
  }
};

const onRunGat = async () => {
  if (!canRunGat.value) {
    ElMessage.warning('GAT 分析当前需要图谱版本 UUID。');
    return;
  }

  gatLoading.value = true;
  try {
    gatResult.value = await runGatOptimization(selectedBatchId.value);
    ElMessage.success('GAT 分析完成');
  } catch (error) {
    const message = error instanceof Error ? error.message : String((error as { message?: string })?.message ?? '未知错误');
    ElMessage.error(`GAT 分析失败：${message}`);
  } finally {
    gatLoading.value = false;
  }
};

onMounted(async () => {
  try {
    availableBatches.value = await fetchBatches();
    const queryBatchId = typeof route.query.batchId === 'string' ? route.query.batchId : '';
    if (queryBatchId) {
      selectedBatchId.value = queryBatchId;
      await loadGraphData();
    } else if (availableBatches.value.length > 0) {
      selectedBatchId.value = availableBatches.value[0]!;
      await loadGraphData();
    }
  } catch {
    availableBatches.value = [];
  }
});
</script>
