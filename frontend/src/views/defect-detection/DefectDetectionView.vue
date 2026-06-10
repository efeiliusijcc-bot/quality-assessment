<template>
  <div class="space-y-6">
    <PageIntroCard
      title="缺陷识别大屏"
      description="展示图像和视频检测结果、缺陷定位框、模型置信度与批量检测记录。"
      badge="DETECTION"
      :metrics="metrics"
    />

    <section class="grid gap-6 xl:grid-cols-[1.25fr_0.75fr]">
      <div class="content-card p-8">
        <div class="flex items-center justify-between gap-4">
          <div>
            <h2 class="panel-title">检测媒体视窗</h2>
            <p class="panel-subtitle">支持图像和视频两类检测载体，边界框层直接呈现后端推理结果。</p>
          </div>
          <el-segmented v-model="activeMediaType" :options="mediaTypeOptions" />
        </div>

        <div class="mt-6 grid gap-6 xl:grid-cols-[0.32fr_0.68fr]">
          <div class="space-y-4 rounded-3xl border border-slate-200 bg-slate-50 p-4">
            <button
              v-for="sample in filteredSamples"
              :key="sample.id"
              type="button"
              class="w-full rounded-2xl border px-4 py-4 text-left transition"
              :class="
                sample.id === activeSampleId
                  ? 'border-cyan-400 bg-cyan-50 shadow-[0_10px_30px_rgba(14,165,233,0.16)]'
                  : 'border-slate-200 bg-white hover:border-cyan-200'
              "
              @click="activeSampleId = sample.id"
            >
              <div class="flex items-center justify-between gap-3">
                <div class="truncate font-semibold text-slate-900">{{ sample.name }}</div>
                <el-tag :type="sample.mediaType === 'image' ? 'primary' : 'success'" round>
                  {{ sample.mediaType === 'image' ? '图像' : '视频' }}
                </el-tag>
              </div>
              <div class="mt-2 text-sm text-slate-500">{{ sample.batchNo || '--' }}</div>
              <div class="mt-3 text-xs text-slate-400">缺陷数：{{ defectCount(sample) }}</div>
            </button>

            <div v-if="filteredSamples.length === 0" class="rounded-2xl border border-dashed border-slate-300 p-6 text-center text-sm text-slate-400">
              暂无{{ activeMediaType === 'image' ? '图像' : '视频' }}样本
            </div>
          </div>

          <div class="rounded-[28px] border border-slate-200 bg-slate-950 p-5">
            <div class="media-stage relative overflow-hidden rounded-[24px] border border-cyan-400/15 bg-slate-950">
              <template v-if="currentSample.mediaType === 'image'">
                <DefectViewer
                  :image-url="currentSample.preview"
                  :alt="currentSample.name"
                  :defects="currentSample.defects"
                  class="h-full w-full"
                />
              </template>
              <template v-else>
                <div class="flex h-full flex-col items-center justify-center text-slate-300">
                  <el-icon :size="48" class="text-cyan-300">
                    <VideoPlay />
                  </el-icon>
                  <div class="mt-4 text-lg font-semibold">{{ currentSample.name }}</div>
                  <div class="mt-2 text-sm text-slate-400">视频流预览区，可接入实时帧检测结果。</div>
                </div>
              </template>
            </div>

            <div class="mt-5 grid gap-4 md:grid-cols-3">
              <div
                v-for="item in detectionStats"
                :key="item.label"
                class="rounded-2xl border border-white/10 bg-white/5 p-4 text-white"
              >
                <div class="text-sm text-slate-400">{{ item.label }}</div>
                <div class="mt-2 text-2xl font-semibold">{{ item.value }}</div>
                <div class="mt-1 text-xs text-slate-500">{{ item.tip }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="content-card p-8">
        <div class="flex items-center justify-between gap-4">
          <div>
            <h2 class="panel-title">识别结果面板</h2>
            <p class="panel-subtitle">展示模型分类、缺陷等级、置信度与定位描述，供人工复核和追溯。</p>
          </div>
          <el-tag :type="riskTagType" effect="dark" round>风险等级 {{ riskLevelLabel }}</el-tag>
        </div>

        <div class="mt-6 rounded-3xl border border-slate-200 bg-slate-50 p-4">
          <el-table :data="currentSample.results" stripe>
            <el-table-column prop="category" label="缺陷类别" min-width="120" />
            <el-table-column prop="level" label="缺陷等级" min-width="100">
              <template #default="{ row }">
                <el-tag :type="levelTagType(row.level)" round>
                  {{ levelLabel(row.level) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="confidence" label="置信度" min-width="100">
              <template #default="{ row }">{{ formatConfidence(row.confidence) }}</template>
            </el-table-column>
            <el-table-column prop="location" label="位置描述" min-width="140" />
          </el-table>
        </div>

        <div class="mt-6 space-y-4">
          <div class="rounded-3xl border border-slate-200 bg-slate-50 p-5">
            <div class="text-sm font-medium text-slate-500">模型结论</div>
            <div class="mt-3 text-base leading-7 text-slate-700">
              {{ currentSample.summary }}
            </div>
          </div>

          <div class="rounded-3xl border border-slate-200 bg-slate-950 p-5 text-slate-100">
            <div class="text-sm tracking-[0.18em] text-cyan-300/75">PIPELINE STATUS</div>
            <div class="mt-4 grid gap-4">
              <div
                v-for="item in pipelineStatus"
                :key="item.label"
                class="rounded-2xl border border-cyan-400/10 bg-white/5 p-4"
              >
                <div class="text-sm text-slate-400">{{ item.label }}</div>
                <div class="mt-2 font-semibold text-white">{{ item.value }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="content-card p-8">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h2 class="panel-title">批量图像检测</h2>
          <p class="panel-subtitle">选择多张图像进行批量缺陷检测，检测结果以列表形式展示。</p>
        </div>
      </div>

      <div class="mt-6 grid gap-6 xl:grid-cols-[1fr_1fr]">
        <div class="rounded-3xl border border-dashed border-cyan-300/40 bg-slate-950/90 p-8 text-center text-slate-100">
          <el-upload
            drag
            action="#"
            multiple
            accept="image/*"
            :show-file-list="true"
            :auto-upload="false"
            :on-change="handleBatchFileChange"
            :on-remove="handleBatchFileRemove"
          >
            <el-icon class="!text-4xl !text-cyan-300">
              <UploadFilled />
            </el-icon>
            <div class="mt-4 text-lg font-medium text-slate-50">拖拽图像到此处或点击上传</div>
            <div class="mt-2 text-sm text-slate-400">支持多张图片批量检测</div>
          </el-upload>

          <div class="mt-4 flex justify-center gap-3">
            <el-button
              type="primary"
              :loading="batchDetecting"
              :disabled="batchFiles.length === 0"
              @click="submitBatchDetect"
            >
              开始批量检测
            </el-button>
            <el-button :disabled="batchDetecting" @click="clearBatchFiles">清空</el-button>
          </div>
        </div>

        <div class="space-y-4">
          <div v-if="batchResults.length > 0" class="rounded-3xl border border-slate-200 bg-slate-50 p-4">
            <div class="mb-3 text-sm font-medium text-slate-500">
              检测结果（共 {{ batchResults.length }} 条）
            </div>
            <el-table :data="batchResults" stripe max-height="400">
              <el-table-column prop="name" label="样本名称" min-width="140" />
              <el-table-column prop="batchNo" label="批次号" min-width="120" />
              <el-table-column label="缺陷类别" min-width="110">
                <template #default="{ row }">
                  {{ row.results[0]?.category ?? '--' }}
                </template>
              </el-table-column>
              <el-table-column label="置信度" min-width="100">
                <template #default="{ row }">
                  {{ row.results[0] ? formatConfidence(row.results[0].confidence) : '--' }}
                </template>
              </el-table-column>
              <el-table-column label="风险等级" min-width="100">
                <template #default="{ row }">
                  <el-tag :type="levelTagType(row.results[0]?.level)" round>
                    {{ levelLabel(row.results[0]?.level) }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div v-else class="flex items-center justify-center rounded-3xl border border-slate-200 bg-slate-50 p-8 text-sm text-slate-400">
            上传图片并执行检测后，结果将显示在此处
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { UploadFilled, VideoPlay } from '@element-plus/icons-vue';
import { ElMessage, type UploadFile } from 'element-plus';

import { batchDetectDefects, fetchDefectSamples, fetchDefectStatistics, type DefectSampleResponse } from '@/api/defect';
import DefectViewer, { type DefectLevel, type DefectViewerItem } from '@/components/defect/DefectViewer.vue';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';

type MediaType = 'image' | 'video';

interface DetectionResult {
  category: string;
  level: DefectLevel;
  confidence: number;
  location: string;
}

interface SampleItem {
  id: string;
  name: string;
  mediaType: MediaType;
  batchNo: string;
  preview: string;
  results: DetectionResult[];
  defects: DefectViewerItem[];
  summary: string;
}

const metrics = ref([
  { label: '模型版本', value: '--', extra: '加载中' },
  { label: '已识别样本', value: '--', extra: '加载中' },
  { label: '平均置信度', value: '--', extra: '加载中' },
]);

const mediaTypeOptions = [
  { label: '图像', value: 'image' },
  { label: '视频', value: 'video' },
];

const fallbackSample: SampleItem = {
  id: 'empty',
  name: '暂无样本',
  mediaType: 'image',
  batchNo: '--',
  preview: '',
  results: [],
  defects: [],
  summary: '暂无检测数据',
};

const samples = ref<SampleItem[]>([]);
const activeMediaType = ref<MediaType>('image');
const activeSampleId = ref('empty');
const batchFiles = ref<File[]>([]);
const batchDetecting = ref(false);
const batchResults = ref<DefectSampleResponse[]>([]);

const normalizeLevel = (level?: string): DefectLevel => {
  switch ((level ?? '').toLowerCase()) {
    case 'severe':
    case 'critical':
    case 'high':
      return 'severe';
    case 'moderate':
    case 'medium':
      return 'moderate';
    case 'minor':
    case 'low':
      return 'minor';
    case 'normal':
    case 'pass':
    case 'ok':
      return 'normal';
    default:
      return 'minor';
  }
};

const normalizeConfidence = (value: number) => {
  if (!Number.isFinite(value)) {
    return 0;
  }
  return value > 1 ? value / 100 : value;
};

const toSampleItem = (item: DefectSampleResponse, index: number): SampleItem => ({
  id: item.id || `sample-${index}`,
  name: item.name || `样本 ${index + 1}`,
  mediaType: item.mediaType === 'video' ? 'video' : 'image',
  batchNo: item.batchNo || '--',
  preview: item.imageUrl || '',
  results: item.results.map((result) => ({
    category: result.category,
    level: normalizeLevel(result.level),
    confidence: normalizeConfidence(result.confidence),
    location: result.location || '--',
  })),
  defects: item.defects.map((defect, defectIndex) => ({
    id: `${item.id || index}-${defectIndex}`,
    label: defect.label,
    level: normalizeLevel(defect.level),
    confidence: normalizeConfidence(defect.confidence),
    bbox: [
      defect.bbox[0] ?? 0,
      defect.bbox[1] ?? 0,
      defect.bbox[2] ?? 0,
      defect.bbox[3] ?? 0,
    ],
  })),
  summary: item.summary || '暂无模型结论',
});

const loadMetrics = async () => {
  try {
    const stats = await fetchDefectStatistics();
    metrics.value = [
      { label: '模型版本', value: stats.modelVersion || '--', extra: '当前推理配置' },
      { label: '已识别样本', value: String(stats.totalSamples), extra: '数据库统计' },
      { label: '平均置信度', value: formatConfidence(normalizeConfidence(stats.avgConfidence)), extra: '检测任务均值' },
    ];
  } catch {
    metrics.value = [
      { label: '模型版本', value: '--', extra: '加载失败' },
      { label: '已识别样本', value: '--', extra: '加载失败' },
      { label: '平均置信度', value: '--', extra: '加载失败' },
    ];
  }
};

const loadSamples = async () => {
  try {
    const data = await fetchDefectSamples();
    samples.value = data.map(toSampleItem);
  } catch {
    samples.value = [];
  }
};

const filteredSamples = computed(() =>
  samples.value.filter((item) => item.mediaType === activeMediaType.value),
);

const currentSample = computed(() => {
  const current = filteredSamples.value.find((item) => item.id === activeSampleId.value);
  return current ?? filteredSamples.value[0] ?? fallbackSample;
});

watch(
  filteredSamples,
  (list) => {
    const firstSample = list[0];
    if (!list.some((item) => item.id === activeSampleId.value)) {
      activeSampleId.value = firstSample?.id ?? 'empty';
    }
  },
  { immediate: true },
);

const defectCount = (sample: SampleItem) =>
  sample.results.filter((item) => item.level !== 'normal' && item.category !== 'normal').length;

const riskLevel = computed<DefectLevel>(() => {
  const levels = currentSample.value.results.map((item) => item.level);
  if (levels.includes('severe')) return 'severe';
  if (levels.includes('moderate')) return 'moderate';
  if (levels.includes('minor')) return 'minor';
  return 'normal';
});

const riskLevelLabel = computed(() => levelLabel(riskLevel.value));
const riskTagType = computed(() => levelTagType(riskLevel.value));

const detectionStats = computed(() => {
  const confidences = currentSample.value.results.map((item) => item.confidence);
  const maxConfidence = confidences.length ? Math.max(...confidences) : 0;
  return [
    {
      label: '检测载体',
      value: currentSample.value.mediaType === 'image' ? '静态图像' : '视频流',
      tip: currentSample.value.batchNo,
    },
    {
      label: '缺陷数量',
      value: String(defectCount(currentSample.value)),
      tip: '当前样本',
    },
    {
      label: '最高置信度',
      value: formatConfidence(maxConfidence),
      tip: '模型输出',
    },
  ];
});

const pipelineStatus = computed(() => [
  { label: '输入预处理', value: '图像元数据已提交至检测接口' },
  { label: '推理模块', value: '支持外部模型服务，未配置时使用规则降级' },
  { label: '复核建议', value: riskLevel.value === 'severe' ? '建议转人工复核' : '建议继续追踪样本' },
]);

const levelLabel = (level?: string) => {
  switch (normalizeLevel(level)) {
    case 'severe':
      return '严重';
    case 'moderate':
      return '中等';
    case 'minor':
      return '轻微';
    case 'normal':
      return '正常';
  }
};

const levelTagType = (level?: string) => {
  switch (normalizeLevel(level)) {
    case 'severe':
      return 'danger';
    case 'moderate':
      return 'warning';
    case 'minor':
      return 'info';
    case 'normal':
      return 'success';
  }
};

const formatConfidence = (value: number) => `${(normalizeConfidence(value) * 100).toFixed(1)}%`;

const handleBatchFileChange = (uploadFile: UploadFile) => {
  if (uploadFile.raw && !batchFiles.value.includes(uploadFile.raw)) {
    batchFiles.value.push(uploadFile.raw);
  }
};

const handleBatchFileRemove = (uploadFile: UploadFile) => {
  if (uploadFile.raw) {
    const index = batchFiles.value.indexOf(uploadFile.raw);
    if (index >= 0) {
      batchFiles.value.splice(index, 1);
    }
  }
};

const clearBatchFiles = () => {
  batchFiles.value = [];
  batchResults.value = [];
};

const submitBatchDetect = async () => {
  if (batchFiles.value.length === 0) {
    ElMessage.warning('请先上传图片');
    return;
  }

  batchDetecting.value = true;
  try {
    const batchNo = `BATCH-${Date.now()}`;
    const items = batchFiles.value.map((file) => ({
      name: file.name,
      batchNo,
      imageUrl: file.name,
    }));

    const result = await batchDetectDefects(items);
    batchResults.value = result.results;
    samples.value = [...result.results.map(toSampleItem), ...samples.value];
    activeMediaType.value = 'image';
    activeSampleId.value = samples.value[0]?.id ?? 'empty';
    ElMessage.success(result.message);
    void loadMetrics();
  } catch {
    ElMessage.error('批量检测失败');
  } finally {
    batchDetecting.value = false;
  }
};

onMounted(() => {
  void loadMetrics();
  void loadSamples();
});
</script>

<style scoped>
.media-stage {
  min-height: 520px;
}
</style>
