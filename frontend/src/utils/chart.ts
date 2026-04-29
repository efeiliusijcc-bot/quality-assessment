import type { EChartsCoreOption } from 'echarts/core';

export const axisLabelColor = '#94a3b8';
export const splitLineColor = 'rgba(148, 163, 184, 0.15)';

export const createTooltip = (): EChartsCoreOption['tooltip'] => ({
  trigger: 'axis',
  backgroundColor: 'rgba(2, 6, 23, 0.9)',
  borderColor: 'rgba(34, 211, 238, 0.2)',
  textStyle: {
    color: '#e2e8f0',
  },
});
