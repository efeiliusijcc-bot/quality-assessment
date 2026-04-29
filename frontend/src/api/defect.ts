import { mockApiSuccess } from '@/api/_mock';
import { isMockEnabled } from '@/constants/env';
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
  if (!isMockEnabled) {
    return request<DefectSampleResponse[]>({
      url: '/defect/samples',
      method: 'GET',
    });
  }

  const response = await mockApiSuccess<DefectSampleResponse[]>([], 'success');
  return response.data;
};

export const batchDetectDefects = async (items: BatchDetectRequestItem[]): Promise<BatchDetectResponse> => {
  if (!isMockEnabled) {
    return request<BatchDetectResponse>({
      url: '/defect/detect/batch',
      method: 'POST',
      data: items,
    });
  }

  const results: DefectSampleResponse[] = items.map((item, index) => ({
    id: `mock-${index}`,
    name: item.name,
    mediaType: 'image',
    batchNo: item.batchNo,
    imageUrl: item.imageUrl,
    results: [
      { category: '虚焊', level: '严重', confidence: 92.3 + Math.random() * 5, location: '左上焊盘区域' },
    ],
    defects: [
      { label: '虚焊', confidence: 0.92, bbox: [180, 160, 260, 120], level: '严重' },
    ],
    summary: '检测到严重虚焊风险，建议人工复核。',
  }));

  const response = await mockApiSuccess<BatchDetectResponse>(
    { results, total: results.length, message: `批量检测完成，共 ${results.length} 张图像` },
    'batch detection completed',
  );
  return response.data;
};

export interface DefectStatisticsResponse {
  totalSamples: number;
  avgConfidence: number;
  modelVersion: string;
}

export const fetchDefectStatistics = async (): Promise<DefectStatisticsResponse> => {
  if (!isMockEnabled) {
    return request<DefectStatisticsResponse>({
      url: '/defect/statistics',
      method: 'GET',
    });
  }

  const response = await mockApiSuccess<DefectStatisticsResponse>(
    { totalSamples: 0, avgConfidence: 0, modelVersion: 'ResNet-50' },
    'success',
  );
  return response.data;
};
