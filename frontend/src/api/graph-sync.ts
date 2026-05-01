import { request } from '@/utils/request';

export interface SyncResult {
  [key: string]: number;
}

export const syncToNeo4j = async (): Promise<SyncResult> => {
  return request<SyncResult>({
    url: '/graph/sync',
    method: 'POST',
  });
};

export const getGraphVisualization = async (graphVersionId: string): Promise<any> => {
  return request({
    url: `/graph/visualization/${graphVersionId}`,
    method: 'GET',
  });
};
