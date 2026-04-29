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
}
