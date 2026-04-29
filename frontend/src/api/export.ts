import { mockApiSuccess } from '@/api/_mock';
import { isMockEnabled } from '@/constants/env';
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

const mockRecords: ExportRecord[] = [
  { id: '1', date: '2026-04-09 08:16:20', batchId: 'BATCH-2408', station: 'Assembly Station 07', defectType: 'Normal', confidence: 0.987, status: 'pass' },
  { id: '2', date: '2026-04-09 08:44:12', batchId: 'BATCH-2408', station: 'Soldering Station B2', defectType: 'Virtual Solder', confidence: 0.932, status: 'fail' },
  { id: '3', date: '2026-04-09 09:06:09', batchId: 'BATCH-2408', station: 'Mounting Station A1', defectType: 'Offset', confidence: 0.914, status: 'fail' },
  { id: '4', date: '2026-04-09 09:21:36', batchId: 'BATCH-2409', station: 'Inspection Station C3', defectType: 'Normal', confidence: 0.981, status: 'pass' },
  { id: '5', date: '2026-04-09 09:57:42', batchId: 'BATCH-2409', station: 'Soldering Station B2', defectType: 'Crack', confidence: 0.946, status: 'fail' },
  { id: '6', date: '2026-04-09 10:13:17', batchId: 'BATCH-2410', station: 'Assembly Station 07', defectType: 'Normal', confidence: 0.976, status: 'pass' },
  { id: '7', date: '2026-04-09 10:41:50', batchId: 'BATCH-2410', station: 'Mounting Station A1', defectType: 'Contamination', confidence: 0.889, status: 'fail' },
  { id: '8', date: '2026-04-09 11:09:28', batchId: 'BATCH-2411', station: 'Inspection Station C3', defectType: 'Normal', confidence: 0.991, status: 'pass' },
  { id: '9', date: '2026-04-09 11:34:55', batchId: 'BATCH-2411', station: 'Soldering Station B2', defectType: 'Missing Pin', confidence: 0.958, status: 'fail' },
  { id: '10', date: '2026-04-09 11:58:23', batchId: 'BATCH-2412', station: 'Assembly Station 07', defectType: 'Normal', confidence: 0.983, status: 'pass' },
  { id: '11', date: '2026-04-09 12:20:40', batchId: 'BATCH-2412', station: 'Mounting Station A1', defectType: 'Offset', confidence: 0.908, status: 'fail' },
  { id: '12', date: '2026-04-09 12:47:15', batchId: 'BATCH-2413', station: 'Inspection Station C3', defectType: 'Scratch', confidence: 0.873, status: 'fail' },
];

const expandedMockRecords = Array.from({ length: 4 }, (_, round) =>
  mockRecords.map((item, index) => {
    const hour = 8 + ((round * 3 + index) % 10);
    return {
      ...item,
      id: `${round + 1}-${item.id}`,
      batchId: item.batchId.replace('24', `${24 + round}`),
      date: `2026-04-0${Math.min(9, round + 6)} ${String(hour).padStart(2, '0')}:${String((index * 7) % 60).padStart(2, '0')}:${String((index * 11) % 60).padStart(2, '0')}`,
    };
  }),
).flat();

export const fetchExportRecords = async (params: ExportSearchParams) => {
  if (!isMockEnabled) {
    return request<ExportPageResult>({
      url: '/export/records',
      method: 'GET',
      params,
    });
  }

  const filtered = expandedMockRecords.filter((item) => {
    const matchesBatch = !params.batchId || item.batchId.toLowerCase().includes(params.batchId.toLowerCase());
    const matchesStation = !params.station || item.station === params.station;
    const matchesStatus = !params.status || params.status === 'all' || item.status === params.status;

    let matchesDate = true;
    if (params.dateRange?.length === 2) {
      const [startDate, endDate] = params.dateRange;
      const itemDate = item.date.slice(0, 10);
      matchesDate = itemDate >= startDate && itemDate <= endDate;
    }

    return matchesBatch && matchesStation && matchesStatus && matchesDate;
  });

  const start = (params.page - 1) * params.pageSize;
  const pageList = filtered.slice(start, start + params.pageSize);

  const response = await mockApiSuccess<ExportPageResult>({
    list: pageList,
    total: filtered.length,
  });

  return response.data;
};

export const exportAssessmentExcel = async (params?: Partial<ExportSearchParams>) => {
  if (!isMockEnabled) {
    return request<{ fileName: string }>({
      url: '/export/excel',
      method: 'POST',
      data: params,
    });
  }

  const response = await mockApiSuccess({ fileName: 'assessment.xlsx' }, 'excel export task created');
  return response.data;
};

export const exportAssessmentPdf = async (params?: Partial<ExportSearchParams>) => {
  if (!isMockEnabled) {
    return request<{ fileName: string }>({
      url: '/export/pdf',
      method: 'POST',
      data: params,
    });
  }

  const response = await mockApiSuccess({ fileName: 'assessment-report.pdf' }, 'pdf export task created');
  return response.data;
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

export const downloadAssessmentExcel = async (params?: Partial<ExportSearchParams>) => {
  if (isMockEnabled) {
    triggerBrowserDownload(
      new Blob(['mock excel content'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }),
      'assessment.xlsx',
    );
    return;
  }

  const blob = await request<Blob>({
    url: '/export/excel/download',
    method: 'POST',
    data: params,
    responseType: 'blob',
    showLoading: false,
  });

  triggerBrowserDownload(blob, 'assessment.xlsx');
};

export const downloadAssessmentPdf = async (params?: Partial<ExportSearchParams>) => {
  if (isMockEnabled) {
    triggerBrowserDownload(new Blob(['mock pdf content'], { type: 'application/pdf' }), 'assessment-report.pdf');
    return;
  }

  const blob = await request<Blob>({
    url: '/export/pdf/download',
    method: 'POST',
    data: params,
    responseType: 'blob',
    showLoading: false,
  });

  triggerBrowserDownload(blob, 'assessment-report.pdf');
};
