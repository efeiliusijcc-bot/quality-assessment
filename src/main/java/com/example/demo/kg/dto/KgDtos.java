package com.example.demo.kg.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KgDtos {

    private KgDtos() {}

    // ─── GAT Optimization Response ───

    public record GatNodeEmbeddingEntry(
        String graphId,
        String label,
        List<Double> embedding
    ) {}

    public record GatAttentionEdge(
        String from,
        String to,
        double attentionWeight,
        String relationType
    ) {}

    public record GatImportantItem(
        String name,
        String category,
        double score,
        String reason
    ) {}

    public record GatOptimizationResponse(
        String batchId,
        int nodeCount,
        int edgeCount,
        int attentionHeads,
        int embeddingDim,
        List<GatNodeEmbeddingEntry> nodeEmbeddings,
        List<GatAttentionEdge> attentionEdges,
        List<GatImportantItem> topParameters,
        List<GatImportantItem> topDefects,
        List<GatImportantItem> topProcessSteps,
        String explanationSummary,
        String summary
    ) {}

    // ─── Graph Visualization ───

    public record GraphVisualizationNode(
        String graphId,
        String label,
        String name,
        Map<String, Object> properties
    ) {}

    public record GraphVisualizationEdge(
        String from,
        String to,
        String type,
        double weight
    ) {}

    public record GraphVisualizationResponse(
        List<GraphVisualizationNode> nodes,
        List<GraphVisualizationEdge> edges
    ) {}

    public record GraphAssociationRelation(
        String source,
        String target,
        String relationType,
        String sourceType,
        String targetType,
        double support,
        double confidence,
        double lift,
        String reason
    ) {}

    public record GraphFilterOptions(
        List<String> defects,
        List<String> processSteps,
        List<String> parameters,
        List<String> relationTypes
    ) {}

    public record GraphAnalysisResponse(
        String batchId,
        List<GraphAssociationRelation> ruleRelations,
        List<GraphAssociationRelation> aprioriRelations,
        GraphFilterOptions filterOptions
    ) {}

    public record GraphPathSearchResponse(
        String batchId,
        String source,
        String target,
        List<GraphVisualizationNode> nodes,
        List<GraphVisualizationEdge> edges,
        String summary
    ) {}

    // ─── GraphVersion CRUD ───

    public record GraphVersionResponse(
        UUID graphVersionId,
        String graphName,
        String versionNo,
        String description
    ) {}

    public record CreateGraphVersionRequest(
        @NotBlank String graphName,
        @NotBlank String versionNo,
        String description
    ) {}

    // ─── KgEntity CRUD ───

    public record KgEntityResponse(
        UUID entityId,
        UUID graphVersionId,
        String entityType,
        String entityCode,
        String entityName,
        Map<String, Object> properties
    ) {}

    public record CreateKgEntityRequest(
        @NotBlank String entityType,
        String entityCode,
        @NotBlank String entityName,
        UUID graphVersionId
    ) {}

    // ─── KgRelation CRUD ───

    public record KgRelationResponse(
        UUID relationId,
        UUID graphVersionId,
        UUID sourceEntityId,
        UUID targetEntityId,
        String relationType,
        BigDecimal relationWeight,
        BigDecimal confidence
    ) {}

    public record CreateKgRelationRequest(
        UUID graphVersionId,
        UUID sourceEntityId,
        UUID targetEntityId,
        @NotBlank String relationType
    ) {}

    // ─── GatAnalysisTask CRUD ───

    public record GatAnalysisTaskResponse(
        UUID gatTaskId,
        UUID graphVersionId,
        String modelName,
        String taskStatus,
        String createdAt,
        String finishedAt
    ) {}

    public record CreateGatAnalysisTaskRequest(
        UUID graphVersionId,
        @NotBlank String modelName
    ) {}

    // ─── GatRelationWeight ───

    public record GatRelationWeightResponse(
        UUID weightId,
        UUID gatTaskId,
        UUID relationId,
        BigDecimal attentionWeight,
        Boolean hiddenRelationFlag
    ) {}
}
