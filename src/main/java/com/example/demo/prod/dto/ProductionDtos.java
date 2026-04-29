package com.example.demo.prod.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class ProductionDtos {

    private ProductionDtos() {}

    public record CreateBatchRequest(
        @NotBlank String batchNo,
        UUID productTypeId,
        Integer planQty
    ) {}

    public record BatchResponse(
        UUID batchId,
        String batchNo,
        String productName,
        Integer planQty,
        Integer actualQty,
        String batchStatus,
        String startTime,
        String endTime
    ) {}

    public record ProcessRunResponse(
        UUID runId,
        String batchNo,
        String stepName,
        String stationName,
        String equipmentName,
        String runStatus,
        String startTime,
        String endTime
    ) {}

    public record ParameterValueRequest(
        UUID paramId,
        double valueNum
    ) {}

    public record ParameterValueResponse(
        UUID valueId,
        String paramName,
        String unit,
        double valueNum,
        String qualityFlag,
        String measuredAt
    ) {}

    public record ImportRequest(
        @NotBlank String batchNo,
        @NotBlank String stepCode
    ) {}

    public record ImportResponse(
        UUID importId,
        UUID runId,
        String importStatus,
        int totalRows,
        int successRows
    ) {}

    public record CreateProcessRunRequest(
        UUID batchId,
        @NotBlank UUID stepId,
        UUID unitId,
        UUID stationId,
        UUID equipmentId,
        UUID recipeId
    ) {}

    public record CreateParameterValueRequest(
        UUID runId,
        UUID paramId,
        Double valueNum,
        String valueText
    ) {}

    public record CreateProcessRecipeRequest(
        @NotBlank String recipeCode,
        @NotBlank String recipeName,
        UUID productTypeId,
        UUID stepId,
        String parameterJson
    ) {}

    public record ProcessRecipeResponse(
        UUID recipeId,
        String recipeCode,
        String recipeName,
        UUID productTypeId,
        UUID stepId,
        String versionNo,
        Boolean isActive
    ) {}

    public record ProductUnitResponse(
        UUID unitId,
        UUID batchId,
        String serialNo,
        UUID currentStepId,
        String unitStatus
    ) {}

    public record CreateProductUnitRequest(
        UUID batchId,
        @NotBlank String serialNo
    ) {}

    public record DeviceLogResponse(
        UUID logId,
        UUID runId,
        UUID equipmentId,
        String logTime,
        String logLevel,
        String alarmCode,
        String alarmName,
        String logContent
    ) {}

    public record ProcessRunDetailResponse(
        UUID runId,
        UUID batchId,
        UUID unitId,
        UUID stepId,
        UUID stationId,
        UUID equipmentId,
        UUID recipeId,
        String runNo,
        String runStatus,
        String startTime,
        String endTime
    ) {}

    public record ParameterValueDetailResponse(
        UUID valueId,
        UUID runId,
        UUID paramId,
        java.math.BigDecimal valueNum,
        String valueText,
        String qualityFlag,
        String measuredAt
    ) {}

    public record CreateDeviceLogRequest(
        UUID runId,
        UUID equipmentId,
        String logLevel,
        String logContent
    ) {}
}
