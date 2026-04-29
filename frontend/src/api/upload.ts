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
  return request<OnlineUploadResult>({
    url: '/upload/online',
    method: 'POST',
    data: payload,
    showLoading: false,
  });
};

export const importManufacturingExcel = async (file: File) => {
  const formData = new FormData();
  formData.append('file', file);

  return request<ManufacturingImportSummary>({
    url: '/upload/manufacturing-data/import',
    method: 'POST',
    data: formData,
    showLoading: false,
    timeout: 300000,
  });
};

export const submitManualRecord = async (payload: ManualRecordPayload) => {
  return request<ManualRecordResult>({
    url: '/upload/manual',
    method: 'POST',
    data: payload,
    showLoading: false,
  });
};

export interface UploadStatisticsResponse {
  totalTasks: number;
  latestSyncTime: string;
}

export const fetchUploadStatistics = async (): Promise<UploadStatisticsResponse> => {
  return request<UploadStatisticsResponse>({
    url: '/upload/statistics',
    method: 'GET',
  });
};
