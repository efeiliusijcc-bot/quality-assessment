import { mockApiSuccess } from '@/api/_mock';
import { isMockEnabled } from '@/constants/env';
import { request } from '@/utils/request';

export interface IntroMetric {
  label: string;
  value: string;
  extra: string;
}

export interface QualifiedResultCard {
  label: string;
  value: string;
  tip: string;
  pass: boolean;
}

export interface StreamMetric {
  label: string;
  value: string;
  percentage: number;
  color: string;
}

export interface QualifiedDashboardData {
  metrics: IntroMetric[];
  timeAxis: string[];
  temperatureData: number[];
  pressureData: number[];
  currentData: number[];
  resultCards: QualifiedResultCard[];
  streamMetrics: StreamMetric[];
  graphReasoning: GraphReasoning;
}

export interface JudgmentDiagnosisItem {
  title: string;
  content: string;
}

export interface JudgmentActionItem {
  label: string;
  value: string;
}

export interface JudgmentDashboardData {
  metrics: IntroMetric[];
  radarIndicators: Array<{ name: string; max: number }>;
  abnormalSampleValues: number[];
  targetValues: number[];
  compareCategories: string[];
  currentParameters: number[];
  targetParameters: number[];
  coreConclusion: string;
  coreDescription: string;
  diagnosisItems: JudgmentDiagnosisItem[];
  actionItems: JudgmentActionItem[];
  graphReasoning: GraphReasoning;
}

export interface PredictionOptimizationRow {
  parameter: string;
  current: string;
  recommended: string;
  effect: string;
}

export interface GraphReasoning {
  riskScore: number;
  mainDefect: string;
  defectChain: string[];
  parameterChain: string[];
  stepChain: string[];
  reasoningSummary: string;
  optimizationHints: string[];
  statistics: {
    nodeCount: number;
    relationCount: number;
    ruleRelationCount: number;
    aprioriRelationCount: number;
    defectCount: number;
    parameterCount: number;
  };
}

export interface PredictionDashboardData {
  metrics: IntroMetric[];
  predictedProbability: number;
  threshold: number;
  triggerCards: Array<{ label: string; value: string; tip: string }>;
  optimizationTable: PredictionOptimizationRow[];
  optimizationSummary: Array<{ label: string; value: string }>;
  graphReasoning: GraphReasoning;
}

const emptyGraphReasoning = (): GraphReasoning => ({
  riskScore: 0,
  mainDefect: 'No Defect',
  defectChain: ['No Defect'],
  parameterChain: [],
  stepChain: [],
  reasoningSummary: 'Knowledge graph reasoning is unavailable in mock mode.',
  optimizationHints: [],
  statistics: {
    nodeCount: 0,
    relationCount: 0,
    ruleRelationCount: 0,
    aprioriRelationCount: 0,
    defectCount: 0,
    parameterCount: 0,
  },
});

export const fetchQualifiedDashboard = async (batchId?: string): Promise<QualifiedDashboardData> => {
  if (!isMockEnabled) {
    return request<QualifiedDashboardData>({
      url: '/assessment/qualified',
      method: 'GET',
      params: { batchId: batchId || undefined },
    });
  }

  const response = await mockApiSuccess<QualifiedDashboardData>({
    metrics: [
      { label: '当前批次', value: 'BATCH-2408', extra: '装配工位 07' },
      { label: '实时良率', value: '98.4%', extra: '滚动窗口 5 分钟' },
      { label: '参数流速', value: '120/s', extra: '多传感器采样' },
    ],
    timeAxis: ['10:00', '10:01', '10:02', '10:03', '10:04', '10:05', '10:06', '10:07'],
    temperatureData: [232, 234, 233, 235, 236, 234, 233, 232],
    pressureData: [4.1, 4.2, 4.3, 4.2, 4.4, 4.3, 4.2, 4.2],
    currentData: [1.1, 1.2, 1.15, 1.22, 1.24, 1.19, 1.16, 1.14],
    resultCards: [
      { label: '批次评估', value: '通过', tip: '综合评分 96.8 / 100', pass: true },
      { label: '关键参数报警', value: '0 项', tip: '当前窗口未触发越界', pass: true },
      { label: '边缘风险提示', value: '压力波动偏高', tip: '建议持续观察工位 07', pass: false },
    ],
    streamMetrics: [
      { label: '焊接温度稳定度', value: '97.6%', percentage: 97.6, color: '#10b981' },
      { label: '贴装压力稳定度', value: '91.2%', percentage: 91.2, color: '#f59e0b' },
      { label: '电流波动健康度', value: '95.4%', percentage: 95.4, color: '#0ea5e9' },
    ],
    graphReasoning: emptyGraphReasoning(),
  });

  return response.data;
};

export const fetchJudgmentDashboard = async (batchId?: string): Promise<JudgmentDashboardData> => {
  if (!isMockEnabled) {
    return request<JudgmentDashboardData>({
      url: '/assessment/judgment',
      method: 'GET',
      params: { batchId: batchId || undefined },
    });
  }

  const response = await mockApiSuccess<JudgmentDashboardData>({
    metrics: [
      { label: '异常样本', value: '07', extra: '待研判' },
      { label: '主要问题', value: '温控偏移', extra: '占异常原因 41%' },
      { label: '分析状态', value: '进行中', extra: 'Mock 报告生成' },
    ],
    radarIndicators: [
      { name: '焊接温度', max: 100 },
      { name: '贴装压力', max: 100 },
      { name: '保压时长', max: 100 },
      { name: '电流稳定性', max: 100 },
      { name: '振动幅值', max: 100 },
      { name: '视觉偏移量', max: 100 },
    ],
    abnormalSampleValues: [69, 58, 62, 81, 48, 55],
    targetValues: [88, 82, 85, 90, 72, 84],
    compareCategories: ['温度', '压力', '保压', '电流', '偏移量'],
    currentParameters: [232, 4.1, 2.6, 1.2, 0.48],
    targetParameters: [238, 4.6, 3.1, 1.15, 0.18],
    coreConclusion: '焊接热输入不足，伴随压力匹配失衡',
    coreDescription:
      '从温度、保压时长与贴装压力三项指标看，当前样本在焊接窗口下沿运行，导致虚焊和轻微偏移并发。',
    diagnosisItems: [
      {
        title: '工艺问题 1: 热输入偏低',
        content: '焊接温度与保压时长同时低于目标窗口，容易导致焊点润湿不足，形成虚焊或低强度连接。',
      },
      {
        title: '工艺问题 2: 贴装压力偏低',
        content: '贴装压力不足会降低元件与焊盘的接触一致性，在局部振动增大时进一步放大偏移风险。',
      },
      {
        title: '工艺问题 3: 工位振动异常',
        content: '振动幅值偏高与视觉偏移量上升相关，建议检查机械臂末端夹具及传送节拍设置。',
      },
    ],
    actionItems: [
      { label: '建议动作', value: '上调焊接温度 4-6℃，同步增加保压时长 0.4s' },
      { label: '复核优先级', value: '高，建议立即复测同批次样本' },
      { label: '责任工位', value: '焊接工位 B2 / 贴装工位 A1' },
    ],
    graphReasoning: emptyGraphReasoning(),
  });

  return response.data;
};

export const fetchPredictionDashboard = async (batchId?: string): Promise<PredictionDashboardData> => {
  if (!isMockEnabled) {
    return request<PredictionDashboardData>({
      url: '/assessment/prediction',
      method: 'GET',
      params: { batchId: batchId || undefined },
    });
  }

  const response = await mockApiSuccess<PredictionDashboardData>({
    metrics: [
      { label: '预测概率', value: '84.7%', extra: '基于仿真样本' },
      { label: '阈值下限', value: '90%', extra: '低于阈值触发优化' },
      { label: '优化方案', value: '03', extra: 'MANSGA-III 候选解' },
    ],
    predictedProbability: 84.7,
    threshold: 90,
    triggerCards: [
      {
        label: '当前状态',
        value: '低于合格阈值',
        tip: '根据预测结果自动判断是否触发优化',
      },
      {
        label: '风险主因',
        value: '热输入不足 + 偏移上升',
        tip: '综合缺陷识别与工艺参数偏差',
      },
      {
        label: '预估提升空间',
        value: '+8.6%',
        tip: '采用推荐参数组合后的模拟提升',
      },
    ],
    optimizationTable: [
      { parameter: '焊接温度', current: '232℃', recommended: '238℃', effect: '提升焊点润湿充分度' },
      { parameter: '贴装压力', current: '4.1N', recommended: '4.6N', effect: '降低偏移与接触不良' },
      { parameter: '保压时长', current: '2.6s', recommended: '3.1s', effect: '增强焊点稳定成形' },
      { parameter: '传送节拍', current: '1.8s/件', recommended: '2.0s/件', effect: '减小工位振动扰动' },
    ],
    optimizationSummary: [
      { label: '推荐方案编号', value: 'Plan-03' },
      { label: '预计合格率', value: '93.3%' },
      { label: '综合代价', value: '中等调整幅度' },
    ],
    graphReasoning: {
      riskScore: 62.5,
      mainDefect: '虚焊',
      defectChain: ['target_temp_reflow', 'target_temp_preheat', 'Defect_虚焊'],
      parameterChain: ['target_temp_reflow', 'target_belt_speed'],
      stepChain: ['SMT回流焊', 'AOI检测'],
      reasoningSummary: '温度参数偏差导致虚焊风险上升',
      optimizationHints: ['提高回流温度至238℃', '增加预热时间'],
      statistics: {
        nodeCount: 12,
        relationCount: 18,
        ruleRelationCount: 8,
        aprioriRelationCount: 10,
        defectCount: 3,
        parameterCount: 6,
      },
    },
  });

  return response.data;
};

export interface AssessmentHistoryItem {
  id: string;
  batchId: string;
  station: string;
  temperature: number;
  pressure: number;
  currentValue: number;
  sampledAt: string;
}

export interface AssessmentHistoryPage {
  records: AssessmentHistoryItem[];
  total: number;
}

export const fetchAssessmentHistory = async (
  batchId?: string,
  page = 1,
  size = 10,
): Promise<AssessmentHistoryPage> => {
  if (!isMockEnabled) {
    return request<AssessmentHistoryPage>({
      url: '/assessment/history',
      method: 'GET',
      params: { batchId: batchId || undefined, page, size },
    });
  }

  const response = await mockApiSuccess<AssessmentHistoryPage>({
    records: Array.from({ length: Math.min(size, 5) }, (_, index) => ({
      id: `hist-${page}-${index}`,
      batchId: batchId || 'BATCH-2408',
      station: '装配工位 07',
      temperature: +(232 + (index % 5) * 0.8).toFixed(1),
      pressure: +(4.1 + (index % 4) * 0.05).toFixed(2),
      currentValue: +(1.12 + (index % 6) * 0.01).toFixed(2),
      sampledAt: `2026-04-0${Math.min(9, 6 + index)} ${String(8 + index).padStart(2, '0')}:${String((index * 13) % 60).padStart(2, '0')}:00`,
    })),
    total: 24,
  });

  return response.data;
};

export const fetchStations = async (): Promise<string[]> => {
  if (!isMockEnabled) {
    return request<string[]>({
      url: '/assessment/stations',
      method: 'GET',
    });
  }

  const response = await mockApiSuccess<string[]>([
    '装配工位 07',
    '贴装工位 A1',
    '焊接工位 B2',
    '检测工位 C3',
  ]);

  return response.data;
};

export interface JudgmentStreamData {
  timeAxis: string[];
  temperature: number[];
  beltSpeed: number[];
  o2Ppm: number[];
  humidity: number[];
  current: number[];
}

export const fetchJudgmentStream = async (batchId?: string): Promise<JudgmentStreamData> => {
  if (!isMockEnabled) {
    return request<JudgmentStreamData>({
      url: '/assessment/judgment/stream',
      method: 'GET',
      params: { batchId: batchId || undefined },
    });
  }

  const timeAxis = Array.from({ length: 12 }, (_, i) => `${String(8 + i).padStart(2, '0')}:${String((i * 5) % 60).padStart(2, '0')}:00`);
  const response = await mockApiSuccess<JudgmentStreamData>({
    timeAxis,
    temperature: timeAxis.map(() => +(232 + Math.random() * 6).toFixed(1)),
    beltSpeed: timeAxis.map(() => +(88 + Math.random() * 4).toFixed(1)),
    o2Ppm: timeAxis.map(() => +(470 + Math.random() * 20).toFixed(1)),
    humidity: timeAxis.map(() => +(45 + Math.random() * 5).toFixed(1)),
    current: timeAxis.map(() => +(3.9 + Math.random() * 0.6).toFixed(2)),
  });

  return response.data;
};

export interface SimulationDataPoint {
  time: string;
  temperature: number;
  pressure: number;
  beltSpeed: number;
  probability: number;
}

export interface SimulationStreamData {
  points: SimulationDataPoint[];
}

export const fetchSimulationStream = async (batchId?: string): Promise<SimulationStreamData> => {
  if (!isMockEnabled) {
    return request<SimulationStreamData>({
      url: '/assessment/prediction/simulation',
      method: 'GET',
      params: { batchId: batchId || undefined },
    });
  }

  const points = Array.from({ length: 24 }, (_, i) => ({
    time: `${String(8 + Math.floor(i / 2)).padStart(2, '0')}:${String((i * 5) % 60).padStart(2, '0')}:00`,
    temperature: +(238 + (Math.random() - 0.5) * 4).toFixed(1),
    pressure: +(4.2 + (Math.random() - 0.5) * 0.4).toFixed(2),
    beltSpeed: +(90 + (Math.random() - 0.5) * 2).toFixed(1),
    probability: +(85 + (Math.random() - 0.5) * 8).toFixed(1),
  }));

  const response = await mockApiSuccess<SimulationStreamData>({ points });
  return response.data;
};

export const fetchBatches = async (): Promise<string[]> => {
  if (!isMockEnabled) {
    return request<string[]>({
      url: '/assessment/batches',
      method: 'GET',
    });
  }

  const response = await mockApiSuccess<string[]>([], 'success');
  return response.data;
};
