<template>
  <div class="space-y-6">
    <PageIntroCard
      title="导入历史"
      description="查看 Excel 导入任务状态、行数统计，以及本次导入触发的数据清洗记录。"
      badge="IMPORT HISTORY"
      :metrics="metrics"
    />

    <section class="content-card p-8">
      <div class="mb-6 flex items-center justify-between gap-4">
        <div>
          <h2 class="panel-title">导入任务列表</h2>
          <p class="panel-subtitle">点击任一任务查看导入详情和关联的清洗日志。</p>
        </div>
        <el-button type="primary" :loading="loading" @click="loadHistory">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="historyList"
        stripe
        highlight-current-row
        style="width: 100%"
        @row-click="showDetail"
      >
        <el-table-column prop="sourceName" label="文件名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="font-medium text-slate-900">{{ row.sourceName || '--' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="sourceType" label="来源" width="110" align="center" />

        <el-table-column label="导入时间" min-width="180">
          <template #default="{ row }">
            {{ row.startedAt || '--' }}
          </template>
        </el-table-column>

        <el-table-column label="状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.importStatus)" effect="dark" round>
              {{ statusLabel(row.importStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="totalRows" label="总行数" width="100" align="center" />

        <el-table-column label="成功" width="100" align="center">
          <template #default="{ row }">
            <span class="font-semibold text-emerald-600">{{ row.successRows }}</span>
          </template>
        </el-table-column>

        <el-table-column label="失败" width="100" align="center">
          <template #default="{ row }">
            <span :class="row.errorRows > 0 ? 'font-semibold text-red-500' : 'text-slate-400'">
              {{ row.errorRows }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="detailVisible" title="导入详情" width="920px" destroy-on-close>
      <div v-if="currentItem" class="space-y-5">
        <el-descriptions :column="2" border label-class-name="!bg-slate-50 !text-slate-600">
          <el-descriptions-item label="任务ID" :span="2">
            <span class="font-mono text-sm">{{ currentItem.importId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="文件名称">{{ currentItem.sourceName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="数据来源">{{ currentItem.sourceType || '--' }}</el-descriptions-item>
          <el-descriptions-item label="目标表">{{ currentItem.targetTable || '--' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(currentItem.importStatus)" effect="dark" round>
              {{ statusLabel(currentItem.importStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总行数">{{ currentItem.totalRows }}</el-descriptions-item>
          <el-descriptions-item label="成功行数">
            <span class="font-semibold text-emerald-600">{{ currentItem.successRows }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="失败行数">
            <span :class="currentItem.errorRows > 0 ? 'font-semibold text-red-500' : ''">
              {{ currentItem.errorRows }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="操作人">{{ currentItem.importedBy || '--' }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ currentItem.startedAt || '--' }}</el-descriptions-item>
          <el-descriptions-item label="完成时间" :span="2">{{ currentItem.finishedAt || '--' }}</el-descriptions-item>
        </el-descriptions>

        <div class="rounded-lg border border-slate-200">
          <div class="flex items-center justify-between border-b border-slate-200 px-4 py-3">
            <div>
              <div class="text-sm font-semibold text-slate-800">本次清洗日志</div>
              <div class="mt-1 text-xs text-slate-500">按导入任务开始和完成时间自动关联。</div>
            </div>
            <el-tag type="success" effect="plain">{{ cleaningLogs.length }} 条</el-tag>
          </div>

          <el-table
            v-loading="loadingCleaningLogs"
            :data="cleaningLogs"
            stripe
            max-height="320"
            style="width: 100%"
            @row-click="showCleaningLog"
          >
            <el-table-column label="执行时间" min-width="170">
              <template #default="{ row }">{{ row.createdAt || '--' }}</template>
            </el-table-column>
            <el-table-column label="来源表" width="140">
              <template #default="{ row }">{{ targetLabel(row.sourceTable) }}</template>
            </el-table-column>
            <el-table-column prop="sourceId" label="来源ID" min-width="200" show-overflow-tooltip />
            <el-table-column label="结果" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.actionResult === 'APPLIED' ? 'success' : 'warning'" round>
                  {{ row.actionResult || '--' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="清洗前" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ compactJson(row.beforeValue) }}</template>
            </el-table-column>
            <el-table-column label="清洗后" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ compactJson(row.afterValue) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="cleaningDetailVisible" title="清洗日志详情" width="720px" destroy-on-close>
      <div v-if="currentCleaningLog" class="grid gap-4 md:grid-cols-2">
        <div>
          <div class="mb-2 text-sm font-semibold text-slate-700">清洗前</div>
          <pre class="json-panel">{{ prettyJson(currentCleaningLog.beforeValue) }}</pre>
        </div>
        <div>
          <div class="mb-2 text-sm font-semibold text-slate-700">清洗后</div>
          <pre class="json-panel">{{ prettyJson(currentCleaningLog.afterValue) }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';

import { fetchImportCleaningLogs, fetchImportHistory, type ImportHistoryItem } from '@/api/import-history';
import type { CleaningLog } from '@/api/cleaning';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';

const loading = ref(false);
const loadingCleaningLogs = ref(false);
const historyList = ref<ImportHistoryItem[]>([]);
const detailVisible = ref(false);
const cleaningDetailVisible = ref(false);
const currentItem = ref<ImportHistoryItem | null>(null);
const cleaningLogs = ref<CleaningLog[]>([]);
const currentCleaningLog = ref<CleaningLog | null>(null);

const metrics = computed(() => {
  const totalRows = historyList.value.reduce((sum, row) => sum + (row.totalRows || 0), 0);
  const latest = historyList.value[0];
  const failedJobs = historyList.value.filter((row) => row.errorRows > 0 || row.importStatus === 'FAILED').length;
  return [
    { label: '导入任务总数', value: String(historyList.value.length), extra: `${failedJobs} 个异常任务` },
    { label: '最近导入', value: latest?.startedAt || '--', extra: latest?.sourceName || '暂无记录' },
    { label: '累计导入行数', value: String(totalRows), extra: '全部导入任务' },
  ];
});

const statusTagType = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'SUCCESS':
    case 'COMPLETED':
      return 'success';
    case 'COMPLETED_WITH_ERRORS':
    case 'IMPORTING':
    case 'PROCESSING':
    case 'RUNNING':
      return 'warning';
    case 'FAILED':
    case 'ERROR':
      return 'danger';
    default:
      return 'info';
  }
};

const statusLabel = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'SUCCESS':
    case 'COMPLETED':
      return '成功';
    case 'COMPLETED_WITH_ERRORS':
      return '部分成功';
    case 'IMPORTING':
    case 'PROCESSING':
    case 'RUNNING':
      return '导入中';
    case 'FAILED':
    case 'ERROR':
      return '失败';
    default:
      return status || '--';
  }
};

const targetLabel = (target?: string | null) => {
  switch (target) {
    case 'parameter_value':
      return '工艺参数值';
    case 'quality_measurement':
      return '质量测量值';
    default:
      return target || '--';
  }
};

const compactJson = (value?: string | null) => {
  if (!value) return '--';
  try {
    return JSON.stringify(JSON.parse(value));
  } catch {
    return value;
  }
};

const prettyJson = (value?: string | null) => {
  if (!value) return '--';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
};

const loadHistory = async () => {
  loading.value = true;
  try {
    historyList.value = (await fetchImportHistory()) ?? [];
  } catch {
    ElMessage.error('加载导入历史失败');
  } finally {
    loading.value = false;
  }
};

const loadCleaningLogs = async (importId: string) => {
  loadingCleaningLogs.value = true;
  try {
    cleaningLogs.value = (await fetchImportCleaningLogs(importId)) ?? [];
  } catch {
    cleaningLogs.value = [];
    ElMessage.error('加载本次清洗日志失败');
  } finally {
    loadingCleaningLogs.value = false;
  }
};

const showDetail = (row: ImportHistoryItem) => {
  currentItem.value = row;
  cleaningLogs.value = [];
  detailVisible.value = true;
  void loadCleaningLogs(row.importId);
};

const showCleaningLog = (row: CleaningLog) => {
  currentCleaningLog.value = row;
  cleaningDetailVisible.value = true;
};

onMounted(() => {
  void loadHistory();
});
</script>

<style scoped>
:deep(.el-table__row) {
  cursor: pointer;
}

.json-panel {
  max-height: 280px;
  overflow: auto;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  padding: 14px;
  font-size: 12px;
  line-height: 1.6;
}
</style>
