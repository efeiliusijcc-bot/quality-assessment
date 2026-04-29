import { nextTick, onBeforeUnmount, onMounted, shallowRef, watch, type Ref } from 'vue';
import type { ECharts, EChartsCoreOption } from 'echarts/core';

type EChartsModule = typeof import('echarts/core');

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

export const useECharts = (
  containerRef: Ref<HTMLDivElement | null>,
  optionRef: Ref<EChartsCoreOption>,
) => {
  const chartInstance = shallowRef<ECharts | null>(null);
  let resizeObserver: ResizeObserver | null = null;
  let rafId = 0;

  const cancelFrame = () => {
    if (rafId) {
      window.cancelAnimationFrame(rafId);
      rafId = 0;
    }
  };

  const schedule = (task: () => void) => {
    cancelFrame();
    rafId = window.requestAnimationFrame(() => {
      rafId = 0;
      task();
    });
  };

  const setChartOption = () => {
    if (!chartInstance.value) {
      return;
    }

    chartInstance.value.setOption(
      {
        animationDuration: 180,
        animationDurationUpdate: 180,
        ...optionRef.value,
      },
      {
        notMerge: true,
        lazyUpdate: true,
        silent: true,
      },
    );
  };

  const resize = () => {
    schedule(() => {
      chartInstance.value?.resize({
        animation: {
          duration: 120,
        },
      });
    });
  };

  const init = async () => {
    await nextTick();
    if (!containerRef.value) {
      return;
    }

    const echarts = await loadECharts();
    if (!containerRef.value) {
      return;
    }

    chartInstance.value = echarts.getInstanceByDom(containerRef.value) ?? echarts.init(containerRef.value);
    setChartOption();

    resizeObserver = new ResizeObserver(() => {
      resize();
    });
    resizeObserver.observe(containerRef.value);
  };

  const dispose = () => {
    cancelFrame();
    resizeObserver?.disconnect();
    resizeObserver = null;
    chartInstance.value?.dispose();
    chartInstance.value = null;
  };

  onMounted(() => {
    void init();
  });

  watch(
    optionRef,
    () => {
      schedule(() => {
        setChartOption();
      });
    },
    { deep: true, flush: 'post' },
  );

  onBeforeUnmount(() => {
    dispose();
  });

  return {
    chartInstance,
    dispose,
    resize,
    setChartOption,
  };
};
