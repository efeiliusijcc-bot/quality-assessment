import { request } from '@/utils/request';

export type ManyObjectiveAlgorithm = 'MANSGA_III' | 'PUSH_PULL';
export type ObjectiveDirection = 'MIN' | 'MAX';

export interface ParetoSolution {
  parameters: Record<string, number>;
  objectiveValues: Record<string, number>;
  crowdingDistance: number;
}

export interface OptimizationStatistics {
  elapsedTimeMs: number;
  totalEvaluations: number;
  paretoFrontSize: number;
}

export interface OptimizationResponse {
  batchId: string;
  algorithm: string;
  generations: number;
  paretoFront: ParetoSolution[];
  recommendedSolution: ParetoSolution | null;
  statistics: OptimizationStatistics;
}

export interface OptimizationObjective {
  code: string;
  name: string;
  direction: ObjectiveDirection;
  description?: string;
  unit?: string;
  weight?: number;
}

export interface ManyObjectiveOptimizationOptions {
  algorithm?: ManyObjectiveAlgorithm;
  populationSize?: number;
  generations?: number;
}

interface OptimizationObjectiveMetadataItem {
  code?: string;
  name?: string;
  label?: string;
  direction?: string;
  description?: string;
  unit?: string;
  weight?: number;
}

interface OptimizationObjectiveMetadata {
  objectives?: OptimizationObjectiveMetadataItem[];
}

export const sevenObjectiveFallback: OptimizationObjective[] = [
  { code: 'defect_severity', name: '缺陷严重程度', direction: 'MIN', description: '综合缺陷等级与严重权重，越低越好。' },
  { code: 'negative_pass_rate', name: '合格率损失', direction: 'MIN', description: '等价于提升合格率，数值越低代表合格率越高。' },
  { code: 'defect_count', name: '缺陷数量', direction: 'MIN', description: '单位批次或工位缺陷数量，越低越好。' },
  { code: 'defect_size', name: '缺陷大小', direction: 'MIN', description: '缺陷面积或尺寸综合值，越低越好。' },
  { code: 'cost', name: '生产成本', direction: 'MIN', description: '材料、能耗与加工成本综合估计，越低越好。' },
  { code: 'compute_time', name: '计算时间', direction: 'MIN', description: '求解耗时或工艺执行时间，越低越好。' },
  { code: 'negative_reliability', name: '可靠性损失', direction: 'MIN', description: '等价于提升可靠性，数值越低代表可靠性越高。' },
];

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const isUuid = (value: string) => uuidPattern.test(value);

const normalizeDirection = (value?: string): ObjectiveDirection =>
  String(value ?? 'MIN').toUpperCase() === 'MAX' ? 'MAX' : 'MIN';

const normalizeObjectives = (payload: OptimizationObjective[] | OptimizationObjectiveMetadata): OptimizationObjective[] => {
  const source = Array.isArray(payload) ? payload : payload.objectives;
  if (!Array.isArray(source)) {
    return sevenObjectiveFallback;
  }

  const result = source
    .map((item) => {
      const objective = item as OptimizationObjectiveMetadataItem;
      return {
        code: objective.code ?? '',
        name: objective.name ?? objective.label ?? objective.code ?? '',
        direction: normalizeDirection(objective.direction),
        description: objective.description,
        unit: objective.unit,
        weight: objective.weight,
      };
    })
    .filter((item) => item.code && item.name);

  return result.length > 0 ? result : sevenObjectiveFallback;
};

export const runOptimization = (batchId: string): Promise<OptimizationResponse> =>
  request<OptimizationResponse>({
    url: `/optimization/run/${batchId}`,
    method: 'POST',
  });

export const runManyObjectiveOptimization = (
  batchId: string,
  options: ManyObjectiveOptimizationOptions = {},
): Promise<OptimizationResponse> =>
  isUuid(batchId)
    ? request<OptimizationResponse>({
        url: `/optimization/many-objective/run/${batchId}`,
        method: 'POST',
        params: {
          algorithm: options.algorithm ?? 'MANSGA_III',
          populationSize: options.populationSize ?? 96,
          generations: options.generations ?? 120,
        },
      })
    : runOptimization(batchId);

export const getOptimizationResult = (batchId: string): Promise<OptimizationResponse> =>
  request<OptimizationResponse>({
    url: `/optimization/result/${batchId}`,
    method: 'GET',
  });

export const fetchOptimizationObjectives = async (): Promise<OptimizationObjective[]> => {
  try {
    const result = await request<OptimizationObjective[] | OptimizationObjectiveMetadata>({
      url: '/optimization/many-objective/objectives',
      method: 'GET',
      showError: false,
    });
    return normalizeObjectives(result);
  } catch {
    return sevenObjectiveFallback;
  }
};
