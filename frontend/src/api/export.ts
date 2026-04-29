import { request } from '@/utils/request';

export interface ExportSearchParams {
  batchId?: string;
  station?: string;
  status?: 'all' | 'pass' | 'fail';
  dateRange?: [string, string] | [];
  page: number;
  pageSize: number;
}

export interface ExportRecord {
  id: string;
  date: string;
  batchId: string;
  station: string;
  defectType: string;
  confidence: number;
  status: 'pass' | 'fail';
}

export interface ExportPageResult {
  list: ExportRecord[];
  total: number;
}

export const fetchExportRecords = async (params: ExportSearchParams): Promise<ExportPageResult> => {
  return request<ExportPageResult>({
    url: '/export/records',
    method: 'GET',
    params,
  });
};

export const exportAssessmentExcel = async (params?: Partial<ExportSearchParams>): Promise<{ fileName: string }> => {
  return request<{ fileName: string }>({
    url: '/export/excel',
    method: 'POST',
    data: params,
  });
};

export const exportAssessmentPdf = async (params?: Partial<ExportSearchParams>): Promise<{ fileName: string }> => {
  return request<{ fileName: string }>({
    url: '/export/pdf',
    method: 'POST',
    data: params,
  });
};

const triggerBrowserDownload = (blob: Blob, fileName: string) => {
  const objectUrl = window.URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.URL.revokeObjectURL(objectUrl);
};

export const downloadAssessmentExcel = async (params?: Partial<ExportSearchParams>): Promise<void> => {
  const blob = await request<Blob>({
    url: '/export/excel/download',
    method: 'POST',
    data: params,
    responseType: 'blob',
    showLoading: false,
  });

  triggerBrowserDownload(blob, 'assessment.xlsx');
};

export const downloadAssessmentPdf = async (params?: Partial<ExportSearchParams>): Promise<void> => {
  const blob = await request<Blob>({
    url: '/export/pdf/download',
    method: 'POST',
    data: params,
    responseType: 'blob',
    showLoading: false,
  });

  triggerBrowserDownload(blob, 'assessment-report.pdf');
};
