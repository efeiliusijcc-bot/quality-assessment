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

export const fetchQualifiedDashboard = async (batchId?: string): Promise<QualifiedDashboardData> => {
  return request<QualifiedDashboardData>({
    url: '/assessment/qualified',
    method: 'GET',
    params: { batchId: batchId || undefined },
  });
};

export const fetchJudgmentDashboard = async (batchId?: string): Promise<JudgmentDashboardData> => {
  return request<JudgmentDashboardData>({
    url: '/assessment/judgment',
    method: 'GET',
    params: { batchId: batchId || undefined },
  });
};

export const fetchPredictionDashboard = async (batchId?: string): Promise<PredictionDashboardData> => {
  return request<PredictionDashboardData>({
    url: '/assessment/prediction',
    method: 'GET',
    params: { batchId: batchId || undefined },
  });
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
  return request<AssessmentHistoryPage>({
    url: '/assessment/history',
    method: 'GET',
    params: { batchId: batchId || undefined, page, size },
  });
};

export const fetchStations = async (): Promise<string[]> => {
  return request<string[]>({
    url: '/assessment/stations',
    method: 'GET',
  });
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
  return request<JudgmentStreamData>({
    url: '/assessment/judgment/stream',
    method: 'GET',
    params: { batchId: batchId || undefined },
  });
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
  return request<SimulationStreamData>({
    url: '/assessment/prediction/simulation',
    method: 'GET',
    params: { batchId: batchId || undefined },
  });
};

export const fetchBatches = async (): Promise<string[]> => {
  return request<string[]>({
    url: '/assessment/batches',
    method: 'GET',
  });
};
