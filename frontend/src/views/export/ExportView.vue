<template>
  <div class="space-y-6">
    <PageIntroCard
      title="结果筛选与资源导出"
      description="按生产批次、工位、合格状态和评估时间筛选检测结果，并导出为 Excel 或 PDF 报告。"
      badge="EXPORT"
      :metrics="metrics"
    />

    <section class="content-card p-6">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 class="panel-title">筛选上下文</h2>
          <p class="panel-subtitle">当前页面筛选会同步到评估模块的全局上下文，便于跨页面保持一致。</p>
        </div>
        <el-button @click="resetSearch">重置筛选</el-button>
      </div>

      <div class="mt-5 grid gap-4 md:grid-cols-4">
        <div class="summary-tile">
          <span>当前批次</span>
          <strong>{{ globalFilters.batchId || '-' }}</strong>
        </div>
        <div class="summary-tile">
          <span>当前工位</span>
          <strong>{{ globalFilters.station || '-' }}</strong>
        </div>
        <div class="summary-tile">
          <span>结果状态</span>
          <strong>{{ resultStatusLabel }}</strong>
        </div>
        <div class="summary-tile">
          <span>最近筛选时间</span>
          <strong>{{ lastAssessmentAt || '-' }}</strong>
        </div>
      </div>
    </section>

    <section class="content-card p-6">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 class="panel-title">检测结果</h2>
          <p class="panel-subtitle">表格按条件分页查看，导出按钮会下载符合当前条件的完整结果集。</p>
        </div>
        <div class="flex flex-wrap gap-3">
          <el-button type="success" plain :loading="exportLoading.excel" @click="handleExportExcel">
            导出 Excel
          </el-button>
          <el-button type="warning" plain :loading="exportLoading.pdf" @click="handleExportPdf">
            导出 PDF
          </el-button>
        </div>
      </div>

      <el-form :inline="true" :model="searchQuery" class="mt-6 export-form">
        <el-form-item label="生产批次">
          <el-input v-model="searchQuery.batchId" placeholder="输入批次号" clearable class="!w-44" />
        </el-form-item>
        <el-form-item label="工位">
          <el-select v-model="searchQuery.station" placeholder="全部工位" clearable class="!w-44">
            <el-option v-for="item in stationOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchQuery.status" placeholder="全部" clearable class="!w-36">
            <el-option label="全部" value="all" />
            <el-option label="合格" value="pass" />
            <el-option label="不合格" value="fail" />
          </el-select>
        </el-form-item>
        <el-form-item label="评估时间">
          <el-date-picker
            v-model="searchQuery.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">筛选</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="mt-5 overflow-hidden rounded-lg border border-slate-200">
        <el-table :data="tableData" border stripe v-loading="loading" class="export-table">
          <el-table-column prop="date" label="评估时间" min-width="170" />
          <el-table-column prop="batchId" label="生产批次" min-width="140" />
          <el-table-column prop="station" label="工位" min-width="130" />
          <el-table-column prop="defectType" label="主要缺陷类型" min-width="150" />
          <el-table-column prop="confidence" label="AI 置信度" min-width="110">
            <template #default="{ row }">
              {{ (row.confidence * 100).toFixed(1) }}%
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" min-width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'pass' ? 'success' : 'danger'" round>
                {{ row.status === 'pass' ? '合格' : '不合格' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="120" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="showDetails(row)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="mt-5 flex justify-end">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  downloadAssessmentExcel,
  downloadAssessmentPdf,
  fetchExportRecords,
  type ExportRecord,
  type ExportSearchParams,
} from '@/api/export';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';
import { useAssessmentStore } from '@/stores/assessment';

type ExportStatus = NonNullable<ExportSearchParams['status']>;

const assessmentStore = useAssessmentStore();
const { globalFilters, lastAssessmentAt } = storeToRefs(assessmentStore);

const stationOptions = computed(() => assessmentStore.availableStations);
const loading = ref(false);
const currentPage = ref(1);
const pageSize = ref(10);
const total = ref(0);
const tableData = ref<ExportRecord[]>([]);
const exportLoading = reactive({
  excel: false,
  pdf: false,
});

const searchQuery = reactive({
  batchId: globalFilters.value.batchId,
  station: globalFilters.value.station,
  status: globalFilters.value.resultStatus as ExportStatus,
  dateRange: globalFilters.value.dateRange as [string, string] | [],
});

const metrics = computed(() => [
  { label: '可导出记录', value: `${total.value}`, extra: '符合当前筛选条件' },
  { label: '导出格式', value: '02', extra: 'Excel / PDF' },
  { label: '每页记录', value: `${pageSize.value}`, extra: '分页查看' },
]);

const resultStatusLabel = computed(() => {
  if (globalFilters.value.resultStatus === 'pass') {
    return '仅合格';
  }
  if (globalFilters.value.resultStatus === 'fail') {
    return '仅不合格';
  }
  return '全部';
});

const buildQueryParams = (): ExportSearchParams => ({
  batchId: searchQuery.batchId.trim(),
  station: searchQuery.station,
  status: searchQuery.status || 'all',
  dateRange: searchQuery.dateRange,
  page: currentPage.value,
  pageSize: pageSize.value,
});

const syncGlobalFilters = () => {
  const batchId = searchQuery.batchId.trim();
  assessmentStore.globalFilters.batchId = batchId;
  assessmentStore.globalFilters.station = searchQuery.station;
  assessmentStore.setResultStatus(searchQuery.status || 'all');
  assessmentStore.setDateRange(searchQuery.dateRange);
  assessmentStore.setBatchContext({
    batchId: batchId || undefined,
    station: searchQuery.station || undefined,
  });
};

const loadTableData = async () => {
  loading.value = true;
  try {
    const response = await fetchExportRecords(buildQueryParams());
    tableData.value = response.list;
    total.value = response.total;
    syncGlobalFilters();
  } finally {
    loading.value = false;
  }
};

const handleSearch = async () => {
  currentPage.value = 1;
  await loadTableData();
};

const resetSearch = async () => {
  searchQuery.batchId = '';
  searchQuery.station = '';
  searchQuery.status = 'all';
  searchQuery.dateRange = [];
  assessmentStore.resetFilters();
  currentPage.value = 1;
  await loadTableData();
};

const handleSizeChange = async (value: number) => {
  pageSize.value = value;
  currentPage.value = 1;
  await loadTableData();
};

const handleCurrentChange = async (value: number) => {
  currentPage.value = value;
  await loadTableData();
};

const handleExportExcel = async () => {
  exportLoading.excel = true;
  try {
    await downloadAssessmentExcel(buildQueryParams());
    ElMessage.success('Excel 文件已开始下载');
  } finally {
    exportLoading.excel = false;
  }
};

const handleExportPdf = async () => {
  exportLoading.pdf = true;
  try {
    await downloadAssessmentPdf(buildQueryParams());
    ElMessage.success('PDF 报告已开始下载');
  } finally {
    exportLoading.pdf = false;
  }
};

const showDetails = (row: ExportRecord) => {
  void ElMessageBox.alert(
    `记录 ID：${row.id}\n生产批次：${row.batchId || '-'}\n工位：${row.station || '-'}\n缺陷类型：${row.defectType}\nAI 置信度：${(row.confidence * 100).toFixed(1)}%\n状态：${row.status === 'pass' ? '合格' : '不合格'}`,
    '检测结果详情',
    { confirmButtonText: '关闭' },
  );
};

onMounted(async () => {
  await assessmentStore.loadStations();
  await loadTableData();
});
</script>

<style scoped>
.summary-tile {
  display: flex;
  min-height: 78px;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  padding: 14px 16px;
}

.summary-tile span {
  font-size: 13px;
  color: #64748b;
}

.summary-tile strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 16px;
  color: #0f172a;
}

:deep(.export-table .el-table__header th) {
  background: #e2e8f0;
  color: #0f172a;
}

:deep(.export-form .el-date-editor) {
  width: 260px;
}
</style>
