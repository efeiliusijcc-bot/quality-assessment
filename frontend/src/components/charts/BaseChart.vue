<template>
  <div class="relative h-full w-full">
    <div ref="chartRef" class="h-full w-full"></div>
    <div
      v-if="!ready"
      class="pointer-events-none absolute inset-0 rounded-[20px] bg-[linear-gradient(110deg,rgba(15,23,42,0.88),rgba(30,41,59,0.72),rgba(15,23,42,0.88))] bg-[length:200%_100%] animate-[chartShimmer_1.6s_linear_infinite]"
    ></div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { ECharts, EChartsCoreOption } from 'echarts/core';

type EChartsModule = typeof import('echarts/core');

const props = withDefaults(
  defineProps<{
    defer?: boolean;
    option: EChartsCoreOption;
  }>(),
  {
    defer: true,
  },
);

let echartsLoader: Promise<EChartsModule> | null = null;

const loadECharts = async () => {
  if (!echartsLoader) {
    echartsLoader = (async () => {
      const echarts = await import('echarts/core');
      const { LineChart, BarChart, RadarChart, GaugeChart, ScatterChart, GraphChart } = await import('echarts/charts');
      const { CanvasRenderer } = await import('echarts/renderers');
      const {
        GridComponent,
        LegendComponent,
        RadarComponent,
        TitleComponent,
        TooltipComponent,
      } = await import('echarts/components');

      echarts.use([
        LineChart,
        BarChart,
        RadarChart,
        GaugeChart,
        ScatterChart,
        GraphChart,
        GridComponent,
        LegendComponent,
        RadarComponent,
        TitleComponent,
        TooltipComponent,
        CanvasRenderer,
      ]);

      return echarts;
    })();
  }

  return echartsLoader;
};

const chartRef = ref<HTMLDivElement | null>(null);
const ready = ref(false);

let chartInstance: ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;
let rafId = 0;
let isUnmounted = false;

const cancelFrame = () => {
  if (rafId) {
    window.cancelAnimationFrame(rafId);
    rafId = 0;
  }
};

const scheduleFrame = (task: () => void) => {
  cancelFrame();
  rafId = window.requestAnimationFrame(() => {
    rafId = 0;
    if (!isUnmounted) {
      task();
    }
  });
};

const applyOption = () => {
  if (!chartInstance) {
    return;
  }

  const normalizedOption: EChartsCoreOption = {
    animationDuration: 180,
    animationDurationUpdate: 180,
    ...props.option,
  };

  chartInstance.setOption(normalizedOption, {
    notMerge: true,
    lazyUpdate: true,
    silent: true,
  });
};

const initChart = async () => {
  await nextTick();
  if (!chartRef.value || isUnmounted) {
    return;
  }

  const echarts = await loadECharts();
  if (!chartRef.value || isUnmounted) {
    return;
  }

  chartInstance = echarts.getInstanceByDom(chartRef.value) ?? echarts.init(chartRef.value);
  applyOption();
  ready.value = true;

  resizeObserver = new ResizeObserver(() => {
    scheduleFrame(() => {
      chartInstance?.resize({
        animation: {
          duration: 120,
        },
      });
    });
  });
  resizeObserver.observe(chartRef.value);
};

const mountChart = () => {
  if (!props.defer) {
    void initChart();
    return;
  }

  scheduleFrame(() => {
    void initChart();
  });
};

onMounted(() => {
  mountChart();
});

watch(
  () => props.option,
  () => {
    if (!chartInstance) {
      return;
    }

    scheduleFrame(() => {
      applyOption();
    });
  },
  { deep: true, flush: 'post' },
);

onBeforeUnmount(() => {
  isUnmounted = true;
  cancelFrame();
  resizeObserver?.disconnect();
  resizeObserver = null;
  chartInstance?.dispose();
  chartInstance = null;
});
</script>

<style scoped>
@keyframes chartShimmer {
  0% {
    background-position: 200% 0;
  }

  100% {
    background-position: -200% 0;
  }
}
</style>
