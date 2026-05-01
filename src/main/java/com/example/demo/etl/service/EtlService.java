package com.example.demo.etl.service;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.core.domain.ParameterDef;
import com.example.demo.core.domain.ProcessStep;
import com.example.demo.core.repository.ParameterDefRepository;
import com.example.demo.core.repository.ProcessStepRepository;
import com.example.demo.etl.domain.CleaningLog;
import com.example.demo.etl.domain.CleaningRule;
import com.example.demo.etl.domain.ImportJob;
import com.example.demo.etl.dto.EtlDtos.*;
import com.example.demo.etl.repository.CleaningLogRepository;
import com.example.demo.etl.repository.CleaningRuleRepository;
import com.example.demo.etl.repository.ImportJobRepository;
import com.example.demo.prod.domain.ParameterValue;
import com.example.demo.prod.domain.ProcessRun;
import com.example.demo.prod.domain.ProductionBatch;
import com.example.demo.prod.repository.ParameterValueRepository;
import com.example.demo.prod.repository.ProcessRunRepository;
import com.example.demo.prod.repository.ProductionBatchRepository;
import com.example.demo.qc.domain.DefectRecord;
import com.example.demo.qc.domain.DefectType;
import com.example.demo.qc.repository.DefectRecordRepository;
import com.example.demo.qc.repository.DefectTypeRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@Transactional
public class EtlService {

    private static final Logger log = LoggerFactory.getLogger(EtlService.class);

    private final ImportJobRepository importJobRepository;
    private final CleaningRuleRepository cleaningRuleRepository;
    private final CleaningLogRepository cleaningLogRepository;
    private final ProductionBatchRepository productionBatchRepository;
    private final ProcessStepRepository processStepRepository;
    private final ProcessRunRepository processRunRepository;
    private final ParameterDefRepository parameterDefRepository;
    private final ParameterValueRepository parameterValueRepository;
    private final DefectTypeRepository defectTypeRepository;
    private final DefectRecordRepository defectRecordRepository;
    private final JdbcTemplate jdbcTemplate;

    public EtlService(
            ImportJobRepository importJobRepository,
            CleaningRuleRepository cleaningRuleRepository,
            CleaningLogRepository cleaningLogRepository,
            ProductionBatchRepository productionBatchRepository,
            ProcessStepRepository processStepRepository,
            ProcessRunRepository processRunRepository,
            ParameterDefRepository parameterDefRepository,
            ParameterValueRepository parameterValueRepository,
            DefectTypeRepository defectTypeRepository,
            DefectRecordRepository defectRecordRepository,
            JdbcTemplate jdbcTemplate) {
        this.importJobRepository = importJobRepository;
        this.cleaningRuleRepository = cleaningRuleRepository;
        this.cleaningLogRepository = cleaningLogRepository;
        this.productionBatchRepository = productionBatchRepository;
        this.processStepRepository = processStepRepository;
        this.processRunRepository = processRunRepository;
        this.parameterDefRepository = parameterDefRepository;
        this.parameterValueRepository = parameterValueRepository;
        this.defectTypeRepository = defectTypeRepository;
        this.defectRecordRepository = defectRecordRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===== ImportJob CRUD =====

    public ImportJobResponse createImportJob(CreateImportJobRequest request) {
        ImportJob job = new ImportJob(request.sourceType(), request.sourceName());
        if (request.fileId() != null) {
            job.setFileId(request.fileId());
        }
        if (request.targetTable() != null) {
            job.setTargetTable(request.targetTable());
        }
        importJobRepository.save(job);
        return toImportJobResponse(job);
    }

    @Transactional(readOnly = true)
    public ImportJobResponse getImportJob(UUID importId) {
        return toImportJobResponse(requireImportJob(importId));
    }

    @Transactional(readOnly = true)
    public List<ImportJobResponse> listImportJobs() {
        return importJobRepository.findAll(Sort.by(Sort.Direction.DESC, "startedAt"))
                .stream().map(this::toImportJobResponse).toList();
    }

    // ===== CleaningRule CRUD =====

    public CleaningRuleResponse createCleaningRule(CreateCleaningRuleRequest request) {
        CleaningRule rule = new CleaningRule(request.ruleCode(), request.ruleName(),
                request.conditionExpr(), request.actionExpr());
        if (request.targetCategory() != null) {
            rule.setTargetCategory(request.targetCategory());
        }
        if (request.priorityNo() != null) {
            rule.setPriorityNo(request.priorityNo());
        }
        cleaningRuleRepository.save(rule);
        return toCleaningRuleResponse(rule);
    }

    @Transactional(readOnly = true)
    public CleaningRuleResponse getCleaningRule(UUID ruleId) {
        return toCleaningRuleResponse(requireCleaningRule(ruleId));
    }

    @Transactional(readOnly = true)
    public List<CleaningRuleResponse> listCleaningRules() {
        return cleaningRuleRepository.findAll().stream()
                .map(this::toCleaningRuleResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CleaningRuleResponse> listCleaningRulesByTargetCategory(String targetCategory) {
        return cleaningRuleRepository.findByTargetCategory(targetCategory).stream()
                .map(this::toCleaningRuleResponse).toList();
    }

    // ===== CleaningLog CRUD =====

    @Transactional(readOnly = true)
    public List<CleaningLogResponse> listCleaningLogs() {
        return cleaningLogRepository.findAll().stream()
                .map(this::toCleaningLogResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CleaningLogResponse> listCleaningLogsByRuleId(UUID ruleId) {
        return cleaningLogRepository.findByRuleId(ruleId).stream()
                .map(this::toCleaningLogResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CleaningLogResponse> listCleaningLogsBySource(String sourceTable, UUID sourceId) {
        List<CleaningLog> logs = sourceTable != null
                ? cleaningLogRepository.findBySourceTable(sourceTable)
                : cleaningLogRepository.findAll();
        return logs.stream()
                .filter(l -> sourceId == null || sourceId.equals(l.getSourceId()))
                .map(this::toCleaningLogResponse).toList();
    }

    // ===== Business Methods =====

    public OnlineUploadResult submitOnlineUpload(OnlineUploadPayload payload) {
        ImportJob job = new ImportJob("ONLINE", payload.station());
        job.setTargetTable("process_setting");
        importJobRepository.save(job);
        return new OnlineUploadResult(
                job.getImportId().toString(),
                payload.station(),
                payload.batchNo(),
                payload.deviceId(),
                payload.frequency(),
                payload.mapping()
        );
    }

    public ManualRecordResult submitManualRecord(ManualRecordPayload payload) {
        // Find batch by batchNo
        ProductionBatch batch = productionBatchRepository.findByBatchNo(payload.batchNo())
                .orElseThrow(() -> new BusinessException(404, "batch not found: " + payload.batchNo()));

        // Find a process step (use first available step as default)
        List<ProcessStep> steps = processStepRepository.findAll();
        if (steps.isEmpty()) {
            throw new BusinessException(400, "no process step configured");
        }
        ProcessStep step = steps.get(0);

        // Create process run
        ProcessRun run = new ProcessRun(batch.getBatchId(), step.getStepId());
        run.setStartTime(Instant.now());
        run.setRunStatus("COMPLETED");
        processRunRepository.save(run);

        // Save numeric parameters
        Map<String, Double> paramMap = new LinkedHashMap<>();
        paramMap.put("temperature", payload.temperature());
        paramMap.put("pressure", payload.pressure());
        paramMap.put("beltSpeed", payload.beltSpeed());
        paramMap.put("o2Ppm", payload.o2Ppm());
        paramMap.put("humidity", payload.humidity());
        paramMap.put("currentValue", payload.currentValue());

        for (Map.Entry<String, Double> entry : paramMap.entrySet()) {
            parameterDefRepository.findByStepIdAndParamCodeAndParamCategory(
                            step.getStepId(), entry.getKey(), "PROCESS")
                    .ifPresent(paramDef -> {
                        ParameterValue pv = new ParameterValue(
                                run.getRunId(), paramDef.getParamId(),
                                BigDecimal.valueOf(entry.getValue()));
                        parameterValueRepository.save(pv);
                    });
        }

        // Create defect record if defectType is provided
        if (payload.defectType() != null && !payload.defectType().isBlank()) {
            defectTypeRepository.findByStepIdAndDefectCode(step.getStepId(), payload.defectType())
                    .ifPresent(defectType -> {
                        DefectRecord dr = new DefectRecord(run.getRunId(), defectType.getDefectTypeId());
                        if (payload.defectConfidence() > 0) {
                            dr.setConfidence(BigDecimal.valueOf(payload.defectConfidence()));
                        }
                        defectRecordRepository.save(dr);
                    });
        }

        return new ManualRecordResult(
                run.getRunId().toString(),
                "Manual record submitted successfully"
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ManufacturingImportSummary importManufacturingData(MultipartFile file) {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        ImportJob job = new ImportJob("EXCEL", fileName);
        job.setTargetTable("manufacturing_data");
        job.setImportStatus("RUNNING");
        importJobRepository.save(job);

        int processSettingCount = 0;
        int equipmentOperationCount = 0;
        int qualityDefectCount = 0;
        int coreDataCount = 0;
        int evalDataCount = 0;
        int kgDataCount = 0;
        int totalRows = 0;
        int errorRows = 0;
        List<String> errorMessages = new ArrayList<>();

        try (InputStream is = file.getInputStream(); Workbook wb = new XSSFWorkbook(is)) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                String sheetName = sheet.getSheetName().toLowerCase();
                if (sheet.getPhysicalNumberOfRows() <= 1) continue;

                // Read header
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) continue;
                List<String> headers = new ArrayList<>();
                for (Cell c : headerRow) {
                    headers.add(getCellStringValue(c).toLowerCase().replace("_", ""));
                }

                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    try {
                        Map<String, String> cells = new LinkedHashMap<>();
                        for (int ci = 0; ci < headers.size(); ci++) {
                            cells.put(headers.get(ci), ci < row.getLastCellNum() ? getCellStringValue(row.getCell(ci)) : "");
                        }

                        switch (sheetName) {
                            case "production_batch": {
                                UUID productTypeId = parseUUID(cells.get("producttypeid"));
                                if (productTypeId == null) productTypeId = UUID.fromString("00000000-0000-0000-0000-000000000001");
                                insertIgnoreDuplicate(
                                    "INSERT INTO prod.production_batch (batch_id, batch_no, product_type_id, plan_qty, actual_qty, start_time, end_time, batch_status, created_at, metadata) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                                    parseUUID(cells.get("batchid")), cells.get("batchno"), productTypeId,
                                    parseInt(cells.get("planqty")), parseInt(cells.get("actualqty")),
                                    parseTimestamp(cells.get("starttime")), parseTimestamp(cells.get("endtime")),
                                    coalesce(cells.get("batchstatus"), "CREATED"),                                    parseTimestampOrNow(cells.get("createdat")), "{}");
                                totalRows++;
                                processSettingCount++;
                                break;
                            }
                            case "product_unit": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO prod.product_unit (unit_id, batch_id, serial_no, current_step_id, unit_status, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("unitid")), parseUUID(cells.get("batchid")),
                                    cells.get("serialno"), parseUUID(cells.get("currentstepid")),
                                    coalesce(cells.get("unitstatus"), "CREATED"),                                    parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
                                processSettingCount++;
                                break;
                            }
                            case "process_run": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO prod.process_run (run_id, batch_id, unit_id, step_id, station_id, equipment_id, recipe_id, operator_id, run_no, start_time, end_time, run_status, created_at, context_json) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                                    parseUUID(cells.get("runid")), parseUUID(cells.get("batchid")), parseUUID(cells.get("unitid")),
                                    parseUUID(cells.get("stepid")), parseUUID(cells.get("stationid")), parseUUID(cells.get("equipmentid")),
                                    parseUUID(cells.get("recipeid")), parseUUID(cells.get("operatorid")), cells.get("runno"),
                                    parseTimestamp(cells.get("starttime")), parseTimestamp(cells.get("endtime")),
                                    coalesce(cells.get("runstatus"), "RUNNING"),                                    parseTimestampOrNow(cells.get("createdat")), "{}");
                                totalRows++;
                                equipmentOperationCount++;
                                break;
                            }
                            case "parameter_value": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO prod.parameter_value (value_id, run_id, param_id, measured_at, value_num, quality_flag, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("valueid")), parseUUID(cells.get("runid")), parseUUID(cells.get("paramid")),
                                    parseTimestampOrNow(cells.get("measuredat")), parseBigDecimal(cells.get("valuenum")),
                                    coalesce(cells.get("qualityflag"), "RAW"),                                    parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
                                equipmentOperationCount++;
                                break;
                            }
                            case "defect_type": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO qc.defect_type (defect_type_id, step_id, defect_code, defect_name, defect_category, default_severity, description, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("defecttypeid")), parseUUID(cells.get("stepid")),
                                    cells.get("defectcode"), cells.get("defectname"), cells.get("defectcategory"),
                                    parseInt(cells.get("defaultseverity")), cells.get("description"), parseTimestampOrNow(cells.get("createdat")));
                                qualityDefectCount++;
                                break;
                            }
                            case "inspection_task": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO qc.inspection_task (inspection_id, run_id, unit_id, step_id, inspection_type, model_name, model_version, result_status, confidence, inspected_at, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("inspectionid")), parseUUID(cells.get("runid")), parseUUID(cells.get("unitid")),
                                    parseUUID(cells.get("stepid")), cells.get("inspectiontype"), cells.get("modelname"),
                                    cells.get("modelversion"), cells.get("resultstatus"), parseBigDecimal(cells.get("confidence")),
                                    parseTimestampOrNow(cells.get("inspectedat")),                                    parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
                                qualityDefectCount++;
                                break;
                            }
                            case "defect_record":{
                                insertIgnoreDuplicate(
                                    "INSERT INTO qc.defect_record (defect_id, inspection_id, unit_id, defect_type_id, defect_count, confidence, severity_level, is_critical, created_at, bbox_json) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                                    parseUUID(cells.get("defectid")), parseUUID(cells.get("inspectionid")), parseUUID(cells.get("unitid")),
                                    parseUUID(cells.get("defecttypeid")), parseInt(cells.get("defectcount")),
                                    parseBigDecimal(cells.get("confidence")), parseInt(cells.get("severitylevel")),
                                    parseBoolean(cells.get("iscritical")),                                    parseTimestampOrNow(cells.get("createdat")), "[]");
                                totalRows++;
                                qualityDefectCount++;
                                break;
                            }
                            case "quality_measurement": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO qc.quality_measurement (measurement_id, run_id, unit_id, metric_id, measured_at, value_num, is_pass, deviation_value, measurement_method, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("measurementid")), parseUUID(cells.get("runid")), parseUUID(cells.get("unitid")),
                                    parseUUID(cells.get("metricid")), parseTimestampOrNow(cells.get("measuredat")),
                                    parseBigDecimal(cells.get("valuenum")), parseBoolean(cells.get("ispass")),
                                    parseBigDecimal(cells.get("deviationvalue")), coalesce(cells.get("measurementmethod"), "自动检测"),
                                    parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
                                qualityDefectCount++;
                                break;
                            }
                            // ===== core_data sheets =====
                            case "process_step": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO core.process_step (step_id, step_code, step_name, step_order, is_inspection, description, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("stepid")), cells.get("stepcode"), cells.get("stepname"),
                                    parseInt(cells.get("steporder")), parseBoolean(cells.get("isinspection")),
                                    cells.get("description"), parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
                                coreDataCount++;
                                break;
                            }
                            case "workstation": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO core.workstation (station_id, step_id, station_code, station_name, location, status, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("stationid")), parseUUID(cells.get("stepid")),
                                    cells.get("stationcode"), cells.get("stationname"),
                                    cells.get("location"), coalesce(cells.get("status"), "ACTIVE"),
                                    parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
                                coreDataCount++;
                                break;
                            }
                            case "equipment": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO core.equipment (equipment_id, station_id, equipment_code, equipment_name, equipment_type, manufacturer, model_no, status, installed_at, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("equipmentid")), parseUUID(cells.get("stationid")),
                                    cells.get("equipmentcode"), cells.get("equipmentname"),
                                    cells.get("equipmenttype"), cells.get("manufacturer"),
                                    cells.get("modelno"), coalesce(cells.get("status"), "ACTIVE"),
                                    parseLocalDate(cells.get("installedat")), parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
                                coreDataCount++;
                                break;
                            }
                            case "product_type": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO core.product_type (product_type_id, product_code, product_name, material_system, specification, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("producttypeid")), cells.get("productcode"),
                                    cells.get("productname"), coalesce(cells.get("materialsystem"), "HTCC"),
                                    cells.get("specification"), parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
                                coreDataCount++;
                                break;
                            }
                            case "parameter_def": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO core.parameter_def (param_id, step_id, param_code, param_name, param_category, data_type, unit, lower_limit, upper_limit, standard_value, required_flag, description, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("paramid")), parseUUID(cells.get("stepid")),
                                    cells.get("paramcode"), cells.get("paramname"),
                                    cells.get("paramcategory"), cells.get("datatype"),
                                    cells.get("unit"), parseBigDecimal(cells.get("lowerlimit")),
                                    parseBigDecimal(cells.get("upperlimit")), parseBigDecimal(cells.get("standardvalue")),
                                    parseBoolean(cells.get("requiredflag")), cells.get("description"),
                                    parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
                                coreDataCount++;
                                break;
                            }
                            // ===== eval_data sheets =====
                            case "assessment_task": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO eval.assessment_task (task_id, task_type, batch_id, step_id, model_name, model_version, task_status, created_at, finished_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("taskid")), cells.get("tasktype"),
                                    parseUUID(cells.get("batchid")), parseUUID(cells.get("stepid")),
                                    cells.get("modelname"), cells.get("modelversion"),
                                    coalesce(cells.get("taskstatus"), "CREATED"),
                                    parseTimestampOrNow(cells.get("createdat")),
                                    parseTimestamp(cells.get("finishedat")));
                                totalRows++;
                                evalDataCount++;
                                break;
                            }
                            case "assessment_result": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO eval.assessment_result (result_id, task_id, assessment_score, pass_probability, is_pass, risk_level, conclusion, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("resultid")), parseUUID(cells.get("taskid")),
                                    parseBigDecimal(cells.get("assessmentscore")),
                                    parseBigDecimal(cells.get("passprobability")),
                                    parseBoolean(cells.get("ispass")),
                                    cells.get("risklevel"), cells.get("conclusion"),
                                    parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
                                evalDataCount++;
                                break;
                            }
                            // ===== kg_data sheets =====
                            case "graph_version": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO kg.graph_version (graph_version_id, graph_name, version_no, description, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("graphversionid")), cells.get("graphname"),
                                    cells.get("versionno"), cells.get("description"),
                                    parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
                                kgDataCount++;
                                break;
                            }
                            case "kg_entity": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO kg.kg_entity (entity_id, graph_version_id, entity_type, ref_schema, ref_table, ref_id, entity_code, entity_name, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("entityid")), parseUUID(cells.get("graphversionid")),
                                    cells.get("entitytype"), cells.get("refschema"),
                                    cells.get("reftable"), parseUUID(cells.get("refid")),
                                    cells.get("entitycode"), cells.get("entityname"),
                                    parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
                                kgDataCount++;
                                break;
                            }
                            case "kg_relation": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO kg.kg_relation (relation_id, graph_version_id, source_entity_id, target_entity_id, relation_type, relation_weight, confidence, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("relationid")), parseUUID(cells.get("graphversionid")),
                                    parseUUID(cells.get("sourceentityid")), parseUUID(cells.get("targetentityid")),
                                    cells.get("relationtype"), parseBigDecimal(cells.get("relationweight")),
                                    parseBigDecimal(cells.get("confidence")),
                                    parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
                                kgDataCount++;
                                break;
                            }
                            default: break;
                        }
                    } catch (Exception rowEx) {
                        errorRows++;
                        errorMessages.add("Sheet '" + sheetName + "' row " + r + ": " + rowEx.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            job.setImportStatus("FAILED");
            job.setErrorLog("[\"" + errorMsg.replace("\"", "'") + "\"]");
            job.setErrorRows(errorRows);
            job.setTotalRows(totalRows);
            job.setFinishedAt(Instant.now());
            try { importJobRepository.save(job); } catch (Exception ignored) {}
            throw new BusinessException(500, "Excel import failed: " + errorMsg);
        }

        job.setImportStatus(errorRows > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED");
        job.setTotalRows(totalRows);
        job.setSuccessRows(totalRows - errorRows);
        job.setErrorRows(errorRows);
        if (!errorMessages.isEmpty()) {
            job.setErrorLog(toJsonArray(errorMessages));
        }
        job.setFinishedAt(Instant.now());
        importJobRepository.save(job);

        return new ManufacturingImportSummary(fileName, processSettingCount, equipmentOperationCount, qualityDefectCount, coreDataCount, evalDataCount, kgDataCount);
    }

    // ===== Excel Helper Methods =====

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: {
                // Check if it's a date cell first
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
                return String.valueOf(v);
            }
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: {
                try { return cell.getStringCellValue().trim(); }
                catch (Exception e) { return String.valueOf(cell.getNumericCellValue()); }
            }
            default: return "";
        }
    }

    private String coalesce(String val, String defaultVal) {
        return (val != null && !val.isBlank()) ? val : defaultVal;
    }

    private UUID parseUUID(String val) {
        if (val == null || val.isBlank()) return null;
        try { return UUID.fromString(val); }
        catch (Exception e) { return null; }
    }

    private Integer parseInt(String val) {
        if (val == null || val.isBlank()) return null;
        try { return Integer.parseInt(val); }
        catch (Exception e) { return null; }
    }

    private BigDecimal parseBigDecimal(String val) {
        if (val == null || val.isBlank()) return null;
        try { return new BigDecimal(val); }
        catch (Exception e) { return null; }
    }

    private Boolean parseBoolean(String val) {
        if (val == null || val.isBlank()) return null;
        return "true".equalsIgnoreCase(val) || "1".equals(val);
    }

    private Timestamp parseTimestampOrNow(String val) {
        Timestamp ts = parseTimestamp(val);
        return ts != null ? ts : Timestamp.from(Instant.now());
    }

    private int insertIgnoreDuplicate(String sql, Object... args) {
        try {
            return jdbcTemplate.update(sql, args);
        } catch (DataIntegrityViolationException e) {
            log.warn("Skipping row due to data integrity violation: {}", e.getMessage());
            return 0;
        }
    }

    private java.time.LocalDate parseLocalDate(String val) {
        if (val == null || val.isBlank()) return null;
        val = val.trim();
        try { return java.time.LocalDate.parse(val); } catch (Exception e1) {
            try { return java.time.LocalDate.parse(val, java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd")); } catch (Exception e2) {
                return null;
            }
        }
    }

    private String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            String escaped = items.get(i)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ");
            sb.append("\"").append(escaped).append("\"");
        }
        return sb.append("]").toString();
    }

    private Timestamp parseTimestamp(String val) {
        if (val == null || val.isBlank()) return null;
        val = val.trim();

        // 1. Try ISO Instant format (e.g. 2024-01-01T10:30:00Z)
        try {
            Instant instant = Instant.parse(val);
            return Timestamp.from(instant);
        } catch (DateTimeParseException ignored) {}

        // 2. Try datetime patterns
        String[] dateTimePatterns = {
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss"
        };
        for (String pattern : dateTimePatterns) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(val, DateTimeFormatter.ofPattern(pattern));
                return Timestamp.from(ldt.atZone(ZoneId.of("Asia/Shanghai")).toInstant());
            } catch (DateTimeParseException ignored) {}
        }

        // 3. Try date-only patterns (use start of day UTC)
        String[] datePatterns = {
            "yyyy-MM-dd",
            "yyyy/MM/dd"
        };
        for (String pattern : datePatterns) {
            try {
                LocalDate ld = LocalDate.parse(val, DateTimeFormatter.ofPattern(pattern));
                return Timestamp.from(ld.atStartOfDay(ZoneOffset.UTC).toInstant());
            } catch (DateTimeParseException ignored) {}
        }

        return null;
    }

    @Transactional(readOnly = true)
    public UploadStatisticsResponse getUploadStatistics() {
        List<ImportJob> allJobs = importJobRepository.findAll();
        int totalTasks = allJobs.size();
        String latestSyncTime = allJobs.stream()
                .filter(j -> j.getFinishedAt() != null)
                .max(Comparator.comparing(ImportJob::getFinishedAt))
                .map(j -> j.getFinishedAt().toString())
                .orElse(null);
        return new UploadStatisticsResponse(totalTasks, latestSyncTime);
    }

    // ===== Private Helpers =====

    private ImportJob requireImportJob(UUID importId) {
        return importJobRepository.findById(importId)
                .orElseThrow(() -> new BusinessException(404, "import job not found"));
    }

    private CleaningRule requireCleaningRule(UUID ruleId) {
        return cleaningRuleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException(404, "cleaning rule not found"));
    }

    private ImportJobResponse toImportJobResponse(ImportJob j) {
        return new ImportJobResponse(
                j.getImportId(),
                j.getSourceType(),
                j.getSourceName(),
                j.getFileId(),
                j.getTargetTable(),
                j.getImportStatus(),
                j.getTotalRows() != null ? j.getTotalRows() : 0,
                j.getSuccessRows() != null ? j.getSuccessRows() : 0,
                j.getErrorRows() != null ? j.getErrorRows() : 0,
                j.getImportedBy() != null ? j.getImportedBy().toString() : null,
                j.getStartedAt() != null ? j.getStartedAt().toString() : null,
                j.getFinishedAt() != null ? j.getFinishedAt().toString() : null
        );
    }

    private CleaningRuleResponse toCleaningRuleResponse(CleaningRule r) {
        return new CleaningRuleResponse(
                r.getRuleId(),
                r.getRuleCode(),
                r.getRuleName(),
                r.getTargetCategory(),
                r.getConditionExpr(),
                r.getActionExpr(),
                r.getPriorityNo(),
                r.getEnabledFlag()
        );
    }

    private CleaningLogResponse toCleaningLogResponse(CleaningLog l) {
        return new CleaningLogResponse(
                l.getCleaningLogId(),
                l.getRuleId(),
                l.getSourceTable(),
                l.getSourceId() != null ? l.getSourceId().toString() : null,
                l.getActionResult()
        );
    }
}
