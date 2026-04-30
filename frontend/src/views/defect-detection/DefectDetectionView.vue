<template>
  <div class="space-y-6">
    <PageIntroCard
      title="缺陷识别大屏"
      description="左侧提供图像/视频检测渲染区并预留 Bounding Box 叠层，右侧展示 ResNet 模型识别结果与缺陷统计。"
      badge="DETECTION"
      :metrics="metrics"
    />

    <section class="grid gap-6 xl:grid-cols-[1.25fr_0.75fr]">
      <div class="content-card p-8">
        <div class="flex items-center justify-between gap-4">
          <div>
            <h2 class="panel-title">检测媒体视窗</h2>
            <p class="panel-subtitle">支持图像和视频两种检测载体，边界框层可直接挂接后续 AI 推理结果。</p>
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
                <div class="font-semibold text-slate-900">{{ sample.name }}</div>
                <el-tag :type="sample.mediaType === 'image' ? 'primary' : 'success'" round>
                  {{ sample.mediaType === 'image' ? '图像' : '视频' }}
                </el-tag>
              </div>
              <div class="mt-2 text-sm text-slate-500">{{ sample.batchNo }}</div>
              <div class="mt-3 text-xs text-slate-400">缺陷数：{{ sample.results.length }}</div>
            </button>
          </div>

          <div class="rounded-[28px] border border-slate-200 bg-slate-950 p-5">
            <div class="media-stage relative overflow-hidden rounded-[24px] border border-cyan-400/15 bg-[radial-gradient(circle_at_top,rgba(14,165,233,0.18),transparent_30%),linear-gradient(180deg,#0f172a,#020617)]">
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
                  <div class="mt-2 text-sm text-slate-400">视频流占位区，后续可接入实时帧渲染</div>
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
            <p class="panel-subtitle">展示 ResNet 模型的分类输出结果，可继续扩展人工复核与复判入口。</p>
          </div>
          <el-tag :type="currentSample.results.length > 2 ? 'danger' : 'warning'" effect="dark" round>
            风险等级 {{ riskLevel }}
          </el-tag>
        </div>

        <div class="mt-6 rounded-3xl border border-slate-200 bg-slate-50 p-4">
          <el-table :data="currentSample.results" stripe>
            <el-table-column prop="category" label="缺陷类别" min-width="120" />
            <el-table-column prop="level" label="缺陷等级" min-width="100">
              <template #default="{ row }">
                <el-tag :type="row.level === '严重' ? 'danger' : 'warning'" round>
                  {{ row.level }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="confidence" label="置信度" min-width="100">
              <template #default="{ row }">{{ row.confidence }}%</template>
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
          <p class="panel-subtitle">上传多张图像进行批量缺陷检测，检测结果以列表形式展示。</p>
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
            <div class="mt-2 text-sm text-slate-400">支持多张图片批量上传检测</div>
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
              <el-table-column label="缺陷类别" min-width="100">
                <template #default="{ row }">
                  {{ row.results[0]?.category ?? '--' }}
                </template>
              </el-table-column>
              <el-table-column label="置信度" min-width="100">
                <template #default="{ row }">
                  {{ row.results[0]?.confidence?.toFixed(1) ?? '--' }}%
                </template>
              </el-table-column>
              <el-table-column label="风险等级" min-width="100">
                <template #default="{ row }">
                  <el-tag
                    :type="row.results[0]?.level === '严重' ? 'danger' : row.results[0]?.level === '中等' ? 'warning' : 'info'"
                    round
                  >
                    {{ row.results[0]?.level ?? '待定' }}
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
import DefectViewer, { type DefectViewerItem } from '@/components/defect/DefectViewer.vue';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';

const metrics = ref([
  { label: '模型版本', value: '--', extra: '加载中' },
  { label: '已识别样本', value: '--', extra: '加载中' },
  { label: '平均置信度', value: '--', extra: '加载中' },
]);

const loadMetrics = async () => {
  try {
    const stats = await fetchDefectStatistics();
    metrics.value = [
      { label: '模型版本', value: stats.modelVersion || '--', extra: '推理引擎已预留' },
      { label: '已识别样本', value: String(stats.totalSamples), extra: '数据库统计' },
      { label: '平均置信度', value: `${stats.avgConfidence.toFixed(1)}%`, extra: '缺陷识别统计' },
    ];
  } catch {
    // keep defaults
  }
};

const mediaTypeOptions = [
  { label: '图像', value: 'image' },
  { label: '视频', value: 'video' },
];

interface DetectionResult {
  category: string;
  level: '中等' | '严重';
  confidence: number;
  location: string;
}

interface SampleItem {
  id: number;
  name: string;
  mediaType: 'image' | 'video';
  batchNo: string;
  preview: string;
  results: DetectionResult[];
  defects: DefectViewerItem[];
  summary: string;
}

const fallbackSample: SampleItem = {
  id: 0,
  name: '暂无样本',
  mediaType: 'image',
  batchNo: '--',
  preview: '',
  results: [],
  defects: [],
  summary: '暂无检测数据',
};

const samples = ref<SampleItem[]>([]);

const loadSamples = async () => {
  try {
    const data = await fetchDefectSamples();
    samples.value = data.map((item, index) => ({
      id: index + 1,
      name: item.name,
      mediaType: (item.mediaType === 'video' ? 'video' : 'image') as 'image' | 'video',
      batchNo: item.batchNo,
      preview: item.imageUrl || '',
      results: item.results.map(r => ({
        category: r.category,
        level: r.level as '中等' | '严重',
        confidence: r.confidence,
        location: r.location,
      })),
      defects: item.defects.map((d, di) => ({
        id: di + 1,
        label: d.label,
        level: d.level as '中等' | '严重',
        confidence: d.confidence,
        bbox: [d.bbox[0] ?? 0, d.bbox[1] ?? 0, d.bbox[2] ?? 0, d.bbox[3] ?? 0] as [number, number, number, number],
      })),
      summary: item.summary,
    }));
  } catch {
    samples.value = [];
  }
};

const activeMediaType = ref<'image' | 'video'>('image');
const activeSampleId = ref(1);

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
    if (!list.some((item) => item.id === activeSampleId.value) && firstSample) {
      activeSampleId.value = firstSample.id;
    }
  },
  { immediate: true },
);

const riskLevel = computed(() =>
  currentSample.value.results.some((item) => item.level === '严重') ? '高' : '中',
);

const detectionStats = computed(() => [
  {
    label: '检测载体',
    value: currentSample.value.mediaType === 'image' ? '静态图像' : '视频流',
    tip: currentSample.value.batchNo,
  },
  {
    label: '缺陷数量',
    value: `${currentSample.value.results.length}`,
    tip: '当前样本',
  },
  {
    label: '最高置信度',
    value: `${Math.max(...currentSample.value.results.map((item) => item.confidence)).toFixed(1)}%`,
    tip: '模型输出',
  },
]);

const pipelineStatus = computed(() => [
  { label: '预处理模块', value: '已完成图像增强与归一化' },
  { label: '分类推理模块', value: 'ResNet 输出已回填至前端结果表格' },
  { label: '复核建议', value: riskLevel.value === '高' ? '建议转人工复判' : '建议继续追踪样本' },
]);

const batchFiles = ref<File[]>([]);
const batchDetecting = ref(false);
const batchResults = ref<DefectSampleResponse[]>([]);

const handleBatchFileChange = (uploadFile: UploadFile) => {
  if (uploadFile.raw) {
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
    const items = batchFiles.value.map((file) => ({
      name: file.name,
      batchNo: `BATCH-${Date.now()}`,
      imageUrl: file.name,
    }));

    const result = await batchDetectDefects(items);
    batchResults.value = result.results;
    ElMessage.success(result.message);
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
