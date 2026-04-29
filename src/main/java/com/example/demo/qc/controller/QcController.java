package com.example.demo.qc.controller;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.qc.dto.QcDtos.*;
import com.example.demo.qc.service.QcService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QcController {

    private final QcService qcService;

    public QcController(QcService qcService) {
        this.qcService = qcService;
    }

    // ===== Defect detection endpoints =====

    @GetMapping("/api/defect/samples")
    public ApiResponse<List<DefectSampleResponse>> getDefectSamples() {
        return ApiResponse.success(qcService.getDefectSamples());
    }

    @PostMapping("/api/defect/detect/batch")
    public ApiResponse<BatchDetectResponse> batchDetect(@Valid @RequestBody List<BatchDetectRequestItem> items) {
        return ApiResponse.success(qcService.batchDetect(items));
    }

    @GetMapping("/api/defect/statistics")
    public ApiResponse<DefectStatisticsResponse> getDefectStatistics() {
        return ApiResponse.success(qcService.getDefectStatistics());
    }

    // ===== Quality metric definition endpoints =====

    @PostMapping("/api/qc/metric-defs")
    public ApiResponse<QualityMetricDefResponse> createMetricDef(
            @Valid @RequestBody CreateQualityMetricDefRequest request) {
        return ApiResponse.success(qcService.createMetricDef(request));
    }

    @GetMapping("/api/qc/metric-defs")
    public ApiResponse<List<QualityMetricDefResponse>> listMetricDefs(
            @RequestParam(required = false) UUID stepId) {
        if (stepId != null) {
            return ApiResponse.success(qcService.listMetricDefsByStep(stepId));
        }
        return ApiResponse.success(qcService.listAllMetricDefs());
    }

    // ===== Quality measurement endpoints =====

    @PostMapping("/api/qc/measurements")
    public ApiResponse<QualityMeasurementResponse> createMeasurement(
            @Valid @RequestBody CreateQualityMeasurementRequest request) {
        return ApiResponse.success(qcService.createMeasurement(request));
    }

    @GetMapping("/api/qc/measurements")
    public ApiResponse<List<QualityMeasurementResponse>> listMeasurements(
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) UUID unitId) {
        if (runId != null) {
            return ApiResponse.success(qcService.listMeasurementsByRun(runId));
        }
        if (unitId != null) {
            return ApiResponse.success(qcService.listMeasurementsByUnit(unitId));
        }
        return ApiResponse.success(List.of());
    }

    // ===== Defect type endpoints =====

    @PostMapping("/api/qc/defect-types")
    public ApiResponse<DefectTypeResponse> createDefectType(
            @Valid @RequestBody CreateDefectTypeRequest request) {
        return ApiResponse.success(qcService.createDefectType(request));
    }

    @GetMapping("/api/qc/defect-types")
    public ApiResponse<List<DefectTypeResponse>> listDefectTypes(
            @RequestParam(required = false) UUID stepId) {
        if (stepId != null) {
            return ApiResponse.success(qcService.listDefectTypesByStep(stepId));
        }
        return ApiResponse.success(qcService.listAllDefectTypes());
    }

    // ===== Inspection task endpoints =====

    @PostMapping("/api/qc/inspections")
    public ApiResponse<InspectionTaskResponse> createInspection(
            @Valid @RequestBody CreateInspectionTaskRequest request) {
        return ApiResponse.success(qcService.createInspection(request));
    }

    @GetMapping("/api/qc/inspections")
    public ApiResponse<List<InspectionTaskResponse>> listInspections(
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) UUID unitId) {
        if (runId != null) {
            return ApiResponse.success(qcService.listInspectionsByRun(runId));
        }
        if (unitId != null) {
            return ApiResponse.success(qcService.listInspectionsByUnit(unitId));
        }
        return ApiResponse.success(qcService.listAllInspections());
    }

    // ===== Defect record endpoints =====

    @PostMapping("/api/qc/defect-records")
    public ApiResponse<DefectRecordResponse> createDefectRecord(
            @Valid @RequestBody CreateDefectRecordRequest request) {
        return ApiResponse.success(qcService.createDefectRecord(request));
    }

    @GetMapping("/api/qc/defect-records")
    public ApiResponse<List<DefectRecordResponse>> listDefectRecords(
            @RequestParam(required = false) UUID inspectionId,
            @RequestParam(required = false) UUID unitId) {
        if (inspectionId != null) {
            return ApiResponse.success(qcService.listDefectRecordsByInspection(inspectionId));
        }
        if (unitId != null) {
            return ApiResponse.success(qcService.listDefectRecordsByUnit(unitId));
        }
        return ApiResponse.success(List.of());
    }
}
