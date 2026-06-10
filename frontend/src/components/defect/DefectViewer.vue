<template>
  <div
    ref="containerRef"
    class="relative overflow-hidden rounded-[24px] border border-cyan-400/15 bg-[radial-gradient(circle_at_top,rgba(14,165,233,0.18),transparent_30%),linear-gradient(180deg,#0f172a,#020617)]"
  >
    <img
      v-if="imageUrl"
      ref="imageRef"
      :src="imageUrl"
      :alt="alt"
      class="block h-full w-full object-contain"
      @load="handleImageLoad"
    />
    <div v-else class="flex h-full min-h-[360px] items-center justify-center text-sm text-slate-400">
      暂无图像预览
    </div>

    <div class="pointer-events-none absolute inset-0">
      <div
        v-for="defect in normalizedDefects"
        :key="defect.id"
        class="absolute rounded-md border-2 shadow-[0_0_0_9999px_rgba(2,6,23,0.08)] transition-all"
        :style="getBoxStyle(defect)"
      >
        <span
          class="absolute left-0 top-0 -translate-y-full whitespace-nowrap rounded-md px-2 py-1 text-xs font-semibold text-white"
          :style="{ backgroundColor: defect.palette.labelBg }"
        >
          {{ defect.label }} {{ formatConfidence(defect.confidence) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';

export type DefectLevel = 'severe' | 'moderate' | 'minor' | 'normal';

export interface DefectViewerItem {
  id?: string | number;
  label: string;
  confidence: number;
  bbox: [number, number, number, number];
  level?: DefectLevel;
}

interface NormalizedDefect extends DefectViewerItem {
  id: string | number;
  level: DefectLevel;
  palette: {
    border: string;
    labelBg: string;
  };
}

const props = withDefaults(
  defineProps<{
    alt?: string;
    defects: DefectViewerItem[];
    imageUrl: string;
  }>(),
  {
    alt: '缺陷检测图像',
  },
);

const imageRef = ref<HTMLImageElement | null>(null);
const containerRef = ref<HTMLDivElement | null>(null);
const naturalSize = ref({ width: 1, height: 1 });
const renderedSize = ref({ width: 1, height: 1 });
let resizeObserver: ResizeObserver | null = null;

const resolveLevel = (item: DefectViewerItem): DefectLevel => {
  if (item.level) {
    return item.level;
  }
  if (item.confidence >= 0.9) {
    return 'severe';
  }
  if (item.confidence >= 0.82) {
    return 'moderate';
  }
  if (item.confidence >= 0.72) {
    return 'minor';
  }
  return 'normal';
};

const paletteMap: Record<DefectLevel, { border: string; labelBg: string }> = {
  severe: {
    border: '#ef4444',
    labelBg: '#b91c1c',
  },
  moderate: {
    border: '#f59e0b',
    labelBg: '#b45309',
  },
  minor: {
    border: '#eab308',
    labelBg: '#a16207',
  },
  normal: {
    border: '#22c55e',
    labelBg: '#15803d',
  },
};

const normalizedDefects = computed<NormalizedDefect[]>(() =>
  props.defects
    .filter((item) => item.bbox.length === 4 && item.bbox[2] > 0 && item.bbox[3] > 0)
    .map((item, index) => {
      const level = resolveLevel(item);
      return {
        ...item,
        id: item.id ?? `${item.label}-${index}`,
        level,
        palette: paletteMap[level],
      };
    }),
);

const syncRenderedSize = () => {
  if (!imageRef.value) {
    return;
  }

  renderedSize.value = {
    width: imageRef.value.clientWidth || 1,
    height: imageRef.value.clientHeight || 1,
  };
};

const handleImageLoad = () => {
  if (!imageRef.value) {
    return;
  }

  naturalSize.value = {
    width: imageRef.value.naturalWidth || 1,
    height: imageRef.value.naturalHeight || 1,
  };

  syncRenderedSize();

  if (!resizeObserver && imageRef.value) {
    resizeObserver = new ResizeObserver(() => {
      syncRenderedSize();
    });
    resizeObserver.observe(imageRef.value);
  }
};

const isPercentBox = ([x, y, width, height]: [number, number, number, number]) =>
  x >= 0 && y >= 0 && width >= 0 && height >= 0 && x + width <= 100 && y + height <= 100;

const getBoxStyle = (defect: NormalizedDefect) => {
  const [x, y, width, height] = defect.bbox;

  if (isPercentBox(defect.bbox)) {
    return {
      left: `${x}%`,
      top: `${y}%`,
      width: `${width}%`,
      height: `${height}%`,
      borderColor: defect.palette.border,
    };
  }

  const scaleX = renderedSize.value.width / naturalSize.value.width;
  const scaleY = renderedSize.value.height / naturalSize.value.height;

  return {
    left: `${x * scaleX}px`,
    top: `${y * scaleY}px`,
    width: `${width * scaleX}px`,
    height: `${height * scaleY}px`,
    borderColor: defect.palette.border,
  };
};

const formatConfidence = (value: number) => `${Math.round(value * 100)}%`;

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
});
</script>
