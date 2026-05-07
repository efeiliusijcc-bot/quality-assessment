package com.example.demo.kg.service;

import com.example.demo.core.domain.Equipment;
import com.example.demo.core.domain.ParameterDef;
import com.example.demo.core.domain.ProcessStep;
import com.example.demo.core.domain.ProductType;
import com.example.demo.core.domain.Workstation;
import com.example.demo.core.repository.EquipmentRepository;
import com.example.demo.core.repository.ParameterDefRepository;
import com.example.demo.core.repository.ProcessStepRepository;
import com.example.demo.core.repository.ProductTypeRepository;
import com.example.demo.core.repository.WorkstationRepository;
import com.example.demo.prod.domain.ParameterValue;
import com.example.demo.prod.domain.ProcessRun;
import com.example.demo.prod.domain.ProductUnit;
import com.example.demo.prod.domain.ProductionBatch;
import com.example.demo.prod.repository.ParameterValueRepository;
import com.example.demo.prod.repository.ProcessRunRepository;
import com.example.demo.prod.repository.ProductUnitRepository;
import com.example.demo.prod.repository.ProductionBatchRepository;
import com.example.demo.qc.domain.DefectRecord;
import com.example.demo.qc.domain.DefectType;
import com.example.demo.qc.domain.InspectionTask;
import com.example.demo.qc.domain.QualityMeasurement;
import com.example.demo.qc.repository.DefectRecordRepository;
import com.example.demo.qc.repository.DefectTypeRepository;
import com.example.demo.qc.repository.InspectionTaskRepository;
import com.example.demo.qc.repository.QualityMeasurementRepository;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class Neo4jSyncService {

    private static final Logger log = LoggerFactory.getLogger(Neo4jSyncService.class);
    private static final int BATCH_SIZE = 100;

    // Core repositories
    private final ProcessStepRepository processStepRepo;
    private final WorkstationRepository workstationRepo;
    private final EquipmentRepository equipmentRepo;
    private final ProductTypeRepository productTypeRepo;
    private final ParameterDefRepository parameterDefRepo;

    // Production repositories
    private final ProductionBatchRepository productionBatchRepo;
    private final ProductUnitRepository productUnitRepo;
    private final ProcessRunRepository processRunRepo;
    private final ParameterValueRepository parameterValueRepo;

    // QC repositories
    private final DefectTypeRepository defectTypeRepo;
    private final InspectionTaskRepository inspectionTaskRepo;
    private final DefectRecordRepository defectRecordRepo;
    private final QualityMeasurementRepository qualityMeasurementRepo;

    // Neo4j driver
    private final Driver neo4jDriver;
    private final JdbcTemplate jdbcTemplate;

    public Neo4jSyncService(
            ProcessStepRepository processStepRepo,
            WorkstationRepository workstationRepo,
            EquipmentRepository equipmentRepo,
            ProductTypeRepository productTypeRepo,
            ParameterDefRepository parameterDefRepo,
            ProductionBatchRepository productionBatchRepo,
            ProductUnitRepository productUnitRepo,
            ProcessRunRepository processRunRepo,
            ParameterValueRepository parameterValueRepo,
            DefectTypeRepository defectTypeRepo,
            InspectionTaskRepository inspectionTaskRepo,
            DefectRecordRepository defectRecordRepo,
            QualityMeasurementRepository qualityMeasurementRepo,
            Driver neo4jDriver,
            JdbcTemplate jdbcTemplate) {
        this.processStepRepo = processStepRepo;
        this.workstationRepo = workstationRepo;
        this.equipmentRepo = equipmentRepo;
        this.productTypeRepo = productTypeRepo;
        this.parameterDefRepo = parameterDefRepo;
        this.productionBatchRepo = productionBatchRepo;
        this.productUnitRepo = productUnitRepo;
        this.processRunRepo = processRunRepo;
        this.parameterValueRepo = parameterValueRepo;
        this.defectTypeRepo = defectTypeRepo;
        this.inspectionTaskRepo = inspectionTaskRepo;
        this.defectRecordRepo = defectRecordRepo;
        this.qualityMeasurementRepo = qualityMeasurementRepo;
        this.neo4jDriver = neo4jDriver;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record GraphSyncTaskStatus(
            String batchId,
            String syncStatus,
            String triggerSource,
            Instant startedAt,
            Instant finishedAt,
            String errorMessage,
            Integer nodeCount,
            Integer relationCount
    ) {}

    /**
     * Full sync: reads all data from PostgreSQL and writes to Neo4j.
     * Returns a summary map with counts for each node/relationship type.
     */
    @Transactional
    public Map<String, Integer> syncAll() {
        ensureGraphSyncTaskTable();
        Map<String, Integer> summary = new LinkedHashMap<>();

        try (Session session = neo4jDriver.session()) {
            // Step 1: Clear existing Neo4j data
            log.info("Clearing existing Neo4j data...");
            session.run("MATCH (n) DETACH DELETE n");
            log.info("Neo4j data cleared.");

            // Step 2: Create all nodes
            summary.putAll(syncNodes(session));

            // Step 3: Create all relationships
            summary.putAll(syncRelationships(session));

            log.info("Neo4j sync completed. Summary: {}", summary);
        } catch (Exception e) {
            log.error("Neo4j sync failed: {}", e.getMessage(), e);
            throw new RuntimeException("Neo4j sync failed: " + e.getMessage(), e);
        }

        return summary;
    }

    public List<GraphSyncTaskStatus> listBatchSyncTasks() {
        ensureGraphSyncTaskTable();

        Map<String, GraphSyncTaskStatus> latestStatusByBatch = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT batch_id, sync_status, trigger_source, started_at, finished_at, error_message, node_count, relation_count " +
                        "FROM kg.graph_sync_task ORDER BY started_at DESC",
                rs -> {
                    String batchId = rs.getString("batch_id");
                    latestStatusByBatch.putIfAbsent(batchId, mapTaskStatus(rs));
                });

        List<ProductionBatch> batches = productionBatchRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<GraphSyncTaskStatus> result = new ArrayList<>();
        for (ProductionBatch batch : batches) {
            String batchNo = batch.getBatchNo();
            GraphSyncTaskStatus persisted = latestStatusByBatch.get(batchNo);
            if (persisted != null) {
                result.add(persisted);
                continue;
            }

            GraphCounts counts = countBatchGraph(batchNo);
            result.add(new GraphSyncTaskStatus(
                    batchNo,
                    counts.nodeCount() > 0 ? "SUCCESS" : "PENDING",
                    counts.nodeCount() > 0 ? "NEO4J_SCAN" : "NOT_SYNCED",
                    null,
                    null,
                    null,
                    counts.nodeCount(),
                    counts.relationCount()));
        }
        return result;
    }

    public GraphSyncTaskStatus retryBatch(String batchId) {
        ensureGraphSyncTaskTable();

        Long taskId = jdbcTemplate.queryForObject(
                "INSERT INTO kg.graph_sync_task (batch_id, sync_status, trigger_source, started_at) " +
                        "VALUES (?, 'RUNNING', 'MANUAL_RETRY', now()) RETURNING id",
                Long.class,
                batchId);

        try {
            syncAll();
            GraphCounts counts = countBatchGraph(batchId);
            jdbcTemplate.update(
                    "UPDATE kg.graph_sync_task SET sync_status = 'SUCCESS', finished_at = now(), " +
                            "error_message = NULL, node_count = ?, relation_count = ? WHERE id = ?",
                    counts.nodeCount(), counts.relationCount(), taskId);
            return latestTaskStatus(batchId).orElseThrow();
        } catch (Exception e) {
            jdbcTemplate.update(
                    "UPDATE kg.graph_sync_task SET sync_status = 'FAILED', finished_at = now(), error_message = ? WHERE id = ?",
                    truncate(e.getMessage(), 1000), taskId);
            return latestTaskStatus(batchId).orElse(new GraphSyncTaskStatus(
                    batchId, "FAILED", "MANUAL_RETRY", null, Instant.now(), e.getMessage(), 0, 0));
        }
    }

    private Optional<GraphSyncTaskStatus> latestTaskStatus(String batchId) {
        List<GraphSyncTaskStatus> rows = jdbcTemplate.query(
                "SELECT batch_id, sync_status, trigger_source, started_at, finished_at, error_message, node_count, relation_count " +
                        "FROM kg.graph_sync_task WHERE batch_id = ? ORDER BY started_at DESC LIMIT 1",
                (rs, rowNum) -> mapTaskStatus(rs),
                batchId);
        return rows.stream().findFirst();
    }

    private void ensureGraphSyncTaskTable() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS kg");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS kg.graph_sync_task (
                    id bigserial PRIMARY KEY,
                    batch_id varchar(128) NOT NULL,
                    sync_status varchar(32) NOT NULL,
                    trigger_source varchar(64) NOT NULL,
                    started_at timestamptz,
                    finished_at timestamptz,
                    error_message text,
                    node_count integer,
                    relation_count integer
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_graph_sync_task_batch_started ON kg.graph_sync_task(batch_id, started_at DESC)");
    }

    private GraphSyncTaskStatus mapTaskStatus(ResultSet rs) throws java.sql.SQLException {
        return new GraphSyncTaskStatus(
                rs.getString("batch_id"),
                rs.getString("sync_status"),
                rs.getString("trigger_source"),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("finished_at")),
                rs.getString("error_message"),
                (Integer) rs.getObject("node_count"),
                (Integer) rs.getObject("relation_count"));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record GraphCounts(int nodeCount, int relationCount) {}

    private GraphCounts countBatchGraph(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return new GraphCounts(0, 0);
        }

        try (Session session = neo4jDriver.session()) {
            Result result = session.run(
                    "MATCH (b) WHERE (b:ProductionBatch OR b:Batch) " +
                            "AND (b.batchNo = $batchId OR b.batchId = $batchId) " +
                            "OPTIONAL MATCH p=(b)-[*0..4]-(n) " +
                            "WITH collect(DISTINCT n) AS nodes, collect(p) AS paths " +
                            "UNWIND paths AS path " +
                            "UNWIND relationships(path) AS rel " +
                            "RETURN size(nodes) AS nodeCount, count(DISTINCT rel) AS relationCount",
                    Values.parameters("batchId", batchId));
            if (!result.hasNext()) {
                return new GraphCounts(0, 0);
            }
            org.neo4j.driver.Record record = result.next();
            return new GraphCounts(record.get("nodeCount").asInt(0), record.get("relationCount").asInt(0));
        } catch (Exception e) {
            log.warn("Failed to count Neo4j batch graph for batchId={}: {}", batchId, e.getMessage());
            return new GraphCounts(0, 0);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    // ─────────────────────────────────────────────────────
    //  NODE SYNC
    // ─────────────────────────────────────────────────────

    private Map<String, Integer> syncNodes(Session session) {
        Map<String, Integer> counts = new LinkedHashMap<>();

        counts.put("ProcessStep", syncProcessSteps(session));
        counts.put("Workstation", syncWorkstations(session));
        counts.put("Equipment", syncEquipments(session));
        counts.put("ProductType", syncProductTypes(session));
        counts.put("ParameterDef", syncParameterDefs(session));
        counts.put("ProductionBatch", syncProductionBatches(session));
        counts.put("ProductUnit", syncProductUnits(session));
        counts.put("ProcessRun", syncProcessRuns(session));
        counts.put("ParameterValue", syncParameterValues(session));
        counts.put("DefectType", syncDefectTypes(session));
        counts.put("InspectionTask", syncInspectionTasks(session));
        counts.put("DefectRecord", syncDefectRecords(session));
        counts.put("QualityMeasurement", syncQualityMeasurements(session));

        return counts;
    }

    private int syncProcessSteps(Session session) {
        List<ProcessStep> items = processStepRepo.findAll();
        int count = 0;
        for (List<ProcessStep> batch : partition(items, BATCH_SIZE)) {
            List<Map<String, Object>> rows = batch.stream()
                    .map(s -> row(
                            "stepId", s.getStepId().toString(),
                            "stepCode", s.getStepCode(),
                            "stepName", s.getStepName(),
                            "stepOrder", s.getStepOrder(),
                            "isInspection", s.getIsInspection(),
                            "description", s.getDescription()))
                    .toList();
            runBatch(session,
                    "UNWIND $rows AS row " +
                            "MERGE (n:ProcessStep {stepId: row.stepId}) " +
                            "SET n.stepCode = row.stepCode, n.stepName = row.stepName, " +
                            "n.stepOrder = row.stepOrder, n.isInspection = row.isInspection, " +
                            "n.description = row.description",
                    rows);
            count += batch.size();
        }
        return count;
    }

    private int syncWorkstations(Session session) {
        List<Workstation> items = workstationRepo.findAll();
        int count = 0;
        for (List<Workstation> batch : partition(items, BATCH_SIZE)) {
            List<Map<String, Object>> rows = batch.stream()
                    .map(w -> row(
                            "stationId", w.getStationId().toString(),
                            "stepId", w.getStepId().toString(),
                            "stationCode", w.getStationCode(),
                            "stationName", w.getStationName(),
                            "location", w.getLocation(),
                            "status", w.getStatus()))
                    .toList();
            runBatch(session,
                    "UNWIND $rows AS row " +
                            "MERGE (n:Workstation {stationId: row.stationId}) " +
                            "SET n.stepId = row.stepId, n.stationCode = row.stationCode, " +
                            "n.stationName = row.stationName, n.location = row.location, n.status = row.status",
                    rows);
            count += batch.size();
        }
        return count;
    }

    private int syncEquipments(Session session) {
        List<Equipment> items = equipmentRepo.findAll();
        int count = 0;
        for (List<Equipment> batch : partition(items, BATCH_SIZE)) {
            List<Map<String, Object>> rows = batch.stream()
                    .map(e -> row(
                            "equipmentId", e.getEquipmentId().toString(),
                            "stationId", e.getStationId() != null ? e.getStationId().toString() : null,
                            "equipmentCode", e.getEquipmentCode(),
                            "equipmentName", e.getEquipmentName(),
                            "equipmentType", e.getEquipmentType(),
                            "manufacturer", e.getManufacturer(),
                            "modelNo", e.getModelNo(),
                            "status", e.getStatus()))
                    .toList();
            runBatch(session,
                    "UNWIND $rows AS row " +
                            "MERGE (n:Equipment {equipmentId: row.equipmentId}) " +
                            "SET n.stationId = row.stationId, n.equipmentCode = row.equipmentCode, " +
                            "n.equipmentName = row.equipmentName, n.equipmentType = row.equipmentType, " +
                            "n.manufacturer = row.manufacturer, n.modelNo = row.modelNo, n.status = row.status",
                    rows);
            count += batch.size();
        }
        return count;
    }

    private int syncProductTypes(Session session) {
        List<ProductType> items = productTypeRepo.findAll();
        int count = 0;
        for (List<ProductType> batch : partition(items, BATCH_SIZE)) {
            List<Map<String, Object>> rows = batch.stream()
                    .map(pt -> row(
                            "productTypeId", pt.getProductTypeId().toString(),
                            "productCode", pt.getProductCode(),
                            "productName", pt.getProductName(),
                            "materialSystem", pt.getMaterialSystem(),
                            "specification", pt.getSpecification()))
                    .toList();
            runBatch(session,
                    "UNWIND $rows AS row " +
                            "MERGE (n:ProductType {productTypeId: row.productTypeId}) " +
                            "SET n.productCode = row.productCode, n.productName = row.productName, " +
                            "n.materialSystem = row.materialSystem, n.specification = row.specification",
                    rows);
            count += batch.size();
        }
        return count;
    }

    private int syncParameterDefs(Session session) {
        List<ParameterDef> items = parameterDefRepo.findAll();
        int count = 0;
        for (List<ParameterDef> batch : partition(items, BATCH_SIZE)) {
            List<Map<String, Object>> rows = batch.stream()
                    .map(pd -> row(
                            "paramId", pd.getParamId().toString(),
                            "stepId", pd.getStepId() != null ? pd.getStepId().toString() : null,
                            "paramCode", pd.getParamCode(),
                            "paramName", pd.getParamName(),
                            "paramCategory", pd.getParamCategory(),
                            "dataType", pd.getDataType(),
                            "unit", pd.getUnit(),
                            "sourceType", pd.getRequiredFlag() != null && pd.getRequiredFlag() ? "REQUIRED" : "OPTIONAL",
                            "requiredFlag", pd.getRequiredFlag()))
                    .toList();
            runBatch(session,
                    "UNWIND $rows AS row " +
                            "MERGE (n:ParameterDef {paramId: row.paramId}) " +
                            "SET n.stepId = row.stepId, n.paramCode = row.paramCode, " +
                            "n.paramName = row.paramName, n.paramCategory = row.paramCategory, " +
                            "n.dataType = row.dataType, n.unit = row.unit, " +
                            "n.sourceType = row.sourceType, n.requiredFlag = row.requiredFlag",
                    rows);
            count += batch.size();
        }
        return count;
    }

    private int syncProductionBatches(Session session) {
        List<ProductionBatch> items = productionBatchRepo.findAll();
        int count = 0;
        for (List<ProductionBatch> batch : partition(items, BATCH_SIZE)) {
            List<Map<String, Object>> rows = batch.stream()
                    .map(pb -> row(
                            "batchId", pb.getBatchId().toString(),
                            "batchNo", pb.getBatchNo(),
                            "productTypeId", pb.getProductTypeId().toString(),
                            "planQty", pb.getPlanQty(),
                            "actualQty", pb.getActualQty(),
                            "batchStatus", pb.getBatchStatus()))
                    .toList();
            runBatch(session,
                    "UNWIND $rows AS row " +
                            "MERGE (n:ProductionBatch {batchId: row.batchId}) " +
                            "SET n.batchNo = row.batchNo, n.productTypeId = row.productTypeId, " +
                            "n.planQty = row.planQty, n.actualQty = row.actualQty, n.batchStatus = row.batchStatus",
                    rows);
            count += batch.size();
        }
        return count;
    }

    private int syncProductUnits(Session session) {
        List<ProductUnit> items = productUnitRepo.findAll();
        int count = 0;
        for (List<ProductUnit> batch : partition(items, BATCH_SIZE)) {
            List<Map<String, Object>> rows = batch.stream()
                    .map(pu -> row(
                            "unitId", pu.getUnitId().toString(),
                            "batchId", pu.getBatchId().toString(),
                            "serialNo", pu.getSerialNo(),
                            "unitStatus", pu.getUnitStatus()))
                    .toList();
            runBatch(session,
                    "UNWIND $rows AS row " +
                            "MERGE (n:ProductUnit {unitId: row.unitId}) " +
                            "SET n.batchId = row.batchId, n.serialNo = row.serialNo, n.unitStatus = row.unitStatus",
                    rows);
            count += batch.size();
        }
        return count;
    }

    private int syncProcessRuns(Session session) {
        List<ProcessRun> items = processRunRepo.findAll();
        int count = 0;
        for (List<ProcessRun> batch : partition(items, BATCH_SIZE)) {
            List<Map<String, Object>> rows = batch.stream()
                    .map(pr -> row(
                            "runId", pr.getRunId().toString(),
                            "batchId", pr.getBatchId().toString(),
                            "unitId", pr.getUnitId() != null ? pr.getUnitId().toString() : null,
                            "stepId", pr.getStepId().toString(),
                            "stationId", pr.getStationId() != null ? pr.getStationId().toString() : null,
                            "equipmentId", pr.getEquipmentId() != null ? pr.getEquipmentId().toString() : null,
                            "runNo", pr.getRunNo(),
                            "runStatus", pr.getRunStatus()))
                    .toList();
            runBatch(session,
                    "UNWIND $rows AS row " +
                            "MERGE (n:ProcessRun {runId: row.runId}) " +
                            "SET n.batchId = row.batchId, n.unitId = row.unitId, n.stepId = row.stepId, " +
                            "n.stationId = row.stationId, n.equipmentId = row.equipmentId, " +
                            "n.runNo = row.runNo, n.runStatus = row.runStatus",
                    rows);
            count += batch.size();
        }
        return count;
    }

    private int syncParameterValues(Session session) {
        List<ParameterValue> items = parameterValueRepo.findAll();
        int count = 0;
        for (List<ParameterValue> batch : partition(items, BATCH_SIZE)) {
            List<Map<String, Object>> rows = batch.stream()
                    .map(pv -> row(
                            "valueId", pv.getValueId().toString(),
                            "runId", pv.getRunId().toString(),
                            "paramId", pv.getParamId().toString(),
                            "valueNum", pv.getValueNum() != null ? pv.getValueNum().doubleValue() : null,
                            "valueText", pv.getValueText(),
                            "qualityFlag", pv.getQualityFlag()))
                    .toList();
            runBatch(session,
                    "UNWIND $rows AS row " +
                            "MERGE (n:ParameterValue {valueId: row.valueId}) " +
                            "SET n.runId = row.runId, n.paramId = row.paramId, n.valueNum = row.valueNum, " +
                            "n.valueText = row.valueText, n.qualityFlag = row.qualityFlag",
                    rows);
            count += batch.size();
        }
        return count;
    }

    private int syncDefectTypes(Session session) {
        List<DefectType> items = defectTypeRepo.findAll();
        int count = 0;
        for (List<DefectType> batch : partition(items, BATCH_SIZE)) {
            List<Map<String, Object>> rows = batch.stream()
                    .map(dt -> row(
                            "defectTypeId", dt.getDefectTypeId().toString(),
                            "stepId", dt.getStepId() != null ? dt.getStepId().toString() : null,
                            "defectCode", dt.getDefectCode(),
                            "defectName", dt.getDefectName(),
                            "defectCategory", dt.getDefectCategory(),
                            "defaultSeverity", dt.getDefaultSeverity(),
                            "description", dt.getDescription()))
                    .toList();
            runBatch(session,
                    "UNWIND $rows AS row " +
                            "MERGE (n:DefectType {defectTypeId: row.defectTypeId}) " +
                            "SET n.stepId = row.stepId, n.defectCode = row.defectCode, " +
                            "n.defectName = row.defectName, n.defectCategory = row.defectCategory, " +
                            "n.defaultSeverity = row.defaultSeverity, n.description = row.description",
                    rows);
            count += batch.size();
        }
        return count;
    }

    private int syncInspectionTasks(Session session) {
        List<InspectionTask> items = inspectionTaskRepo.findAll();
        int count = 0;
        for (List<InspectionTask> batch : partition(items, BATCH_SIZE)) {
            List<Map<String, Object>> rows = batch.stream()
                    .map(it -> row(
                            "inspectionId", it.getInspectionId().toString(),
                            "runId", it.getRunId().toString(),
                            "unitId", it.getUnitId() != null ? it.getUnitId().toString() : null,
                            "stepId", it.getStepId().toString(),
                            "inspectionType", it.getInspectionType(),
                            "modelName", it.getModelName(),
                            "modelVersion", it.getModelVersion(),
                            "resultStatus", it.getResultStatus(),
                            "confidence", it.getConfidence() != null ? it.getConfidence().doubleValue() : null))
                    .toList();
            runBatch(session,
                    "UNWIND $rows AS row " +
                            "MERGE (n:InspectionTask {inspectionId: row.inspectionId}) " +
                            "SET n.runId = row.runId, n.unitId = row.unitId, n.stepId = row.stepId, " +
                            "n.inspectionType = row.inspectionType, n.modelName = row.modelName, " +
                            "n.modelVersion = row.modelVersion, n.resultStatus = row.resultStatus, n.confidence = row.confidence",
                    rows);
            count += batch.size();
        }
        return count;
    }

    private int syncDefectRecords(Session session) {
        List<DefectRecord> items = defectRecordRepo.findAll();
        int count = 0;
        for (List<DefectRecord> batch : partition(items, BATCH_SIZE)) {
            List<Map<String, Object>> rows = batch.stream()
                    .map(dr -> row(
                            "defectId", dr.getDefectId().toString(),
                            "inspectionId", dr.getInspectionId().toString(),
                            "unitId", dr.getUnitId() != null ? dr.getUnitId().toString() : null,
                            "defectTypeId", dr.getDefectTypeId().toString(),
                            "defectCount", dr.getDefectCount(),
                            "confidence", dr.getConfidence() != null ? dr.getConfidence().doubleValue() : null,
                            "severityLevel", dr.getSeverityLevel(),
                            "isCritical", dr.getIsCritical()))
                    .toList();
            runBatch(session,
                    "UNWIND $rows AS row " +
                            "MERGE (n:DefectRecord {defectId: row.defectId}) " +
                            "SET n.inspectionId = row.inspectionId, n.unitId = row.unitId, " +
                            "n.defectTypeId = row.defectTypeId, n.defectCount = row.defectCount, " +
                            "n.confidence = row.confidence, n.severityLevel = row.severityLevel, n.isCritical = row.isCritical",
                    rows);
            count += batch.size();
        }
        return count;
    }

    private int syncQualityMeasurements(Session session) {
        List<QualityMeasurement> items = qualityMeasurementRepo.findAll();
        int count = 0;
        for (List<QualityMeasurement> batch : partition(items, BATCH_SIZE)) {
            List<Map<String, Object>> rows = batch.stream()
                    .map(qm -> row(
                            "measurementId", qm.getMeasurementId().toString(),
                            "runId", qm.getRunId().toString(),
                            "unitId", qm.getUnitId() != null ? qm.getUnitId().toString() : null,
                            "metricId", qm.getMetricId().toString(),
                            "valueNum", qm.getValueNum() != null ? qm.getValueNum().doubleValue() : null,
                            "valueText", qm.getValueText(),
                            "isPass", qm.getIsPass(),
                            "deviationValue", qm.getDeviationValue() != null ? qm.getDeviationValue().doubleValue() : null,
                            "measurementMethod", qm.getMeasurementMethod()))
                    .toList();
            runBatch(session,
                    "UNWIND $rows AS row " +
                            "MERGE (n:QualityMeasurement {measurementId: row.measurementId}) " +
                            "SET n.runId = row.runId, n.unitId = row.unitId, n.metricId = row.metricId, " +
                            "n.valueNum = row.valueNum, n.valueText = row.valueText, n.isPass = row.isPass, " +
                            "n.deviationValue = row.deviationValue, n.measurementMethod = row.measurementMethod",
                    rows);
            count += batch.size();
        }
        return count;
    }

    // ─────────────────────────────────────────────────────
    //  RELATIONSHIP SYNC
    // ─────────────────────────────────────────────────────

    private Map<String, Integer> syncRelationships(Session session) {
        Map<String, Integer> counts = new LinkedHashMap<>();

        // 1. ProcessStep -[:HAS_WORKSTATION]-> Workstation
        counts.put("HAS_WORKSTATION", createRelationshipsViaProperty(
                session, "Workstation", "stationId", "stepId",
                "ProcessStep", "stepId", "HAS_WORKSTATION"));

        // 2. Workstation -[:HAS_EQUIPMENT]-> Equipment
        counts.put("HAS_EQUIPMENT", createRelationshipsViaProperty(
                session, "Equipment", "equipmentId", "stationId",
                "Workstation", "stationId", "HAS_EQUIPMENT"));

        // 3. ProcessStep -[:HAS_PARAMETER]-> ParameterDef
        counts.put("HAS_PARAMETER", createRelationshipsViaProperty(
                session, "ParameterDef", "paramId", "stepId",
                "ProcessStep", "stepId", "HAS_PARAMETER"));

        // 4. ProductionBatch -[:HAS_UNIT]-> ProductUnit
        counts.put("HAS_UNIT", createRelationshipsViaProperty(
                session, "ProductUnit", "unitId", "batchId",
                "ProductionBatch", "batchId", "HAS_UNIT"));

        // 5. ProductUnit -[:HAS_RUN]-> ProcessRun
        counts.put("HAS_RUN", createRelationshipsViaProperty(
                session, "ProcessRun", "runId", "unitId",
                "ProductUnit", "unitId", "HAS_RUN"));

        // 6. ProcessRun -[:HAS_PARAM_VALUE]-> ParameterValue
        counts.put("HAS_PARAM_VALUE", createRelationshipsViaProperty(
                session, "ParameterValue", "valueId", "runId",
                "ProcessRun", "runId", "HAS_PARAM_VALUE"));

        // 7. ProcessRun -[:HAS_INSPECTION]-> InspectionTask
        counts.put("HAS_INSPECTION", createRelationshipsViaProperty(
                session, "InspectionTask", "inspectionId", "runId",
                "ProcessRun", "runId", "HAS_INSPECTION"));

        // 8. InspectionTask -[:FOUND_DEFECT]-> DefectRecord
        counts.put("FOUND_DEFECT", createRelationshipsViaProperty(
                session, "DefectRecord", "defectId", "inspectionId",
                "InspectionTask", "inspectionId", "FOUND_DEFECT"));

        // 9. InspectionTask -[:HAS_MEASUREMENT]-> QualityMeasurement
        //    QualityMeasurement.runId -> ProcessRun.runId, but we link via InspectionTask
        //    We match InspectionTask.runId = QualityMeasurement.runId
        counts.put("HAS_MEASUREMENT", createMeasurementRelationships(session));

        // 10. DefectRecord -[:OF_TYPE]-> DefectType
        counts.put("OF_TYPE", createRelationshipsViaProperty(
                session, "DefectRecord", "defectId", "defectTypeId",
                "DefectType", "defectTypeId", "OF_TYPE"));

        // 11. ParameterValue -[:OF_PARAMETER]-> ParameterDef
        counts.put("OF_PARAMETER", createRelationshipsViaProperty(
                session, "ParameterValue", "valueId", "paramId",
                "ParameterDef", "paramId", "OF_PARAMETER"));

        return counts;
    }

    /**
     * Generic method to create relationships by matching a foreign-key property on the source node
     * to the key property on the target node.
     *
     * For sourceLabel/sourceKey/fkProperty -> targetLabel/targetKey via relType
     */
    private int createRelationshipsViaProperty(
            Session session,
            String sourceLabel, String sourceKey, String fkProperty,
            String targetLabel, String targetKey, String relType) {

        // First, create an index on target key for better performance
        try {
            session.run(String.format(
                    "CREATE INDEX IF NOT EXISTS FOR (n:%s) ON (n.%s)", targetLabel, targetKey));
        } catch (Exception ignored) {
            // Index may already exist
        }

        int count = 0;
        String query = String.format("MATCH (n:%s) RETURN n.%s AS srcKey, n.%s AS fkVal",
                sourceLabel, sourceKey, fkProperty);

        Result result = session.run(query);
        List<org.neo4j.driver.Record> records = result.list();
        String relCypher = String.format(
                "UNWIND $rows AS row " +
                        "MATCH (a:%s {%s: row.srcKey}) " +
                        "MATCH (b:%s {%s: row.tgtKey}) " +
                        "MERGE (a)-[:%s]->(b)",
                sourceLabel, sourceKey,
                targetLabel, targetKey,
                relType);

        for (List<org.neo4j.driver.Record> batch : partition(records, BATCH_SIZE)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (org.neo4j.driver.Record rec : batch) {
                Value fkVal = rec.get("fkVal");
                if (fkVal.isNull()) continue;
                rows.add(row("srcKey", rec.get("srcKey").asString(), "tgtKey", fkVal.asString()));
            }
            if (!rows.isEmpty()) {
                runBatch(session, relCypher, rows);
                count += rows.size();
            }
        }
        return count;
    }

    /**
     * Special method for HAS_MEASUREMENT: InspectionTask -> QualityMeasurement
     * Matches via runId: InspectionTask.runId = QualityMeasurement.runId
     */
    private int createMeasurementRelationships(Session session) {
        Result result = session.run(
                "MATCH (i:InspectionTask) " +
                        "MATCH (qm:QualityMeasurement {runId: i.runId}) " +
                        "MERGE (i)-[:HAS_MEASUREMENT]->(qm) " +
                        "RETURN count(*) AS count");
        return result.single().get("count").asInt(0);
    }

    // ─────────────────────────────────────────────────────
    //  UTILITY
    // ─────────────────────────────────────────────────────

    private static <T> List<List<T>> partition(List<T> list, int size) {
        if (list.isEmpty()) return Collections.emptyList();
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    private void runBatch(Session session, String cypher, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        session.run(cypher, Values.parameters("rows", rows)).consume();
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put((String) values[i], values[i + 1]);
        }
        return row;
    }
}
