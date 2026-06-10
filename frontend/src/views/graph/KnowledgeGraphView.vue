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

        <div class="flex items-end">
          <el-segmented v-model="graphViewMode" :options="graphViewModeOptions" @change="loadGraphData" />
        </div>
      </div>

      <el-alert
        v-if="selectedBatchId && !canRunGat"
        class="mt-4"
        type="info"
        show-icon
        :closable="false"
        title="请先加载当前批次图谱；图谱有节点后即可执行批次级 GAT 注意力分析。"
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
            <GraphVisualization
              :nodes="graphData.nodes"
              :edges="graphData.edges"
              :attention-edges="gatResult?.attentionEdges ?? []"
              :highlight-node-names="visualFocusNodeNames"
              :highlight-edges="visualFocusEdges"
              :focus-mode="visualFocusNodeNames.length > 0 || visualFocusEdges.length > 0"
            />
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
          <h2 class="panel-title">路径与筛选</h2>
          <p class="panel-subtitle">按缺陷、工序或节点路径聚焦当前批次子图。</p>

          <div class="mt-6 space-y-4">
            <el-select v-model="selectedDefectFilter" clearable filterable placeholder="按缺陷筛选" class="w-full">
              <el-option
                v-for="defect in graphAnalysis?.filterOptions.defects ?? []"
                :key="defect"
                :label="defect"
                :value="defect"
              />
            </el-select>
            <el-select v-model="selectedStepFilter" clearable filterable placeholder="按工序筛选" class="w-full">
              <el-option
                v-for="step in graphAnalysis?.filterOptions.processSteps ?? []"
                :key="step"
                :label="step"
                :value="step"
              />
            </el-select>

            <div class="grid gap-3">
              <el-select v-model="pathSource" filterable clearable placeholder="路径起点" class="w-full">
                <el-option v-for="node in pathNodeOptions" :key="`s-${node}`" :label="node" :value="node" />
              </el-select>
              <el-select v-model="pathTarget" filterable clearable placeholder="路径终点" class="w-full">
                <el-option v-for="node in pathNodeOptions" :key="`t-${node}`" :label="node" :value="node" />
              </el-select>
              <div class="flex gap-2">
                <el-button type="primary" :loading="pathLoading" :disabled="!pathSource || !pathTarget" @click="onSearchPath">
                  搜索路径
                </el-button>
                <el-button @click="clearGraphFilters">清除</el-button>
              </div>
            </div>

            <div v-if="pathResult" class="rounded-2xl border border-cyan-100 bg-cyan-50 p-4 text-sm text-cyan-900">
              {{ pathResult.summary }}
            </div>
          </div>
        </section>

        <section class="content-card p-8">
          <h2 class="panel-title">规则 / Apriori</h2>
          <p class="panel-subtitle">规则关系来自显式工艺映射，Apriori 关系来自批次内运行事务共现挖掘。</p>

          <div class="mt-6 space-y-4">
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="mb-3 text-sm font-semibold text-slate-800">规则关系前 5</div>
              <div v-for="item in topRuleRelations" :key="`${item.source}-${item.target}-${item.relationType}`" class="mb-2 text-xs text-slate-600">
                {{ item.source }} -> {{ item.target }} · {{ item.relationType }}
              </div>
              <div v-if="!topRuleRelations.length" class="text-xs text-slate-400">暂无规则关系</div>
            </div>
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div class="mb-3 text-sm font-semibold text-slate-800">Apriori 关联前 5</div>
              <div v-for="item in topAprioriRelations" :key="`${item.source}-${item.target}-${item.relationType}`" class="mb-2 text-xs text-slate-600">
                {{ item.source }} -> {{ item.target }} · lift {{ item.lift.toFixed(2) }} · support {{ (item.support * 100).toFixed(1) }}%
              </div>
              <div v-if="!topAprioriRelations.length" class="text-xs text-slate-400">暂无 Apriori 关联</div>
            </div>
          </div>
        </section>
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
          <p class="panel-subtitle">基于当前批次子图计算注意力权重，突出关键参数、缺陷和工序关系。</p>

          <div v-if="gatResult" class="mt-6 space-y-4">
            <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-700">
              {{ gatResult.explanationSummary || gatResult.summary }}
            </div>
            <div class="grid gap-3">
              <div
                v-if="activeGatFocus"
                class="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900"
              >
                <div class="flex items-center justify-between gap-3">
                  <span>
                    当前聚焦：{{ activeGatFocus.label }} {{ activeGatFocus.name }}，
                    已突出 {{ focusedNodeNames.length }} 个相关节点、{{ focusedAttentionEdges.length }} 条相关关系
                  </span>
                  <el-button size="small" text @click="clearGatFocus">清除筛选</el-button>
                </div>
              </div>
              <div
                v-for="group in gatImportantGroups"
                :key="group.title"
                class="rounded-2xl border border-slate-200 bg-white p-4"
              >
                <div class="mb-3 flex items-center justify-between">
                  <span class="text-sm font-semibold text-slate-800">{{ group.title }}</span>
                  <el-tag size="small" type="info" round>{{ group.items.length }}</el-tag>
                </div>
                <div v-if="group.items.length" class="space-y-2">
                  <div
                    v-for="item in group.items"
                    :key="`${group.title}-${item.name}`"
                    class="cursor-pointer rounded-xl p-3 transition"
                    :class="isActiveGatFocus(groupFocusType(group.title), item.name) ? 'bg-amber-100 ring-2 ring-amber-300' : 'bg-slate-50 hover:bg-slate-100'"
                    @click="toggleGatFocus(groupFocusType(group.title), group.title, item.name)"
                  >
                    <div class="flex items-center justify-between gap-3">
                      <span class="truncate text-sm font-medium text-slate-700">{{ item.name }}</span>
                      <el-tag size="small" :type="item.score > 0.7 ? 'danger' : 'warning'" round>
                        {{ (item.score * 100).toFixed(1) }}%
                      </el-tag>
                    </div>
                    <div class="mt-1 line-clamp-2 text-xs leading-5 text-slate-500">{{ item.reason }}</div>
                  </div>
                </div>
                <div v-else class="rounded-xl bg-slate-50 p-3 text-xs text-slate-400">暂无高权重项</div>
              </div>
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
            先加载批次图谱，再点击页面顶部的 GAT 分析查看注意力权重和重点节点。
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
  fetchGraphAnalysis,
  fetchGraphVisualization,
  runGatOptimization,
  searchGraphPath,
  type GatAttentionEdge,
  type GraphAnalysisResponse,
  type GatOptimizationResponse,
  type GraphVisualizationEdge,
  type GraphVisualizationNode,
  type GraphPathSearchResponse,
} from '@/api/graph';
import GraphVisualization from '@/components/charts/GraphVisualization.vue';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';

const selectedBatchId = ref('');
const route = useRoute();
const availableBatches = ref<string[]>([]);
const loading = ref(false);
const gatLoading = ref(false);
const pathLoading = ref(false);
const graphViewMode = ref<'summary' | 'full'>('summary');
const graphData = ref<{ nodes: GraphVisualizationNode[]; edges: GraphVisualizationEdge[] }>({
  nodes: [],
  edges: [],
});
const gatResult = ref<GatOptimizationResponse | null>(null);
const activeGatFocus = ref<{ type: 'parameter' | 'defect' | 'step'; label: string; name: string } | null>(null);
const graphAnalysis = ref<GraphAnalysisResponse | null>(null);
const selectedDefectFilter = ref('');
const selectedStepFilter = ref('');
const pathSource = ref('');
const pathTarget = ref('');
const pathResult = ref<GraphPathSearchResponse | null>(null);
const graphViewModeOptions = [
  { label: '摘要图', value: 'summary' },
  { label: '完整图', value: 'full' },
];

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

const canRunGat = computed(() => Boolean(selectedBatchId.value && graphData.value.nodes.length > 0));

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

const topRuleRelations = computed(() => graphAnalysis.value?.ruleRelations.slice(0, 5) ?? []);
const topAprioriRelations = computed(() => graphAnalysis.value?.aprioriRelations.slice(0, 5) ?? []);

const pathNodeOptions = computed(() =>
  graphData.value.nodes
    .map((node) => node.name || node.graphId)
    .filter(Boolean)
    .sort(),
);

const toAttentionEdge = (edge: GraphVisualizationEdge): GatAttentionEdge => ({
  from: edge.from,
  to: edge.to,
  relationType: edge.type,
  attentionWeight: Math.max(0.3, Math.min(1, edge.weight || 1)),
});

const nodeNameById = computed(() => new Map(graphData.value.nodes.map((node) => [node.graphId, node.name || node.graphId])));

const graphFilterEdges = computed(() => {
  if (!selectedDefectFilter.value && !selectedStepFilter.value) return [];
  const selected = new Set([selectedDefectFilter.value, selectedStepFilter.value].filter(Boolean).map(normalizeName));
  return graphData.value.edges.filter((edge) => {
    const fromName = nodeNameById.value.get(edge.from) ?? edge.from;
    const toName = nodeNameById.value.get(edge.to) ?? edge.to;
    return selected.has(normalizeName(fromName)) || selected.has(normalizeName(toName));
  });
});

const gatImportantGroups = computed(() => {
  if (!gatResult.value) return [];
  return [
    { title: '重点参数', items: gatResult.value.topParameters.slice(0, 3) },
    { title: '重点缺陷', items: gatResult.value.topDefects.slice(0, 3) },
    { title: '重点工序', items: gatResult.value.topProcessSteps.slice(0, 3) },
  ];
});

const normalizeName = (value: string) => value.trim().toLowerCase();

const focusedAttentionEdges = computed(() => {
  if (!gatResult.value || !activeGatFocus.value) return [];
  const focusName = normalizeName(activeGatFocus.value.name);
  return gatResult.value.attentionEdges.filter((edge) =>
    normalizeName(edge.from) === focusName || normalizeName(edge.to) === focusName,
  );
});

const focusedNodeNames = computed(() => {
  if (!activeGatFocus.value) return [];
  const names = new Set<string>([activeGatFocus.value.name]);
  for (const edge of focusedAttentionEdges.value) {
    names.add(edge.from);
    names.add(edge.to);
  }
  return [...names];
});

const visualFocusEdges = computed(() => {
  const edges: GatAttentionEdge[] = [];
  edges.push(...focusedAttentionEdges.value);
  edges.push(...graphFilterEdges.value.map(toAttentionEdge));
  edges.push(...(pathResult.value?.edges ?? []).map(toAttentionEdge));
  const unique = new Map<string, GatAttentionEdge>();
  for (const edge of edges) {
    unique.set(`${edge.from}|${edge.to}|${edge.relationType}`, edge);
  }
  return [...unique.values()];
});

const visualFocusNodeNames = computed(() => {
  const names = new Set<string>(focusedNodeNames.value);
  for (const edge of graphFilterEdges.value) {
    names.add(nodeNameById.value.get(edge.from) ?? edge.from);
    names.add(nodeNameById.value.get(edge.to) ?? edge.to);
  }
  for (const node of pathResult.value?.nodes ?? []) {
    names.add(node.name || node.graphId);
    names.add(node.graphId);
  }
  return [...names];
});

const entityTable = computed(() =>
  graphData.value.nodes.slice(0, 80).map((node) => ({
    name: node.name || node.graphId,
    label: node.label,
  })),
);

const displayLabel = (label: string) => labelNameMap[label] || label;

const groupFocusType = (title: string): 'parameter' | 'defect' | 'step' => {
  if (title.includes('缺') || title.includes('缂')) return 'defect';
  if (title.includes('工') || title.includes('宸')) return 'step';
  return 'parameter';
};

const isActiveGatFocus = (type: 'parameter' | 'defect' | 'step', name: string) =>
  activeGatFocus.value?.type === type && activeGatFocus.value.name === name;

const toggleGatFocus = (type: 'parameter' | 'defect' | 'step', label: string, name: string) => {
  if (isActiveGatFocus(type, name)) {
    activeGatFocus.value = null;
    return;
  }
  activeGatFocus.value = { type, label, name };
};

const clearGatFocus = () => {
  activeGatFocus.value = null;
};

const clearGraphFilters = () => {
  selectedDefectFilter.value = '';
  selectedStepFilter.value = '';
  pathSource.value = '';
  pathTarget.value = '';
  pathResult.value = null;
};

const onBatchChange = () => {
  gatResult.value = null;
  activeGatFocus.value = null;
  clearGraphFilters();
  void loadGraphData();
};

const loadGraphData = async () => {
  if (!selectedBatchId.value) {
    graphData.value = { nodes: [], edges: [] };
    graphAnalysis.value = null;
    return;
  }

  loading.value = true;
  try {
    const [result, analysis] = await Promise.all([
      fetchGraphVisualization(selectedBatchId.value, graphViewMode.value === 'full'),
      fetchGraphAnalysis(selectedBatchId.value).catch(() => null),
    ]);
    graphData.value = {
      nodes: result.nodes ?? [],
      edges: result.edges ?? [],
    };
    graphAnalysis.value = analysis;
    if (graphData.value.nodes.length === 0) {
      ElMessage.info('该批次暂无图谱数据，请先执行知识图谱同步。');
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String((error as { message?: string })?.message ?? '未知错误');
    ElMessage.error(`加载图谱失败：${message}`);
    graphData.value = { nodes: [], edges: [] };
    graphAnalysis.value = null;
  } finally {
    loading.value = false;
  }
};

const onSearchPath = async () => {
  if (!selectedBatchId.value || !pathSource.value || !pathTarget.value) return;
  pathLoading.value = true;
  try {
    pathResult.value = await searchGraphPath(selectedBatchId.value, pathSource.value, pathTarget.value);
    if (!pathResult.value.nodes.length) {
      ElMessage.info(pathResult.value.summary || '未找到路径');
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String((error as { message?: string })?.message ?? '未知错误');
    ElMessage.error(`路径搜索失败：${message}`);
  } finally {
    pathLoading.value = false;
  }
};

const onRunGat = async () => {
  if (!canRunGat.value) {
    ElMessage.warning('请先选择批次并加载图谱数据。');
    return;
  }

  gatLoading.value = true;
  try {
    gatResult.value = await runGatOptimization(selectedBatchId.value);
    activeGatFocus.value = null;
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
