import { mockApiSuccess } from '@/api/_mock';
import { isMockEnabled } from '@/constants/env';
import { request } from '@/utils/request';

export interface OnlineUploadPayload {
  station: string;
  batchNo: string;
  deviceId: string;
  frequency: string;
  mapping: string;
}

export interface OnlineUploadResult extends OnlineUploadPayload {
  taskId: string;
}

export interface ManufacturingImportSummary {
  fileName: string;
  processSettingCount: number;
  equipmentOperationCount: number;
  qualityDefectCount: number;
}

export interface ManualRecordPayload {
  batchNo: string;
  station: string;
  componentId: string;
  temperature: number;
  pressure: number;
  beltSpeed: number;
  o2Ppm: number;
  humidity: number;
  currentValue: number;
  defectType: string;
  defectLevel: string;
  defectConfidence: number;
}

export interface ManualRecordResult {
  id: string;
  message: string;
}

export const submitOnlineUploadTask = async (payload: OnlineUploadPayload) => {
  if (!isMockEnabled) {
    return request<OnlineUploadResult>({
      url: '/upload/online',
      method: 'POST',
      data: payload,
      showLoading: false,
    });
  }

  const response = await mockApiSuccess<OnlineUploadResult>(
    {
      taskId: `UPLOAD-${Date.now()}`,
      ...payload,
    },
    '上传任务已创建',
  );

  return response.data;
};

export const importManufacturingExcel = async (file: File) => {
  if (!isMockEnabled) {
    const formData = new FormData();
    formData.append('file', file);

    return request<ManufacturingImportSummary>({
      url: '/upload/manufacturing-data/import',
      method: 'POST',
      data: formData,
      showLoading: false,
      timeout: 300000,
    });
  }

  const response = await mockApiSuccess<ManufacturingImportSummary>({
    fileName: file.name,
    processSettingCount: 1200,
    equipmentOperationCount: 4800,
    qualityDefectCount: 320,
  }, 'manufacturing data imported');

  return response.data;
};

export const submitManualRecord = async (payload: ManualRecordPayload) => {
  if (!isMockEnabled) {
    return request<ManualRecordResult>({
      url: '/upload/manual',
      method: 'POST',
      data: payload,
      showLoading: false,
    });
  }

  const response = await mockApiSuccess<ManualRecordResult>(
    { id: `MANUAL-${Date.now()}`, message: '逐条录入成功' },
    'manual record saved',
  );
  return response.data;
};

export interface UploadStatisticsResponse {
  totalTasks: number;
  latestSyncTime: string;
}

export const fetchUploadStatistics = async (): Promise<UploadStatisticsResponse> => {
  if (!isMockEnabled) {
    return request<UploadStatisticsResponse>({
      url: '/upload/statistics',
      method: 'GET',
    });
  }

  const response = await mockApiSuccess<UploadStatisticsResponse>(
    { totalTasks: 0, latestSyncTime: '--' },
    'success',
  );
  return response.data;
};
