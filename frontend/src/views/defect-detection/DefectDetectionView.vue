<template>
  <div class="space-y-5">
    <PageIntroCard
      title="缺陷识别大屏"
      description="展示图像和视频检测结果、缺陷定位框、模型置信度与批量检测记录。"
      badge="DETECTION"
      :metrics="metrics"
    />

    <section class="grid gap-5 xl:grid-cols-[1.15fr_0.85fr]">
      <SectionCard title="检测媒体视窗" description="选择检测样本后，可直接查看图像预览和缺陷定位框。" compact>
        <template #extra>
          <el-segmented v-model="activeMediaType" :options="mediaTypeOptions" />
        </template>
        <div class="grid gap-5 xl:grid-cols-[0.32fr_0.68fr]">
          <div class="space-y-3 rounded-3xl border border-slate-200 bg-slate-50 p-4">
            <button
              v-for="sample in filteredSamples"
              :key="sample.id"
              type="button"
              class="w-full rounded-2xl border px-4 py-4 text-left transition"
              :class="sample.id === activeSampleId ? 'border-cyan-400 bg-cyan-50 shadow-[0_10px_30px_rgba(14,165,233,0.16)]' : 'border-slate-200 bg-white hover:border-cyan-200'"
              @click="activeSampleId = sample.id"
            >
              <div class="flex items-center justify-between gap-3">
                <div class="truncate font-semibold text-slate-900">{{ sample.name }}</div>
                <el-tag :type="sample.mediaType === 'image' ? 'primary' : 'success'" round>{{ sample.mediaType === 'image' ? '图像' : '视频' }}</el-tag>
              </div>
              <div class="mt-2 text-sm text-slate-500">{{ sample.batchNo || '--' }}</div>
              <div class="mt-3 text-xs text-slate-400">缺陷数：{{ defectCount(sample) }}</div>
            </button>
            <EmptyState v-if="filteredSamples.length === 0" title="暂无检测样本" description="请上传图像或切换媒体类型后重试。" />
          </div>

          <div class="rounded-[24px] border border-slate-200 bg-slate-950 p-4">
            <div class="media-stage relative overflow-hidden rounded-[22px] border border-cyan-400/15 bg-slate-950">
              <DefectViewer
                v-if="currentSample.mediaType === 'image'"
                :image-url="currentSample.preview"
                :alt="currentSample.name"
                :defects="currentSample.defects"
                class="h-full w-full"
              />
              <div v-else class="flex h-full min-h-[360px] flex-col items-center justify-center text-slate-300">
                <el-icon :size="48" class="text-cyan-300"><VideoPlay /></el-icon>
                <div class="mt-4 text-lg font-semibold">{{ currentSample.name }}</div>
                <div class="mt-2 text-sm text-slate-400">视频流预览区，可接入实时帧检测结果。</div>
              </div>
            </div>
            <div class="mt-4 grid gap-3 md:grid-cols-3">
              <MetricTile v-for="item in detectionStats" :key="item.label" :label="item.label" :value="item.value" :extra="item.tip" />
            </div>
          </div>
        </div>
      </SectionCard>

      <SectionCard title="识别结果面板" description="展示缺陷类别、等级、置信度与定位描述，供人工复核。" compact>
        <template #extra>
          <el-tag :type="riskTagType" effect="dark" round>风险等级 {{ riskLevelLabel }}</el-tag>
        </template>
        <div class="overflow-hidden rounded-3xl border border-slate-200 bg-slate-50">
          <el-table :data="currentSample.results" stripe>
            <el-table-column prop="category" label="缺陷类别" min-width="120" />
            <el-table-column prop="level" label="缺陷等级" min-width="100">
              <template #default="{ row }">
                <el-tag :type="levelTagType(row.level)" round>{{ levelLabel(row.level) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="confidence" label="置信度" min-width="100">
              <template #default="{ row }">{{ formatConfidence(row.confidence) }}</template>
            </el-table-column>
            <el-table-column prop="location" label="位置描述" min-width="140" />
          </el-table>
        </div>
        <div class="mt-4 rounded-3xl border border-slate-200 bg-slate-50 p-5">
          <div class="text-sm font-medium text-slate-500">模型结论</div>
          <div class="mt-3 text-base leading-7 text-slate-700">{{ currentSample.summary || '暂无检测结论。' }}</div>
        </div>
        <div v-if="currentSample.results.length === 0" class="mt-4">
          <EmptyState title="暂无识别结果" description="请选择有检测结果的样本，或上传图片执行批量检测。" />
        </div>
      </SectionCard>
    </section>

    <SectionCard title="批量图像检测" description="选择多张图像进行批量缺陷检测，结果可用于人工复核和后续质量评估。" compact>
      <div class="grid gap-5 xl:grid-cols-[0.9fr_1.1fr]">
        <div class="rounded-3xl border border-dashed border-cyan-300/40 bg-slate-950/90 p-7 text-center text-slate-100">
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
            <el-icon class="!text-4xl !text-cyan-300"><UploadFilled /></el-icon>
            <div class="mt-4 text-lg font-medium text-slate-50">拖拽图像到此处或点击上传</div>
            <div class="mt-2 text-sm text-slate-400">支持多张图片批量检测</div>
          </el-upload>
          <div class="mt-4 flex justify-center gap-3">
            <el-button type="primary" :loading="batchDetecting" :disabled="batchFiles.length === 0" @click="submitBatchDetect">开始批量检测</el-button>
            <el-button :disabled="batchDetecting" @click="clearBatchFiles">清空</el-button>
          </div>
        </div>

        <div>
          <div v-if="batchResults.length > 0" class="overflow-hidden rounded-3xl border border-slate-200 bg-slate-50">
            <el-table :data="batchResults" stripe max-height="380">
              <el-table-column prop="name" label="样本名称" min-width="140" />
              <el-table-column prop="batchNo" label="批次号" min-width="120" />
              <el-table-column label="缺陷类别" min-width="110"><template #default="{ row }">{{ row.results[0]?.category ?? '--' }}</template></el-table-column>
              <el-table-column label="置信度" min-width="100"><template #default="{ row }">{{ row.results[0] ? formatConfidence(row.results[0].confidence) : '--' }}</template></el-table-column>
              <el-table-column label="风险等级" min-width="100"><template #default="{ row }"><el-tag :type="levelTagType(row.results[0]?.level)" round>{{ levelLabel(row.results[0]?.level) }}</el-tag></template></el-table-column>
            </el-table>
          </div>
          <EmptyState v-else title="暂无批量检测结果" description="上传图片并执行检测后，结果将显示在此处。" />
        </div>
      </div>
    </SectionCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { UploadFilled, VideoPlay } from '@element-plus/icons-vue';
import { ElMessage, type UploadFile } from 'element-plus';

import { batchDetectDefects, fetchDefectSamples, fetchDefectStatistics, type DefectSampleResponse } from '@/api/defect';
import EmptyState from '@/components/common/EmptyState.vue';
import MetricTile from '@/components/common/MetricTile.vue';
import SectionCard from '@/components/common/SectionCard.vue';
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
  summary: '暂无检测样本，请上传图像或选择历史检测任务。',
};

const activeMediaType = ref<MediaType>('image');
const activeSampleId = ref('');
const samples = ref<SampleItem[]>([]);
const batchFiles = ref<UploadFile[]>([]);
const batchResults = ref<SampleItem[]>([]);
const batchDetecting = ref(false);

const normalizeLevel = (level?: string): DefectLevel => {
  if (level === 'severe' || level === 'moderate' || level === 'minor' || level === 'normal') return level;
  if (level === '严重' || level === '高') return 'severe';
  if (level === '中等' || level === '中') return 'moderate';
  if (level === '轻微' || level === '低') return 'minor';
  return 'normal';
};

const mapSample = (item: DefectSampleResponse): SampleItem => ({
  id: item.id,
  name: item.name,
  mediaType: item.mediaType === 'video' ? 'video' : 'image',
  batchNo: item.batchNo,
  preview: item.imageUrl,
  results: (item.results ?? []).map((result) => ({ ...result, level: normalizeLevel(result.level) })),
  defects: (item.defects ?? []).map((defect, index) => ({
    id: `${item.id}-${index}`,
    label: defect.label,
    confidence: defect.confidence,
    bbox: defect.bbox as [number, number, number, number],
    level: normalizeLevel(defect.level),
  })),
  summary: item.summary,
});

const filteredSamples = computed(() => samples.value.filter((sample) => sample.mediaType === activeMediaType.value));
const currentSample = computed(() => filteredSamples.value.find((sample) => sample.id === activeSampleId.value) ?? filteredSamples.value[0] ?? fallbackSample);

const defectCount = (sample: SampleItem) => sample.results.length || sample.defects.length;
const maxConfidence = computed(() => currentSample.value.results.reduce((max, item) => Math.max(max, item.confidence), 0));
const riskLevelLabel = computed(() => {
  if (currentSample.value.results.some((item) => item.level === 'severe')) return '高';
  if (currentSample.value.results.some((item) => item.level === 'moderate')) return '中';
  if (currentSample.value.results.length > 0) return '低';
  return '正常';
});
const riskTagType = computed(() => (riskLevelLabel.value === '高' ? 'danger' : riskLevelLabel.value === '中' ? 'warning' : 'success'));
const detectionStats = computed(() => [
  { label: '当前缺陷数', value: String(defectCount(currentSample.value)), tip: '检测结果数量' },
  { label: '最高置信度', value: formatConfidence(maxConfidence.value), tip: '当前样本' },
  { label: '媒体类型', value: currentSample.value.mediaType === 'image' ? '图像' : '视频', tip: '检测载体' },
]);

const formatConfidence = (value: number) => `${Math.round((value || 0) * 100)}%`;
const levelLabel = (level?: string) => ({ severe: '严重', moderate: '中等', minor: '轻微', normal: '正常' }[level || 'normal'] ?? '正常');
const levelTagType = (level?: string) => (level === 'severe' ? 'danger' : level === 'moderate' ? 'warning' : level === 'minor' ? 'info' : 'success');

const loadSamples = async () => {
  const result = await fetchDefectSamples();
  samples.value = result.map(mapSample);
  activeSampleId.value = filteredSamples.value[0]?.id ?? '';
};

const loadStatistics = async () => {
  try {
    const stats = await fetchDefectStatistics();
    metrics.value = [
      { label: '模型版本', value: stats.modelVersion || '--', extra: '当前推理配置' },
      { label: '已识别样本', value: String(stats.totalSamples ?? 0), extra: '数据集统计' },
      { label: '平均置信度', value: `${Math.round((stats.avgConfidence ?? 0) * 100)}%`, extra: '检测任务均值' },
    ];
  } catch {
    metrics.value = [
      { label: '模型版本', value: 'v2.1', extra: '当前推理配置' },
      { label: '已识别样本', value: String(samples.value.length), extra: '数据集统计' },
      { label: '平均置信度', value: '--', extra: '检测任务均值' },
    ];
  }
};

const handleBatchFileChange = (file: UploadFile) => {
  batchFiles.value = [...batchFiles.value.filter((item) => item.uid !== file.uid), file];
};

const handleBatchFileRemove = (file: UploadFile) => {
  batchFiles.value = batchFiles.value.filter((item) => item.uid !== file.uid);
};

const clearBatchFiles = () => {
  batchFiles.value = [];
  batchResults.value = [];
};

const submitBatchDetect = async () => {
  batchDetecting.value = true;
  try {
    const items = batchFiles.value.map((file, index) => ({
      name: file.name,
      batchNo: `BATCH-UPLOAD-${index + 1}`,
      imageUrl: file.url || '',
    }));
    const result = await batchDetectDefects(items);
    batchResults.value = result.results.map(mapSample);
    samples.value = [...batchResults.value, ...samples.value];
    activeSampleId.value = batchResults.value[0]?.id ?? activeSampleId.value;
    ElMessage.success(result.message || `已完成 ${result.total ?? batchResults.value.length} 条检测`);
  } finally {
    batchDetecting.value = false;
  }
};

watch(activeMediaType, () => {
  activeSampleId.value = filteredSamples.value[0]?.id ?? '';
});

onMounted(async () => {
  await loadSamples();
  await loadStatistics();
});
</script>

<style scoped>
.media-stage {
  min-height: 380px;
}
</style>
