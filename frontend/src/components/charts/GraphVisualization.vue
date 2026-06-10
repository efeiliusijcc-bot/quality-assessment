<template>
  <div ref="chartRef" class="h-full w-full"></div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { EChartsCoreOption } from 'echarts/core';
import { useECharts } from '@/hooks/useECharts';
import type { GatAttentionEdge, GraphVisualizationNode, GraphVisualizationEdge } from '@/api/graph';

const props = defineProps<{
  nodes: GraphVisualizationNode[];
  edges: GraphVisualizationEdge[];
  attentionEdges?: GatAttentionEdge[];
  highlightNodeNames?: string[];
  highlightEdges?: GatAttentionEdge[];
  focusMode?: boolean;
}>();

const chartRef = ref<HTMLDivElement | null>(null);

const labelColorMap: Record<string, string> = {
  Batch: '#3b82f6',
  ProductionBatch: '#3b82f6',
  ProcessStep: '#8b5cf6',
  ProcessParameter: '#10b981',
  ParameterDef: '#10b981',
  ParameterValue: '#22c55e',
  QualityParameter: '#f59e0b',
  QualityMeasurement: '#f59e0b',
  Defect: '#ef4444',
  DefectType: '#ef4444',
  DefectRecord: '#f97316',
  InspectionTask: '#06b6d4',
  ProductUnit: '#6366f1',
  ProcessRun: '#a855f7',
  Equipment: '#64748b',
  Workstation: '#14b8a6',
};

const chartOption = computed<EChartsCoreOption>(() => {
  const categories = [...new Set(props.nodes.map(n => n.label))];
  const categoryMap = Object.fromEntries(categories.map((c, i) => [c, i]));
  const nodeMap = new Map(props.nodes.map(node => [node.graphId, node]));
  const normalize = (value: string) => value.trim().toLowerCase();
  const highlightNames = new Set((props.highlightNodeNames ?? []).map(normalize));
  const focusedEdges = props.focusMode ? props.highlightEdges ?? [] : props.attentionEdges ?? [];
  const attentionMap = new Map<string, GatAttentionEdge>();

  for (const edge of focusedEdges) {
    const key = `${normalize(edge.from)}|${normalize(edge.to)}|${normalize(edge.relationType)}`;
    attentionMap.set(key, edge);
  }

  const findAttention = (edge: GraphVisualizationEdge) => {
    const fromNode = nodeMap.get(edge.from);
    const toNode = nodeMap.get(edge.to);
    const candidates = [
      `${edge.from}|${edge.to}|${edge.type}`,
      `${fromNode?.name ?? ''}|${toNode?.name ?? ''}|${edge.type}`,
      `${fromNode?.graphId ?? ''}|${toNode?.graphId ?? ''}|${edge.type}`,
    ];

    for (const candidate of candidates) {
      const hit = attentionMap.get(candidate.split('|').map(normalize).join('|'));
      if (hit) return hit;
    }
    return undefined;
  };

  const seriesData = props.nodes.map(node => {
    const displayName = node.name || node.graphId;
    const isHighlighted = props.focusMode
      ? highlightNames.has(normalize(displayName)) || highlightNames.has(normalize(node.graphId))
      : false;
    const baseSize = node.label === 'Batch' || node.label === 'ProductionBatch' ? 42 : node.label.includes('Defect') ? 30 : 22;

    return {
      id: node.graphId,
      name: node.graphId,
      displayName,
      category: categoryMap[node.label] ?? 0,
      symbolSize: isHighlighted ? baseSize + 14 : baseSize,
      itemStyle: {
        color: isHighlighted ? '#f59e0b' : labelColorMap[node.label] || '#94a3b8',
        opacity: props.focusMode && !isHighlighted ? 0.28 : 1,
        borderColor: isHighlighted ? '#fef3c7' : undefined,
        borderWidth: isHighlighted ? 3 : 0,
      },
      label: {
        show: !props.focusMode || isHighlighted,
        fontSize: isHighlighted ? 12 : 10,
        fontWeight: isHighlighted ? 700 : 400,
        color: isHighlighted ? '#fef3c7' : '#e2e8f0',
        formatter: () => displayName,
      },
    };
  });

  const seriesLinks = props.edges.map(edge => {
    const attention = findAttention(edge);
    const attentionWeight = attention?.attentionWeight ?? 0;
    const isHighlighted = attentionWeight > 0;
    const isDimmed = Boolean(props.focusMode && !isHighlighted);

    return {
      source: edge.from,
      target: edge.to,
      value: edge.type,
      attentionWeight,
      label: {
        show: isHighlighted,
        formatter: isHighlighted ? `${edge.type} ${(attentionWeight * 100).toFixed(0)}%` : edge.type,
        color: isHighlighted ? '#fde68a' : '#94a3b8',
        fontSize: 9,
      },
      lineStyle: {
        width: isHighlighted ? Math.max(3, Math.min(9, attentionWeight * 10)) : Math.max(1, Math.min(6, edge.weight * 3)),
        color: isHighlighted ? '#f59e0b' : '#64748b',
        opacity: isHighlighted ? 0.95 : isDimmed ? 0.12 : 0.55,
        curveness: 0.2,
      },
    };
  });

  return {
    tooltip: {
      backgroundColor: 'rgba(2, 6, 23, 0.9)',
      textStyle: { color: '#e2e8f0' },
      formatter: (params: any) => {
        if (params.dataType === 'edge') {
          return `${params.data.source}<br/>${params.data.value}<br/>${params.data.target}`;
        }
        return `${params.data.displayName}<br/>${params.data.category ?? ''}`;
      },
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
