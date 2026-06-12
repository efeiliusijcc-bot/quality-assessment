<template>
  <div class="space-y-5">
    <PageIntroCard
      title="质量缺陷数据立方体"
      description="围绕批次、工序、设备、缺陷类型、时间与严重等级等维度，对质量缺陷数据进行多维组织、统计和共享。"
      badge="QUALITY CUBE"
      :metrics="introMetrics"
    />

    <div class="grid grid-cols-1 gap-4 xl:grid-cols-4">
      <MetricTile label="缺陷总数" :value="formatNumber(overview?.totals?.defect_total)" extra="累计缺陷数量" />
      <MetricTile
        label="高风险缺陷"
        :value="formatNumber(overview?.totals?.critical_defect_total)"
        extra="严重或关键缺陷数量"
        tag="风险"
        tag-type="danger"
      />
      <MetricTile label="平均置信度" :value="formatPercent(overview?.totals?.avg_confidence)" extra="模型检测均值" />
      <MetricTile label="覆盖工序" :value="formatNumber(overview?.totals?.step_count)" extra="已纳入立方体工序" />
    </div>

    <div class="grid grid-cols-1 gap-5 xl:grid-cols-3">
      <SectionCard title="批次-工序缺陷矩阵" description="用于定位不同批次在各关键工序中的缺陷分布" class="xl:col-span-2">
        <template #extra>
          <div class="flex gap-2">
            <el-button size="small" type="primary" :loading="loading" @click="loadAll">刷新</el-button>
            <el-button size="small" :loading="refreshing" @click="refreshCube">刷新物化视图</el-button>
          </div>
        </template>
        <el-table :data="batchStepRows" height="330" size="small" stripe>
          <el-table-column prop="batch_no" label="批次号" min-width="130" />
          <el-table-column prop="step_name" label="工序" min-width="120" />
          <el-table-column prop="defect_total" label="缺陷数" width="90" sortable />
          <el-table-column prop="critical_defect_total" label="高风险" width="90" sortable />
          <el-table-column label="平均置信度" width="110">
            <template #default="{ row }">{{ formatPercent(row.avg_confidence) }}</template>
          </el-table-column>
          <el-table-column label="风险状态" width="110">
            <template #default="{ row }">
              <el-tag :type="Number(row.critical_defect_total || 0) > 0 ? 'danger' : 'success'" effect="dark">
                {{ Number(row.critical_defect_total || 0) > 0 ? '需关注' : '正常' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </SectionCard>

      <SectionCard title="立方体关键发现" description="自动汇总缺陷最集中的工序、类型与设备">
        <div class="space-y-3">
          <div class="insight-card">
            <p class="label">缺陷最多工序</p>
            <p class="value">{{ overview?.topStep?.step_name || '--' }}</p>
            <p class="desc">缺陷数：{{ formatNumber(overview?.topStep?.defect_total) }}</p>
          </div>
          <div class="insight-card">
            <p class="label">主要缺陷类型</p>
            <p class="value">{{ overview?.topDefect?.defect_name || '--' }}</p>
            <p class="desc">缺陷数：{{ formatNumber(overview?.topDefect?.defect_total) }}</p>
          </div>
          <div class="insight-card">
            <p class="label">重点设备</p>
            <p class="value">{{ overview?.topEquipment?.equipment_name || '--' }}</p>
            <p class="desc">缺陷数：{{ formatNumber(overview?.topEquipment?.defect_total) }}</p>
          </div>
        </div>
      </SectionCard>
    </div>

    <div class="grid grid-cols-1 gap-5 xl:grid-cols-2">
      <SectionCard title="工序-缺陷类型分布" description="用于支撑缺陷机理识别与知识图谱关系构建">
        <el-table :data="stepTypeRows" height="300" size="small" stripe>
          <el-table-column prop="step_name" label="工序" min-width="120" />
          <el-table-column prop="defect_name" label="缺陷类型" min-width="130" />
          <el-table-column prop="defect_category" label="类别" width="100" />
          <el-table-column prop="defect_total" label="缺陷数" width="90" sortable />
          <el-table-column label="置信度" width="100">
            <template #default="{ row }">{{ formatPercent(row.avg_confidence) }}</template>
          </el-table-column>
        </el-table>
      </SectionCard>

      <SectionCard title="设备维度缺陷统计" description="用于分析设备状态与质量缺陷之间的关联">
        <el-table :data="equipmentRows" height="300" size="small" stripe>
          <el-table-column prop="equipment_code" label="设备编码" min-width="120" />
          <el-table-column prop="equipment_name" label="设备名称" min-width="140" />
          <el-table-column prop="step_name" label="工序" min-width="100" />
          <el-table-column prop="defect_total" label="缺陷数" width="90" sortable />
          <el-table-column prop="critical_defect_total" label="高风险" width="90" sortable />
        </el-table>
      </SectionCard>
    </div>

    <div class="grid grid-cols-1 gap-5 xl:grid-cols-2">
      <SectionCard title="时间趋势统计" description="按检测日期统计缺陷数量变化">
        <div v-if="timeRows.length" class="space-y-2">
          <div
            v-for="row in timeRows.slice(0, 10)"
            :key="`${row.stat_date}-${row.step_code}-${row.defect_code}`"
            class="trend-row"
          >
            <span class="w-28">{{ row.stat_date }}</span>
            <span class="flex-1 truncate">{{ row.step_name }} / {{ row.defect_name }}</span>
            <span class="font-semibold text-cyan-300">{{ row.defect_total }}</span>
          </div>
        </div>
        <EmptyState v-else title="暂无趋势数据" description="请先导入缺陷检测数据并刷新质量缺陷数据立方体。" />
      </SectionCard>

      <SectionCard title="元数据共享目录" description="基于元数据描述质量问题数据的来源、含义、共享路径与指标属性">
        <el-table :data="metadataRows" height="300" size="small" stripe>
          <el-table-column prop="field_name_cn" label="字段中文名" min-width="120" />
          <el-table-column prop="source_table" label="来源表/视图" min-width="150" />
          <el-table-column prop="source_field" label="字段" min-width="120" />
          <el-table-column prop="business_meaning" label="业务含义" min-width="220" show-overflow-tooltip />
          <el-table-column label="属性" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.is_dimension" size="small">维度</el-tag>
              <el-tag v-else-if="row.is_measure" size="small" type="success">指标</el-tag>
              <el-tag v-else size="small" type="info">字段</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </SectionCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';

import EmptyState from '@/components/common/EmptyState.vue';
import MetricTile from '@/components/common/MetricTile.vue';
import SectionCard from '@/components/common/SectionCard.vue';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';
import { qualityCubeApi, type QualityCubeOverview, type QualityCubeRow } from '@/api/qualityCube';

const overview = ref<QualityCubeOverview | null>(null);
const batchStepRows = ref<QualityCubeRow[]>([]);
const stepTypeRows = ref<QualityCubeRow[]>([]);
const equipmentRows = ref<QualityCubeRow[]>([]);
const timeRows = ref<QualityCubeRow[]>([]);
const metadataRows = ref<QualityCubeRow[]>([]);
const loading = ref(false);
const refreshing = ref(false);

const introMetrics = computed(() => [
  { label: '缺陷总数', value: formatNumber(overview.value?.totals?.defect_total), extra: '立方体累计' },
  { label: '高风险缺陷', value: formatNumber(overview.value?.totals?.critical_defect_total), extra: '严重缺陷' },
  { label: '共享字段', value: String(metadataRows.value.length || 0), extra: '元数据目录' },
]);

const loadAll = async () => {
  loading.value = true;
  try {
    const [overviewRes, batchStepRes, stepTypeRes, equipmentRes, timeRes, metadataRes] = await Promise.all([
      qualityCubeApi.getOverview(),
      qualityCubeApi.getByBatchStep({ limit: 100 }),
      qualityCubeApi.getByStepType({ limit: 100 }),
      qualityCubeApi.getByEquipment({ limit: 100 }),
      qualityCubeApi.getByTime({ limit: 100 }),
      qualityCubeApi.getMetadata(),
    ]);
    overview.value = overviewRes;
    batchStepRows.value = batchStepRes;
    stepTypeRows.value = stepTypeRes;
    equipmentRows.value = equipmentRes;
    timeRows.value = timeRes;
    metadataRows.value = metadataRes;
  } catch (error) {
    console.error(error);
    ElMessage.error('质量缺陷数据立方体加载失败，请检查接口和物化视图。');
  } finally {
    loading.value = false;
  }
};

const refreshCube = async () => {
  refreshing.value = true;
  try {
    await qualityCubeApi.refresh();
    ElMessage.success('质量缺陷数据立方体已刷新');
    await loadAll();
  } catch (error) {
    console.error(error);
    ElMessage.error('刷新失败，请检查 PostgreSQL 物化视图刷新函数。');
  } finally {
    refreshing.value = false;
  }
};

const formatNumber = (value: unknown) => {
  const n = Number(value || 0);
  return Number.isFinite(n) ? n.toLocaleString() : '0';
};

const formatPercent = (value: unknown) => {
  const n = Number(value || 0);
  if (!Number.isFinite(n)) {
    return '0%';
  }
  const normalized = n > 1 ? n : n * 100;
  return `${normalized.toFixed(1)}%`;
};

onMounted(loadAll);
</script>

<style scoped>
.insight-card {
  border: 1px solid rgb(34 211 238 / 0.1);
  border-radius: 8px;
  background: rgb(2 6 23 / 0.6);
  padding: 1rem;
}

.insight-card .label {
  color: rgb(148 163 184);
  font-size: 0.75rem;
  line-height: 1rem;
}

.insight-card .value {
  margin-top: 0.25rem;
  color: rgb(241 245 249);
  font-size: 1.125rem;
  font-weight: 600;
  line-height: 1.75rem;
}

.insight-card .desc {
  margin-top: 0.25rem;
  color: rgb(103 232 249);
  font-size: 0.75rem;
  line-height: 1rem;
}

.trend-row {
  align-items: center;
  background: rgb(2 6 23 / 0.5);
  border: 1px solid rgb(255 255 255 / 0.1);
  border-radius: 8px;
  color: rgb(226 232 240);
  display: flex;
  font-size: 0.875rem;
  gap: 0.75rem;
  line-height: 1.25rem;
  padding: 0.5rem 0.75rem;
}
</style>
