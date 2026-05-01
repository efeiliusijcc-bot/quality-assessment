import { request } from '@/utils/request';

export interface GatNodeEmbeddingEntry {
  graphId: string;
  label: string;
  embedding: number[];
}

export interface GatAttentionEdge {
  from: string;
  to: string;
  attentionWeight: number;
  relationType: string;
}

export interface GatOptimizationResponse {
  batchId: string;
  nodeCount: number;
  edgeCount: number;
  attentionHeads: number;
  embeddingDim: number;
  nodeEmbeddings: GatNodeEmbeddingEntry[];
  attentionEdges: GatAttentionEdge[];
  summary: string;
}

export const runGatOptimization = (batchId: string): Promise<GatOptimizationResponse> => {
  if (!batchId) {
    return Promise.reject(new Error('batchId is required'));
  }
  return request<GatOptimizationResponse>({
    url: `/graph/gat/optimize/${batchId}`,
    method: 'POST',
  });
};

export interface GraphVisualizationNode {
  graphId: string;
  label: string;
  name: string;
  properties: Record<string, unknown>;
}

export interface GraphVisualizationEdge {
  from: string;
  to: string;
  type: string;
  weight: number;
}

export interface GraphVisualizationResponse {
  nodes: GraphVisualizationNode[];
  edges: GraphVisualizationEdge[];
}

export const fetchGraphVisualization = async (batchId: string): Promise<GraphVisualizationResponse> => {
  if (!batchId) {
    return { nodes: [], edges: [] };
  }
  return request<GraphVisualizationResponse>({
    url: `/graph/visualization/${batchId}`,
    method: 'GET',
  });
};
