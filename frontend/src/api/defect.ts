import { request } from '@/utils/request';

export interface DetectionResult {
  category: string;
  level: string;
  confidence: number;
  location: string;
}

export interface DefectBox {
  label: string;
  confidence: number;
  bbox: number[];
  level: string;
}

export interface DefectSampleResponse {
  id: string;
  name: string;
  mediaType: string;
  batchNo: string;
  imageUrl: string;
  results: DetectionResult[];
  defects: DefectBox[];
  summary: string;
}

export interface BatchDetectRequestItem {
  name: string;
  batchNo: string;
  imageUrl: string;
}

export interface BatchDetectResponse {
  results: DefectSampleResponse[];
  total: number;
  message: string;
}

export const fetchDefectSamples = async (): Promise<DefectSampleResponse[]> => {
  return request<DefectSampleResponse[]>({
    url: '/defect/samples',
    method: 'GET',
  });
};

export const batchDetectDefects = async (items: BatchDetectRequestItem[]): Promise<BatchDetectResponse> => {
  return request<BatchDetectResponse>({
    url: '/defect/detect/batch',
    method: 'POST',
    data: items,
  });
};

export interface DefectStatisticsResponse {
  totalSamples: number;
  avgConfidence: number;
  modelVersion: string;
}

export const fetchDefectStatistics = async (): Promise<DefectStatisticsResponse> => {
  return request<DefectStatisticsResponse>({
    url: '/defect/statistics',
    method: 'GET',
  });
};
