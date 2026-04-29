<template>
  <div ref="chartRef" class="h-full w-full"></div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { EChartsCoreOption } from 'echarts/core';
import { useECharts } from '@/hooks/useECharts';
import type { GraphVisualizationNode, GraphVisualizationEdge } from '@/api/graph';

const props = defineProps<{
  nodes: GraphVisualizationNode[];
  edges: GraphVisualizationEdge[];
}>();

const chartRef = ref<HTMLDivElement | null>(null);

const labelColorMap: Record<string, string> = {
  Batch: '#3b82f6',
  ProcessStep: '#8b5cf6',
  ProcessParameter: '#10b981',
  QualityParameter: '#f59e0b',
  Defect: '#ef4444',
};

const chartOption = computed<EChartsCoreOption>(() => {
  const categories = [...new Set(props.nodes.map(n => n.label))];
  const categoryMap = Object.fromEntries(categories.map((c, i) => [c, i]));

  const seriesData = props.nodes.map(node => ({
    name: node.name || node.graphId,
    category: categoryMap[node.label] ?? 0,
    symbolSize: node.label === 'Batch' ? 40 : node.label === 'Defect' ? 28 : 20,
    itemStyle: { color: labelColorMap[node.label] || '#94a3b8' },
    label: { show: true, fontSize: 10, color: '#e2e8f0' },
  }));

  const seriesLinks = props.edges.map(edge => ({
    source: props.nodes.find(n => n.graphId === edge.from)?.name || edge.from,
    target: props.nodes.find(n => n.graphId === edge.to)?.name || edge.to,
    lineStyle: {
      width: Math.max(1, Math.min(6, edge.weight * 3)),
      color: '#64748b',
      curveness: 0.2,
    },
  }));

  return {
    tooltip: {
      backgroundColor: 'rgba(2, 6, 23, 0.9)',
      textStyle: { color: '#e2e8f0' },
    },
    legend: {
      data: categories,
      top: 10,
      textStyle: { color: '#cbd5e1', fontSize: 11 },
    },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        force: {
          repulsion: 200,
          edgeLength: [80, 200],
          gravity: 0.1,
        },
        data: seriesData,
        links: seriesLinks,
        categories: categories.map(name => ({
          name,
          itemStyle: { color: labelColorMap[name] || '#94a3b8' },
        })),
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 4 },
        },
        label: {
          show: true,
          position: 'bottom',
          fontSize: 10,
          color: '#e2e8f0',
        },
      },
    ],
  };
});

const { chartInstance } = useECharts(chartRef, chartOption);
</script>
