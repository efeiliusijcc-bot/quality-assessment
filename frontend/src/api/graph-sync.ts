import { request } from '@/utils/request';

export interface SyncResult {
  [key: string]: number;
}

export interface GraphSyncTaskStatus {
  batchId: string;
  syncStatus: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | string;
  triggerSource: string;
  startedAt: string | null;
  finishedAt: string | null;
  errorMessage: string | null;
  nodeCount: number | null;
  relationCount: number | null;
}

export const syncToNeo4j = async (): Promise<SyncResult> => {
  return request<SyncResult>({
    url: '/graph/sync',
    method: 'POST',
    timeout: 600000, // 10 minutes - sync can take a while for large datasets
  });
};

export const fetchGraphSyncTasks = async (): Promise<GraphSyncTaskStatus[]> => {
  return request<GraphSyncTaskStatus[]>({
    url: '/graph/sync/tasks',
    method: 'GET',
    timeout: 120000,
  });
};

export const retryGraphSyncTask = async (batchId: string): Promise<GraphSyncTaskStatus> => {
  return request<GraphSyncTaskStatus>({
    url: `/graph/sync/${encodeURIComponent(batchId)}/retry`,
    method: 'POST',
    timeout: 600000,
  });
};

export const getGraphVisualization = async (graphVersionId: string): Promise<any> => {
  return request({
    url: `/graph/visualization/${graphVersionId}`,
    method: 'GET',
  });
};
