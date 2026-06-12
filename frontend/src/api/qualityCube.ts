import { request } from '@/utils/request';

export interface QualityCubeOverview {
  totals?: Record<string, unknown>;
  topStep?: Record<string, unknown> | null;
  topDefect?: Record<string, unknown> | null;
  topEquipment?: Record<string, unknown> | null;
  generatedAt?: string;
}

export interface QualityCubeQuery {
  batchNo?: string;
  stepCode?: string;
  defectCode?: string;
  equipmentCode?: string;
  severityLevel?: string;
  from?: string;
  to?: string;
  limit?: number;
}

export type QualityCubeRow = Record<string, any>;

const cleanParams = (params: QualityCubeQuery) => {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );
};

export const qualityCubeApi = {
  getOverview() {
    return request<QualityCubeOverview>({
      url: '/quality-cube/overview',
      method: 'GET',
    });
  },
  getByBatchStep(params: QualityCubeQuery = {}) {
    return request<QualityCubeRow[]>({
      url: '/quality-cube/by-batch-step',
      method: 'GET',
      params: cleanParams(params),
    });
  },
  getByStepType(params: QualityCubeQuery = {}) {
    return request<QualityCubeRow[]>({
      url: '/quality-cube/by-step-type',
      method: 'GET',
      params: cleanParams(params),
    });
  },
  getByEquipment(params: QualityCubeQuery = {}) {
    return request<QualityCubeRow[]>({
      url: '/quality-cube/by-equipment',
      method: 'GET',
      params: cleanParams(params),
    });
  },
  getByTime(params: QualityCubeQuery = {}) {
    return request<QualityCubeRow[]>({
      url: '/quality-cube/by-time',
      method: 'GET',
      params: cleanParams(params),
    });
  },
  getBySeverity(params: QualityCubeQuery = {}) {
    return request<QualityCubeRow[]>({
      url: '/quality-cube/by-severity',
      method: 'GET',
      params: cleanParams(params),
    });
  },
  getMetadata(dataDomain = 'defect_cube') {
    return request<QualityCubeRow[]>({
      url: '/quality-cube/metadata',
      method: 'GET',
      params: { dataDomain },
    });
  },
  refresh() {
    return request<Record<string, unknown>>({
      url: '/quality-cube/refresh',
      method: 'POST',
    });
  },
};
