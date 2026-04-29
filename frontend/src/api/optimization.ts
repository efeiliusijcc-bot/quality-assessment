import { request } from '@/utils/request';

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

export const runOptimization = (batchId: string): Promise<OptimizationResponse> =>
  request<OptimizationResponse>({
    url: `/optimization/run/${batchId}`,
    method: 'POST',
  });

export const getOptimizationResult = (batchId: string): Promise<OptimizationResponse> =>
  request<OptimizationResponse>({
    url: `/optimization/result/${batchId}`,
    method: 'GET',
  });
