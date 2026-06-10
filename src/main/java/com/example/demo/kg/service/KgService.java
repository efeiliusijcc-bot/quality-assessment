package com.example.demo.kg.service;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.graph.gat.GraphAttentionNetwork;
import com.example.demo.kg.domain.GatAnalysisTask;
import com.example.demo.kg.domain.GatRelationWeight;
import com.example.demo.kg.domain.GraphVersion;
import com.example.demo.kg.domain.KgEntity;
import com.example.demo.kg.domain.KgRelation;
import com.example.demo.kg.dto.KgDtos.CreateGatAnalysisTaskRequest;
import com.example.demo.kg.dto.KgDtos.CreateGraphVersionRequest;
import com.example.demo.kg.dto.KgDtos.CreateKgEntityRequest;
import com.example.demo.kg.dto.KgDtos.CreateKgRelationRequest;
import com.example.demo.kg.dto.KgDtos.GatAnalysisTaskResponse;
import com.example.demo.kg.dto.KgDtos.GatAttentionEdge;
import com.example.demo.kg.dto.KgDtos.GatImportantItem;
import com.example.demo.kg.dto.KgDtos.GatNodeEmbeddingEntry;
import com.example.demo.kg.dto.KgDtos.GatOptimizationResponse;
import com.example.demo.kg.dto.KgDtos.GatRelationWeightResponse;
import com.example.demo.kg.dto.KgDtos.GraphAnalysisResponse;
import com.example.demo.kg.dto.KgDtos.GraphAssociationRelation;
import com.example.demo.kg.dto.KgDtos.GraphFilterOptions;
import com.example.demo.kg.dto.KgDtos.GraphPathSearchResponse;
import com.example.demo.kg.dto.KgDtos.GraphVersionResponse;
import com.example.demo.kg.dto.KgDtos.GraphVisualizationEdge;
import com.example.demo.kg.dto.KgDtos.GraphVisualizationNode;
import com.example.demo.kg.dto.KgDtos.GraphVisualizationResponse;
import com.example.demo.kg.dto.KgDtos.KgEntityResponse;
import com.example.demo.kg.dto.KgDtos.KgRelationResponse;
import com.example.demo.kg.repository.GatAnalysisTaskRepository;
import com.example.demo.kg.repository.GatRelationWeightRepository;
import com.example.demo.kg.repository.GraphVersionRepository;
import com.example.demo.kg.repository.KgEntityRepository;
import com.example.demo.kg.repository.KgRelationRepository;
import com.example.demo.prod.domain.ProductionBatch;
import com.example.demo.prod.repository.ProductionBatchRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KgService {

    private static final Logger log = LoggerFactory.getLogger(KgService.class);
    private static final int MAX_GAT_NODES = 160;

    private final GraphVersionRepository graphVersionRepository;
    private final KgEntityRepository kgEntityRepository;
    private final KgRelationRepository kgRelationRepository;
    private final GatAnalysisTaskRepository gatAnalysisTaskRepository;
    private final GatRelationWeightRepository gatRelationWeightRepository;
    private final ProductionBatchRepository productionBatchRepository;
    private final Driver neo4jDriver;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public KgService(GraphVersionRepository graphVersionRepository,
                     KgEntityRepository kgEntityRepository,
                     KgRelationRepository kgRelationRepository,
                     GatAnalysisTaskRepository gatAnalysisTaskRepository,
                     GatRelationWeightRepository gatRelationWeightRepository,
                     ProductionBatchRepository productionBatchRepository,
                     Driver neo4jDriver,
                     JdbcTemplate jdbcTemplate,
                     ObjectMapper objectMapper) {
        this.graphVersionRepository = graphVersionRepository;
        this.kgEntityRepository = kgEntityRepository;
        this.kgRelationRepository = kgRelationRepository;
        this.gatAnalysisTaskRepository = gatAnalysisTaskRepository;
        this.gatRelationWeightRepository = gatRelationWeightRepository;
        this.productionBatchRepository = productionBatchRepository;
        this.neo4jDriver = neo4jDriver;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════
    // GraphVersion CRUD
    // ═══════════════════════════════════════════════════════

    @Transactional
    public GraphVersionResponse createVersion(CreateGraphVersionRequest request) {
        if (graphVersionRepository.findByGraphNameAndVersionNo(request.graphName(), request.versionNo()).isPresent()) {
            throw new BusinessException(400, "graphName + versionNo already exists");
        }
        GraphVersion entity = new GraphVersion(request.graphName(), request.versionNo());
        // description is set via reflection or we need a setter - use a helper
        setDescription(entity, request.description());
        graphVersionRepository.save(entity);
        return toVersionResponse(entity);
    }

    public GraphVersionResponse getVersion(UUID id) {
        return toVersionResponse(requireGraphVersion(id));
    }

    public List<GraphVersionResponse> listVersions() {
        return graphVersionRepository.findAll().stream().map(this::toVersionResponse).toList();
    }

    public Optional<GraphVersionResponse> getLatest() {
        return graphVersionRepository.findTop1ByOrderByCreatedAtDesc().stream()
                .map(this::toVersionResponse)
                .findFirst();
    }

    // ═══════════════════════════════════════════════════════
    // KgEntity CRUD
    // ═══════════════════════════════════════════════════════

    @Transactional
    public KgEntityResponse createEntity(CreateKgEntityRequest request) {
        KgEntity entity = new KgEntity(request.entityType(), request.entityName());
        entity.setGraphVersionId(request.graphVersionId());
        entity.setEntityCode(request.entityCode());
        kgEntityRepository.save(entity);
        return toEntityResponse(entity);
    }

    public KgEntityResponse getEntityById(UUID id) {
        return toEntityResponse(requireKgEntity(id));
    }

    public List<KgEntityResponse> listAllEntities() {
        return kgEntityRepository.findAll().stream().map(this::toEntityResponse).toList();
    }

    public List<KgEntityResponse> listEntitiesByGraphVersion(UUID graphVersionId) {
        return kgEntityRepository.findByGraphVersionId(graphVersionId).stream()
                .map(this::toEntityResponse).toList();
    }

    public List<KgEntityResponse> listEntitiesByType(String entityType) {
        return kgEntityRepository.findByEntityType(entityType).stream()
                .map(this::toEntityResponse).toList();
    }

    // ═══════════════════════════════════════════════════════
    // KgRelation CRUD
    // ═══════════════════════════════════════════════════════

    @Transactional
    public KgRelationResponse createRelation(CreateKgRelationRequest request) {
        KgRelation relation = new KgRelation(request.sourceEntityId(), request.targetEntityId(), request.relationType());
        relation.setGraphVersionId(request.graphVersionId());
        kgRelationRepository.save(relation);
        return toRelationResponse(relation);
    }

    public List<KgRelationResponse> listAllRelations() {
        return kgRelationRepository.findAll().stream().map(this::toRelationResponse).toList();
    }

    public List<KgRelationResponse> listRelationsByGraphVersion(UUID graphVersionId) {
        return kgRelationRepository.findByGraphVersionId(graphVersionId).stream()
                .map(this::toRelationResponse).toList();
    }

    public List<KgRelationResponse> listRelationsBySource(UUID sourceEntityId) {
        return kgRelationRepository.findBySourceEntityId(sourceEntityId).stream()
                .map(this::toRelationResponse).toList();
    }

    public List<KgRelationResponse> listRelationsByTarget(UUID targetEntityId) {
        return kgRelationRepository.findByTargetEntityId(targetEntityId).stream()
                .map(this::toRelationResponse).toList();
    }

    // ═══════════════════════════════════════════════════════
    // GatAnalysisTask CRUD
    // ═══════════════════════════════════════════════════════

    @Transactional
    public GatAnalysisTaskResponse createGatTask(CreateGatAnalysisTaskRequest request) {
        GatAnalysisTask task = new GatAnalysisTask(request.graphVersionId());
        task.setModelName(request.modelName());
        gatAnalysisTaskRepository.save(task);
        return toTaskResponse(task);
    }

    public GatAnalysisTaskResponse getGatTaskById(UUID id) {
        return toTaskResponse(requireGatTask(id));
    }

    public List<GatAnalysisTaskResponse> listAllGatTasks() {
        return gatAnalysisTaskRepository.findAll().stream().map(this::toTaskResponse).toList();
    }

    // ═══════════════════════════════════════════════════════
    // GatRelationWeight
    // ═══════════════════════════════════════════════════════

    @Transactional
    public GatRelationWeightResponse createGatWeight(UUID gatTaskId, UUID relationId, BigDecimal attentionWeight) {
        GatRelationWeight weight = new GatRelationWeight(gatTaskId, relationId, attentionWeight);
        gatRelationWeightRepository.save(weight);
        return toWeightResponse(weight);
    }

    public List<GatRelationWeightResponse> listWeightsByTask(UUID gatTaskId) {
        return gatRelationWeightRepository.findByGatTaskId(gatTaskId).stream()
                .map(this::toWeightResponse).toList();
    }

    // ═══════════════════════════════════════════════════════
    // Core: GAT Optimization
    // ═══════════════════════════════════════════════════════

    @Transactional
    public GatOptimizationResponse runGatOptimization(String batchId) {
        UUID graphVersionId;
        try {
            graphVersionId = UUID.fromString(batchId);
        } catch (IllegalArgumentException e) {
            Optional<ProductionBatch> batch = productionBatchRepository.findByBatchNo(batchId);
            if (batch.isEmpty()) {
                throw new BusinessException(404, "No batch found for batchId=" + batchId);
            }
            GraphVisualizationResponse graph = buildGatGraphFromPostgres(batchId);
            return runGatOnVisualizationGraph(batchId, batch.get().getBatchId(), graph);
        }

        List<KgEntity> entities = kgEntityRepository.findByGraphVersionId(graphVersionId);
        List<KgRelation> relations = kgRelationRepository.findByGraphVersionId(graphVersionId);

        if (entities.isEmpty()) {
            Optional<ProductionBatch> batch = productionBatchRepository.findById(graphVersionId);
            if (batch.isPresent()) {
                GraphVisualizationResponse graph = buildGatGraphFromPostgres(batch.get().getBatchNo());
                return runGatOnVisualizationGraph(batch.get().getBatchNo(), batch.get().getBatchId(), graph);
            }
        }

        // 2. Try to enrich from Neo4j
        try {
            enrichFromNeo4j(batchId, entities, relations);
        } catch (Exception e) {
            log.warn("Neo4j enrichment failed for batchId={}, using PostgreSQL data only: {}", batchId, e.getMessage());
        }

        if (entities.isEmpty()) {
            throw new BusinessException(404, "No entities found for batchId=" + batchId);
        }

        // 3. Build node index and adjacency matrix
        Map<String, Integer> nodeIndex = new LinkedHashMap<>();
        List<String> nodeLabels = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            KgEntity ent = entities.get(i);
            String key = ent.getEntityId().toString();
            nodeIndex.put(key, i);
            nodeLabels.add(ent.getEntityName() != null ? ent.getEntityName() : ent.getEntityType());
        }

        int n = entities.size();
        int[][] adjacency = new int[n][n];
        for (KgRelation rel : relations) {
            Integer src = nodeIndex.get(rel.getSourceEntityId().toString());
            Integer tgt = nodeIndex.get(rel.getTargetEntityId().toString());
            if (src != null && tgt != null) {
                adjacency[src][tgt] = 1;
                adjacency[tgt][src] = 1;
            }
        }

        // 4. Build feature matrix (one-hot entity type encoding)
        List<String> distinctTypes = entities.stream()
                .map(KgEntity::getEntityType).distinct().toList();
        Map<String, Integer> typeIndex = new HashMap<>();
        for (int i = 0; i < distinctTypes.size(); i++) {
            typeIndex.put(distinctTypes.get(i), i);
        }
        int featureDim = Math.max(distinctTypes.size(), 1);
        double[][] nodeFeatures = new double[n][featureDim];
        for (int i = 0; i < n; i++) {
            Integer ti = typeIndex.get(entities.get(i).getEntityType());
            if (ti != null) {
                nodeFeatures[i][ti] = 1.0;
            }
        }

        // 5. Run GAT
        GraphAttentionNetwork gat = GraphAttentionNetwork.createDefault(featureDim);
        double[][] embeddings = gat.forward(nodeFeatures, adjacency);
        double[][] attentionMatrix = gat.computeAttentionWeights(nodeFeatures, adjacency);

        // 6. Create GatAnalysisTask
        GatAnalysisTask task = new GatAnalysisTask(graphVersionId);
        task.setModelName("GAT");
        task.setModelVersion("batch-subgraph-v1");
        task.setInputScope("{\"scope\":\"graphVersion\",\"batchId\":\"" + batchId + "\"}");
        task.setTaskStatus("COMPLETED");
        task.setFinishedAt(Instant.now());
        gatAnalysisTaskRepository.save(task);

        // 7. Build attention edges and save GatRelationWeight
        List<GatAttentionEdge> attentionEdges = new ArrayList<>();
        Map<String, KgRelation> relationLookup = new HashMap<>();
        for (KgRelation rel : relations) {
            String key = rel.getSourceEntityId() + "->" + rel.getTargetEntityId();
            relationLookup.put(key, rel);
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double w = (attentionMatrix[i][j] + attentionMatrix[j][i]) / 2.0;
                if (w > 0.01) {
                    String fromLabel = nodeLabels.get(i);
                    String toLabel = nodeLabels.get(j);
                    String relationType = "UNKNOWN";

                    KgRelation matchedRel = relationLookup.get(
                            entities.get(i).getEntityId() + "->" + entities.get(j).getEntityId());
                    if (matchedRel == null) {
                        matchedRel = relationLookup.get(
                                entities.get(j).getEntityId() + "->" + entities.get(i).getEntityId());
                    }
                    if (matchedRel != null) {
                        relationType = matchedRel.getRelationType();
                        // Save weight
                        GatRelationWeight grw = new GatRelationWeight(
                                task.getGatTaskId(), matchedRel.getRelationId(), BigDecimal.valueOf(w));
                        gatRelationWeightRepository.save(grw);
                    }

                    attentionEdges.add(new GatAttentionEdge(fromLabel, toLabel, w, relationType));
                }
            }
        }

        // 8. Build node embeddings list
        List<GatNodeEmbeddingEntry> nodeEmbeddings = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<Double> emb = new ArrayList<>();
            for (double v : embeddings[i]) {
                emb.add(Math.round(v * 10000.0) / 10000.0);
            }
            nodeEmbeddings.add(new GatNodeEmbeddingEntry(
                    entities.get(i).getEntityId().toString(),
                    entities.get(i).getEntityType(),
                    emb));
        }

        int attentionHeads = gat.getLayers().get(0).getHeadCount();
        int embeddingDim = gat.getEmbeddingDim();
        String summary = String.format("GAT optimization completed: %d nodes, %d edges, %d attention heads, embedding dim %d",
                n, relations.size(), attentionHeads, embeddingDim);

        return new GatOptimizationResponse(
                batchId, n, relations.size(), attentionHeads, embeddingDim,
                nodeEmbeddings,
                attentionEdges,
                summarizeImportantItems(attentionEdges, Set.of("ParameterDef", "ParameterValue", "ProcessParameter"), "parameter"),
                summarizeImportantItems(attentionEdges, Set.of("DefectType", "DefectRecord", "Defect"), "defect"),
                summarizeImportantItems(attentionEdges, Set.of("ProcessStep"), "process-step"),
                buildGatExplanation(attentionEdges),
                summary);
    }

    private GatOptimizationResponse runGatOnVisualizationGraph(
            String batchId,
            UUID taskScopeId,
            GraphVisualizationResponse graph) {
        GraphVisualizationResponse gatGraph = compactGraphForGat(graph);
        List<GraphVisualizationNode> nodes = gatGraph.nodes();
        List<GraphVisualizationEdge> edges = gatGraph.edges();
        if (nodes.isEmpty()) {
            throw new BusinessException(404, "No graph nodes found for batchId=" + batchId);
        }

        Map<String, Integer> nodeIndex = new LinkedHashMap<>();
        List<String> nodeLabels = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            GraphVisualizationNode node = nodes.get(i);
            nodeIndex.put(node.graphId(), i);
            nodeLabels.add(node.name() != null && !node.name().isBlank() ? node.name() : node.label());
        }

        int n = nodes.size();
        int[][] adjacency = new int[n][n];
        for (GraphVisualizationEdge edge : edges) {
            Integer src = nodeIndex.get(edge.from());
            Integer tgt = nodeIndex.get(edge.to());
            if (src != null && tgt != null) {
                adjacency[src][tgt] = 1;
                adjacency[tgt][src] = 1;
            }
        }

        List<String> distinctTypes = nodes.stream().map(GraphVisualizationNode::label).distinct().toList();
        Map<String, Integer> typeIndex = new HashMap<>();
        for (int i = 0; i < distinctTypes.size(); i++) {
            typeIndex.put(distinctTypes.get(i), i);
        }
        int featureDim = Math.max(distinctTypes.size(), 1);
        double[][] nodeFeatures = new double[n][featureDim];
        for (int i = 0; i < n; i++) {
            Integer ti = typeIndex.get(nodes.get(i).label());
            if (ti != null) {
                nodeFeatures[i][ti] = 1.0;
            }
        }

        GraphAttentionNetwork gat = GraphAttentionNetwork.createDefault(featureDim);
        double[][] embeddings = gat.forward(nodeFeatures, adjacency);
        double[][] attentionMatrix = gat.computeAttentionWeights(nodeFeatures, adjacency);

        GatAnalysisTask task = new GatAnalysisTask(null);
        task.setModelName("GAT-BATCH-SUBGRAPH");
        task.setModelVersion("batch-subgraph-v1");
        task.setInputScope("{\"scope\":\"batch\",\"batchId\":\"" + batchId + "\"}");
        task.setTaskStatus("COMPLETED");
        task.setFinishedAt(Instant.now());
        gatAnalysisTaskRepository.save(task);

        List<GatAttentionEdge> attentionEdges = new ArrayList<>();
        Set<String> attentionEdgeKeys = new LinkedHashSet<>();
        for (GraphVisualizationEdge edge : edges) {
            Integer src = nodeIndex.get(edge.from());
            Integer tgt = nodeIndex.get(edge.to());
            if (src == null || tgt == null) {
                continue;
            }
            double w = (attentionMatrix[src][tgt] + attentionMatrix[tgt][src]) / 2.0;
            if (w > 0.0) {
                String dedupeKey = nodeLabels.get(src) + "|" + nodeLabels.get(tgt) + "|" + edge.type();
                if (!attentionEdgeKeys.add(dedupeKey)) {
                    continue;
                }
                attentionEdges.add(new GatAttentionEdge(
                        nodeLabels.get(src),
                        nodeLabels.get(tgt),
                        Math.round(w * 1_000_000.0) / 1_000_000.0,
                        edge.type()));
            }
        }
        attentionEdges = attentionEdges.stream()
                .sorted((a, b) -> Double.compare(b.attentionWeight(), a.attentionWeight()))
                .limit(80)
                .toList();

        List<GatNodeEmbeddingEntry> nodeEmbeddings = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<Double> emb = new ArrayList<>();
            for (double v : embeddings[i]) {
                emb.add(Math.round(v * 10000.0) / 10000.0);
            }
            nodeEmbeddings.add(new GatNodeEmbeddingEntry(nodes.get(i).graphId(), nodes.get(i).label(), emb));
        }

        int attentionHeads = gat.getLayers().get(0).getHeadCount();
        int embeddingDim = gat.getEmbeddingDim();
        String summary = String.format(
                "GAT batch-subgraph analysis completed for %s: %d nodes, %d relationships, %d attention heads, embedding dim %d. Top attention edges indicate the strongest graph-neighborhood influences.",
                batchId, n, edges.size(), attentionHeads, embeddingDim);

        return new GatOptimizationResponse(
                batchId, n, edges.size(), attentionHeads, embeddingDim,
                nodeEmbeddings,
                attentionEdges,
                summarizeImportantItems(attentionEdges, Set.of("ParameterDef", "ParameterValue", "ProcessParameter"), "parameter"),
                summarizeImportantItems(attentionEdges, Set.of("DefectType", "DefectRecord", "Defect"), "defect"),
                summarizeImportantItems(attentionEdges, Set.of("ProcessStep"), "process-step"),
                buildGatExplanation(attentionEdges),
                summary);
    }

    private List<GatImportantItem> summarizeImportantItems(
            List<GatAttentionEdge> attentionEdges,
            Set<String> labelHints,
            String category) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (GatAttentionEdge edge : attentionEdges) {
            String candidate = candidateForGatCategory(edge, category);
            if (candidate != null && !candidate.isBlank()) {
                scores.merge(candidate, edge.attentionWeight(), Double::sum);
            }
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(8)
                .map(entry -> new GatImportantItem(
                        entry.getKey(),
                        category,
                        Math.round(entry.getValue() * 1_000_000.0) / 1_000_000.0,
                        buildImportantItemReason(category, entry.getKey(), entry.getValue())))
                .toList();
    }

    private String candidateForGatCategory(GatAttentionEdge edge, String category) {
        String relationType = edge.relationType();
        if (relationType == null) {
            return null;
        }
        return switch (category) {
            case "parameter" -> {
                if ("HAS_PARAMETER".equals(relationType)) {
                    yield edge.to();
                }
                if ("ASSOCIATED_WITH_DEFECT".equals(relationType)) {
                    yield edge.from();
                }
                yield null;
            }
            case "defect" -> {
                if ("PRODUCES_DEFECT_RISK".equals(relationType)
                        || "ASSOCIATED_WITH_DEFECT".equals(relationType)
                        || "FOUND_DEFECT".equals(relationType)) {
                    yield edge.to();
                }
                yield null;
            }
            case "process-step" -> {
                if ("HAS_STEP".equals(relationType)) {
                    yield edge.to();
                }
                if ("PRODUCES_DEFECT_RISK".equals(relationType) || "HAS_PARAMETER".equals(relationType)) {
                    yield edge.from();
                }
                yield null;
            }
            default -> null;
        };
    }

    private String buildImportantItemReason(String category, String name, double score) {
        double rounded = Math.round(score * 10_000.0) / 10_000.0;
        return switch (category) {
            case "parameter" -> name + " has high attention through parameter-defect or step-parameter relations, score=" + rounded;
            case "defect" -> name + " receives strong attention from process or parameter relations, score=" + rounded;
            case "process-step" -> name + " is a high-impact process node in the current batch subgraph, score=" + rounded;
            default -> name + " has attention score=" + rounded;
        };
    }

    private String buildGatExplanation(List<GatAttentionEdge> attentionEdges) {
        if (attentionEdges.isEmpty()) {
            return "GAT did not find high-attention relations in the current batch graph.";
        }
        GatAttentionEdge top = attentionEdges.get(0);
        return "GAT identified the strongest relation as " + top.from() + " -> " + top.to()
                + " (" + top.relationType() + "), attention=" + top.attentionWeight()
                + ". Use high-attention relations as priority evidence for parameter review and defect prevention.";
    }

    private GraphVisualizationResponse buildGatGraphFromPostgres(String batchId) {
        Map<String, GraphVisualizationNode> nodes = new LinkedHashMap<>();
        List<GraphVisualizationEdge> edges = new ArrayList<>();

        List<Map<String, Object>> batches = jdbcTemplate.queryForList("""
                SELECT batch_id::text AS batch_id, batch_no
                FROM prod.production_batch
                WHERE batch_no = ? OR batch_id::text = ?
                LIMIT 1
                """, batchId, batchId);
        if (batches.isEmpty()) {
            return new GraphVisualizationResponse(List.of(), List.of());
        }

        String batchNodeId = "ProductionBatch:" + batches.get(0).get("batch_id");
        String batchNo = String.valueOf(batches.get(0).get("batch_no"));
        nodes.put(batchNodeId, new GraphVisualizationNode(
                batchNodeId,
                "ProductionBatch",
                batchNo,
                Map.of("batchId", batches.get(0).get("batch_id"), "batchNo", batchNo)));

        List<Map<String, Object>> steps = jdbcTemplate.queryForList("""
                SELECT DISTINCT ps.step_id::text AS step_id, ps.step_code, ps.step_name
                FROM prod.production_batch b
                JOIN prod.process_run r ON r.batch_id = b.batch_id
                JOIN core.process_step ps ON ps.step_id = r.step_id
                WHERE b.batch_no = ? OR b.batch_id::text = ?
                ORDER BY ps.step_code
                """, batchId, batchId);
        for (Map<String, Object> row : steps) {
            String id = "ProcessStep:" + row.get("step_id");
            nodes.put(id, new GraphVisualizationNode(
                    id,
                    "ProcessStep",
                    String.valueOf(row.get("step_code")),
                    Map.of("stepId", row.get("step_id"), "stepName", row.get("step_name"))));
            edges.add(new GraphVisualizationEdge(batchNodeId, id, "HAS_STEP", 1.0));
        }

        List<Map<String, Object>> params = jdbcTemplate.queryForList("""
                SELECT DISTINCT pd.param_id::text AS param_id, pd.step_id::text AS step_id, pd.param_name, pd.param_code
                FROM prod.production_batch b
                JOIN prod.process_run r ON r.batch_id = b.batch_id
                JOIN prod.parameter_value pv ON pv.run_id = r.run_id
                JOIN core.parameter_def pd ON pd.param_id = pv.param_id
                WHERE b.batch_no = ? OR b.batch_id::text = ?
                ORDER BY pd.param_name
                """, batchId, batchId);
        for (Map<String, Object> row : params) {
            String id = "ParameterDef:" + row.get("param_id");
            nodes.put(id, new GraphVisualizationNode(
                    id,
                    "ParameterDef",
                    String.valueOf(row.get("param_name")),
                    Map.of("paramId", row.get("param_id"), "paramCode", row.get("param_code"))));
            String stepId = "ProcessStep:" + row.get("step_id");
            if (nodes.containsKey(stepId)) {
                edges.add(new GraphVisualizationEdge(stepId, id, "HAS_PARAMETER", 1.0));
            }
        }

        List<Map<String, Object>> defects = jdbcTemplate.queryForList("""
                SELECT DISTINCT dt.defect_type_id::text AS defect_type_id,
                       dt.step_id::text AS step_id,
                       dt.defect_name,
                       COALESCE(MAX(dr.severity_level), MAX(dt.default_severity), 1) AS severity,
                       COALESCE(AVG(dr.confidence), 0) AS confidence
                FROM prod.production_batch b
                JOIN prod.process_run r ON r.batch_id = b.batch_id
                JOIN qc.inspection_task i ON i.run_id = r.run_id
                JOIN qc.defect_record dr ON dr.inspection_id = i.inspection_id
                JOIN qc.defect_type dt ON dt.defect_type_id = dr.defect_type_id
                WHERE b.batch_no = ? OR b.batch_id::text = ?
                GROUP BY dt.defect_type_id, dt.step_id, dt.defect_name
                ORDER BY severity DESC, dt.defect_name
                """, batchId, batchId);
        for (Map<String, Object> row : defects) {
            String id = "DefectType:" + row.get("defect_type_id");
            nodes.put(id, new GraphVisualizationNode(
                    id,
                    "DefectType",
                    String.valueOf(row.get("defect_name")),
                    Map.of("defectTypeId", row.get("defect_type_id"), "severity", row.get("severity"), "confidence", row.get("confidence"))));
            String stepId = "ProcessStep:" + row.get("step_id");
            if (nodes.containsKey(stepId)) {
                edges.add(new GraphVisualizationEdge(stepId, id, "PRODUCES_DEFECT_RISK", 1.0));
            }
            for (Map<String, Object> param : params) {
                if (String.valueOf(param.get("step_id")).equals(String.valueOf(row.get("step_id")))) {
                    edges.add(new GraphVisualizationEdge(
                            "ParameterDef:" + param.get("param_id"),
                            id,
                            "ASSOCIATED_WITH_DEFECT",
                            1.0));
                }
            }
        }

        return new GraphVisualizationResponse(new ArrayList<>(nodes.values()), dedupeVisualizationEdges(edges));
    }

    private GraphVisualizationResponse compactGraphForGat(GraphVisualizationResponse graph) {
        List<GraphVisualizationNode> allNodes = graph.nodes();
        if (allNodes.size() <= MAX_GAT_NODES) {
            return new GraphVisualizationResponse(allNodes, dedupeVisualizationEdges(graph.edges()));
        }

        List<GraphVisualizationNode> selectedNodes = allNodes.stream()
                .sorted(Comparator
                        .comparingInt((GraphVisualizationNode node) -> gatLabelPriority(node.label()))
                        .thenComparing(node -> node.name() != null ? node.name() : node.graphId()))
                .limit(MAX_GAT_NODES)
                .toList();
        Set<String> selectedIds = selectedNodes.stream()
                .map(GraphVisualizationNode::graphId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<GraphVisualizationEdge> selectedEdges = graph.edges().stream()
                .filter(edge -> selectedIds.contains(edge.from()) && selectedIds.contains(edge.to()))
                .toList();
        return new GraphVisualizationResponse(selectedNodes, dedupeVisualizationEdges(selectedEdges));
    }

    private List<GraphVisualizationEdge> dedupeVisualizationEdges(List<GraphVisualizationEdge> edges) {
        Map<String, GraphVisualizationEdge> unique = new LinkedHashMap<>();
        for (GraphVisualizationEdge edge : edges) {
            String key = edge.from() + "|" + edge.to() + "|" + edge.type();
            unique.putIfAbsent(key, edge);
        }
        return new ArrayList<>(unique.values());
    }

    private int gatLabelPriority(String label) {
        return switch (label) {
            case "ProductionBatch", "Batch" -> 0;
            case "DefectRecord", "Defect", "DefectType" -> 1;
            case "ParameterDef", "ProcessParameter", "ParameterValue", "QualityMeasurement" -> 2;
            case "ProcessStep", "InspectionTask" -> 3;
            case "ProcessRun" -> 4;
            case "ProductUnit" -> 5;
            default -> 9;
        };
    }

    // ═══════════════════════════════════════════════════════
    // Core: Graph Visualization
    // ═══════════════════════════════════════════════════════

    public GraphVisualizationResponse getGraphVisualization(String batchId) {
        return getGraphVisualization(batchId, false);
    }

    public GraphVisualizationResponse getGraphVisualization(String batchId, boolean full) {
        if (!full) {
            GraphVisualizationResponse summaryGraph = buildGatGraphFromPostgres(batchId);
            if (!summaryGraph.nodes().isEmpty()) {
                return summaryGraph;
            }
        }

        GraphVisualizationResponse neo4jGraph = getGraphVisualizationFromNeo4j(batchId);
        if (!neo4jGraph.nodes().isEmpty()) {
            return neo4jGraph;
        }

        UUID parsedId = null;
        try {
            parsedId = UUID.fromString(batchId);
        } catch (IllegalArgumentException e) {
            Optional<ProductionBatch> batch = productionBatchRepository.findByBatchNo(batchId);
            if (batch.isPresent()) {
                parsedId = batch.get().getBatchId();
            }
        }

        if (parsedId == null) {
            return new GraphVisualizationResponse(List.of(), List.of());
        }

        // 先尝试用 batchId 作为 graphVersionId 查询
        List<KgEntity> entities = kgEntityRepository.findByGraphVersionId(parsedId);
        List<KgRelation> relations = kgRelationRepository.findByGraphVersionId(parsedId);

        // 如果查询结果为空，尝试从 ProductionBatchRepository 查询 batchId 对应的批次，查找关联的图版本
        if (entities.isEmpty()) {
            Optional<ProductionBatch> batch = productionBatchRepository.findById(parsedId);
            if (batch.isPresent()) {
                // 尝试用 batchNo 匹配 graphName 或 versionNo
                String batchNo = batch.get().getBatchNo();
                List<GraphVersion> versions = graphVersionRepository.findAll();
                for (GraphVersion gv : versions) {
                    if (batchNo.equals(gv.getGraphName()) || batchNo.equals(gv.getVersionNo())) {
                        entities = kgEntityRepository.findByGraphVersionId(gv.getGraphVersionId());
                        relations = kgRelationRepository.findByGraphVersionId(gv.getGraphVersionId());
                        if (!entities.isEmpty()) {
                            break;
                        }
                    }
                }
            }
        }

        // 如果还是找不到，返回空的可视化数据
        if (entities.isEmpty()) {
            GraphVisualizationResponse batchGraph = buildGatGraphFromPostgres(batchId);
            if (!batchGraph.nodes().isEmpty()) {
                return batchGraph;
            }
            return new GraphVisualizationResponse(List.of(), List.of());
        }

        // Try to enrich from Neo4j
        try {
            enrichFromNeo4j(batchId, entities, relations);
        } catch (Exception e) {
            log.warn("Neo4j enrichment failed for batchId={}, using PostgreSQL data only: {}", batchId, e.getMessage());
        }

        List<GraphVisualizationNode> nodes = new ArrayList<>();
        for (KgEntity ent : entities) {
            Map<String, Object> props = parseProperties(ent.getProperties());
            props.put("entityType", ent.getEntityType());
            if (ent.getEntityCode() != null) {
                props.put("entityCode", ent.getEntityCode());
            }
            nodes.add(new GraphVisualizationNode(
                    ent.getEntityId().toString(),
                    ent.getEntityType(),
                    ent.getEntityName(),
                    props));
        }

        List<GraphVisualizationEdge> edges = new ArrayList<>();
        for (KgRelation rel : relations) {
            double weight = rel.getRelationWeight() != null ? rel.getRelationWeight().doubleValue() : 1.0;
            edges.add(new GraphVisualizationEdge(
                    rel.getSourceEntityId().toString(),
                    rel.getTargetEntityId().toString(),
                    rel.getRelationType(),
                    weight));
        }

        return new GraphVisualizationResponse(nodes, edges);
    }

    public GraphAnalysisResponse getGraphAnalysis(String batchId) {
        GraphVisualizationResponse graph = getGraphVisualization(batchId);
        Map<String, GraphVisualizationNode> nodeMap = graph.nodes().stream()
                .collect(Collectors.toMap(GraphVisualizationNode::graphId, node -> node, (a, b) -> a, LinkedHashMap::new));

        List<GraphAssociationRelation> ruleRelations = graph.edges().stream()
                .filter(edge -> isRuleRelation(edge.type()))
                .map(edge -> toRuleAssociation(edge, nodeMap))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(GraphAssociationRelation::relationType)
                        .thenComparing(GraphAssociationRelation::source)
                        .thenComparing(GraphAssociationRelation::target))
                .toList();

        List<GraphAssociationRelation> aprioriRelations = mineAprioriRelations(batchId);
        GraphFilterOptions filterOptions = buildGraphFilterOptions(graph);
        return new GraphAnalysisResponse(batchId, ruleRelations, aprioriRelations, filterOptions);
    }

    public GraphPathSearchResponse searchGraphPath(String batchId, String source, String target) {
        GraphVisualizationResponse graph = getGraphVisualization(batchId);
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            return new GraphPathSearchResponse(batchId, source, target, List.of(), List.of(), "source and target are required");
        }

        Map<String, GraphVisualizationNode> nodeMap = graph.nodes().stream()
                .collect(Collectors.toMap(GraphVisualizationNode::graphId, node -> node, (a, b) -> a, LinkedHashMap::new));
        String sourceId = findNodeIdByName(graph.nodes(), source);
        String targetId = findNodeIdByName(graph.nodes(), target);
        if (sourceId == null || targetId == null) {
            return new GraphPathSearchResponse(batchId, source, target, List.of(), List.of(), "source or target node not found");
        }

        Map<String, List<GraphVisualizationEdge>> adjacency = new LinkedHashMap<>();
        for (GraphVisualizationEdge edge : graph.edges()) {
            adjacency.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
            adjacency.computeIfAbsent(edge.to(), ignored -> new ArrayList<>())
                    .add(new GraphVisualizationEdge(edge.to(), edge.from(), edge.type(), edge.weight()));
        }

        Map<String, String> previousNode = new HashMap<>();
        Map<String, GraphVisualizationEdge> previousEdge = new HashMap<>();
        List<String> queue = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        queue.add(sourceId);
        visited.add(sourceId);

        for (int idx = 0; idx < queue.size(); idx++) {
            String current = queue.get(idx);
            if (current.equals(targetId)) {
                break;
            }
            for (GraphVisualizationEdge edge : adjacency.getOrDefault(current, List.of())) {
                String next = edge.to();
                if (visited.add(next)) {
                    previousNode.put(next, current);
                    previousEdge.put(next, edge);
                    queue.add(next);
                }
            }
        }

        if (!visited.contains(targetId)) {
            return new GraphPathSearchResponse(batchId, source, target, List.of(), List.of(), "no path found");
        }

        List<String> pathIds = new ArrayList<>();
        List<GraphVisualizationEdge> pathEdges = new ArrayList<>();
        String cursor = targetId;
        pathIds.add(cursor);
        while (!cursor.equals(sourceId)) {
            GraphVisualizationEdge edge = previousEdge.get(cursor);
            if (edge == null) {
                break;
            }
            pathEdges.add(edge);
            cursor = previousNode.get(cursor);
            pathIds.add(cursor);
        }
        java.util.Collections.reverse(pathIds);
        java.util.Collections.reverse(pathEdges);

        List<GraphVisualizationNode> pathNodes = pathIds.stream()
                .map(nodeMap::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        String summary = String.format("Found path with %d nodes and %d relations", pathNodes.size(), pathEdges.size());
        return new GraphPathSearchResponse(batchId, source, target, pathNodes, pathEdges, summary);
    }

    private boolean isRuleRelation(String relationType) {
        return Set.of(
                "PRODUCES_DEFECT_RISK",
                "ASSOCIATED_WITH_DEFECT",
                "INDICATES_DEFECT",
                "FOUND_DEFECT",
                "HAS_PARAM_VALUE",
                "OF_PARAMETER").contains(relationType);
    }

    private Optional<GraphAssociationRelation> toRuleAssociation(
            GraphVisualizationEdge edge,
            Map<String, GraphVisualizationNode> nodeMap) {
        GraphVisualizationNode source = nodeMap.get(edge.from());
        GraphVisualizationNode target = nodeMap.get(edge.to());
        if (source == null || target == null) {
            return Optional.empty();
        }
        String reason = switch (edge.type()) {
            case "PRODUCES_DEFECT_RISK" -> "Rule: process step is mapped to a defect risk observed in this batch.";
            case "ASSOCIATED_WITH_DEFECT" -> "Rule: process parameter belongs to the same process step as the defect.";
            case "INDICATES_DEFECT" -> "Rule: quality signal indicates the observed defect.";
            case "FOUND_DEFECT" -> "Rule: inspection task found a concrete defect record in this batch.";
            case "HAS_PARAM_VALUE" -> "Rule: process run contains this measured parameter value.";
            case "OF_PARAMETER" -> "Rule: parameter value belongs to the corresponding parameter definition.";
            default -> "Rule-derived graph relation.";
        };
        return Optional.of(new GraphAssociationRelation(
                source.name(), target.name(), edge.type(), source.label(), target.label(),
                1.0, Math.max(0.0, Math.min(1.0, edge.weight())), 1.0, reason));
    }

    private GraphFilterOptions buildGraphFilterOptions(GraphVisualizationResponse graph) {
        List<String> defects = namesByLabels(graph, Set.of("Defect", "DefectType", "DefectRecord"));
        List<String> processSteps = namesByLabels(graph, Set.of("ProcessStep"));
        List<String> parameters = namesByLabels(graph, Set.of("ProcessParameter", "ParameterDef", "ParameterValue", "QualityParameter", "QualityMeasurement"));
        List<String> relationTypes = graph.edges().stream()
                .map(GraphVisualizationEdge::type)
                .distinct()
                .sorted()
                .toList();
        return new GraphFilterOptions(defects, processSteps, parameters, relationTypes);
    }

    private List<String> namesByLabels(GraphVisualizationResponse graph, Set<String> labels) {
        return graph.nodes().stream()
                .filter(node -> labels.contains(node.label()))
                .map(GraphVisualizationNode::name)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private String findNodeIdByName(List<GraphVisualizationNode> nodes, String name) {
        String normalized = normalizeGraphName(name);
        return nodes.stream()
                .filter(node -> normalizeGraphName(node.name()).equals(normalized)
                        || normalizeGraphName(node.graphId()).equals(normalized))
                .map(GraphVisualizationNode::graphId)
                .findFirst()
                .orElse(null);
    }

    private String normalizeGraphName(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    // ═══════════════════════════════════════════════════════
    // Neo4j Enrichment
    // ═══════════════════════════════════════════════════════

    private List<GraphAssociationRelation> mineAprioriRelations(String batchId) {
        List<Map<String, Object>> parameterRows = jdbcTemplate.queryForList("""
                SELECT r.run_id::text AS run_id, pd.param_name
                FROM prod.production_batch b
                JOIN prod.process_run r ON r.batch_id = b.batch_id
                JOIN prod.parameter_value pv ON pv.run_id = r.run_id
                JOIN core.parameter_def pd ON pd.param_id = pv.param_id
                WHERE b.batch_no = ? OR b.batch_id::text = ?
                """, batchId, batchId);
        List<Map<String, Object>> defectRows = jdbcTemplate.queryForList("""
                SELECT r.run_id::text AS run_id, dt.defect_name
                FROM prod.production_batch b
                JOIN prod.process_run r ON r.batch_id = b.batch_id
                JOIN qc.inspection_task i ON i.run_id = r.run_id
                JOIN qc.defect_record dr ON dr.inspection_id = i.inspection_id
                JOIN qc.defect_type dt ON dt.defect_type_id = dr.defect_type_id
                WHERE b.batch_no = ? OR b.batch_id::text = ?
                """, batchId, batchId);

        Map<String, Set<String>> transactions = new LinkedHashMap<>();
        for (Map<String, Object> row : parameterRows) {
            String runId = String.valueOf(row.get("run_id"));
            String name = String.valueOf(row.get("param_name"));
            transactions.computeIfAbsent(runId, ignored -> new LinkedHashSet<>()).add("P:" + name);
        }
        for (Map<String, Object> row : defectRows) {
            String runId = String.valueOf(row.get("run_id"));
            String name = String.valueOf(row.get("defect_name"));
            transactions.computeIfAbsent(runId, ignored -> new LinkedHashSet<>()).add("D:" + name);
        }

        List<Set<String>> baskets = transactions.values().stream()
                .filter(items -> items.size() >= 2)
                .toList();
        int total = baskets.size();
        if (total == 0) {
            return List.of();
        }

        Map<String, Integer> itemCounts = new HashMap<>();
        Map<String, Integer> pairCounts = new HashMap<>();
        for (Set<String> basket : baskets) {
            List<String> items = basket.stream().sorted().toList();
            for (String item : items) {
                itemCounts.merge(item, 1, Integer::sum);
            }
            for (int i = 0; i < items.size(); i++) {
                for (int j = i + 1; j < items.size(); j++) {
                    pairCounts.merge(items.get(i) + "||" + items.get(j), 1, Integer::sum);
                }
            }
        }

        double minSupport = total <= 5 ? 1.0 / total : 0.15;
        List<GraphAssociationRelation> relations = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : pairCounts.entrySet()) {
            String[] parts = entry.getKey().split("\\|\\|", 2);
            if (parts.length != 2) {
                continue;
            }
            double support = entry.getValue() / (double) total;
            if (support < minSupport) {
                continue;
            }
            String left = parts[0];
            String right = parts[1];
            String relationType = aprioriRelationType(left, right);
            if (relationType == null) {
                continue;
            }

            double confidence = entry.getValue() / (double) Math.max(1, itemCounts.getOrDefault(left, 1));
            double rightSupport = itemCounts.getOrDefault(right, 1) / (double) total;
            double lift = rightSupport == 0 ? 0 : confidence / rightSupport;
            relations.add(new GraphAssociationRelation(
                    stripAprioriPrefix(left),
                    stripAprioriPrefix(right),
                    relationType,
                    aprioriNodeType(left),
                    aprioriNodeType(right),
                    roundMetric(support),
                    roundMetric(confidence),
                    roundMetric(lift),
                    "Apriori: items co-occurred in " + entry.getValue() + " of " + total + " process-run transactions."));
        }

        return relations.stream()
                .sorted(Comparator.comparingDouble(GraphAssociationRelation::lift).reversed()
                        .thenComparing(Comparator.comparingDouble(GraphAssociationRelation::support).reversed()))
                .limit(30)
                .toList();
    }

    private String aprioriRelationType(String left, String right) {
        boolean leftParameter = left.startsWith("P:");
        boolean rightParameter = right.startsWith("P:");
        boolean leftDefect = left.startsWith("D:");
        boolean rightDefect = right.startsWith("D:");
        if ((leftParameter && rightDefect) || (leftDefect && rightParameter)) {
            return "APRIORI_ASSOCIATED_WITH_DEFECT";
        }
        if ((leftParameter && rightParameter) || (leftDefect && rightDefect)) {
            return "CO_OCCURS_WITH";
        }
        return null;
    }

    private String aprioriNodeType(String item) {
        return item.startsWith("D:") ? "DefectType" : "ParameterDef";
    }

    private String stripAprioriPrefix(String item) {
        return item.length() > 2 ? item.substring(2) : item;
    }

    private double roundMetric(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private GraphVisualizationResponse getGraphVisualizationFromNeo4j(String batchId) {
        if (batchId == null || batchId.isBlank() || neo4jDriver == null) {
            return new GraphVisualizationResponse(List.of(), List.of());
        }

        try (Session session = neo4jDriver.session()) {
            Map<String, Object> params = Map.of("batchId", batchId);
            String batchMatch = "(b) WHERE (b:ProductionBatch OR b:Batch) " +
                    "AND (b.batchNo = $batchId OR b.batchId = $batchId)";

            Result nodeResult = session.run(
                    "MATCH " + batchMatch + " MATCH p=(b)-[*0..4]-(n) " +
                            "UNWIND nodes(p) AS node RETURN DISTINCT node LIMIT 300",
                    params);

            Map<String, GraphVisualizationNode> nodes = new LinkedHashMap<>();
            while (nodeResult.hasNext()) {
                Node node = nodeResult.next().get("node").asNode();
                GraphVisualizationNode dto = toNeo4jVisualizationNode(node);
                nodes.put(dto.graphId(), dto);
            }

            Result edgeResult = session.run(
                    "MATCH " + batchMatch + " MATCH p=(b)-[*1..4]-(n) " +
                            "UNWIND relationships(p) AS rel " +
                            "RETURN DISTINCT rel, startNode(rel) AS source, endNode(rel) AS target LIMIT 600",
                    params);

            List<GraphVisualizationEdge> edges = new ArrayList<>();
            while (edgeResult.hasNext()) {
                Record record = edgeResult.next();
                Relationship rel = record.get("rel").asRelationship();
                Node source = record.get("source").asNode();
                Node target = record.get("target").asNode();
                double weight = rel.containsKey("weight") && !rel.get("weight").isNull()
                        ? rel.get("weight").asDouble()
                        : 1.0;
                edges.add(new GraphVisualizationEdge(
                        neo4jNodeGraphId(source),
                        neo4jNodeGraphId(target),
                        rel.type(),
                        weight));
            }

            return new GraphVisualizationResponse(new ArrayList<>(nodes.values()), edges);
        } catch (Exception e) {
            log.warn("Neo4j visualization query failed for batchId={}, falling back to PostgreSQL KG data: {}", batchId, e.getMessage());
            return new GraphVisualizationResponse(List.of(), List.of());
        }
    }

    private GraphVisualizationNode toNeo4jVisualizationNode(Node node) {
        String label = node.labels().iterator().hasNext() ? node.labels().iterator().next() : "Unknown";
        Map<String, Object> props = new LinkedHashMap<>();
        node.keys().forEach(key -> props.put(key, node.get(key).asObject()));
        props.put("entityType", label);

        String name = firstStringProperty(node,
                "name", "batchNo", "stepName", "stationName", "equipmentName", "productName",
                "paramName", "defectName", "inspectionType", "runNo", "serialNo");
        if (name == null || name.isBlank()) {
            name = label + "-" + neo4jNodeGraphId(node);
        }

        return new GraphVisualizationNode(neo4jNodeGraphId(node), label, name, props);
    }

    private String firstStringProperty(Node node, String... keys) {
        for (String key : keys) {
            if (node.containsKey(key) && !node.get(key).isNull()) {
                String value = node.get(key).asObject().toString();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private String neo4jNodeGraphId(Node node) {
        String label = node.labels().iterator().hasNext() ? node.labels().iterator().next() : "Node";
        String[] keyCandidates = neo4jKeyCandidates(label);
        for (String key : keyCandidates) {
            if (node.containsKey(key) && !node.get(key).isNull()) {
                return label + ":" + node.get(key).asObject();
            }
        }
        return label + ":" + node.id();
    }

    private String[] neo4jKeyCandidates(String label) {
        return switch (label) {
            case "ProductionBatch", "Batch" -> new String[]{"batchId", "batchNo"};
            case "ProductUnit" -> new String[]{"unitId", "serialNo", "batchId"};
            case "ProcessRun" -> new String[]{"runId", "runNo", "batchId"};
            case "ParameterValue" -> new String[]{"valueId", "runId", "paramId", "batchId"};
            case "InspectionTask" -> new String[]{"inspectionId", "runId", "batchId"};
            case "DefectRecord" -> new String[]{"defectId", "inspectionId", "defectTypeId", "batchId"};
            case "QualityMeasurement" -> new String[]{"measurementId", "inspectionId", "metricId", "batchId"};
            case "ProcessStep" -> new String[]{"stepId", "stepCode"};
            case "Workstation" -> new String[]{"stationId", "stationCode"};
            case "Equipment" -> new String[]{"equipmentId", "equipmentCode"};
            case "ProductType" -> new String[]{"productTypeId", "productCode"};
            case "ParameterDef", "ProcessParameter" -> new String[]{"paramId", "paramCode", "name", "batchId"};
            case "DefectType", "Defect" -> new String[]{"defectTypeId", "defectCode", "name", "batchId"};
            default -> new String[]{"id", "uuid", "batchId"};
        };
    }

    private void enrichFromNeo4j(String batchId, List<KgEntity> entities, List<KgRelation> relations) {
        if (neo4jDriver == null) {
            return;
        }
        try (Session session = neo4jDriver.session()) {
            // Query Neo4j for nodes related to this batch
            Result result = session.run(
                    "MATCH (n)-[r]->(m) WHERE n.batchId = $batchId OR m.batchId = $batchId RETURN n, r, m LIMIT 500",
                    Map.of("batchId", batchId));

            Set<String> existingEntityIds = entities.stream()
                    .map(e -> e.getEntityId().toString())
                    .collect(Collectors.toSet());

            while (result.hasNext()) {
                Record record = result.next();
                Node n = record.get("n").asNode();
                Node m = record.get("m").asNode();
                Relationship r = record.get("r").asRelationship();

                // Add nodes if not already present
                addNeo4jNodeIfAbsent(n, entities, existingEntityIds);
                addNeo4jNodeIfAbsent(m, entities, existingEntityIds);
            }
        } catch (Exception e) {
            // Neo4j 不可用时静默降级，不影响主流程
            return;
        }
    }

    private void addNeo4jNodeIfAbsent(Node neo4jNode, List<KgEntity> entities, Set<String> existingIds) {
        String nodeId = String.valueOf(neo4jNode.id());
        if (existingIds.contains(nodeId)) {
            return;
        }
        String label = neo4jNode.labels().iterator().hasNext()
                ? neo4jNode.labels().iterator().next()
                : "Unknown";
        String name = neo4jNode.containsKey("name") && !neo4jNode.get("name").isNull()
                ? neo4jNode.get("name").asString()
                : label + "_" + nodeId;

        KgEntity entity = new KgEntity(label, name);
        // Store Neo4j node properties
        Map<String, Object> props = new HashMap<>();
        neo4jNode.keys().forEach(k -> props.put(k, neo4jNode.get(k).asObject()));
        entity.setGraphVersionId(null); // Neo4j nodes don't have a graphVersionId
        entities.add(entity);
        existingIds.add(nodeId);
    }

    // ═══════════════════════════════════════════════════════
    // Helper: Entity ↔ DTO mapping
    // ═══════════════════════════════════════════════════════

    private GraphVersionResponse toVersionResponse(GraphVersion gv) {
        return new GraphVersionResponse(
                gv.getGraphVersionId(), gv.getGraphName(), gv.getVersionNo(), getDescription(gv));
    }

    private KgEntityResponse toEntityResponse(KgEntity e) {
        return new KgEntityResponse(
                e.getEntityId(), e.getGraphVersionId(), e.getEntityType(),
                e.getEntityCode(), e.getEntityName(), parseProperties(e.getProperties()));
    }

    private KgRelationResponse toRelationResponse(KgRelation r) {
        return new KgRelationResponse(
                r.getRelationId(), r.getGraphVersionId(),
                r.getSourceEntityId(), r.getTargetEntityId(),
                r.getRelationType(), r.getRelationWeight(), r.getConfidence());
    }

    private GatAnalysisTaskResponse toTaskResponse(GatAnalysisTask t) {
        return new GatAnalysisTaskResponse(
                t.getGatTaskId(), t.getGraphVersionId(), t.getModelName(),
                t.getTaskStatus(),
                t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
                t.getFinishedAt() != null ? t.getFinishedAt().toString() : null);
    }

    private GatRelationWeightResponse toWeightResponse(GatRelationWeight w) {
        return new GatRelationWeightResponse(
                w.getWeightId(), w.getGatTaskId(), w.getRelationId(),
                w.getAttentionWeight(), w.getHiddenRelationFlag());
    }

    // ═══════════════════════════════════════════════════════
    // Helper: Lookups
    // ═══════════════════════════════════════════════════════

    private GraphVersion requireGraphVersion(UUID id) {
        return graphVersionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "GraphVersion not found: " + id));
    }

    private KgEntity requireKgEntity(UUID id) {
        return kgEntityRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "KgEntity not found: " + id));
    }

    private GatAnalysisTask requireGatTask(UUID id) {
        return gatAnalysisTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "GatAnalysisTask not found: " + id));
    }

    // ═══════════════════════════════════════════════════════
    // Helper: JSONB / property access
    // ═══════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseProperties(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSONB properties: {}", e.getMessage());
            return Map.of();
        }
    }

    private String getDescription(GraphVersion gv) {
        return gv.getDescription();
    }

    private void setDescription(GraphVersion gv, String description) {
        gv.setDescription(description);
    }
}
