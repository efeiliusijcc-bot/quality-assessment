package com.example.demo.etl.service;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.core.domain.ParameterDef;
import com.example.demo.core.domain.ProcessStep;
import com.example.demo.core.domain.Workstation;
import com.example.demo.core.repository.ParameterDefRepository;
import com.example.demo.core.repository.ProcessStepRepository;
import com.example.demo.core.repository.WorkstationRepository;
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
import com.example.demo.qc.domain.InspectionTask;
import com.example.demo.qc.repository.DefectRecordRepository;
import com.example.demo.qc.repository.DefectTypeRepository;
import com.example.demo.qc.repository.InspectionTaskRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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
    private final WorkstationRepository workstationRepository;
    private final ProcessRunRepository processRunRepository;
    private final ParameterDefRepository parameterDefRepository;
    private final ParameterValueRepository parameterValueRepository;
    private final DefectTypeRepository defectTypeRepository;
    private final InspectionTaskRepository inspectionTaskRepository;
    private final DefectRecordRepository defectRecordRepository;
    private final JdbcTemplate jdbcTemplate;

    public EtlService(
            ImportJobRepository importJobRepository,
            CleaningRuleRepository cleaningRuleRepository,
            CleaningLogRepository cleaningLogRepository,
            ProductionBatchRepository productionBatchRepository,
            ProcessStepRepository processStepRepository,
            WorkstationRepository workstationRepository,
            ProcessRunRepository processRunRepository,
            ParameterDefRepository parameterDefRepository,
            ParameterValueRepository parameterValueRepository,
            DefectTypeRepository defectTypeRepository,
            InspectionTaskRepository inspectionTaskRepository,
            DefectRecordRepository defectRecordRepository,
            JdbcTemplate jdbcTemplate) {
        this.importJobRepository = importJobRepository;
        this.cleaningRuleRepository = cleaningRuleRepository;
        this.cleaningLogRepository = cleaningLogRepository;
        this.productionBatchRepository = productionBatchRepository;
        this.processStepRepository = processStepRepository;
        this.workstationRepository = workstationRepository;
        this.processRunRepository = processRunRepository;
        this.parameterDefRepository = parameterDefRepository;
        this.parameterValueRepository = parameterValueRepository;
        this.defectTypeRepository = defectTypeRepository;
        this.inspectionTaskRepository = inspectionTaskRepository;
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

    @Transactional(readOnly = true)
    public List<CleaningLogResponse> listCleaningLogsByImportJob(UUID importId) {
        ImportJob job = requireImportJob(importId);
        Instant startedAt = job.getStartedAt();
        Instant finishedAt = job.getFinishedAt() != null ? job.getFinishedAt() : Instant.now();

        return cleaningLogRepository.findAll().stream()
                .filter(log -> log.getCreatedAt() != null)
                .filter(log -> !log.getCreatedAt().isBefore(startedAt) && !log.getCreatedAt().isAfter(finishedAt))
                .map(this::toCleaningLogResponse)
                .toList();
    }

    // ===== Business Methods =====

    public OnlineUploadResult submitOnlineUpload(OnlineUploadPayload payload) {
        validateOnlineUploadPayload(payload);
        ImportJob job = new ImportJob("ONLINE", payload.station());
        job.setTargetTable("online_stream");
        job.setImportStatus("RUNNING");
        job.setErrorLog(toJsonObject(new LinkedHashMap<>(Map.of(
                "batchNo", payload.batchNo(),
                "deviceId", payload.deviceId(),
                "frequency", payload.frequency(),
                "mapping", payload.mapping()
        ))));
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

        ProcessStep step = resolveManualRecordStep(payload.station());
        Workstation station = resolveManualRecordStation(payload.station()).orElse(null);

        // Create process run
        ProcessRun run = new ProcessRun(batch.getBatchId(), step.getStepId());
        if (station != null) {
            run.setStationId(station.getStationId());
        }
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
                        BigDecimal confidence = BigDecimal.valueOf(Math.max(0, Math.min(1, payload.defectConfidence())));
                        InspectionTask inspection = new InspectionTask(run.getRunId(), step.getStepId(), "MANUAL");
                        inspection.setResultStatus("COMPLETED");
                        inspection.setConfidence(confidence);
                        inspectionTaskRepository.save(inspection);

                        DefectRecord dr = new DefectRecord(inspection.getInspectionId(), defectType.getDefectTypeId());
                        dr.setConfidence(confidence);
                        dr.setSeverityLevel(resolveManualDefectSeverity(payload.defectLevel(), defectType));
                        defectRecordRepository.save(dr);
                    });
        }

        return new ManualRecordResult(
                run.getRunId().toString(),
                "逐条录入已提交"
        );
    }

    private void validateOnlineUploadPayload(OnlineUploadPayload payload) {
        if (payload == null
                || isBlank(payload.station())
                || isBlank(payload.batchNo())
                || isBlank(payload.deviceId())
                || isBlank(payload.frequency())
                || isBlank(payload.mapping())) {
            throw new BusinessException(400, "online upload fields are required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ProcessStep resolveManualRecordStep(String stationValue) {
        Optional<Workstation> station = resolveManualRecordStation(stationValue);
        if (station.isPresent()) {
            return processStepRepository.findById(station.get().getStepId())
                    .orElseThrow(() -> new BusinessException(404, "process step not found for station: " + stationValue));
        }

        List<ProcessStep> steps = processStepRepository.findAll(Sort.by(Sort.Direction.ASC, "stepOrder"));
        if (steps.isEmpty()) {
            throw new BusinessException(400, "no process step configured");
        }
        return steps.get(0);
    }

    private Optional<Workstation> resolveManualRecordStation(String stationValue) {
        if (stationValue == null || stationValue.isBlank()) {
            return Optional.empty();
        }
        Optional<Workstation> byCode = workstationRepository.findByStationCode(stationValue);
        if (byCode.isPresent()) {
            return byCode;
        }
        String normalized = stationValue.trim();
        return workstationRepository.findAll().stream()
                .filter(station -> normalized.equals(station.getStationName()))
                .findFirst();
    }

    private int resolveManualDefectSeverity(String defectLevel, DefectType defectType) {
        if (defectLevel != null) {
            String normalized = defectLevel.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains("严重") || normalized.contains("high") || normalized.contains("critical")) {
                return 4;
            }
            if (normalized.contains("中") || normalized.contains("medium")) {
                return 3;
            }
            if (normalized.contains("轻") || normalized.contains("low")) {
                return 2;
            }
        }
        return defectType.getDefaultSeverity() != null ? defectType.getDefaultSeverity() : 1;
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
        List<CleaningRule> cleaningRules = cleaningRuleRepository.findByEnabledFlagTrueOrderByPriorityNoAsc();

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
                                UUID recipeId = existingUuid("prod.process_recipe", "recipe_id", cells.get("recipeid"));
                                UUID operatorId = existingUuid("app.app_user", "user_id", cells.get("operatorid"));
                                insertIgnoreDuplicate(
                                    "INSERT INTO prod.process_run (run_id, batch_id, unit_id, step_id, station_id, equipment_id, recipe_id, operator_id, run_no, start_time, end_time, run_status, created_at, context_json) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                                    parseUUID(cells.get("runid")), parseUUID(cells.get("batchid")), parseUUID(cells.get("unitid")),
                                    parseUUID(cells.get("stepid")), parseUUID(cells.get("stationid")), parseUUID(cells.get("equipmentid")),
                                    recipeId, operatorId, cells.get("runno"),
                                    parseTimestamp(cells.get("starttime")), parseTimestamp(cells.get("endtime")),
                                    coalesce(cells.get("runstatus"), "RUNNING"),                                    parseTimestampOrNow(cells.get("createdat")), "{}");
                                totalRows++;
                                equipmentOperationCount++;
                                break;
                            }
                            case "parameter_value": {
                                UUID valueId = parseUUID(cells.get("valueid"));
                                Map<String, Object> values = new LinkedHashMap<>();
                                values.put("valueNum", parseBigDecimal(cells.get("valuenum")));
                                values.put("qualityFlag", coalesce(cells.get("qualityflag"), "RAW"));
                                List<CleaningLog> logs = applyCleaningRules(
                                        "parameter_value", valueId, values, cleaningRules);

                                int inserted = insertIgnoreDuplicate(
                                    "INSERT INTO prod.parameter_value (value_id, run_id, param_id, measured_at, value_num, quality_flag, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                                    valueId, parseUUID(cells.get("runid")), parseUUID(cells.get("paramid")),
                                    parseTimestampOrNow(cells.get("measuredat")), values.get("valueNum"),
                                    values.get("qualityFlag"),                                    parseTimestampOrNow(cells.get("createdat")));
                                if (inserted > 0) {
                                    cleaningLogRepository.saveAll(logs);
                                }
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
                                    parseSeverityLevel(cells.get("defaultseverity")), cells.get("description"), parseTimestampOrNow(cells.get("createdat")));
                                qualityDefectCount++;
                                break;
                            }
                            case "quality_metric_def": {
                                insertIgnoreDuplicate(
                                    "INSERT INTO qc.quality_metric_def (metric_id, step_id, metric_code, metric_name, unit, lower_limit, upper_limit, target_value, pass_rule, severity_weight, description, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                    parseUUID(cells.get("metricid")), parseUUID(cells.get("stepid")),
                                    cells.get("metriccode"), cells.get("metricname"), cells.get("unit"),
                                    parseBigDecimal(cells.get("lowerlimit")), parseBigDecimal(cells.get("upperlimit")),
                                    parseBigDecimal(cells.get("targetvalue")), cells.get("passrule"),
                                    parseBigDecimal(cells.get("severityweight")), cells.get("description"),
                                    parseTimestampOrNow(cells.get("createdat")));
                                totalRows++;
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
                                UUID inspectionId = parseUUID(cells.get("inspectionid"));
                                UUID unitId = existingUuid("prod.product_unit", "unit_id", cells.get("unitid"));
                                if (unitId == null) {
                                    unitId = lookupUuid(
                                            "SELECT unit_id FROM qc.inspection_task WHERE inspection_id = ?",
                                            inspectionId);
                                }
                                insertIgnoreDuplicate(
                                    "INSERT INTO qc.defect_record (defect_id, inspection_id, unit_id, defect_type_id, defect_count, confidence, severity_level, is_critical, created_at, bbox_json) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                                    parseUUID(cells.get("defectid")), inspectionId, unitId,
                                    parseUUID(cells.get("defecttypeid")), parseInt(cells.get("defectcount")),
                                    parseBigDecimal(cells.get("confidence")), parseSeverityLevel(cells.get("severitylevel")),
                                    parseBoolean(cells.get("iscritical")),                                    parseTimestampOrNow(cells.get("createdat")), "[]");
                                totalRows++;
                                qualityDefectCount++;
                                break;
                            }
                            case "quality_measurement": {
                                UUID measurementId = parseUUID(cells.get("measurementid"));
                                UUID unitId = existingUuid("prod.product_unit", "unit_id", cells.get("unitid"));
                                Map<String, Object> values = new LinkedHashMap<>();
                                values.put("valueNum", parseBigDecimal(cells.get("valuenum")));
                                values.put("isPass", parseBoolean(cells.get("ispass")));
                                values.put("deviationValue", parseBigDecimal(cells.get("deviationvalue")));
                                values.put("measurementMethod", coalesce(cells.get("measurementmethod"), "自动检测"));
                                List<CleaningLog> logs = applyCleaningRules(
                                        "quality_measurement", measurementId, values, cleaningRules);

                                int inserted = insertIgnoreDuplicate(
                                    "INSERT INTO qc.quality_measurement (measurement_id, run_id, unit_id, metric_id, measured_at, value_num, is_pass, deviation_value, measurement_method, created_at) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                    measurementId, parseUUID(cells.get("runid")), unitId,
                                    parseUUID(cells.get("metricid")), parseTimestampOrNow(cells.get("measuredat")),
                                    values.get("valueNum"), values.get("isPass"),
                                    values.get("deviationValue"), values.get("measurementMethod"),
                                    parseTimestampOrNow(cells.get("createdat")));
                                if (inserted > 0) {
                                    cleaningLogRepository.saveAll(logs);
                                }
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

    private Integer parseSeverityLevel(String val) {
        Integer numeric = parseInt(val);
        if (numeric != null) return numeric;
        if (val == null || val.isBlank()) return null;

        String normalized = val.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "critical", "severe", "high", "严重", "高", "重度" -> 3;
            case "medium", "moderate", "中等", "中", "中度" -> 2;
            case "low", "minor", "light", "轻微", "低", "轻度" -> 1;
            default -> null;
        };
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
        } catch (DuplicateKeyException e) {
            log.debug("Skipping duplicate row: {}", e.getMessage());
            return 0;
        } catch (DataIntegrityViolationException e) {
            throw e;
        }
    }

    private UUID existingUuid(String tableName, String columnName, String value) {
        UUID id = parseUUID(value);
        if (id == null) {
            return null;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ?",
                Integer.class,
                id);
        return count != null && count > 0 ? id : null;
    }

    private UUID lookupUuid(String sql, UUID id) {
        if (id == null) {
            return null;
        }
        List<UUID> values = jdbcTemplate.query(sql, (rs, rowNum) -> (UUID) rs.getObject(1), id);
        return values.isEmpty() ? null : values.get(0);
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

    private List<CleaningLog> applyCleaningRules(
            String sourceTable,
            UUID sourceId,
            Map<String, Object> values,
            List<CleaningRule> rules) {
        if (rules.isEmpty()) {
            return List.of();
        }

        List<CleaningLog> logs = new ArrayList<>();
        for (CleaningRule rule : rules) {
            if (!matchesTarget(sourceTable, rule.getTargetCategory())) {
                continue;
            }
            if (!matchesCondition(rule.getConditionExpr(), values)) {
                continue;
            }

            String before = toJsonObject(values);
            boolean changed = applyCleaningAction(rule.getActionExpr(), values);
            CleaningLog cleaningLog = new CleaningLog(rule.getRuleId(), sourceId);
            cleaningLog.setSourceTable(sourceTable);
            cleaningLog.setBeforeValue(before);
            cleaningLog.setAfterValue(toJsonObject(values));
            cleaningLog.setActionResult(changed ? "APPLIED" : "MATCHED");
            logs.add(cleaningLog);
        }
        return logs;
    }

    private boolean matchesTarget(String sourceTable, String targetCategory) {
        if (targetCategory == null || targetCategory.isBlank()) {
            return true;
        }
        String normalized = targetCategory.trim().toLowerCase(Locale.ROOT);
        return "all".equals(normalized)
                || normalized.equals(sourceTable)
                || normalized.equals(sourceTable.replace("_", ""))
                || normalized.equals(sourceTable.replace("_", "-"));
    }

    private boolean matchesCondition(String conditionExpr, Map<String, Object> values) {
        if (conditionExpr == null || conditionExpr.isBlank() || "true".equalsIgnoreCase(conditionExpr.trim())) {
            return true;
        }

        String expr = conditionExpr.trim();
        String lower = expr.toLowerCase(Locale.ROOT);
        if (lower.endsWith(" is null")) {
            String field = normalizeField(expr.substring(0, lower.lastIndexOf(" is null")));
            return values.get(field) == null;
        }
        if (lower.endsWith(" is not null")) {
            String field = normalizeField(expr.substring(0, lower.lastIndexOf(" is not null")));
            return values.get(field) != null;
        }

        String[] operators = {">=", "<=", "==", "!=", ">", "<", "="};
        for (String op : operators) {
            int index = expr.indexOf(op);
            if (index <= 0) {
                continue;
            }
            String field = normalizeField(expr.substring(0, index));
            Object left = values.get(field);
            Object right = parseLiteral(expr.substring(index + op.length()));
            return compareValues(left, right, op);
        }
        return false;
    }

    private boolean applyCleaningAction(String actionExpr, Map<String, Object> values) {
        if (actionExpr == null || actionExpr.isBlank()) {
            return false;
        }

        boolean changed = false;
        for (String rawAction : actionExpr.split("[;\\n]")) {
            String action = rawAction.trim();
            if (action.isEmpty()) {
                continue;
            }
            if ("set_null".equalsIgnoreCase(action)) {
                changed |= setIfChanged(values, "valueNum", null);
                continue;
            }
            if (action.toLowerCase(Locale.ROOT).startsWith("clamp:")) {
                changed |= clampValueNum(action, values);
                continue;
            }

            int equalsIndex = action.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }
            String field = normalizeField(action.substring(0, equalsIndex));
            Object value = coerceValueForField(field, action.substring(equalsIndex + 1));
            changed |= setIfChanged(values, field, value);
        }
        return changed;
    }

    private boolean clampValueNum(String action, Map<String, Object> values) {
        String[] parts = action.split(":");
        if (parts.length != 3) {
            return false;
        }
        BigDecimal current = asBigDecimal(values.get("valueNum"));
        BigDecimal min = parseBigDecimal(parts[1].trim());
        BigDecimal max = parseBigDecimal(parts[2].trim());
        if (current == null || min == null || max == null) {
            return false;
        }
        BigDecimal clamped = current.max(min).min(max);
        return setIfChanged(values, "valueNum", clamped);
    }

    private boolean setIfChanged(Map<String, Object> values, String field, Object value) {
        if (!values.containsKey(field) || Objects.equals(values.get(field), value)) {
            return false;
        }
        values.put(field, value);
        return true;
    }

    private String normalizeField(String field) {
        String normalized = field == null ? "" : field.trim();
        return switch (normalized.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "")) {
            case "v", "value", "valuenum" -> "valueNum";
            case "qualityflag" -> "qualityFlag";
            case "ispass", "pass" -> "isPass";
            case "deviation", "deviationvalue" -> "deviationValue";
            case "measurementmethod" -> "measurementMethod";
            default -> normalized;
        };
    }

    private Object parseLiteral(String literal) {
        String value = stripQuotes(literal == null ? "" : literal.trim());
        if ("null".equalsIgnoreCase(value)) {
            return null;
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        BigDecimal decimal = parseBigDecimal(value);
        return decimal != null ? decimal : value;
    }

    private Object coerceValueForField(String field, String rawValue) {
        String value = stripQuotes(rawValue == null ? "" : rawValue.trim());
        return switch (field) {
            case "valueNum", "deviationValue" -> parseBigDecimal(value);
            case "isPass" -> parseBoolean(value);
            default -> value;
        };
    }

    private boolean compareValues(Object left, Object right, String operator) {
        if ("=".equals(operator)) {
            operator = "==";
        }
        if (left == null || right == null) {
            return switch (operator) {
                case "==" -> left == right;
                case "!=" -> left != right;
                default -> false;
            };
        }

        BigDecimal leftNumber = asBigDecimal(left);
        BigDecimal rightNumber = asBigDecimal(right);
        int comparison;
        if (leftNumber != null && rightNumber != null) {
            comparison = leftNumber.compareTo(rightNumber);
        } else if (left instanceof Boolean || right instanceof Boolean) {
            comparison = Boolean.compare(Boolean.parseBoolean(String.valueOf(left)), Boolean.parseBoolean(String.valueOf(right)));
        } else {
            comparison = String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
        }

        return switch (operator) {
            case "==" -> comparison == 0;
            case "!=" -> comparison != 0;
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            default -> false;
        };
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return value == null ? null : parseBigDecimal(String.valueOf(value));
    }

    private String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String toJsonObject(Map<String, Object> values) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (i++ > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
            }
        }
        return sb.append("}").toString();
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ");
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
                l.getBeforeValue(),
                l.getAfterValue(),
                l.getActionResult(),
                l.getCreatedAt() != null ? l.getCreatedAt().toString() : null
        );
    }
}
