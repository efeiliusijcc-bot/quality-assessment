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
import com.example.demo.kg.dto.KgDtos.GatNodeEmbeddingEntry;
import com.example.demo.kg.dto.KgDtos.GatOptimizationResponse;
import com.example.demo.kg.dto.KgDtos.GatRelationWeightResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KgService {

    private static final Logger log = LoggerFactory.getLogger(KgService.class);

    private final GraphVersionRepository graphVersionRepository;
    private final KgEntityRepository kgEntityRepository;
    private final KgRelationRepository kgRelationRepository;
    private final GatAnalysisTaskRepository gatAnalysisTaskRepository;
    private final GatRelationWeightRepository gatRelationWeightRepository;
    private final ProductionBatchRepository productionBatchRepository;
    private final Driver neo4jDriver;
    private final ObjectMapper objectMapper;

    public KgService(GraphVersionRepository graphVersionRepository,
                     KgEntityRepository kgEntityRepository,
                     KgRelationRepository kgRelationRepository,
                     GatAnalysisTaskRepository gatAnalysisTaskRepository,
                     GatRelationWeightRepository gatRelationWeightRepository,
                     ProductionBatchRepository productionBatchRepository,
                     Driver neo4jDriver,
                     ObjectMapper objectMapper) {
        this.graphVersionRepository = graphVersionRepository;
        this.kgEntityRepository = kgEntityRepository;
        this.kgRelationRepository = kgRelationRepository;
        this.gatAnalysisTaskRepository = gatAnalysisTaskRepository;
        this.gatRelationWeightRepository = gatRelationWeightRepository;
        this.productionBatchRepository = productionBatchRepository;
        this.neo4jDriver = neo4jDriver;
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
        // 1. Collect graph data from PostgreSQL
        UUID graphVersionId;
        try {
            graphVersionId = UUID.fromString(batchId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Invalid batchId format, expected UUID");
        }

        List<KgEntity> entities = kgEntityRepository.findByGraphVersionId(graphVersionId);
        List<KgRelation> relations = kgRelationRepository.findByGraphVersionId(graphVersionId);

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
                nodeEmbeddings, attentionEdges, summary);
    }

    // ═══════════════════════════════════════════════════════
    // Core: Graph Visualization
    // ═══════════════════════════════════════════════════════

    public GraphVisualizationResponse getGraphVisualization(String batchId) {
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

    // ═══════════════════════════════════════════════════════
    // Neo4j Enrichment
    // ═══════════════════════════════════════════════════════

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
