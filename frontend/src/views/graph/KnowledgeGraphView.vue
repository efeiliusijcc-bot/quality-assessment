<template>
  <div class="space-y-6">
    <PageIntroCard
      title="知识图谱可视化"
      description="基于 Neo4j 构建的电子元器件装配质量知识图谱，支持节点探索、关系查询与 GAT 注意力权重分析。"
      badge="KNOWLEDGE GRAPH"
      :metrics="metrics"
    />

    <!-- 控制栏 -->
    <section class="content-card p-6">
      <div class="grid gap-4 md:grid-cols-[1fr_1fr_auto_auto]">
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">选择批次</div>
          <el-select
            v-model="selectedBatchId"
            placeholder="请输入或选择批次号"
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

        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">图谱版本</div>
          <el-select
            v-model="selectedVersionId"
            placeholder="全部版本"
            class="mt-2 w-full"
            clearable
            @change="loadGraphData"
          >
            <el-option
              v-for="v in graphVersions"
              :key="v.id"
              :label="v.description"
              :value="v.id"
            />
          </el-select>
        </div>

        <div class="flex items-end">
          <el-button type="primary" :loading="gatLoading" :disabled="!selectedBatchId" @click="onRunGat">
            <el-icon class="mr-1"><MagicStick /></el-icon>
            GAT 分析
          </el-button>
        </div>

        <div class="flex items-end">
          <el-button :loading="loading" @click="loadGraphData">
            <el-icon class="mr-1"><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>
    </section>

    <!-- 主内容区：图谱 + 侧栏 -->
    <section class="grid gap-6 xl:grid-cols-[1.3fr_0.7fr]">
      <!-- 图谱画布 -->
      <div class="content-card p-8">
        <div class="flex items-center justify-between gap-4">
          <div>
            <h2 class="panel-title">图谱网络</h2>
            <p class="panel-subtitle">力导向布局展示实体节点与关系连线，支持拖拽与缩放。</p>
          </div>
          <div class="flex items-center gap-3">
            <el-tag type="info" effect="dark" round>节点 {{ graphData.nodes.length }}</el-tag>
            <el-tag type="info" effect="dark" round>关系 {{ graphData.edges.length }}</el-tag>
          </div>
        </div>

        <div class="mt-6 h-[520px] rounded-[28px] border border-slate-200 bg-slate-950 p-5">
          <div v-if="graphData.nodes.length > 0" class="h-full w-full">
            <GraphVisualization :nodes="graphData.nodes" :edges="graphData.edges" />
          </div>
          <div v-else class="flex h-full flex-col items-center justify-center text-slate-400">
            <el-icon :size="48" class="text-cyan-300"><Connection /></el-icon>
            <div class="mt-4 text-lg font-semibold">暂无图谱数据</div>
            <div class="mt-2 text-sm">选择批次后加载知识图谱</div>
          </div>
        </div>
      </div>

      <!-- 右侧面板 -->
      <div class="space-y-6">
        <!-- 图谱统计 -->
        <section class="content-card p-8">
          <h2 class="panel-title">图谱统计</h2>
          <p class="panel-subtitle">当前图谱的实体与关系概览。</p>

          <div class="mt-6 grid gap-4 md:grid-cols-2">
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

        <!-- GAT 分析结果 -->
        <section class="content-card p-8">
          <div class="flex items-center justify-between gap-4">
            <div>
              <h2 class="panel-title">GAT 注意力分析</h2>
              <p class="panel-subtitle">图注意力网络权重排序。</p>
            </div>
            <el-tag v-if="gatResult" :type="gatResult.edgeCount > 50 ? 'danger' : 'success'" effect="dark" round>
              边权重 {{ gatResult.edgeCount }}
            </el-tag>
          </div>

          <div v-if="gatResult" class="mt-6 space-y-4">
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm text-slate-500">分析摘要</div>
              <div class="mt-2 text-sm leading-6 text-slate-700">{{ gatResult.summary }}</div>
            </div>

            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm font-medium text-slate-500">注意力 Top 关系</div>
              <div class="mt-4 space-y-3">
                <div
                  v-for="(edge, idx) in topAttentionEdges"
                  :key="idx"
                  class="flex items-center gap-3 rounded-xl border border-slate-100 bg-white p-3"
                >
                  <div class="text-sm font-medium text-slate-700">{{ edge.from }}</div>
                  <el-icon class="text-slate-400"><Right /></el-icon>
                  <div class="text-sm font-medium text-slate-700">{{ edge.to }}</div>
                  <div class="ml-auto">
                    <el-tag size="small" :type="edge.attentionWeight > 0.7 ? 'danger' : 'info'" round>
                      {{ (edge.attentionWeight * 100).toFixed(1) }}%
                    </el-tag>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-else class="mt-6 rounded-2xl border border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-400">
            点击「GAT 分析」按钮运行图注意力网络
          </div>
        </section>

        <!-- 实体列表 -->
        <section class="content-card p-8">
          <h2 class="panel-title">实体列表</h2>
          <p class="panel-subtitle">当前图谱中的节点实体。</p>

          <div class="mt-6 overflow-hidden rounded-[16px] border border-slate-200">
            <el-table :data="entityTable" stripe max-height="300" size="small">
              <el-table-column prop="name" label="名称" min-width="120" />
              <el-table-column prop="label" label="类型" min-width="100">
                <template #default="{ row }">
                  <el-tag size="small" :color="labelColorMap[row.label] || '#94a3b8'" effect="dark" round style="border:none">
                    {{ row.label }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Connection, MagicStick, Refresh, Right } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';
import GraphVisualization from '@/components/charts/GraphVisualization.vue';
import {
  fetchGraphVisualization,
  runGatOptimization,
  type GraphVisualizationNode,
  type GraphVisualizationEdge,
  type GatOptimizationResponse,
  type GatAttentionEdge,
} from '@/api/graph';
import { fetchBatches } from '@/api/assessment';

// ─── State ───

const selectedBatchId = ref('');
const selectedVersionId = ref('');
const availableBatches = ref<string[]>([]);
const graphVersions = ref<Array<{ id: string; description: string }>>([]);

const loading = ref(false);
const gatLoading = ref(false);

const graphData = ref<{ nodes: GraphVisualizationNode[]; edges: GraphVisualizationEdge[] }>({
  nodes: [],
  edges: [],
});

const gatResult = ref<GatOptimizationResponse | null>(null);

const labelColorMap: Record<string, string> = {
  Batch: '#3b82f6',
  ProcessStep: '#8b5cf6',
  ProcessParameter: '#10b981',
  QualityParameter: '#f59e0b',
  Defect: '#ef4444',
};

// ─── Metrics ───

const metrics = computed(() => [
  { label: '节点总数', value: String(graphData.value.nodes.length), extra: 'Neo4j 实体' },
  { label: '关系总数', value: String(graphData.value.edges.length), extra: '图谱边' },
  { label: '实体类型', value: String(new Set(graphData.value.nodes.map(n => n.label)).size), extra: '分类统计' },
]);

const statsCards = computed(() => {
  const nodes = graphData.value.nodes;
  const edges = graphData.value.edges;
  const labels = [...new Set(nodes.map(n => n.label))];
  return [
    { label: '实体节点', value: nodes.length },
    { label: '关系边', value: edges.length },
    ...labels.slice(0, 4).map(l => ({
      label: l,
      value: nodes.filter(n => n.label === l).length,
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
  graphData.value.nodes.slice(0, 50).map(n => ({
    name: n.name || n.graphId,
    label: n.label,
  })),
);

// ─── Actions ───

const onBatchChange = () => {
  gatResult.value = null;
  loadGraphData();
};

const loadGraphData = async () => {
  if (!selectedBatchId.value) {
    graphData.value = { nodes: [], edges: [] };
    return;
  }

  loading.value = true;
  try {
    const result = await fetchGraphVisualization(selectedBatchId.value);
    graphData.value = { nodes: result.nodes, edges: result.edges };
  } catch (e: any) {
    ElMessage.error('加载图谱数据失败: ' + (e.message || '未知错误'));
    graphData.value = { nodes: [], edges: [] };
  } finally {
    loading.value = false;
  }
};

const onRunGat = async () => {
  if (!selectedBatchId.value) {
    ElMessage.warning('请先选择批次');
    return;
  }

  gatLoading.value = true;
  try {
    const result = await runGatOptimization(selectedBatchId.value);
    gatResult.value = result;
    ElMessage.success('GAT 分析完成');
  } catch (e: any) {
    ElMessage.error('GAT 分析失败: ' + (e.message || '未知错误'));
  } finally {
    gatLoading.value = false;
  }
};

// ─── Init ───

onMounted(async () => {
  try {
    availableBatches.value = await fetchBatches();
  } catch {
    availableBatches.value = [];
  }
});
</script>
