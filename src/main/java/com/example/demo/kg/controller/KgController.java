package com.example.demo.kg.controller;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.eval.dto.EvalDtos.GraphReasoning;
import com.example.demo.eval.service.GraphReasoningService;
import com.example.demo.kg.dto.KgDtos.CreateGatAnalysisTaskRequest;
import com.example.demo.kg.dto.KgDtos.CreateGraphVersionRequest;
import com.example.demo.kg.dto.KgDtos.CreateKgEntityRequest;
import com.example.demo.kg.dto.KgDtos.CreateKgRelationRequest;
import com.example.demo.kg.dto.KgDtos.GatAnalysisTaskResponse;
import com.example.demo.kg.dto.KgDtos.GatOptimizationResponse;
import com.example.demo.kg.dto.KgDtos.GatRelationWeightResponse;
import com.example.demo.kg.dto.KgDtos.GraphAnalysisResponse;
import com.example.demo.kg.dto.KgDtos.GraphPathSearchResponse;
import com.example.demo.kg.dto.KgDtos.GraphVersionResponse;
import com.example.demo.kg.dto.KgDtos.GraphVisualizationResponse;
import com.example.demo.kg.dto.KgDtos.KgEntityResponse;
import com.example.demo.kg.dto.KgDtos.KgRelationResponse;
import com.example.demo.kg.service.KgService;
import com.example.demo.kg.service.Neo4jSyncService;
import com.example.demo.kg.service.Neo4jSyncService.GraphSyncTaskStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class KgController {

    private final KgService kgService;
    private final Neo4jSyncService neo4jSyncService;
    private final GraphReasoningService graphReasoningService;

    public KgController(KgService kgService,
                        Neo4jSyncService neo4jSyncService,
                        GraphReasoningService graphReasoningService) {
        this.kgService = kgService;
        this.neo4jSyncService = neo4jSyncService;
        this.graphReasoningService = graphReasoningService;
    }

    // ─── Neo4j Sync ───

    @PostMapping("/graph/sync")
    public ApiResponse<Map<String, Integer>> syncToNeo4j() {
        try {
            return ApiResponse.success(neo4jSyncService.syncAll());
        } catch (Exception e) {
            return ApiResponse.failure(500, "Neo4j sync failed: " + e.getMessage());
        }
    }

    @GetMapping("/graph/sync/tasks")
    public ApiResponse<List<GraphSyncTaskStatus>> listSyncTasks() {
        return ApiResponse.success(neo4jSyncService.listBatchSyncTasks());
    }

    @PostMapping("/graph/sync/{batchId}/retry")
    public ApiResponse<GraphSyncTaskStatus> retryBatchSync(@PathVariable String batchId) {
        return ApiResponse.success(neo4jSyncService.retryBatch(batchId));
    }

    // ─── GAT Optimization ───

    @PostMapping("/graph/gat/optimize/{batchId}")
    public ApiResponse<GatOptimizationResponse> runGatOptimization(@PathVariable String batchId) {
        return ApiResponse.success(kgService.runGatOptimization(batchId));
    }

    // ─── Graph Visualization ───

    @GetMapping("/graph/visualization/{batchId}")
    public ApiResponse<GraphVisualizationResponse> getGraphVisualization(
            @PathVariable String batchId,
            @RequestParam(defaultValue = "false") boolean full) {
        return ApiResponse.success(kgService.getGraphVisualization(batchId, full));
    }

    @GetMapping("/graph/analysis/{batchId}")
    public ApiResponse<GraphAnalysisResponse> getGraphAnalysis(@PathVariable String batchId) {
        return ApiResponse.success(kgService.getGraphAnalysis(batchId));
    }

    @GetMapping("/graph/path/{batchId}")
    public ApiResponse<GraphPathSearchResponse> searchGraphPath(
            @PathVariable String batchId,
            @RequestParam String source,
            @RequestParam String target) {
        return ApiResponse.success(kgService.searchGraphPath(batchId, source, target));
    }

    @GetMapping("/graph/reasoning/{batchId}")
    public ApiResponse<GraphReasoning> getGraphReasoning(@PathVariable String batchId) {
        return ApiResponse.success(graphReasoningService.evaluateBatch(batchId));
    }

    // ─── GraphVersion ───

    @PostMapping("/graph/versions")
    public ApiResponse<GraphVersionResponse> createVersion(@Valid @RequestBody CreateGraphVersionRequest request) {
        return ApiResponse.success(kgService.createVersion(request));
    }

    @GetMapping("/graph/versions")
    public ApiResponse<List<GraphVersionResponse>> listVersions() {
        return ApiResponse.success(kgService.listVersions());
    }

    @GetMapping("/graph/versions/{id}")
    public ApiResponse<GraphVersionResponse> getVersion(@PathVariable UUID id) {
        return ApiResponse.success(kgService.getVersion(id));
    }

    // ─── KgEntity ───

    @PostMapping("/graph/entities")
    public ApiResponse<KgEntityResponse> createEntity(@Valid @RequestBody CreateKgEntityRequest request) {
        return ApiResponse.success(kgService.createEntity(request));
    }

    @GetMapping("/graph/entities")
    public ApiResponse<List<KgEntityResponse>> listEntities(
            @RequestParam(required = false) UUID graphVersionId,
            @RequestParam(required = false) String entityType) {
        if (graphVersionId != null) {
            return ApiResponse.success(kgService.listEntitiesByGraphVersion(graphVersionId));
        }
        if (entityType != null) {
            return ApiResponse.success(kgService.listEntitiesByType(entityType));
        }
        return ApiResponse.success(kgService.listAllEntities());
    }

    // ─── KgRelation ───

    @PostMapping("/graph/relations")
    public ApiResponse<KgRelationResponse> createRelation(@Valid @RequestBody CreateKgRelationRequest request) {
        return ApiResponse.success(kgService.createRelation(request));
    }

    @GetMapping("/graph/relations")
    public ApiResponse<List<KgRelationResponse>> listRelations(
            @RequestParam(required = false) UUID graphVersionId) {
        if (graphVersionId != null) {
            return ApiResponse.success(kgService.listRelationsByGraphVersion(graphVersionId));
        }
        return ApiResponse.success(kgService.listAllRelations());
    }

    // ─── GatAnalysisTask ───

    @PostMapping("/graph/gat-tasks")
    public ApiResponse<GatAnalysisTaskResponse> createGatTask(@Valid @RequestBody CreateGatAnalysisTaskRequest request) {
        return ApiResponse.success(kgService.createGatTask(request));
    }

    @GetMapping("/graph/gat-tasks")
    public ApiResponse<List<GatAnalysisTaskResponse>> listGatTasks() {
        return ApiResponse.success(kgService.listAllGatTasks());
    }

    // ─── GatRelationWeight ───

    @GetMapping("/graph/gat-tasks/{id}/weights")
    public ApiResponse<List<GatRelationWeightResponse>> listWeightsByTask(@PathVariable UUID id) {
        return ApiResponse.success(kgService.listWeightsByTask(id));
    }

    // ─── Graph Stats ───

    @GetMapping("/graph/stats")
    public ApiResponse<Map<String, Object>> getGraphStats() {
        try {
            Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("entityCount", kgService.listAllEntities().size());
            stats.put("relationCount", kgService.listAllRelations().size());
            stats.put("versionCount", kgService.listVersions().size());
            return ApiResponse.success(stats);
        } catch (Exception e) {
            return ApiResponse.failure(500, "Failed to get graph stats: " + e.getMessage());
        }
    }
}
