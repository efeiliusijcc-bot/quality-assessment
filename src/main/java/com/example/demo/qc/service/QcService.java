package com.example.demo.qc.service;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.qc.domain.DefectRecord;
import com.example.demo.qc.domain.DefectType;
import com.example.demo.qc.domain.InspectionTask;
import com.example.demo.qc.domain.QualityMeasurement;
import com.example.demo.qc.domain.QualityMetricDef;
import com.example.demo.qc.dto.QcDtos.*;
import com.example.demo.qc.repository.DefectRecordRepository;
import com.example.demo.qc.repository.DefectTypeRepository;
import com.example.demo.qc.repository.InspectionTaskRepository;
import com.example.demo.qc.repository.QualityMeasurementRepository;
import com.example.demo.qc.repository.QualityMetricDefRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class QcService {

    private final QualityMetricDefRepository metricDefRepository;
    private final QualityMeasurementRepository measurementRepository;
    private final DefectTypeRepository defectTypeRepository;
    private final InspectionTaskRepository inspectionTaskRepository;
    private final DefectRecordRepository defectRecordRepository;

    public QcService(
            QualityMetricDefRepository metricDefRepository,
            QualityMeasurementRepository measurementRepository,
            DefectTypeRepository defectTypeRepository,
            InspectionTaskRepository inspectionTaskRepository,
            DefectRecordRepository defectRecordRepository) {
        this.metricDefRepository = metricDefRepository;
        this.measurementRepository = measurementRepository;
        this.defectTypeRepository = defectTypeRepository;
        this.inspectionTaskRepository = inspectionTaskRepository;
        this.defectRecordRepository = defectRecordRepository;
    }

    // ===== QualityMetricDef =====

    public QualityMetricDefResponse createMetricDef(CreateQualityMetricDefRequest request) {
        if (metricDefRepository.findByMetricCode(request.metricCode()).isPresent()) {
            throw new BusinessException(400, "metricCode already exists");
        }
        QualityMetricDef entity = new QualityMetricDef(request.metricCode(), request.metricName());
        entity.setStepId(request.stepId());
        entity.setUnit(request.unit());
        metricDefRepository.save(entity);
        return toMetricDefResponse(entity);
    }

    public QualityMetricDefResponse getMetricDefById(UUID metricId) {
        return toMetricDefResponse(requireMetricDef(metricId));
    }

    public List<QualityMetricDefResponse> listAllMetricDefs() {
        return metricDefRepository.findAll().stream().map(this::toMetricDefResponse).toList();
    }

    public List<QualityMetricDefResponse> listMetricDefsByStep(UUID stepId) {
        return metricDefRepository.findByStepId(stepId).stream().map(this::toMetricDefResponse).toList();
    }

    // ===== QualityMeasurement =====

    public QualityMeasurementResponse createMeasurement(CreateQualityMeasurementRequest request) {
        QualityMeasurement entity = new QualityMeasurement(request.runId(), request.metricId(), request.valueNum());
        measurementRepository.save(entity);
        return toMeasurementResponse(entity);
    }

    public List<QualityMeasurementResponse> listMeasurementsByRun(UUID runId) {
        return measurementRepository.findByRunIdOrderByMeasuredAtAsc(runId).stream()
                .map(this::toMeasurementResponse).toList();
    }

    public List<QualityMeasurementResponse> listMeasurementsByUnit(UUID unitId) {
        return measurementRepository.findByUnitId(unitId).stream()
                .map(this::toMeasurementResponse).toList();
    }

    // ===== DefectType =====

    public DefectTypeResponse createDefectType(CreateDefectTypeRequest request) {
        if (defectTypeRepository.findByStepIdAndDefectCode(request.stepId(), request.defectCode()).isPresent()) {
            throw new BusinessException(400, "defectCode already exists for this step");
        }
        DefectType entity = new DefectType(request.stepId(), request.defectCode(), request.defectName(), null, null);
        defectTypeRepository.save(entity);
        return toDefectTypeResponse(entity);
    }

    public DefectTypeResponse getDefectTypeById(UUID defectTypeId) {
        return toDefectTypeResponse(requireDefectType(defectTypeId));
    }

    public List<DefectTypeResponse> listAllDefectTypes() {
        return defectTypeRepository.findAll().stream().map(this::toDefectTypeResponse).toList();
    }

    public List<DefectTypeResponse> listDefectTypesByStep(UUID stepId) {
        return defectTypeRepository.findByStepId(stepId).stream().map(this::toDefectTypeResponse).toList();
    }

    // ===== InspectionTask =====

    public InspectionTaskResponse createInspection(CreateInspectionTaskRequest request) {
        InspectionTask entity = new InspectionTask(request.runId(), request.stepId(), request.inspectionType());
        inspectionTaskRepository.save(entity);
        return toInspectionTaskResponse(entity);
    }

    public InspectionTaskResponse getInspectionById(UUID inspectionId) {
        return toInspectionTaskResponse(requireInspection(inspectionId));
    }

    public List<InspectionTaskResponse> listAllInspections() {
        return inspectionTaskRepository.findAll().stream().map(this::toInspectionTaskResponse).toList();
    }

    public List<InspectionTaskResponse> listInspectionsByRun(UUID runId) {
        return inspectionTaskRepository.findByRunIdOrderByInspectedAtDesc(runId).stream()
                .map(this::toInspectionTaskResponse).toList();
    }

    public List<InspectionTaskResponse> listInspectionsByUnit(UUID unitId) {
        return inspectionTaskRepository.findByUnitId(unitId).stream()
                .map(this::toInspectionTaskResponse).toList();
    }

    // ===== DefectRecord =====

    public DefectRecordResponse createDefectRecord(CreateDefectRecordRequest request) {
        DefectRecord entity = new DefectRecord(request.inspectionId(), request.defectTypeId());
        if (request.unitId() != null) {
            entity.setUnitId(request.unitId());
        }
        if (request.defectCount() != null) {
            entity.setDefectCount(request.defectCount());
        }
        defectRecordRepository.save(entity);
        return toDefectRecordResponse(entity);
    }

    public List<DefectRecordResponse> listDefectRecordsByInspection(UUID inspectionId) {
        return defectRecordRepository.findByInspectionId(inspectionId).stream()
                .map(this::toDefectRecordResponse).toList();
    }

    public List<DefectRecordResponse> listDefectRecordsByUnit(UUID unitId) {
        return defectRecordRepository.findByUnitId(unitId).stream()
                .map(this::toDefectRecordResponse).toList();
    }

    // ===== Defect detection endpoints =====

    public List<DefectSampleResponse> getDefectSamples() {
        List<InspectionTask> inspections = inspectionTaskRepository.findAll();
        if (inspections.isEmpty()) {
            return List.of();
        }

        // Fetch all defect types for lookup
        Map<UUID, DefectType> defectTypeMap = defectTypeRepository.findAll().stream()
                .collect(Collectors.toMap(DefectType::getDefectTypeId, dt -> dt));

        // Batch-load all defect records by inspection IDs
        List<UUID> inspectionIds = inspections.stream()
                .map(InspectionTask::getInspectionId).toList();
        Map<UUID, List<DefectRecord>> recordsByInspection = new java.util.LinkedHashMap<>();
        for (DefectRecord dr : defectRecordRepository.findByInspectionIdIn(inspectionIds)) {
            recordsByInspection.computeIfAbsent(dr.getInspectionId(), k -> new java.util.ArrayList<>()).add(dr);
        }

        List<DefectSampleResponse> samples = new ArrayList<>();
        for (InspectionTask inspection : inspections) {
            List<DefectRecord> records = recordsByInspection
                    .getOrDefault(inspection.getInspectionId(), List.of());

            List<DetectionResult> results = new ArrayList<>();
            List<DefectBox> defects = new ArrayList<>();

            for (DefectRecord record : records) {
                DefectType dt = defectTypeMap.get(record.getDefectTypeId());
                String category = dt != null ? dt.getDefectCategory() : "unknown";
                String label = dt != null ? dt.getDefectName() : "unknown";
                String level = record.getSeverityLevel() != null
                        ? String.valueOf(record.getSeverityLevel()) : "low";
                double conf = record.getConfidence() != null
                        ? record.getConfidence().doubleValue() : 0.0;

                results.add(new DetectionResult(category, level, conf, ""));
                defects.add(new DefectBox(label, conf, new double[0], level));
            }

            String summary = records.isEmpty()
                    ? "No defects detected"
                    : records.size() + " defect(s) found";

            samples.add(new DefectSampleResponse(
                    inspection.getInspectionId(),
                    inspection.getInspectionType(),
                    "image",
                    null,
                    null,
                    results,
                    defects,
                    summary
            ));
        }
        return samples;
    }

    public BatchDetectResponse batchDetect(List<BatchDetectRequestItem> items) {
        List<DefectSampleResponse> results = new ArrayList<>();

        for (BatchDetectRequestItem item : items) {
            // Create an inspection task for each item
            InspectionTask inspection = new InspectionTask(
                    UUID.randomUUID(), UUID.randomUUID(), "defect_detection");
            inspection.setResultStatus("completed");
            inspection.setConfidence(new BigDecimal("0.95"));
            inspectionTaskRepository.save(inspection);

            // Build simulated detection result
            List<DetectionResult> detectionResults = List.of(
                    new DetectionResult("scratch", "minor", 0.92, "center"),
                    new DetectionResult("dent", "moderate", 0.87, "top-right")
            );
            List<DefectBox> defectBoxes = List.of(
                    new DefectBox("scratch", 0.92, new double[]{100, 100, 200, 150}, "minor"),
                    new DefectBox("dent", 0.87, new double[]{300, 50, 400, 120}, "moderate")
            );

            results.add(new DefectSampleResponse(
                    inspection.getInspectionId(),
                    item.name(),
                    "image",
                    item.batchNo(),
                    item.imageUrl(),
                    detectionResults,
                    defectBoxes,
                    "2 defect(s) detected"
            ));
        }

        return new BatchDetectResponse(results, results.size(), "Batch detection completed");
    }

    public DefectStatisticsResponse getDefectStatistics() {
        List<InspectionTask> all = inspectionTaskRepository.findAll();
        int totalSamples = all.size();
        double avgConfidence = all.stream()
                .filter(t -> t.getConfidence() != null)
                .mapToDouble(t -> t.getConfidence().doubleValue())
                .average()
                .orElse(0.0);
        avgConfidence = BigDecimal.valueOf(avgConfidence)
                .setScale(4, RoundingMode.HALF_UP).doubleValue();

        String modelVersion = all.stream()
                .filter(t -> t.getModelVersion() != null)
                .map(InspectionTask::getModelVersion)
                .findFirst()
                .orElse("v1.0");

        return new DefectStatisticsResponse(totalSamples, avgConfidence, modelVersion);
    }

    // ===== Private helpers =====

    private QualityMetricDef requireMetricDef(UUID metricId) {
        return metricDefRepository.findById(metricId)
                .orElseThrow(() -> new BusinessException(404, "metric definition not found"));
    }

    private DefectType requireDefectType(UUID defectTypeId) {
        return defectTypeRepository.findById(defectTypeId)
                .orElseThrow(() -> new BusinessException(404, "defect type not found"));
    }

    private InspectionTask requireInspection(UUID inspectionId) {
        return inspectionTaskRepository.findById(inspectionId)
                .orElseThrow(() -> new BusinessException(404, "inspection task not found"));
    }

    private QualityMetricDefResponse toMetricDefResponse(QualityMetricDef e) {
        return new QualityMetricDefResponse(
                e.getMetricId(), e.getStepId(), e.getMetricCode(), e.getMetricName(),
                e.getUnit(), e.getLowerLimit(), e.getUpperLimit(), e.getTargetValue(),
                null, e.getSeverityWeight()
        );
    }

    private QualityMeasurementResponse toMeasurementResponse(QualityMeasurement e) {
        return new QualityMeasurementResponse(
                e.getMeasurementId(), e.getRunId(), e.getUnitId(), e.getMetricId(),
                e.getValueNum(), e.getIsPass(), e.getDeviationValue(),
                e.getMeasuredAt() != null ? e.getMeasuredAt().toString() : null
        );
    }

    private DefectTypeResponse toDefectTypeResponse(DefectType e) {
        return new DefectTypeResponse(
                e.getDefectTypeId(), e.getStepId(), e.getDefectCode(), e.getDefectName(),
                e.getDefectCategory(),
                e.getDefaultSeverity() != null ? String.valueOf(e.getDefaultSeverity()) : null
        );
    }

    private InspectionTaskResponse toInspectionTaskResponse(InspectionTask e) {
        return new InspectionTaskResponse(
                e.getInspectionId(), e.getRunId(), e.getUnitId(), e.getStepId(),
                e.getInspectionType(), e.getResultStatus(), e.getConfidence(),
                e.getInspectedAt() != null ? e.getInspectedAt().toString() : null
        );
    }

    private DefectRecordResponse toDefectRecordResponse(DefectRecord e) {
        return new DefectRecordResponse(
                e.getDefectId(), e.getInspectionId(), e.getUnitId(), e.getDefectTypeId(),
                e.getDefectCount(), e.getConfidence(),
                e.getSeverityLevel() != null ? String.valueOf(e.getSeverityLevel()) : null,
                e.getIsCritical()
        );
    }
}
