import { request } from '@/utils/request';

export interface ImportHistoryItem {
  importId: string;
  sourceType: string;
  sourceName: string;
  fileId: string | null;
  targetTable: string;
  importStatus: string;
  totalRows: number;
  successRows: number;
  errorRows: number;
  importedBy: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export const fetchImportHistory = async (): Promise<ImportHistoryItem[]> => {
  return request<ImportHistoryItem[]>({
    url: '/etl/import-jobs',
    method: 'GET',
  });
};
