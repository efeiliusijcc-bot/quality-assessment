package com.example.demo.qc.cube;

import com.example.demo.common.api.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quality-cube")
public class QualityCubeController {

    private final QualityCubeService qualityCubeService;

    public QualityCubeController(QualityCubeService qualityCubeService) {
        this.qualityCubeService = qualityCubeService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success(qualityCubeService.overview());
    }

    @GetMapping("/by-batch-step")
    public ApiResponse<List<Map<String, Object>>> byBatchStep(
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String stepCode,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        return ApiResponse.success(qualityCubeService.byBatchStep(batchNo, stepCode, limit));
    }

    @GetMapping("/by-step-type")
    public ApiResponse<List<Map<String, Object>>> byStepType(
            @RequestParam(required = false) String stepCode,
            @RequestParam(required = false) String defectCode,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        return ApiResponse.success(qualityCubeService.byStepType(stepCode, defectCode, limit));
    }

    @GetMapping("/by-equipment")
    public ApiResponse<List<Map<String, Object>>> byEquipment(
            @RequestParam(required = false) String equipmentCode,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        return ApiResponse.success(qualityCubeService.byEquipment(equipmentCode, limit));
    }

    @GetMapping("/by-time")
    public ApiResponse<List<Map<String, Object>>> byTime(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String stepCode,
            @RequestParam(required = false, defaultValue = "365") Integer limit) {
        return ApiResponse.success(qualityCubeService.byTime(from, to, stepCode, limit));
    }

    @GetMapping("/by-severity")
    public ApiResponse<List<Map<String, Object>>> bySeverity(
            @RequestParam(required = false) String severityLevel,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        return ApiResponse.success(qualityCubeService.bySeverity(severityLevel, limit));
    }

    @GetMapping("/metadata")
    public ApiResponse<List<Map<String, Object>>> metadata(
            @RequestParam(required = false, defaultValue = "defect_cube") String dataDomain) {
        return ApiResponse.success(qualityCubeService.metadata(dataDomain));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh() {
        return ApiResponse.success(qualityCubeService.refresh());
    }
}
