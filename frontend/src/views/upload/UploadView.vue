<template>
  <div class="space-y-6">
    <PageIntroCard
      title="多模态资源上传"
      description="支持在线数据自动录入、逐条数据录入和 Excel 批量导入。Excel 导入已切换为真实接口请求。"
      badge="UPLOAD"
      :metrics="metrics"
    />

    <section class="content-card p-8">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h2 class="panel-title">资源接入工作台</h2>
          <p class="panel-subtitle">根据现场场景切换录入方式，所有数据优先进入统一接入链路。</p>
        </div>
        <el-tag :type="isMockEnabled ? 'info' : 'success'" effect="dark" round>
          {{ isMockEnabled ? 'Mock 模式' : 'Production API' }}
        </el-tag>
      </div>

      <el-tabs v-model="activeTab" class="mt-8">
        <el-tab-pane label="在线数据自动录入" name="online">
          <div class="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
            <el-form
              ref="onlineFormRef"
              v-loading="onlineSubmitting"
              :model="onlineForm"
              :rules="onlineRules"
              label-position="top"
              class="rounded-3xl border border-slate-200 bg-slate-50 p-6"
            >
              <div class="grid gap-5 md:grid-cols-2">
                <el-form-item label="采集工位" prop="station">
                  <el-select v-model="onlineForm.station" placeholder="选择工位">
                    <el-option v-for="station in stationOptions" :key="station" :label="station" :value="station" />
                  </el-select>
                </el-form-item>
                <el-form-item label="批次号" prop="batchNo">
                  <el-input v-model="onlineForm.batchNo" placeholder="例如 BATCH-2408-A" />
                </el-form-item>
                <el-form-item label="设备编码" prop="deviceId">
                  <el-input v-model="onlineForm.deviceId" placeholder="例如 DEV-07-AX" />
                </el-form-item>
                <el-form-item label="采样频率" prop="frequency">
                  <el-select v-model="onlineForm.frequency" placeholder="选择采样频率">
                    <el-option label="5 秒/次" value="5 秒/次" />
                    <el-option label="10 秒/次" value="10 秒/次" />
                    <el-option label="30 秒/次" value="30 秒/次" />
                  </el-select>
                </el-form-item>
              </div>
              <el-form-item label="数据映射说明" prop="mapping">
                <el-input
                  v-model="onlineForm.mapping"
                  type="textarea"
                  :rows="4"
                  placeholder="填写 PLC/传感器字段与平台字段的映射说明"
                />
              </el-form-item>
              <div class="flex flex-wrap gap-3">
                <el-button type="primary" :loading="onlineSubmitting" @click="submitOnlineForm">
                  启动自动录入
                </el-button>
                <el-button @click="fillOnlineDemo">填充示例</el-button>
              </div>
            </el-form>

            <div class="rounded-3xl border border-slate-200 bg-slate-950 p-6 text-slate-100">
              <div class="text-sm tracking-[0.18em] text-cyan-300/70">STREAM STATUS</div>
              <div class="mt-5 grid gap-4">
                <div
                  v-for="item in onlineStatusCards"
                  :key="item.label"
                  class="rounded-2xl border border-cyan-400/15 bg-white/5 p-4"
                >
                  <div class="text-sm text-slate-400">{{ item.label }}</div>
                  <div class="mt-2 text-2xl font-semibold text-white">{{ item.value }}</div>
                  <div class="mt-2 text-xs text-slate-500">{{ item.tip }}</div>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="逐条数据录入" name="manual">
          <div class="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
            <div class="space-y-4 rounded-3xl border border-slate-200 bg-slate-50 p-6">
              <div class="flex items-center justify-between">
                <div>
                  <h3 class="text-lg font-semibold text-slate-900">逐条录入表单</h3>
                  <p class="mt-2 text-sm text-slate-500">逐条录入设备运行参数和缺陷信息，数据将直接写入数据库。</p>
                </div>
              </div>

              <el-form
                ref="manualFormRef"
                :model="manualForm"
                :rules="manualRules"
                label-position="top"
              >
                <div class="grid gap-4 md:grid-cols-2">
                  <el-form-item label="批次号" prop="batchNo">
                    <el-input v-model="manualForm.batchNo" placeholder="例如 BATCH-2408-A" />
                  </el-form-item>
                  <el-form-item label="工位" prop="station">
                    <el-select v-model="manualForm.station" placeholder="选择工位">
                      <el-option v-for="station in stationOptions" :key="station" :label="station" :value="station" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="元件编号" prop="componentId">
                    <el-input v-model="manualForm.componentId" placeholder="例如 CMP-A-001" />
                  </el-form-item>
                  <el-form-item label="回流温度 (°C)" prop="temperature">
                    <el-input-number v-model="manualForm.temperature" :min="0" :max="300" :controls="false" class="!w-full" />
                  </el-form-item>
                  <el-form-item label="贴装压力 (MPa)" prop="pressure">
                    <el-input-number v-model="manualForm.pressure" :min="0" :max="10" :step="0.1" :controls="false" class="!w-full" />
                  </el-form-item>
                  <el-form-item label="链速 (cm/min)" prop="beltSpeed">
                    <el-input-number v-model="manualForm.beltSpeed" :min="0" :max="200" :step="1" :controls="false" class="!w-full" />
                  </el-form-item>
                  <el-form-item label="O2浓度 (ppm)" prop="o2Ppm">
                    <el-input-number v-model="manualForm.o2Ppm" :min="0" :max="10000" :controls="false" class="!w-full" />
                  </el-form-item>
                  <el-form-item label="湿度 (%)" prop="humidity">
                    <el-input-number v-model="manualForm.humidity" :min="0" :max="100" :controls="false" class="!w-full" />
                  </el-form-item>
                  <el-form-item label="电流 (A)" prop="currentValue">
                    <el-input-number v-model="manualForm.currentValue" :min="0" :max="50" :step="0.1" :controls="false" class="!w-full" />
                  </el-form-item>
                  <el-form-item label="缺陷类型" prop="defectType">
                    <el-select v-model="manualForm.defectType" placeholder="选择缺陷类型" clearable>
                      <el-option v-for="item in defectTypeOptions" :key="item" :label="item" :value="item" />
                    </el-select>
                  </el-form-item>
                  <el-form-item v-if="manualForm.defectType" label="缺陷等级" prop="defectLevel">
                    <el-select v-model="manualForm.defectLevel" placeholder="选择等级">
                      <el-option label="中等" value="中等" />
                      <el-option label="严重" value="严重" />
                    </el-select>
                  </el-form-item>
                  <el-form-item v-if="manualForm.defectType" label="缺陷置信度" prop="defectConfidence">
                    <el-input-number v-model="manualForm.defectConfidence" :min="0" :max="1" :step="0.01" :controls="false" class="!w-full" />
                  </el-form-item>
                </div>

                <div class="mt-4 flex gap-3">
                  <el-button type="primary" :loading="manualSubmitting" @click="submitManualForm">
                    提交录入
                  </el-button>
                  <el-button @click="resetManualForm">重置表单</el-button>
                </div>
              </el-form>
            </div>

            <div class="rounded-3xl border border-slate-200 bg-slate-950 p-6 text-white">
              <div class="text-sm tracking-[0.18em] text-cyan-300/75">CURRENT PAYLOAD</div>
              <div class="mt-4 rounded-2xl bg-slate-900 p-4 font-mono text-sm leading-6 text-cyan-100">
                <pre class="overflow-auto whitespace-pre-wrap">{{ manualPreview }}</pre>
              </div>

              <div v-if="manualSubmitResults.length > 0" class="mt-5">
                <div class="text-sm tracking-[0.18em] text-cyan-300/75">SUBMISSION LOG</div>
                <div class="mt-3 space-y-2">
                  <div
                    v-for="(result, idx) in manualSubmitResults"
                    :key="idx"
                    class="rounded-xl border border-white/10 bg-white/5 p-3 text-sm"
                  >
                    <span class="text-emerald-400">{{ result.id }}</span>
                    <span class="ml-2 text-slate-400">{{ result.message }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="Excel 批量导入" name="excel">
          <div class="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
            <div class="rounded-3xl border border-dashed border-cyan-300/40 bg-slate-950/90 p-8 text-center text-slate-100">
              <el-upload
                drag
                action="#"
                :show-file-list="false"
                :auto-upload="false"
                :on-change="handleExcelFileChange"
              >
                <el-icon class="!text-4xl !text-cyan-300">
                  <UploadFilled />
                </el-icon>
                <div class="mt-4 text-lg font-medium text-slate-50">拖拽 Excel 文件到此处</div>
                <div class="mt-2 text-sm text-slate-400">支持 .xls / .xlsx，点击“开始导入”后将调用真实后端接口。</div>
              </el-upload>

              <div
                v-if="excelFileName"
                class="mt-6 rounded-2xl border border-cyan-400/20 bg-white/5 p-4 text-left"
              >
                <div class="text-sm text-slate-400">当前文件</div>
                <div class="mt-2 text-base font-semibold text-white">{{ excelFileName }}</div>
                <el-progress
                  class="mt-4"
                  :percentage="excelProgress"
                  :status="excelProgress === 100 ? 'success' : undefined"
                  :stroke-width="12"
                />
                <div class="mt-3 flex gap-3">
                  <el-button
                    type="primary"
                    :loading="excelUploading"
                    :disabled="excelUploading || !excelFile"
                    @click="submitExcelImport"
                  >
                    开始导入
                  </el-button>
                  <el-button :disabled="excelUploading" @click="resetExcelImport">重置</el-button>
                </div>
              </div>
            </div>

            <div class="rounded-3xl border border-slate-200 bg-slate-50 p-6">
              <h3 class="text-lg font-semibold text-slate-900">导入结果预览</h3>
              <p class="mt-2 text-sm text-slate-500">展示最近一次 Excel 导入的结果统计。</p>

              <div class="mt-6 grid gap-4">
                <div
                  v-for="item in excelSummary"
                  :key="item.label"
                  class="rounded-2xl border border-slate-200 bg-white p-4"
                >
                  <div class="text-sm text-slate-500">{{ item.label }}</div>
                  <div class="mt-2 text-2xl font-bold text-slate-900">{{ item.value }}</div>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { UploadFilled } from '@element-plus/icons-vue';
import { ElMessage, type FormInstance, type FormRules, type UploadFile, type UploadFiles } from 'element-plus';

import { fetchUploadStatistics, importManufacturingExcel, submitManualRecord, submitOnlineUploadTask } from '@/api/upload';
import { useAssessmentStore } from '@/stores/assessment';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';
import { isMockEnabled } from '@/constants/env';

const assessmentStore = useAssessmentStore();

const metrics = ref([
  { label: '当前接入产线', value: '--', extra: '加载中' },
  { label: '上传任务数', value: '--', extra: '加载中' },
  { label: '最近同步', value: '--', extra: '加载中' },
]);

const loadMetrics = async () => {
  try {
    const stats = await fetchUploadStatistics();
    metrics.value = [
      { label: '当前接入产线', value: String(assessmentStore.availableStations.length), extra: '已注册工位' },
      { label: '上传任务数', value: String(stats.totalTasks), extra: '数据库统计' },
      { label: '最近同步', value: stats.latestSyncTime, extra: '设备自动采集' },
    ];
  } catch {
    // keep defaults
  }
};

const activeTab = ref('online');
const onlineFormRef = ref<FormInstance>();
const onlineSubmitting = ref(false);
const onlineStreamStarted = ref(false);
const onlineTaskId = ref('');

const stationOptions = computed(() => assessmentStore.availableStations);
const defectTypeOptions = ['虚焊', '偏移', '裂纹', '引脚缺失'];

const onlineForm = reactive({
  station: '',
  batchNo: '',
  deviceId: '',
  frequency: '',
  mapping: '',
});

const onlineRules: FormRules<typeof onlineForm> = {
  station: [{ required: true, message: '请选择采集工位', trigger: 'change' }],
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  deviceId: [{ required: true, message: '请输入设备编码', trigger: 'blur' }],
  frequency: [{ required: true, message: '请选择采样频率', trigger: 'change' }],
  mapping: [{ required: true, message: '请填写数据映射说明', trigger: 'blur' }],
};

const onlineStatusCards = computed(() => [
  {
    label: '流状态',
    value: onlineStreamStarted.value ? '采集中' : '待启动',
    tip: isMockEnabled ? 'Mock / API 双模式兼容' : '真实接口已接入',
  },
  {
    label: '目标批次',
    value: onlineForm.batchNo || '--',
    tip: onlineTaskId.value || onlineForm.station || '请选择工位',
  },
  {
    label: '采样频率',
    value: onlineForm.frequency || '--',
    tip: onlineForm.deviceId || '未绑定设备',
  },
]);

const fillOnlineDemo = () => {
  const firstStation = assessmentStore.availableStations[0] || '';
  const firstBatch = assessmentStore.availableBatches[0] || '';
  onlineForm.station = firstStation;
  onlineForm.batchNo = firstBatch;
  onlineForm.deviceId = 'DEV-DEMO';
  onlineForm.frequency = '10 秒/次';
  onlineForm.mapping = 'PLC_TEMP -> 焊接温度，PLC_PRESS -> 贴装压力，VISION_TAG -> 视觉缺陷标记';
};

const submitOnlineForm = async () => {
  const valid = await onlineFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }

  onlineSubmitting.value = true;

  try {
    const result = await submitOnlineUploadTask({
      station: onlineForm.station,
      batchNo: onlineForm.batchNo,
      deviceId: onlineForm.deviceId,
      frequency: onlineForm.frequency,
      mapping: onlineForm.mapping,
    });

    onlineTaskId.value = result.taskId;
    onlineStreamStarted.value = true;
    ElMessage.success(`自动录入任务已启动：${result.taskId}`);
  } finally {
    onlineSubmitting.value = false;
  }
};

interface ManualRow {
  id: number;
  componentId: string;
  defectType: string;
  temperature: number;
  pressure: number;
}

const manualFormRef = ref<FormInstance>();
const manualSubmitting = ref(false);
const manualSubmitResults = ref<{ id: string; message: string }[]>([]);

const defaultManualForm = {
  batchNo: '',
  station: '',
  componentId: '',
  temperature: 235,
  pressure: 4.5,
  beltSpeed: 65,
  o2Ppm: 500,
  humidity: 45,
  currentValue: 3.2,
  defectType: '',
  defectLevel: '中等',
  defectConfidence: 0.95,
};

const manualForm = reactive({ ...defaultManualForm });

const manualRules: FormRules<typeof manualForm> = {
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  station: [{ required: true, message: '请选择工位', trigger: 'change' }],
  temperature: [{ required: true, message: '请输入温度', trigger: 'blur' }],
};

const manualPreview = computed(() =>
  JSON.stringify(manualForm, null, 2),
);

const submitManualForm = async () => {
  const valid = await manualFormRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }

  manualSubmitting.value = true;
  try {
    const result = await submitManualRecord(manualForm);
    manualSubmitResults.value.unshift({ id: result.id, message: result.message });
    ElMessage.success(result.message);
  } catch {
    ElMessage.error('逐条录入失败');
  } finally {
    manualSubmitting.value = false;
  }
};

const resetManualForm = () => {
  Object.assign(manualForm, defaultManualForm);
};

const excelFile = ref<File | null>(null);
const excelFileName = ref('');
const excelUploading = ref(false);
const excelProgress = ref(0);
const excelParsedRows = ref(0);
const excelSuccessRows = ref(0);

const excelSummary = computed(() => [
  { label: '导入文件', value: excelFileName.value || '--' },
  { label: '导入进度', value: `${excelProgress.value}%` },
  { label: '工艺设定参数', value: `${excelParsedRows.value}` },
  { label: '设备/缺陷记录', value: `${excelSuccessRows.value}` },
]);

const handleExcelFileChange = (uploadFile: UploadFile, _uploadFiles: UploadFiles) => {
  if (!uploadFile.raw) {
    return;
  }

  excelFile.value = uploadFile.raw;
  excelFileName.value = uploadFile.raw.name;
  excelProgress.value = 0;
  excelParsedRows.value = 0;
  excelSuccessRows.value = 0;
  ElMessage.info(`已选择文件：${uploadFile.raw.name}`);
};

const submitExcelImport = async () => {
  if (!excelFile.value) {
    ElMessage.warning('请先选择 Excel 文件');
    return;
  }

  excelUploading.value = true;
  excelProgress.value = 30;

  try {
    const result = await importManufacturingExcel(excelFile.value);
    excelProgress.value = 100;
    excelParsedRows.value = result.processSettingCount;
    excelSuccessRows.value = result.equipmentOperationCount + result.qualityDefectCount;
    ElMessage.success(
      `导入成功：工艺设定 ${result.processSettingCount} 条，设备运行 ${result.equipmentOperationCount} 条，质量缺陷 ${result.qualityDefectCount} 条`,
    );
  } catch (error) {
    excelProgress.value = 0;
    throw error;
  } finally {
    excelUploading.value = false;
  }
};

const resetExcelImport = () => {
  excelFile.value = null;
  excelFileName.value = '';
  excelProgress.value = 0;
  excelParsedRows.value = 0;
  excelSuccessRows.value = 0;
};

onMounted(() => {
  // 先显示页面，异步加载数据
  void assessmentStore.loadBatches();
  void assessmentStore.loadStations();
  void loadMetrics();
});
</script>

<style scoped>
:deep(.el-tabs__item) {
  font-weight: 600;
}

:deep(.el-upload-dragger) {
  border: none;
  background: transparent;
}
</style>
