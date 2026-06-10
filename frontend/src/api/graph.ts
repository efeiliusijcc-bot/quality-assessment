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

export interface GatImportantItem {
  name: string;
  category: string;
  score: number;
  reason: string;
}

export interface GatOptimizationResponse {
  batchId: string;
  nodeCount: number;
  edgeCount: number;
  attentionHeads: number;
  embeddingDim: number;
  nodeEmbeddings: GatNodeEmbeddingEntry[];
  attentionEdges: GatAttentionEdge[];
  topParameters: GatImportantItem[];
  topDefects: GatImportantItem[];
  topProcessSteps: GatImportantItem[];
  explanationSummary: string;
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

export interface GraphAssociationRelation {
  source: string;
  target: string;
  relationType: string;
  sourceType: string;
  targetType: string;
  support: number;
  confidence: number;
  lift: number;
  reason: string;
}

export interface GraphFilterOptions {
  defects: string[];
  processSteps: string[];
  parameters: string[];
  relationTypes: string[];
}

export interface GraphAnalysisResponse {
  batchId: string;
  ruleRelations: GraphAssociationRelation[];
  aprioriRelations: GraphAssociationRelation[];
  filterOptions: GraphFilterOptions;
}

export interface GraphPathSearchResponse {
  batchId: string;
  source: string;
  target: string;
  nodes: GraphVisualizationNode[];
  edges: GraphVisualizationEdge[];
  summary: string;
}

export const fetchGraphVisualization = async (batchId: string, full = false): Promise<GraphVisualizationResponse> => {
  if (!batchId) {
    return { nodes: [], edges: [] };
  }
  return request<GraphVisualizationResponse>({
    url: `/graph/visualization/${batchId}`,
    method: 'GET',
    params: { full },
  });
};

export const fetchGraphAnalysis = async (batchId: string): Promise<GraphAnalysisResponse> => {
  return request<GraphAnalysisResponse>({
    url: `/graph/analysis/${batchId}`,
    method: 'GET',
  });
};

export const searchGraphPath = async (
  batchId: string,
  source: string,
  target: string,
): Promise<GraphPathSearchResponse> => {
  return request<GraphPathSearchResponse>({
    url: `/graph/path/${batchId}`,
    method: 'GET',
    params: { source, target },
  });
};
