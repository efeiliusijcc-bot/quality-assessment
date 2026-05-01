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
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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
            Driver neo4jDriver) {
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
    }

    /**
     * Full sync: reads all data from PostgreSQL and writes to Neo4j.
     * Returns a summary map with counts for each node/relationship type.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> syncAll() {
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
            try (Transaction tx = session.beginTransaction()) {
                for (ProcessStep s : batch) {
                    tx.run("MERGE (n:ProcessStep {stepId: $stepId}) " +
                                    "SET n.stepCode = $stepCode, n.stepName = $stepName, " +
                                    "n.stepOrder = $stepOrder, n.isInspection = $isInspection, " +
                                    "n.description = $description",
                            Values.parameters(
                                    "stepId", s.getStepId().toString(),
                                    "stepCode", s.getStepCode(),
                                    "stepName", s.getStepName(),
                                    "stepOrder", s.getStepOrder(),
                                    "isInspection", s.getIsInspection(),
                                    "description", s.getDescription()
                            ));
                }
                tx.commit();
                count += batch.size();
            }
        }
        return count;
    }

    private int syncWorkstations(Session session) {
        List<Workstation> items = workstationRepo.findAll();
        int count = 0;
        for (List<Workstation> batch : partition(items, BATCH_SIZE)) {
            try (Transaction tx = session.beginTransaction()) {
                for (Workstation w : batch) {
                    tx.run("MERGE (n:Workstation {stationId: $stationId}) " +
                                    "SET n.stepId = $stepId, n.stationCode = $stationCode, " +
                                    "n.stationName = $stationName, n.location = $location, " +
                                    "n.status = $status",
                            Values.parameters(
                                    "stationId", w.getStationId().toString(),
                                    "stepId", w.getStepId().toString(),
                                    "stationCode", w.getStationCode(),
                                    "stationName", w.getStationName(),
                                    "location", w.getLocation(),
                                    "status", w.getStatus()
                            ));
                }
                tx.commit();
                count += batch.size();
            }
        }
        return count;
    }

    private int syncEquipments(Session session) {
        List<Equipment> items = equipmentRepo.findAll();
        int count = 0;
        for (List<Equipment> batch : partition(items, BATCH_SIZE)) {
            try (Transaction tx = session.beginTransaction()) {
                for (Equipment e : batch) {
                    tx.run("MERGE (n:Equipment {equipmentId: $equipmentId}) " +
                                    "SET n.stationId = $stationId, n.equipmentCode = $equipmentCode, " +
                                    "n.equipmentName = $equipmentName, n.equipmentType = $equipmentType, " +
                                    "n.manufacturer = $manufacturer, n.modelNo = $modelNo, " +
                                    "n.status = $status",
                            Values.parameters(
                                    "equipmentId", e.getEquipmentId().toString(),
                                    "stationId", e.getStationId() != null ? e.getStationId().toString() : null,
                                    "equipmentCode", e.getEquipmentCode(),
                                    "equipmentName", e.getEquipmentName(),
                                    "equipmentType", e.getEquipmentType(),
                                    "manufacturer", e.getManufacturer(),
                                    "modelNo", e.getModelNo(),
                                    "status", e.getStatus()
                            ));
                }
                tx.commit();
                count += batch.size();
            }
        }
        return count;
    }

    private int syncProductTypes(Session session) {
        List<ProductType> items = productTypeRepo.findAll();
        int count = 0;
        for (List<ProductType> batch : partition(items, BATCH_SIZE)) {
            try (Transaction tx = session.beginTransaction()) {
                for (ProductType pt : batch) {
                    tx.run("MERGE (n:ProductType {productTypeId: $productTypeId}) " +
                                    "SET n.productCode = $productCode, n.productName = $productName, " +
                                    "n.materialSystem = $materialSystem, n.specification = $specification",
                            Values.parameters(
                                    "productTypeId", pt.getProductTypeId().toString(),
                                    "productCode", pt.getProductCode(),
                                    "productName", pt.getProductName(),
                                    "materialSystem", pt.getMaterialSystem(),
                                    "specification", pt.getSpecification()
                            ));
                }
                tx.commit();
                count += batch.size();
            }
        }
        return count;
    }

    private int syncParameterDefs(Session session) {
        List<ParameterDef> items = parameterDefRepo.findAll();
        int count = 0;
        for (List<ParameterDef> batch : partition(items, BATCH_SIZE)) {
            try (Transaction tx = session.beginTransaction()) {
                for (ParameterDef pd : batch) {
                    tx.run("MERGE (n:ParameterDef {paramId: $paramId}) " +
                                    "SET n.stepId = $stepId, n.paramCode = $paramCode, " +
                                    "n.paramName = $paramName, n.paramCategory = $paramCategory, " +
                                    "n.dataType = $dataType, n.unit = $unit, " +
                                    "n.sourceType = $sourceType, n.requiredFlag = $requiredFlag",
                            Values.parameters(
                                    "paramId", pd.getParamId().toString(),
                                    "stepId", pd.getStepId() != null ? pd.getStepId().toString() : null,
                                    "paramCode", pd.getParamCode(),
                                    "paramName", pd.getParamName(),
                                    "paramCategory", pd.getParamCategory(),
                                    "dataType", pd.getDataType(),
                                    "unit", pd.getUnit(),
                                    "sourceType", pd.getRequiredFlag() != null && pd.getRequiredFlag() ? "REQUIRED" : "OPTIONAL",
                                    "requiredFlag", pd.getRequiredFlag()
                            ));
                }
                tx.commit();
                count += batch.size();
            }
        }
        return count;
    }

    private int syncProductionBatches(Session session) {
        List<ProductionBatch> items = productionBatchRepo.findAll();
        int count = 0;
        for (List<ProductionBatch> batch : partition(items, BATCH_SIZE)) {
            try (Transaction tx = session.beginTransaction()) {
                for (ProductionBatch pb : batch) {
                    tx.run("MERGE (n:ProductionBatch {batchId: $batchId}) " +
                                    "SET n.batchNo = $batchNo, n.productTypeId = $productTypeId, " +
                                    "n.planQty = $planQty, n.actualQty = $actualQty, " +
                                    "n.batchStatus = $batchStatus",
                            Values.parameters(
                                    "batchId", pb.getBatchId().toString(),
                                    "batchNo", pb.getBatchNo(),
                                    "productTypeId", pb.getProductTypeId().toString(),
                                    "planQty", pb.getPlanQty(),
                                    "actualQty", pb.getActualQty(),
                                    "batchStatus", pb.getBatchStatus()
                            ));
                }
                tx.commit();
                count += batch.size();
            }
        }
        return count;
    }

    private int syncProductUnits(Session session) {
        List<ProductUnit> items = productUnitRepo.findAll();
        int count = 0;
        for (List<ProductUnit> batch : partition(items, BATCH_SIZE)) {
            try (Transaction tx = session.beginTransaction()) {
                for (ProductUnit pu : batch) {
                    tx.run("MERGE (n:ProductUnit {unitId: $unitId}) " +
                                    "SET n.batchId = $batchId, n.serialNo = $serialNo, " +
                                    "n.unitStatus = $unitStatus",
                            Values.parameters(
                                    "unitId", pu.getUnitId().toString(),
                                    "batchId", pu.getBatchId().toString(),
                                    "serialNo", pu.getSerialNo(),
                                    "unitStatus", pu.getUnitStatus()
                            ));
                }
                tx.commit();
                count += batch.size();
            }
        }
        return count;
    }

    private int syncProcessRuns(Session session) {
        List<ProcessRun> items = processRunRepo.findAll();
        int count = 0;
        for (List<ProcessRun> batch : partition(items, BATCH_SIZE)) {
            try (Transaction tx = session.beginTransaction()) {
                for (ProcessRun pr : batch) {
                    tx.run("MERGE (n:ProcessRun {runId: $runId}) " +
                                    "SET n.batchId = $batchId, n.unitId = $unitId, " +
                                    "n.stepId = $stepId, n.stationId = $stationId, " +
                                    "n.equipmentId = $equipmentId, n.runNo = $runNo, " +
                                    "n.runStatus = $runStatus",
                            Values.parameters(
                                    "runId", pr.getRunId().toString(),
                                    "batchId", pr.getBatchId().toString(),
                                    "unitId", pr.getUnitId() != null ? pr.getUnitId().toString() : null,
                                    "stepId", pr.getStepId().toString(),
                                    "stationId", pr.getStationId() != null ? pr.getStationId().toString() : null,
                                    "equipmentId", pr.getEquipmentId() != null ? pr.getEquipmentId().toString() : null,
                                    "runNo", pr.getRunNo(),
                                    "runStatus", pr.getRunStatus()
                            ));
                }
                tx.commit();
                count += batch.size();
            }
        }
        return count;
    }

    private int syncParameterValues(Session session) {
        List<ParameterValue> items = parameterValueRepo.findAll();
        int count = 0;
        for (List<ParameterValue> batch : partition(items, BATCH_SIZE)) {
            try (Transaction tx = session.beginTransaction()) {
                for (ParameterValue pv : batch) {
                    tx.run("MERGE (n:ParameterValue {valueId: $valueId}) " +
                                    "SET n.runId = $runId, n.paramId = $paramId, " +
                                    "n.valueNum = $valueNum, n.valueText = $valueText, " +
                                    "n.qualityFlag = $qualityFlag",
                            Values.parameters(
                                    "valueId", pv.getValueId().toString(),
                                    "runId", pv.getRunId().toString(),
                                    "paramId", pv.getParamId().toString(),
                                    "valueNum", pv.getValueNum() != null ? pv.getValueNum().doubleValue() : null,
                                    "valueText", pv.getValueText(),
                                    "qualityFlag", pv.getQualityFlag()
                            ));
                }
                tx.commit();
                count += batch.size();
            }
        }
        return count;
    }

    private int syncDefectTypes(Session session) {
        List<DefectType> items = defectTypeRepo.findAll();
        int count = 0;
        for (List<DefectType> batch : partition(items, BATCH_SIZE)) {
            try (Transaction tx = session.beginTransaction()) {
                for (DefectType dt : batch) {
                    tx.run("MERGE (n:DefectType {defectTypeId: $defectTypeId}) " +
                                    "SET n.stepId = $stepId, n.defectCode = $defectCode, " +
                                    "n.defectName = $defectName, n.defectCategory = $defectCategory, " +
                                    "n.defaultSeverity = $defaultSeverity, n.description = $description",
                            Values.parameters(
                                    "defectTypeId", dt.getDefectTypeId().toString(),
                                    "stepId", dt.getStepId() != null ? dt.getStepId().toString() : null,
                                    "defectCode", dt.getDefectCode(),
                                    "defectName", dt.getDefectName(),
                                    "defectCategory", dt.getDefectCategory(),
                                    "defaultSeverity", dt.getDefaultSeverity(),
                                    "description", dt.getDescription()
                            ));
                }
                tx.commit();
                count += batch.size();
            }
        }
        return count;
    }

    private int syncInspectionTasks(Session session) {
        List<InspectionTask> items = inspectionTaskRepo.findAll();
        int count = 0;
        for (List<InspectionTask> batch : partition(items, BATCH_SIZE)) {
            try (Transaction tx = session.beginTransaction()) {
                for (InspectionTask it : batch) {
                    tx.run("MERGE (n:InspectionTask {inspectionId: $inspectionId}) " +
                                    "SET n.runId = $runId, n.unitId = $unitId, " +
                                    "n.stepId = $stepId, n.inspectionType = $inspectionType, " +
                                    "n.modelName = $modelName, n.modelVersion = $modelVersion, " +
                                    "n.resultStatus = $resultStatus, n.confidence = $confidence",
                            Values.parameters(
                                    "inspectionId", it.getInspectionId().toString(),
                                    "runId", it.getRunId().toString(),
                                    "unitId", it.getUnitId() != null ? it.getUnitId().toString() : null,
                                    "stepId", it.getStepId().toString(),
                                    "inspectionType", it.getInspectionType(),
                                    "modelName", it.getModelName(),
                                    "modelVersion", it.getModelVersion(),
                                    "resultStatus", it.getResultStatus(),
                                    "confidence", it.getConfidence() != null ? it.getConfidence().doubleValue() : null
                            ));
                }
                tx.commit();
                count += batch.size();
            }
        }
        return count;
    }

    private int syncDefectRecords(Session session) {
        List<DefectRecord> items = defectRecordRepo.findAll();
        int count = 0;
        for (List<DefectRecord> batch : partition(items, BATCH_SIZE)) {
            try (Transaction tx = session.beginTransaction()) {
                for (DefectRecord dr : batch) {
                    tx.run("MERGE (n:DefectRecord {defectId: $defectId}) " +
                                    "SET n.inspectionId = $inspectionId, n.unitId = $unitId, " +
                                    "n.defectTypeId = $defectTypeId, n.defectCount = $defectCount, " +
                                    "n.confidence = $confidence, n.severityLevel = $severityLevel, " +
                                    "n.isCritical = $isCritical",
                            Values.parameters(
                                    "defectId", dr.getDefectId().toString(),
                                    "inspectionId", dr.getInspectionId().toString(),
                                    "unitId", dr.getUnitId() != null ? dr.getUnitId().toString() : null,
                                    "defectTypeId", dr.getDefectTypeId().toString(),
                                    "defectCount", dr.getDefectCount(),
                                    "confidence", dr.getConfidence() != null ? dr.getConfidence().doubleValue() : null,
                                    "severityLevel", dr.getSeverityLevel(),
                                    "isCritical", dr.getIsCritical()
                            ));
                }
                tx.commit();
                count += batch.size();
            }
        }
        return count;
    }

    private int syncQualityMeasurements(Session session) {
        List<QualityMeasurement> items = qualityMeasurementRepo.findAll();
        int count = 0;
        for (List<QualityMeasurement> batch : partition(items, BATCH_SIZE)) {
            try (Transaction tx = session.beginTransaction()) {
                for (QualityMeasurement qm : batch) {
                    tx.run("MERGE (n:QualityMeasurement {measurementId: $measurementId}) " +
                                    "SET n.runId = $runId, n.unitId = $unitId, " +
                                    "n.metricId = $metricId, n.valueNum = $valueNum, " +
                                    "n.valueText = $valueText, n.isPass = $isPass, " +
                                    "n.deviationValue = $deviationValue, n.measurementMethod = $measurementMethod",
                            Values.parameters(
                                    "measurementId", qm.getMeasurementId().toString(),
                                    "runId", qm.getRunId().toString(),
                                    "unitId", qm.getUnitId() != null ? qm.getUnitId().toString() : null,
                                    "metricId", qm.getMetricId().toString(),
                                    "valueNum", qm.getValueNum() != null ? qm.getValueNum().doubleValue() : null,
                                    "valueText", qm.getValueText(),
                                    "isPass", qm.getIsPass(),
                                    "deviationValue", qm.getDeviationValue() != null ? qm.getDeviationValue().doubleValue() : null,
                                    "measurementMethod", qm.getMeasurementMethod()
                            ));
                }
                tx.commit();
                count += batch.size();
            }
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

        // Use UNWIND approach for batch relationship creation
        String relCypher = String.format(
                "MATCH (a:%s {%s: $srcKey}) " +
                        "MATCH (b:%s {%s: $tgtKey}) " +
                        "MERGE (a)-[:%s]->(b)",
                sourceLabel, sourceKey,
                targetLabel, targetKey,
                relType);

        int count = 0;
        String query = String.format("MATCH (n:%s) RETURN n.%s AS srcKey, n.%s AS fkVal",
                sourceLabel, sourceKey, fkProperty);

        Result result = session.run(query);
        List<org.neo4j.driver.Record> records = result.list();

        for (List<org.neo4j.driver.Record> batch : partition(records, BATCH_SIZE)) {
            try (Transaction tx = session.beginTransaction()) {
                for (org.neo4j.driver.Record rec : batch) {
                    String srcKeyValue = rec.get("srcKey").asString();
                    Value fkVal = rec.get("fkVal");
                    if (fkVal.isNull()) continue;

                    String fkValue = fkVal.asString();
                    tx.run(relCypher,
                            Values.parameters("srcKey", srcKeyValue, "tgtKey", fkValue));
                    count++;
                }
                tx.commit();
            }
        }
        return count;
    }

    /**
     * Special method for HAS_MEASUREMENT: InspectionTask -> QualityMeasurement
     * Matches via runId: InspectionTask.runId = QualityMeasurement.runId
     */
    private int createMeasurementRelationships(Session session) {
        String cypher =
                "MATCH (i:InspectionTask) " +
                        "MATCH (qm:QualityMeasurement {runId: i.runId}) " +
                        "MERGE (i)-[:HAS_MEASUREMENT]->(qm)";

        int count = 0;
        try (Transaction tx = session.beginTransaction()) {
            Result result = tx.run("MATCH (i:InspectionTask) RETURN i.runId AS runId");
            List<org.neo4j.driver.Record> records = result.list();

            for (List<org.neo4j.driver.Record> batch : partition(records, BATCH_SIZE)) {
                try (Transaction batchTx = session.beginTransaction()) {
                    for (org.neo4j.driver.Record rec : batch) {
                        String runId = rec.get("runId").asString();
                        batchTx.run(
                                "MATCH (i:InspectionTask {runId: $runId}) " +
                                        "MATCH (qm:QualityMeasurement {runId: $runId}) " +
                                        "MERGE (i)-[:HAS_MEASUREMENT]->(qm)",
                                Values.parameters("runId", runId));
                        count++;
                    }
                    batchTx.commit();
                }
            }
            tx.commit();
        }
        return count;
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
}
