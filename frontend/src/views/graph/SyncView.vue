<template>
  <div class="space-y-6">
    <PageIntroCard
      title="知识图谱同步"
      description="将 PostgreSQL 数据同步到 Neo4j 知识图谱，支持节点与关系的增量构建与全量更新。"
      badge="GRAPH SYNC"
      :metrics="metrics"
    />

    <!-- 同步操作区 -->
    <section class="content-card p-8">
      <div class="flex items-start justify-between gap-6">
        <div>
          <h2 class="panel-title">同步操作</h2>
          <p class="panel-subtitle">点击按钮开始将数据库数据同步到 Neo4j 知识图谱。</p>
        </div>
        <el-button
          type="primary"
          size="large"
          :loading="syncing"
          :disabled="syncing"
          @click="handleSync"
        >
          <el-icon class="mr-1"><Refresh /></el-icon>
          开始同步
        </el-button>
      </div>

      <!-- 同步状态 -->
      <div v-if="syncStatus !== 'idle'" class="mt-6">
        <div
          class="rounded-2xl border p-5"
          :class="{
            'border-blue-200 bg-blue-50': syncStatus === 'syncing',
            'border-green-200 bg-green-50': syncStatus === 'success',
            'border-red-200 bg-red-50': syncStatus === 'error',
          }"
        >
          <div class="flex items-center gap-3">
            <el-icon
              v-if="syncStatus === 'syncing'"
              class="animate-spin text-blue-500"
              :size="20"
            >
              <Loading />
            </el-icon>
            <el-icon
              v-else-if="syncStatus === 'success'"
              class="text-green-500"
              :size="20"
            >
              <CircleCheckFilled />
            </el-icon>
            <el-icon
              v-else
              class="text-red-500"
              :size="20"
            >
              <CircleCloseFilled />
            </el-icon>
            <span
              class="text-sm font-medium"
              :class="{
                'text-blue-700': syncStatus === 'syncing',
                'text-green-700': syncStatus === 'success',
                'text-red-700': syncStatus === 'error',
              }"
            >
              {{ statusMessage }}
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- 同步结果 -->
    <section v-if="syncResult" class="content-card p-8">
      <h2 class="panel-title">同步结果</h2>
      <p class="panel-subtitle">本次同步的节点与关系统计。</p>

      <div class="mt-6 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        <div
          v-for="(count, key) in syncResult"
          :key="key"
          class="rounded-2xl border border-slate-200 bg-slate-50 p-5"
        >
          <div class="text-sm text-slate-500">{{ formatLabel(String(key)) }}</div>
          <div class="mt-3 text-2xl font-bold text-slate-900">{{ count }}</div>
          <div class="mt-1 text-xs text-slate-400">{{ formatUnit(String(key)) }}</div>
        </div>
      </div>

      <!-- 汇总 -->
      <div class="mt-6 rounded-2xl border border-slate-200 bg-slate-50 p-5">
        <div class="text-sm text-slate-500">同步汇总</div>
        <div class="mt-4 grid gap-4 md:grid-cols-2">
          <div>
            <span class="text-sm text-slate-500">总节点数：</span>
            <span class="text-base font-semibold text-slate-900">{{ totalNodes }}</span>
          </div>
          <div>
            <span class="text-sm text-slate-500">总关系数：</span>
            <span class="text-base font-semibold text-slate-900">{{ totalRelationships }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 当前图谱统计 -->
    <section class="content-card p-8">
      <div class="flex items-start justify-between gap-4">
        <div>
          <h2 class="panel-title">当前图谱统计</h2>
          <p class="panel-subtitle">Neo4j 知识图谱中的节点与关系概览。</p>
        </div>
        <el-button :loading="statsLoading" @click="loadStats">
          <el-icon class="mr-1"><Refresh /></el-icon>
          刷新统计
        </el-button>
      </div>

      <div class="mt-6">
        <el-descriptions
          :column="2"
          border
          class="rounded-2xl overflow-hidden"
        >
          <el-descriptions-item label="节点总数">
            <el-tag type="primary" effect="dark" round>{{ stats.totalNodes }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="关系总数">
            <el-tag type="success" effect="dark" round>{{ stats.totalRelationships }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="实体类型数">
            <el-tag type="info" effect="dark" round>{{ stats.entityTypes }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="最后同步时间">
            <span class="text-sm text-slate-600">{{ stats.lastSyncTime || '暂无记录' }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 节点类型分布 -->
      <div v-if="stats.nodeTypeCounts && Object.keys(stats.nodeTypeCounts).length > 0" class="mt-6">
        <h3 class="text-sm font-medium text-slate-500 mb-4">节点类型分布</h3>
        <div class="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
          <div
            v-for="(count, type) in stats.nodeTypeCounts"
            :key="type"
            class="flex items-center justify-between rounded-xl border border-slate-200 bg-white px-4 py-3"
          >
            <div class="flex items-center gap-2">
              <div
                class="w-3 h-3 rounded-full"
                :style="{ backgroundColor: nodeTypeColors[String(type)] || '#94a3b8' }"
              />
              <span class="text-sm text-slate-700">{{ String(type) }}</span>
            </div>
            <el-tag size="small" round>{{ count }}</el-tag>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  CircleCheckFilled,
  CircleCloseFilled,
  Loading,
  Refresh,
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';
import { syncToNeo4j, type SyncResult } from '@/api/graph-sync';
import { request } from '@/utils/request';

// ─── State ───

const syncing = ref(false);
const syncStatus = ref<'idle' | 'syncing' | 'success' | 'error'>('idle');
const statusMessage = ref('');
const syncResult = ref<SyncResult | null>(null);

const statsLoading = ref(false);
const stats = reactive({
  totalNodes: 0,
  totalRelationships: 0,
  entityTypes: 0,
  lastSyncTime: '',
  nodeTypeCounts: {} as Record<string, number>,
});

const nodeTypeColors: Record<string, string> = {
  Batch: '#3b82f6',
  ProcessStep: '#8b5cf6',
  ProcessParameter: '#10b981',
  QualityParameter: '#f59e0b',
  Defect: '#ef4444',
};

// ─── Computed ───

const metrics = computed(() => [
  {
    label: '节点总数',
    value: String(stats.totalNodes),
    extra: 'Neo4j 实体节点',
  },
  {
    label: '关系总数',
    value: String(stats.totalRelationships),
    extra: '图谱关系边',
  },
  {
    label: '最后同步',
    value: stats.lastSyncTime || '暂无',
    extra: '同步时间',
  },
]);

const totalNodes = computed(() => {
  if (!syncResult.value) return 0;
  return Object.entries(syncResult.value)
    .filter(([key]) => key.toLowerCase().includes('node') || (!key.toLowerCase().includes('relationship') && !key.toLowerCase().includes('rel')))
    .reduce((sum, [, count]) => sum + count, 0);
});

const totalRelationships = computed(() => {
  if (!syncResult.value) return 0;
  return Object.entries(syncResult.value)
    .filter(([key]) => key.toLowerCase().includes('relationship') || key.toLowerCase().includes('rel'))
    .reduce((sum, [, count]) => sum + count, 0);
});

// ─── Helpers ───

const formatLabel = (key: string): string => {
  const labelMap: Record<string, string> = {
    nodes_created: '创建节点',
    nodes_updated: '更新节点',
    relationships_created: '创建关系',
    relationships_updated: '更新关系',
    labels_added: '添加标签',
    properties_set: '设置属性',
    batch_nodes: '批次节点',
    process_step_nodes: '工艺步骤节点',
    process_parameter_nodes: '工艺参数节点',
    quality_parameter_nodes: '质量参数节点',
    defect_nodes: '缺陷节点',
    contains_relationships: '包含关系',
    has_parameter_relationships: '参数关系',
    produces_defect_relationships: '缺陷关系',
  };
  return labelMap[key] || key.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
};

const formatUnit = (key: string): string => {
  if (key.toLowerCase().includes('node')) return '个节点';
  if (key.toLowerCase().includes('relationship') || key.toLowerCase().includes('rel')) return '条关系';
  if (key.toLowerCase().includes('label')) return '个标签';
  if (key.toLowerCase().includes('property')) return '个属性';
  return '条记录';
};

// ─── Actions ───

const handleSync = async () => {
  syncing.value = true;
  syncStatus.value = 'syncing';
  statusMessage.value = '正在同步数据到 Neo4j 知识图谱，请稍候...';
  syncResult.value = null;

  try {
    const result = await syncToNeo4j();
    syncResult.value = result;
    syncStatus.value = 'success';
    statusMessage.value = '同步完成！数据已成功写入 Neo4j 知识图谱。';
    ElMessage.success('知识图谱同步完成');

    // 刷新统计
    loadStats();
  } catch (e: any) {
    syncStatus.value = 'error';
    statusMessage.value = '同步失败：' + (e.message || '未知错误');
    ElMessage.error('同步失败：' + (e.message || '未知错误'));
  } finally {
    syncing.value = false;
  }
};

const loadStats = async () => {
  statsLoading.value = true;
  try {
    const data = await request({
      url: '/graph/stats',
      method: 'GET',
      showError: false,
    }) as any;

    if (data) {
      stats.totalNodes = data.totalNodes ?? data.nodeCount ?? 0;
      stats.totalRelationships = data.totalRelationships ?? data.relationshipCount ?? 0;
      stats.entityTypes = data.entityTypes ?? data.labelCount ?? 0;
      stats.lastSyncTime = data.lastSyncTime ?? data.last_sync_time ?? '';
      stats.nodeTypeCounts = data.nodeTypeCounts ?? data.node_type_counts ?? {};
    }
  } catch {
    // Stats endpoint may not exist yet, silently fail
    stats.totalNodes = 0;
    stats.totalRelationships = 0;
    stats.entityTypes = 0;
    stats.lastSyncTime = '';
    stats.nodeTypeCounts = {};
  } finally {
    statsLoading.value = false;
  }
};

// ─── Init ───

onMounted(() => {
  loadStats();
});
</script>
