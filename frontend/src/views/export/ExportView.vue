<template>
  <div class="space-y-6">
    <PageIntroCard
      title="结果筛选与资源导出"
      description="按批次、工位、合格状态和时间范围筛选评估结果，并导出 Excel 或 PDF 报告。"
      badge="EXPORT"
      :metrics="metrics"
    />

    <section class="content-card p-8">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 class="panel-title">共享筛选上下文</h2>
          <p class="panel-subtitle">导出页会同步读取评估看板维护的全局批次、工位和筛选状态。</p>
        </div>
        <el-button @click="assessmentStore.resetFilters">重置共享筛选</el-button>
      </div>

      <div class="mt-6 grid gap-4 md:grid-cols-4">
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">当前批次</div>
          <div class="mt-2 text-lg font-semibold text-slate-900">{{ globalFilters.batchId || '-' }}</div>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">当前工位</div>
          <div class="mt-2 text-lg font-semibold text-slate-900">{{ globalFilters.station || '-' }}</div>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">结果状态</div>
          <div class="mt-2 text-lg font-semibold text-slate-900">{{ resultStatusLabel }}</div>
        </div>
        <div class="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div class="text-sm text-slate-500">最近评估时间</div>
          <div class="mt-2 text-lg font-semibold text-slate-900">{{ lastAssessmentAt }}</div>
        </div>
      </div>
    </section>

    <section class="content-card p-8">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 class="panel-title">结果筛选</h2>
          <p class="panel-subtitle">支持复合条件筛选，并将筛选条件同步到全局评估上下文。</p>
        </div>
        <div class="flex flex-wrap gap-3">
          <el-button type="success" plain :loading="exportLoading.excel" @click="handleExportExcel">
            导出为 Excel
          </el-button>
          <el-button type="warning" plain :loading="exportLoading.pdf" @click="handleExportPdf">
            导出评估报告 PDF
          </el-button>
        </div>
      </div>

      <el-form :inline="true" :model="searchQuery" class="mt-8">
        <el-form-item label="生产批次">
          <el-input v-model="searchQuery.batchId" placeholder="请输入批次号" clearable class="!w-44" />
        </el-form-item>
        <el-form-item label="工位选择">
          <el-select v-model="searchQuery.station" placeholder="所有工位" clearable class="!w-44">
            <el-option v-for="item in stationOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="合格状态">
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

      <div class="mt-6 overflow-hidden rounded-[24px] border border-slate-200">
        <el-table :data="tableData" border stripe v-loading="loading" class="export-table">
          <el-table-column prop="date" label="评估时间" min-width="180" />
          <el-table-column prop="batchId" label="生产批次" min-width="140" />
          <el-table-column prop="station" label="工位" min-width="140" />
          <el-table-column prop="defectType" label="主要缺陷类型" min-width="160" />
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

      <div class="mt-6 flex justify-end">
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
import { ElMessage } from 'element-plus';

import {
  downloadAssessmentExcel,
  downloadAssessmentPdf,
  fetchExportRecords,
  type ExportRecord,
} from '@/api/export';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';
import { useAssessmentStore } from '@/stores/assessment';

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
  status: globalFilters.value.resultStatus,
  dateRange: globalFilters.value.dateRange as [string, string] | [],
});

const metrics = computed(() => [
  { label: '可检索记录', value: `${total.value}`, extra: '符合当前筛选条件' },
  { label: '导出模板', value: '02', extra: 'Excel / PDF' },
  { label: '当前页容量', value: `${pageSize.value}`, extra: '分页查询' },
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

const buildQueryParams = () => ({
  batchId: searchQuery.batchId.trim(),
  station: searchQuery.station,
  status: searchQuery.status,
  dateRange: searchQuery.dateRange,
  page: currentPage.value,
  pageSize: pageSize.value,
});

const syncGlobalFilters = () => {
  assessmentStore.setBatchContext({
    batchId: searchQuery.batchId.trim() || assessmentStore.currentBatchId,
    station: searchQuery.station || assessmentStore.selectedStation,
  });
  assessmentStore.setResultStatus(searchQuery.status);
  assessmentStore.setDateRange(searchQuery.dateRange);
  assessmentStore.globalFilters.batchId = searchQuery.batchId.trim();
  assessmentStore.globalFilters.station = searchQuery.station;
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
  searchQuery.batchId = assessmentStore.currentBatchId;
  searchQuery.station = assessmentStore.selectedStation;
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
    ElMessage.success('Excel 导出已开始');
  } finally {
    exportLoading.excel = false;
  }
};

const handleExportPdf = async () => {
  exportLoading.pdf = true;

  try {
    await downloadAssessmentPdf(buildQueryParams());
    ElMessage.success('PDF 导出已开始');
  } finally {
    exportLoading.pdf = false;
  }
};

const showDetails = (row: ExportRecord) => {
  const statusText = row.status === 'pass' ? '合格' : '不合格';
  ElMessage.info(`${row.batchId} | ${row.station} | ${row.defectType} | ${statusText}`);
};

onMounted(() => {
  assessmentStore.loadStations();
  void loadTableData();
});
</script>

<style scoped>
:deep(.export-table .el-table__header th) {
  background: #e2e8f0;
  color: #0f172a;
}
</style>
