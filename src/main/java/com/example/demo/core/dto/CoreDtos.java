package com.example.demo.core.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.UUID;

public final class CoreDtos {

    private CoreDtos() {}

    // ---- ProcessStep ----

    public record ProcessStepResponse(
        UUID stepId,
        String stepCode,
        String stepName,
        Integer stepOrder,
        Boolean isInspection,
        String description
    ) {}

    public record CreateProcessStepRequest(
        @NotBlank String stepCode,
        @NotBlank String stepName,
        Integer stepOrder,
        Boolean isInspection,
        String description
    ) {}

    // ---- Workstation ----

    public record WorkstationResponse(
        UUID stationId,
        UUID stepId,
        String stepCode,
        String stationCode,
        String stationName,
        String location,
        String status
    ) {}

    public record CreateWorkstationRequest(
        @NotBlank String stationCode,
        @NotBlank String stationName,
        UUID stepId,
        String location
    ) {}

    // ---- Equipment ----

    public record EquipmentResponse(
        UUID equipmentId,
        UUID stationId,
        String equipmentCode,
        String equipmentName,
        String equipmentType,
        String manufacturer,
        String modelNo,
        String status,
        String installedAt
    ) {}

    public record CreateEquipmentRequest(
        @NotBlank String equipmentCode,
        @NotBlank String equipmentName,
        UUID stationId,
        String equipmentType
    ) {}

    // ---- ProductType ----

    public record ProductTypeResponse(
        UUID productTypeId,
        String productCode,
        String productName,
        String materialSystem,
        String specification
    ) {}

    public record CreateProductTypeRequest(
        @NotBlank String productCode,
        @NotBlank String productName,
        String materialSystem
    ) {}

    // ---- ParameterDef ----

    public record ParameterDefResponse(
        UUID paramId,
        UUID stepId,
        String paramCode,
        String paramName,
        String paramCategory,
        String dataType,
        String unit,
        BigDecimal lowerLimit,
        BigDecimal upperLimit,
        BigDecimal standardValue,
        Boolean requiredFlag,
        String description
    ) {}

    public record CreateParameterDefRequest(
        @NotBlank String paramCode,
        @NotBlank String paramName,
        UUID stepId,
        String paramCategory,
        String dataType,
        String unit
    ) {}

    // ---- FileResource ----

    public record FileResourceResponse(
        UUID fileId,
        String fileType,
        String fileName,
        String filePath,
        String mimeType,
        Long fileSize,
        String sha256,
        UUID uploadedBy,
        String uploadedAt
    ) {}
}
