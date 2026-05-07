<template>
  <div class="space-y-6">
    <PageIntroCard
      title="知识图谱同步"
      description="将 PostgreSQL 中的生产、质检、工艺基础数据同步到 Neo4j，并查看每个批次的同步状态。"
      badge="GRAPH SYNC"
      :metrics="metrics"
    />

    <section class="content-card p-8">
      <div class="flex flex-col gap-5 md:flex-row md:items-start md:justify-between">
        <div>
          <h2 class="panel-title">同步操作</h2>
          <p class="panel-subtitle">
            全量同步会重建 Neo4j 图谱。批次重试用于处理 FAILED/PENDING 状态的批次，并记录重试结果。
          </p>
        </div>
        <div class="flex flex-wrap gap-3">
          <el-button :loading="tasksLoading" @click="loadTasks">
            <el-icon class="mr-1"><Refresh /></el-icon>
            刷新状态
          </el-button>
          <el-button
            type="primary"
            size="large"
            :loading="syncing"
            :disabled="syncing"
            @click="handleSync"
          >
            <el-icon class="mr-1"><Refresh /></el-icon>
            开始全量同步
          </el-button>
        </div>
      </div>

      <div v-if="syncStatus !== 'idle'" class="mt-6">
        <div class="rounded-2xl border p-5" :class="statusClass">
          <div class="flex items-center gap-3">
            <el-icon v-if="syncStatus === 'syncing'" class="animate-spin text-blue-500" :size="20">
              <Loading />
            </el-icon>
            <el-icon v-else-if="syncStatus === 'success'" class="text-green-500" :size="20">
              <CircleCheckFilled />
            </el-icon>
            <el-icon v-else class="text-red-500" :size="20">
              <CircleCloseFilled />
            </el-icon>
            <span class="text-sm font-medium">{{ statusMessage }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="content-card p-8">
      <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <h2 class="panel-title">批次同步状态</h2>
          <p class="panel-subtitle">FAILED 和 PENDING 批次支持点击重试；SUCCESS 批次可直接进入图谱可视化查看。</p>
        </div>
        <div class="flex flex-wrap gap-2">
          <el-tag type="success" effect="dark" round>SUCCESS {{ statusCounts.SUCCESS }}</el-tag>
          <el-tag type="danger" effect="dark" round>FAILED {{ statusCounts.FAILED }}</el-tag>
          <el-tag type="warning" effect="dark" round>PENDING {{ statusCounts.PENDING }}</el-tag>
          <el-tag type="info" effect="dark" round>RUNNING {{ statusCounts.RUNNING }}</el-tag>
        </div>
      </div>

      <div class="mt-6 overflow-hidden rounded-[24px] border border-slate-200">
        <el-table :data="tasks" border stripe v-loading="tasksLoading" class="export-table">
          <el-table-column prop="batchId" label="批次号" min-width="220" show-overflow-tooltip />
          <el-table-column prop="syncStatus" label="同步状态" min-width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.syncStatus)" effect="dark" round>
                {{ row.syncStatus }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="triggerSource" label="触发来源" min-width="140" />
          <el-table-column prop="nodeCount" label="节点数" min-width="100">
            <template #default="{ row }">{{ row.nodeCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column prop="relationCount" label="关系数" min-width="100">
            <template #default="{ row }">{{ row.relationCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column prop="finishedAt" label="完成时间" min-width="190">
            <template #default="{ row }">{{ formatTime(row.finishedAt) }}</template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="失败原因" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMessage || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="150">
            <template #default="{ row }">
              <el-button
                v-if="canRetry(row.syncStatus)"
                type="primary"
                size="small"
                :loading="retryingBatchId === row.batchId"
                @click="handleRetry(row.batchId)"
              >
                重试
              </el-button>
              <el-button
                v-else
                size="small"
                @click="openVisualization(row.batchId)"
              >
                查看图谱
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section v-if="syncResult" class="content-card p-8">
      <h2 class="panel-title">最近一次全量同步结果</h2>
      <p class="panel-subtitle">本次 Neo4j 写入的节点和关系统计。</p>

      <div class="mt-6 grid gap-4 md:grid-cols-3">
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-5">
          <div class="text-sm text-slate-500">节点写入</div>
          <div class="mt-3 text-3xl font-bold text-slate-900">{{ syncNodeTotal }}</div>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-5">
          <div class="text-sm text-slate-500">关系写入</div>
          <div class="mt-3 text-3xl font-bold text-slate-900">{{ syncRelationTotal }}</div>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-5">
          <div class="text-sm text-slate-500">统计项数</div>
          <div class="mt-3 text-3xl font-bold text-slate-900">{{ syncItems.length }}</div>
        </div>
      </div>
    </section>

    <section class="content-card p-8">
      <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <h2 class="panel-title">当前图谱统计</h2>
          <p class="panel-subtitle">从后端图谱统计接口读取当前 PostgreSQL/图谱侧统计概览。</p>
        </div>
        <el-button :loading="statsLoading" @click="loadStats">
          <el-icon class="mr-1"><Refresh /></el-icon>
          刷新统计
        </el-button>
      </div>

      <el-descriptions :column="3" border class="mt-6 overflow-hidden rounded-2xl">
        <el-descriptions-item label="实体数量">
          <el-tag type="primary" effect="dark" round>{{ stats.entityCount }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="关系数量">
          <el-tag type="success" effect="dark" round>{{ stats.relationCount }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="图谱版本">
          <el-tag type="info" effect="dark" round>{{ stats.versionCount }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  CircleCheckFilled,
  CircleCloseFilled,
  Loading,
  Refresh,
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

import {
  fetchGraphSyncTasks,
  retryGraphSyncTask,
  syncToNeo4j,
  type GraphSyncTaskStatus,
  type SyncResult,
} from '@/api/graph-sync';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';
import { request } from '@/utils/request';

interface GraphStats {
  entityCount: number;
  relationCount: number;
  versionCount: number;
}

const router = useRouter();

const relationKeys = new Set([
  'HAS_WORKSTATION',
  'HAS_EQUIPMENT',
  'HAS_PARAMETER',
  'HAS_UNIT',
  'HAS_RUN',
  'HAS_PARAM_VALUE',
  'HAS_INSPECTION',
  'FOUND_DEFECT',
  'HAS_MEASUREMENT',
  'OF_TYPE',
  'OF_PARAMETER',
]);

const syncing = ref(false);
const syncStatus = ref<'idle' | 'syncing' | 'success' | 'error'>('idle');
const statusMessage = ref('');
const syncResult = ref<SyncResult | null>(null);
const statsLoading = ref(false);
const tasksLoading = ref(false);
const retryingBatchId = ref('');
const tasks = ref<GraphSyncTaskStatus[]>([]);
const stats = reactive<GraphStats>({
  entityCount: 0,
  relationCount: 0,
  versionCount: 0,
});

const syncItems = computed(() => {
  if (!syncResult.value) return [];
  return Object.entries(syncResult.value).map(([key, count]) => ({
    key,
    count,
    kind: relationKeys.has(key) ? 'relation' : 'node',
  }));
});

const syncNodeTotal = computed(() =>
  syncItems.value
    .filter((item) => item.kind === 'node')
    .reduce((sum, item) => sum + item.count, 0),
);

const syncRelationTotal = computed(() =>
  syncItems.value
    .filter((item) => item.kind === 'relation')
    .reduce((sum, item) => sum + item.count, 0),
);

const statusCounts = computed(() => {
  const counts = { SUCCESS: 0, FAILED: 0, PENDING: 0, RUNNING: 0 };
  for (const task of tasks.value) {
    const key = task.syncStatus as keyof typeof counts;
    if (key in counts) counts[key] += 1;
  }
  return counts;
});

const metrics = computed(() => [
  { label: '同步成功', value: String(statusCounts.value.SUCCESS), extra: 'SUCCESS 批次' },
  { label: '同步失败', value: String(statusCounts.value.FAILED), extra: 'FAILED 可重试' },
  { label: '待同步', value: String(statusCounts.value.PENDING), extra: 'PENDING 批次' },
]);

const statusClass = computed(() => ({
  'border-blue-200 bg-blue-50 text-blue-700': syncStatus.value === 'syncing',
  'border-green-200 bg-green-50 text-green-700': syncStatus.value === 'success',
  'border-red-200 bg-red-50 text-red-700': syncStatus.value === 'error',
}));

const statusTagType = (status: string) => {
  if (status === 'SUCCESS') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'RUNNING') return 'warning';
  return 'info';
};

const canRetry = (status: string) => status === 'FAILED' || status === 'PENDING';

const formatTime = (value: string | null) => {
  if (!value) return '-';
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
};

const handleSync = async () => {
  syncing.value = true;
  syncStatus.value = 'syncing';
  statusMessage.value = '正在同步数据到 Neo4j，请稍候。';
  syncResult.value = null;

  try {
    syncResult.value = await syncToNeo4j();
    syncStatus.value = 'success';
    statusMessage.value = '全量同步完成，数据已写入 Neo4j。';
    ElMessage.success('知识图谱全量同步完成');
    await Promise.all([loadStats(), loadTasks()]);
  } catch (error) {
    const message = error instanceof Error ? error.message : String((error as { message?: string })?.message ?? '未知错误');
    syncStatus.value = 'error';
    statusMessage.value = `同步失败：${message}`;
    ElMessage.error(statusMessage.value);
  } finally {
    syncing.value = false;
  }
};

const handleRetry = async (batchId: string) => {
  retryingBatchId.value = batchId;
  try {
    const updated = await retryGraphSyncTask(batchId);
    const index = tasks.value.findIndex((item) => item.batchId === batchId);
    if (index >= 0) {
      tasks.value[index] = updated;
    }
    ElMessage.success(`${batchId} 重试完成：${updated.syncStatus}`);
    await Promise.all([loadStats(), loadTasks()]);
  } catch (error) {
    const message = error instanceof Error ? error.message : String((error as { message?: string })?.message ?? '未知错误');
    ElMessage.error(`批次重试失败：${message}`);
    await loadTasks();
  } finally {
    retryingBatchId.value = '';
  }
};

const openVisualization = (batchId: string) => {
  void router.push({ name: 'knowledge-graph', query: { batchId } });
};

const loadTasks = async () => {
  tasksLoading.value = true;
  try {
    tasks.value = await fetchGraphSyncTasks();
  } catch {
    tasks.value = [];
  } finally {
    tasksLoading.value = false;
  }
};

const loadStats = async () => {
  statsLoading.value = true;
  try {
    const data = await request<Partial<GraphStats>>({
      url: '/graph/stats',
      method: 'GET',
      showError: false,
    });

    stats.entityCount = Number(data.entityCount ?? 0);
    stats.relationCount = Number(data.relationCount ?? 0);
    stats.versionCount = Number(data.versionCount ?? 0);
  } catch {
    stats.entityCount = 0;
    stats.relationCount = 0;
    stats.versionCount = 0;
  } finally {
    statsLoading.value = false;
  }
};

onMounted(() => {
  void Promise.all([loadStats(), loadTasks()]);
});
</script>
