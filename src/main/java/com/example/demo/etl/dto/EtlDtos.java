package com.example.demo.etl.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class EtlDtos {

    private EtlDtos() {}

    // ===== Upload DTOs =====

    public record OnlineUploadPayload(
        String station,
        String batchNo,
        String deviceId,
        String frequency,
        String mapping
    ) {}

    public record OnlineUploadResult(
        String taskId,
        String station,
        String batchNo,
        String deviceId,
        String frequency,
        String mapping
    ) {}

    public record ManualRecordPayload(
        @NotBlank String batchNo,
        @NotBlank String station,
        String componentId,
        double temperature,
        double pressure,
        double beltSpeed,
        double o2Ppm,
        double humidity,
        double currentValue,
        String defectType,
        String defectLevel,
        double defectConfidence
    ) {}

    public record ManualRecordResult(
        String id,
        String message
    ) {}

    public record ManufacturingImportSummary(
        String fileName,
        int processSettingCount,
        int equipmentOperationCount,
        int qualityDefectCount
    ) {}

    public record UploadStatisticsResponse(
        int totalTasks,
        String latestSyncTime
    ) {}

    // ===== CRUD DTOs =====

    public record ImportJobResponse(
        UUID importId,
        String sourceType,
        String sourceName,
        UUID fileId,
        String targetTable,
        String importStatus,
        int totalRows,
        int successRows,
        int errorRows,
        String importedBy,
        String startedAt,
        String finishedAt
    ) {}

    public record CreateImportJobRequest(
        @NotBlank String sourceType,
        String sourceName,
        UUID fileId,
        String targetTable
    ) {}

    public record CleaningRuleResponse(
        UUID ruleId,
        String ruleCode,
        String ruleName,
        String targetCategory,
        String conditionExpr,
        String actionExpr,
        Integer priorityNo,
        Boolean enabledFlag
    ) {}

    public record CreateCleaningRuleRequest(
        @NotBlank String ruleCode,
        @NotBlank String ruleName,
        String targetCategory,
        String conditionExpr,
        String actionExpr,
        Integer priorityNo
    ) {}

    public record CleaningLogResponse(
        UUID cleaningLogId,
        UUID ruleId,
        String sourceTable,
        String sourceId,
        String actionResult
    ) {}
}
