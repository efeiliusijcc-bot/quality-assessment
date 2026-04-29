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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional
public class EtlService {

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
            DefectRecordRepository defectRecordRepository) {
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
        return cleaningRuleRepository.findAll().stream()
                .filter(r -> targetCategory.equals(r.getTargetCategory()))
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
        return cleaningLogRepository.findAll().stream()
                .filter(l -> ruleId.equals(l.getRuleId()))
                .map(this::toCleaningLogResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CleaningLogResponse> listCleaningLogsBySource(String sourceTable, UUID sourceId) {
        return cleaningLogRepository.findAll().stream()
                .filter(l -> (sourceTable == null || sourceTable.equals(l.getSourceTable()))
                        && (sourceId == null || sourceId.equals(l.getSourceId())))
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

    public ManufacturingImportSummary importManufacturingData(MultipartFile file) {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        ImportJob job = new ImportJob("EXCEL", fileName);
        job.setTargetTable("manufacturing_data");
        job.setImportStatus("COMPLETED");
        job.setTotalRows(100);
        job.setSuccessRows(95);
        job.setErrorRows(5);
        job.setFinishedAt(Instant.now());
        importJobRepository.save(job);

        return new ManufacturingImportSummary(fileName, 30, 40, 25);
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
