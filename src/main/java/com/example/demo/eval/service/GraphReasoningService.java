package com.example.demo.eval.service;

import com.example.demo.core.domain.ParameterDef;
import com.example.demo.core.domain.ProcessStep;
import com.example.demo.core.repository.ParameterDefRepository;
import com.example.demo.core.repository.ProcessStepRepository;
import com.example.demo.eval.dto.EvalDtos.GraphReasoning;
import com.example.demo.eval.dto.EvalDtos.ReasoningStatistics;
import com.example.demo.prod.domain.ParameterValue;
import com.example.demo.prod.domain.ProcessRun;
import com.example.demo.prod.domain.ProductionBatch;
import com.example.demo.prod.repository.ParameterValueRepository;
import com.example.demo.prod.repository.ProcessRunRepository;
import com.example.demo.prod.repository.ProductionBatchRepository;
import com.example.demo.qc.domain.DefectRecord;
import com.example.demo.qc.domain.DefectType;
import com.example.demo.qc.domain.InspectionTask;
import com.example.demo.qc.repository.DefectRecordRepository;
import com.example.demo.qc.repository.DefectTypeRepository;
import com.example.demo.qc.repository.InspectionTaskRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GraphReasoningService {

    private static final Logger log = LoggerFactory.getLogger(GraphReasoningService.class);
    private static final long CACHE_TTL_MILLIS = 60_000;

    private final Driver neo4jDriver;
    private final JdbcTemplate jdbcTemplate;
    private final ProductionBatchRepository batchRepo;
    private final ProcessRunRepository processRunRepo;
    private final ParameterValueRepository parameterValueRepo;
    private final ParameterDefRepository parameterDefRepo;
    private final ProcessStepRepository processStepRepo;
    private final InspectionTaskRepository inspectionTaskRepo;
    private final DefectRecordRepository defectRecordRepo;
    private final DefectTypeRepository defectTypeRepo;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public GraphReasoningService(
            Driver neo4jDriver,
            JdbcTemplate jdbcTemplate,
            ProductionBatchRepository batchRepo,
            ProcessRunRepository processRunRepo,
            ParameterValueRepository parameterValueRepo,
            ParameterDefRepository parameterDefRepo,
            ProcessStepRepository processStepRepo,
            InspectionTaskRepository inspectionTaskRepo,
            DefectRecordRepository defectRecordRepo,
            DefectTypeRepository defectTypeRepo) {
        this.neo4jDriver = neo4jDriver;
        this.jdbcTemplate = jdbcTemplate;
        this.batchRepo = batchRepo;
        this.processRunRepo = processRunRepo;
        this.parameterValueRepo = parameterValueRepo;
        this.parameterDefRepo = parameterDefRepo;
        this.processStepRepo = processStepRepo;
        this.inspectionTaskRepo = inspectionTaskRepo;
        this.defectRecordRepo = defectRecordRepo;
        this.defectTypeRepo = defectTypeRepo;
    }

    public GraphReasoning evaluateBatch(String batchNoOrId) {
        if (batchNoOrId == null || batchNoOrId.isBlank()) {
            return emptyReasoning();
        }
        CacheEntry cached = cache.get(batchNoOrId);
        if (cached != null && System.currentTimeMillis() - cached.createdAtMillis() < CACHE_TTL_MILLIS) {
            return cached.reasoning();
        }
        GraphReasoning result;
        if (resolveBatch(batchNoOrId).isPresent()) {
            result = evaluateFromPostgresJdbc(batchNoOrId);
        } else {
            try {
                GraphReasoning reasoning = evaluateFromNeo4j(batchNoOrId);
                if (reasoning.statistics().nodeCount() > 0
                        && (!reasoning.parameterChain().isEmpty() || reasoning.statistics().defectCount() > 0)) {
                    result = reasoning;
                } else {
                    result = evaluateFromPostgres(batchNoOrId);
                }
            } catch (Exception e) {
                log.warn("Neo4j reasoning failed for batchId={}, falling back to PostgreSQL: {}", batchNoOrId, e.getMessage());
                result = evaluateFromPostgres(batchNoOrId);
            }
        }
        cache.put(batchNoOrId, new CacheEntry(result, System.currentTimeMillis()));
        return result;
    }

    private GraphReasoning evaluateFromNeo4j(String batchNoOrId) {
        try (Session session = neo4jDriver.session()) {
            Map<String, Object> params = Map.of("batchId", batchNoOrId);
            Record nodeStats = session.run("""
                    MATCH (b:ProductionBatch)
                    WHERE b.batchNo = $batchId OR b.batchId = $batchId
                    OPTIONAL MATCH p=(b)-[*0..5]-(n)
                    UNWIND nodes(p) AS node
                    RETURN count(DISTINCT node) AS nodeCount
                    """, params).single();
            Record relationStats = session.run("""
                    MATCH (b:ProductionBatch)
                    WHERE b.batchNo = $batchId OR b.batchId = $batchId
                    OPTIONAL MATCH p=(b)-[*1..5]-(n)
                    UNWIND relationships(p) AS rel
                    RETURN count(DISTINCT rel) AS relationCount
                    """, params).single();
            int nodeCount = nodeStats.get("nodeCount").asInt(0);
            int relationCount = relationStats.get("relationCount").asInt(0);

            List<DefectSignal> defects = session.run("""
                    MATCH (b:ProductionBatch)
                    WHERE b.batchNo = $batchId OR b.batchId = $batchId
                    MATCH (b)-[:HAS_UNIT]->(:ProductUnit)-[:HAS_RUN]->(:ProcessRun)-[:HAS_INSPECTION]->(:InspectionTask)
                          -[:FOUND_DEFECT]->(dr:DefectRecord)-[:OF_TYPE]->(dt:DefectType)
                    RETURN dt.defectName AS name,
                           coalesce(max(dr.severityLevel), max(dt.defaultSeverity), 1) AS severity,
                           coalesce(avg(dr.confidence), 0.0) AS confidence,
                           count(dr) AS count
                    ORDER BY count DESC, severity DESC
                    """, params).list(record -> new DefectSignal(
                    record.get("name").asString("Unknown Defect"),
                    record.get("severity").asInt(1),
                    record.get("confidence").asDouble(0.0),
                    record.get("count").asInt(0)));

            List<String> parameterChain = session.run("""
                    MATCH (b:ProductionBatch)
                    WHERE b.batchNo = $batchId OR b.batchId = $batchId
                    MATCH (b)-[:HAS_UNIT]->(:ProductUnit)-[:HAS_RUN]->(:ProcessRun)-[:HAS_PARAM_VALUE]->(:ParameterValue)-[:OF_PARAMETER]->(pd:ParameterDef)
                    RETURN DISTINCT pd.paramName AS name
                    ORDER BY name
                    LIMIT 12
                    """, params).list(record -> record.get("name").asString());

            List<String> stepChain = session.run("""
                    MATCH (b:ProductionBatch)
                    WHERE b.batchNo = $batchId OR b.batchId = $batchId
                    MATCH (b)-[:HAS_UNIT]->(:ProductUnit)-[:HAS_RUN]->(r:ProcessRun)
                    MATCH (ps:ProcessStep {stepId: r.stepId})
                    RETURN DISTINCT ps.stepCode AS code, ps.stepName AS name
                    ORDER BY code
                    LIMIT 10
                    """, params).list(record -> {
                String code = record.get("code").asString("");
                String name = record.get("name").asString("");
                return code.isBlank() ? name : code;
            });

            return buildReasoning(batchNoOrId, defects, parameterChain, stepChain, nodeCount, relationCount);
        }
    }

    private GraphReasoning evaluateFromPostgresJdbc(String batchNoOrId) {
        List<DefectSignal> defects = jdbcTemplate.query("""
                SELECT dt.defect_name AS name,
                       COALESCE(MAX(dr.severity_level), MAX(dt.default_severity), 1) AS severity,
                       COALESCE(AVG(dr.confidence), 0) AS confidence,
                       COALESCE(SUM(dr.defect_count), COUNT(dr.defect_id), 0) AS defect_count
                FROM prod.production_batch b
                JOIN prod.process_run r ON r.batch_id = b.batch_id
                JOIN qc.inspection_task i ON i.run_id = r.run_id
                JOIN qc.defect_record dr ON dr.inspection_id = i.inspection_id
                JOIN qc.defect_type dt ON dt.defect_type_id = dr.defect_type_id
                WHERE b.batch_no = ? OR b.batch_id::text = ?
                GROUP BY dt.defect_name
                ORDER BY defect_count DESC, severity DESC
                """, (rs, rowNum) -> new DefectSignal(
                rs.getString("name"),
                rs.getInt("severity"),
                rs.getDouble("confidence"),
                rs.getInt("defect_count")), batchNoOrId, batchNoOrId);

        List<String> parameterChain = jdbcTemplate.query("""
                SELECT DISTINCT pd.param_name
                FROM prod.production_batch b
                JOIN prod.process_run r ON r.batch_id = b.batch_id
                JOIN prod.parameter_value pv ON pv.run_id = r.run_id
                JOIN core.parameter_def pd ON pd.param_id = pv.param_id
                WHERE b.batch_no = ? OR b.batch_id::text = ?
                ORDER BY pd.param_name
                LIMIT 12
                """, (rs, rowNum) -> rs.getString("param_name"), batchNoOrId, batchNoOrId);

        List<String> stepChain = jdbcTemplate.query("""
                SELECT DISTINCT ps.step_code
                FROM prod.production_batch b
                JOIN prod.process_run r ON r.batch_id = b.batch_id
                JOIN core.process_step ps ON ps.step_id = r.step_id
                WHERE b.batch_no = ? OR b.batch_id::text = ?
                ORDER BY ps.step_code
                LIMIT 10
                """, (rs, rowNum) -> rs.getString("step_code"), batchNoOrId, batchNoOrId);

        Integer nodeCount = jdbcTemplate.queryForObject("""
                SELECT (
                    1
                    + (SELECT COUNT(*) FROM prod.process_run r JOIN prod.production_batch b ON b.batch_id = r.batch_id WHERE b.batch_no = ? OR b.batch_id::text = ?)
                    + (SELECT COUNT(*) FROM prod.parameter_value pv JOIN prod.process_run r ON r.run_id = pv.run_id JOIN prod.production_batch b ON b.batch_id = r.batch_id WHERE b.batch_no = ? OR b.batch_id::text = ?)
                    + (SELECT COUNT(*) FROM qc.inspection_task i JOIN prod.process_run r ON r.run_id = i.run_id JOIN prod.production_batch b ON b.batch_id = r.batch_id WHERE b.batch_no = ? OR b.batch_id::text = ?)
                    + (SELECT COUNT(*) FROM qc.defect_record dr JOIN qc.inspection_task i ON i.inspection_id = dr.inspection_id JOIN prod.process_run r ON r.run_id = i.run_id JOIN prod.production_batch b ON b.batch_id = r.batch_id WHERE b.batch_no = ? OR b.batch_id::text = ?)
                ) AS node_count
                """, Integer.class, batchNoOrId, batchNoOrId, batchNoOrId, batchNoOrId,
                batchNoOrId, batchNoOrId, batchNoOrId, batchNoOrId);
        int safeNodeCount = nodeCount != null ? nodeCount : 0;
        return buildReasoning(batchNoOrId, defects, parameterChain, stepChain, safeNodeCount, Math.max(0, safeNodeCount - 1));
    }

    private GraphReasoning evaluateFromPostgres(String batchNoOrId) {
        Optional<ProductionBatch> batchOpt = resolveBatch(batchNoOrId);
        if (batchOpt.isEmpty()) {
            return emptyReasoning();
        }

        UUID batchId = batchOpt.get().getBatchId();
        List<ProcessRun> runs = processRunRepo.findByBatchIdOrderByCreatedAtAsc(batchId);
        List<UUID> runIds = runs.stream().map(ProcessRun::getRunId).toList();
        List<InspectionTask> inspections = runIds.isEmpty()
                ? List.of()
                : inspectionTaskRepo.findByRunIdIn(runIds);

        List<DefectRecord> defectRecords = inspections.isEmpty()
                ? List.of()
                : defectRecordRepo.findByInspectionIdIn(inspections.stream().map(InspectionTask::getInspectionId).toList());
        Map<UUID, DefectType> defectTypes = defectTypeRepo.findAllById(
                defectRecords.stream().map(DefectRecord::getDefectTypeId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(DefectType::getDefectTypeId, Function.identity()));

        Map<String, DefectAccumulator> defectMap = new LinkedHashMap<>();
        for (DefectRecord record : defectRecords) {
            DefectType type = defectTypes.get(record.getDefectTypeId());
            String name = type != null ? type.getDefectName() : "Unknown Defect";
            DefectAccumulator acc = defectMap.computeIfAbsent(name, DefectAccumulator::new);
            acc.count += Optional.ofNullable(record.getDefectCount()).orElse(1);
            acc.maxSeverity = Math.max(acc.maxSeverity, Optional.ofNullable(record.getSeverityLevel())
                    .orElse(type != null && type.getDefaultSeverity() != null ? type.getDefaultSeverity() : 1));
            acc.confidenceSum += record.getConfidence() != null ? record.getConfidence().doubleValue() : 0.0;
            acc.confidenceSamples += record.getConfidence() != null ? 1 : 0;
        }
        List<DefectSignal> defects = defectMap.values().stream()
                .map(DefectAccumulator::toSignal)
                .sorted(Comparator.comparingInt(DefectSignal::count).reversed()
                        .thenComparing(Comparator.comparingInt(DefectSignal::severity).reversed()))
                .toList();

        List<ParameterValue> values = runIds.isEmpty()
                ? List.of()
                : parameterValueRepo.findByRunIdInOrderByMeasuredAtAsc(runIds);
        Map<UUID, ParameterDef> parameterDefs = parameterDefRepo.findAllById(
                values.stream().map(ParameterValue::getParamId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(ParameterDef::getParamId, Function.identity()));
        List<String> parameterChain = values.stream()
                .map(value -> parameterDefs.get(value.getParamId()))
                .filter(def -> def != null && def.getParamName() != null)
                .map(ParameterDef::getParamName)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(12)
                .toList();

        Map<UUID, ProcessStep> steps = processStepRepo.findAllById(
                runs.stream().map(ProcessRun::getStepId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(ProcessStep::getStepId, Function.identity()));
        List<String> stepChain = runs.stream()
                .map(run -> steps.get(run.getStepId()))
                .filter(step -> step != null && step.getStepCode() != null)
                .map(ProcessStep::getStepCode)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(10)
                .toList();

        int nodeCount = 1 + runs.size() + values.size() + inspections.size() + defectRecords.size();
        int relationCount = runs.size() + values.size() + inspections.size() + defectRecords.size();
        return buildReasoning(batchNoOrId, defects, parameterChain, stepChain, nodeCount, relationCount);
    }

    private Optional<ProductionBatch> resolveBatch(String batchNoOrId) {
        try {
            UUID uuid = UUID.fromString(batchNoOrId);
            return batchRepo.findById(uuid);
        } catch (IllegalArgumentException ignored) {
            return batchRepo.findByBatchNo(batchNoOrId);
        }
    }

    private GraphReasoning buildReasoning(
            String batchId,
            List<DefectSignal> defects,
            List<String> parameterChain,
            List<String> stepChain,
            int nodeCount,
            int relationCount) {
        int defectCount = defects.stream().mapToInt(DefectSignal::count).sum();
        DefectSignal main = defects.isEmpty() ? null : defects.get(0);
        double riskScore = defects.isEmpty()
                ? 0.0
                : Math.min(100.0, defects.stream().mapToDouble(d ->
                        d.count() * 4.0 + d.severity() * 18.0 + d.confidence() * 20.0).sum());
        List<String> defectChain = defects.isEmpty()
                ? List.of("No Defect")
                : defects.stream().map(DefectSignal::name).distinct().limit(6).toList();
        String mainDefect = main == null ? "No Defect" : main.name();
        String summary = main == null
                ? "Knowledge graph shows no active defect path for batch " + batchId + "."
                : "Knowledge graph links batch " + batchId + " to dominant defect " + mainDefect
                        + " with " + parameterChain.size() + " related parameter signals.";
        List<String> hints = main == null
                ? List.of("Keep the current process window and continue monitoring batch-level drift.")
                : buildOptimizationHints(mainDefect, parameterChain);

        return new GraphReasoning(
                riskScore,
                mainDefect,
                defectChain,
                parameterChain,
                stepChain,
                summary,
                hints,
                new ReasoningStatistics(nodeCount, relationCount, defects.size(), 0, defectCount, parameterChain.size()));
    }

    private List<String> buildOptimizationHints(String mainDefect, List<String> parameterChain) {
        List<String> hints = new ArrayList<>();
        hints.add("Review process window to reduce defect propagation related to " + mainDefect + ".");
        parameterChain.stream().limit(3).forEach(param ->
                hints.add("Check drift and control limits for " + param + "."));
        return hints;
    }

    private GraphReasoning emptyReasoning() {
        return new GraphReasoning(
                0.0,
                "No Defect",
                List.of("No Defect"),
                List.of(),
                List.of(),
                "No batch graph data is available.",
                List.of("Import production and quality data, then run graph sync."),
                new ReasoningStatistics(0, 0, 0, 0, 0, 0));
    }

    private record DefectSignal(String name, int severity, double confidence, int count) {}

    private record CacheEntry(GraphReasoning reasoning, long createdAtMillis) {}

    private static class DefectAccumulator {
        private final String name;
        private int count;
        private int maxSeverity = 1;
        private double confidenceSum;
        private int confidenceSamples;

        private DefectAccumulator(String name) {
            this.name = name;
        }

        private DefectSignal toSignal() {
            double confidence = confidenceSamples == 0 ? 0.0 : confidenceSum / confidenceSamples;
            return new DefectSignal(name, maxSeverity, confidence, count);
        }
    }
}
