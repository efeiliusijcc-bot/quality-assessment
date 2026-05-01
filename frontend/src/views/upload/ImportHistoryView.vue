<template>
  <div class="space-y-6">
    <PageIntroCard
      title="导入历史"
      description="查看所有 Excel 导入任务的状态与结果统计，点击行可查看详情。"
      badge="IMPORT HISTORY"
      :metrics="metrics"
    />

    <section class="content-card p-8">
      <div class="flex items-center justify-between gap-4 mb-6">
        <div>
          <h2 class="panel-title">导入任务列表</h2>
          <p class="panel-subtitle">所有 Excel 批量导入记录，包含成功 / 失败行数统计。</p>
        </div>
        <el-button type="primary" :loading="loading" @click="loadHistory">
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
        <el-table-column prop="sourceName" label="文件名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="font-medium text-slate-900">{{ row.sourceName || '--' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="导入时间" min-width="180">
          <template #default="{ row }">
            {{ row.startedAt || '--' }}
          </template>
        </el-table-column>

        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.importStatus)" effect="dark" round>
              {{ statusLabel(row.importStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="totalRows" label="总行数" width="100" align="center" />

        <el-table-column label="成功" width="100" align="center">
          <template #default="{ row }">
            <span class="text-emerald-600 font-semibold">{{ row.successRows }}</span>
          </template>
        </el-table-column>

        <el-table-column label="失败" width="100" align="center">
          <template #default="{ row }">
            <span :class="row.errorRows > 0 ? 'text-red-500 font-semibold' : 'text-slate-400'">
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

    <!-- Detail Dialog -->
    <el-dialog
      v-model="detailVisible"
      title="导入详情"
      width="600px"
      destroy-on-close
    >
      <el-descriptions
        v-if="currentItem"
        :column="2"
        border
        label-class-name="!bg-slate-50 !text-slate-600"
      >
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
          <span class="text-emerald-600 font-semibold">{{ currentItem.successRows }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="失败行数">
          <span :class="currentItem.errorRows > 0 ? 'text-red-500 font-semibold' : ''">
            {{ currentItem.errorRows }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentItem.importedBy || '--' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ currentItem.startedAt || '--' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间" :span="2">{{ currentItem.finishedAt || '--' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';

import { fetchImportHistory, type ImportHistoryItem } from '@/api/import-history';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';

const loading = ref(false);
const historyList = ref<ImportHistoryItem[]>([]);
const detailVisible = ref(false);
const currentItem = ref<ImportHistoryItem | null>(null);

const metrics = ref([
  { label: '导入任务总数', value: '--', extra: '加载中' },
  { label: '最近导入', value: '--', extra: '加载中' },
  { label: '总导入行数', value: '--', extra: '加载中' },
]);

const statusTagType = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'SUCCESS':
    case 'COMPLETED':
      return 'success';
    case 'COMPLETED_WITH_ERRORS':
      return 'warning';
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

const showDetail = (row: ImportHistoryItem) => {
  currentItem.value = row;
  detailVisible.value = true;
};

const loadHistory = async () => {
  loading.value = true;
  try {
    const list = await fetchImportHistory();
    historyList.value = list ?? [];

    const totalRows = historyList.value.reduce((s, r) => s + (r.totalRows || 0), 0);
    const latest = historyList.value[0];
    metrics.value = [
      { label: '导入任务总数', value: String(historyList.value.length), extra: '全部记录' },
      { label: '最近导入', value: latest?.startedAt || '--', extra: latest?.sourceName || '暂无' },
      { label: '总导入行数', value: String(totalRows), extra: '累计统计' },
    ];
  } catch {
    ElMessage.error('加载导入历史失败');
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  void loadHistory();
});
</script>

<style scoped>
:deep(.el-table__row) {
  cursor: pointer;
}
</style>
