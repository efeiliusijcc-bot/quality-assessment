package com.example.demo.qc.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class QcDtos {

    private QcDtos() {}

    // ===== Defect detection DTOs =====

    public record DetectionResult(
        String category,
        String level,
        double confidence,
        String location
    ) {}

    public record DefectBox(
        String label,
        double confidence,
        double[] bbox,
        String level
    ) {}

    public record DefectSampleResponse(
        UUID id,
        String name,
        String mediaType,
        String batchNo,
        String imageUrl,
        List<DetectionResult> results,
        List<DefectBox> defects,
        String summary
    ) {}

    public record BatchDetectRequestItem(
        String name,
        String batchNo,
        String imageUrl
    ) {}

    public record BatchDetectResponse(
        List<DefectSampleResponse> results,
        int total,
        String message
    ) {}

    public record DefectStatisticsResponse(
        int totalSamples,
        double avgConfidence,
        String modelVersion
    ) {}

    // ===== Quality metric definition DTOs =====

    public record QualityMetricDefResponse(
        UUID metricId,
        UUID stepId,
        String metricCode,
        String metricName,
        String unit,
        BigDecimal lowerLimit,
        BigDecimal upperLimit,
        BigDecimal targetValue,
        String passRule,
        BigDecimal severityWeight
    ) {}

    public record CreateQualityMetricDefRequest(
        @NotBlank String metricCode,
        @NotBlank String metricName,
        UUID stepId,
        String unit
    ) {}

    // ===== Quality measurement DTOs =====

    public record QualityMeasurementResponse(
        UUID measurementId,
        UUID runId,
        UUID unitId,
        UUID metricId,
        BigDecimal valueNum,
        Boolean isPass,
        BigDecimal deviationValue,
        String measuredAt
    ) {}

    public record CreateQualityMeasurementRequest(
        UUID runId,
        UUID metricId,
        BigDecimal valueNum
    ) {}

    // ===== Defect type DTOs =====

    public record DefectTypeResponse(
        UUID defectTypeId,
        UUID stepId,
        String defectCode,
        String defectName,
        String defectCategory,
        String defaultSeverity
    ) {}

    public record CreateDefectTypeRequest(
        @NotBlank String defectCode,
        @NotBlank String defectName,
        UUID stepId
    ) {}

    // ===== Inspection task DTOs =====

    public record InspectionTaskResponse(
        UUID inspectionId,
        UUID runId,
        UUID unitId,
        UUID stepId,
        String inspectionType,
        String resultStatus,
        BigDecimal confidence,
        String inspectedAt
    ) {}

    public record CreateInspectionTaskRequest(
        UUID runId,
        UUID stepId,
        @NotBlank String inspectionType
    ) {}

    // ===== Defect record DTOs =====

    public record DefectRecordResponse(
        UUID defectId,
        UUID inspectionId,
        UUID unitId,
        UUID defectTypeId,
        Integer defectCount,
        BigDecimal confidence,
        String severityLevel,
        Boolean isCritical
    ) {}

    public record CreateDefectRecordRequest(
        UUID inspectionId,
        UUID unitId,
        UUID defectTypeId,
        Integer defectCount
    ) {}
}
