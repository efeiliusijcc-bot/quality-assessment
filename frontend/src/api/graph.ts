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

export interface ExtractedEntity {
  text: string;
  normalizedName?: string;
  entityType: string;
  startOffset?: number;
  endOffset?: number;
  confidence?: number;
}

export interface EntityExtractionResponse {
  rawText?: string;
  tokens?: string[];
  entities: ExtractedEntity[];
  entityCount?: number;
}

export interface AprioriRule {
  antecedents?: string[];
  consequents?: string[];
  antecedentLabel?: string;
  consequentLabel?: string;
  support: number;
  confidence: number;
  lift: number;
  reason?: string;
  relationType?: string;
}

export interface AprioriMiningResponse {
  transactionCount: number;
  frequentItemsets?: unknown[];
  rules?: AprioriRule[];
  savedRelationCount?: number;
  minedRuleCount?: number;
  touchedEntityCount?: number;
  insertedRelationCount?: number;
}

export interface Neo4jRepairResponse {
  repairedCount?: number;
  reversedCount?: number;
  deletedOldDirectionCount?: number;
  summary?: string;
}

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const uuidOrUndefined = (value?: string) => (value && uuidPattern.test(value) ? value : undefined);

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

export const extractKgEntities = async (text: string): Promise<EntityExtractionResponse> =>
  request<EntityExtractionResponse>({
    url: '/graph/nlp/extract',
    method: 'POST',
    data: { text },
  });

export const refreshKgLexicon = async (): Promise<void> =>
  request<void>({
    url: '/graph/nlp/lexicon/refresh',
    method: 'POST',
  });

export const mineAprioriRules = async (batchId?: string): Promise<AprioriMiningResponse> =>
  request<AprioriMiningResponse>({
    url: '/graph/apriori/mine',
    method: 'GET',
    params: { batchId: uuidOrUndefined(batchId) },
  });

export const mineAndSaveAprioriRules = async (batchId?: string): Promise<AprioriMiningResponse> =>
  request<AprioriMiningResponse>({
    url: '/graph/apriori/mine-and-save',
    method: 'POST',
    data: { batchId: uuidOrUndefined(batchId) },
  });

export const repairNeo4jDirections = async (): Promise<Neo4jRepairResponse> =>
  request<Neo4jRepairResponse>({
    url: '/graph/neo4j/repair-directions',
    method: 'POST',
  });
