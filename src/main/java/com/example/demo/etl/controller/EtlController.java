package com.example.demo.etl.controller;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.etl.dto.EtlDtos.CleaningLogResponse;
import com.example.demo.etl.dto.EtlDtos.CleaningRuleResponse;
import com.example.demo.etl.dto.EtlDtos.CreateCleaningRuleRequest;
import com.example.demo.etl.dto.EtlDtos.CreateImportJobRequest;
import com.example.demo.etl.dto.EtlDtos.ImportJobResponse;
import com.example.demo.etl.service.EtlService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/etl")
public class EtlController {

    private final EtlService etlService;

    public EtlController(EtlService etlService) {
        this.etlService = etlService;
    }

    // ===== ImportJob endpoints =====

    @PostMapping("/import-jobs")
    public ApiResponse<ImportJobResponse> createImportJob(@Valid @RequestBody CreateImportJobRequest request) {
        return ApiResponse.success(etlService.createImportJob(request));
    }

    @GetMapping("/import-jobs")
    public ApiResponse<List<ImportJobResponse>> listImportJobs() {
        return ApiResponse.success(etlService.listImportJobs());
    }

    @GetMapping("/import-jobs/{id}")
    public ApiResponse<ImportJobResponse> getImportJob(@PathVariable UUID id) {
        return ApiResponse.success(etlService.getImportJob(id));
    }

    @GetMapping("/import-jobs/{id}/cleaning-logs")
    public ApiResponse<List<CleaningLogResponse>> listImportJobCleaningLogs(@PathVariable UUID id) {
        return ApiResponse.success(etlService.listCleaningLogsByImportJob(id));
    }

    // ===== CleaningRule endpoints =====

    @PostMapping("/cleaning-rules")
    public ApiResponse<CleaningRuleResponse> createCleaningRule(@Valid @RequestBody CreateCleaningRuleRequest request) {
        return ApiResponse.success(etlService.createCleaningRule(request));
    }

    @GetMapping("/cleaning-rules")
    public ApiResponse<List<CleaningRuleResponse>> listCleaningRules(
            @RequestParam(required = false) String targetCategory) {
        if (targetCategory != null && !targetCategory.isBlank()) {
            return ApiResponse.success(etlService.listCleaningRulesByTargetCategory(targetCategory));
        }
        return ApiResponse.success(etlService.listCleaningRules());
    }

    @GetMapping("/cleaning-rules/{id}")
    public ApiResponse<CleaningRuleResponse> getCleaningRule(@PathVariable UUID id) {
        return ApiResponse.success(etlService.getCleaningRule(id));
    }

    // ===== CleaningLog endpoints =====

    @GetMapping("/cleaning-logs")
    public ApiResponse<List<CleaningLogResponse>> listCleaningLogs(
            @RequestParam(required = false) UUID ruleId,
            @RequestParam(required = false) String sourceTable,
            @RequestParam(required = false) UUID sourceId) {
        if (ruleId != null) {
            return ApiResponse.success(etlService.listCleaningLogsByRuleId(ruleId));
        }
        if ((sourceTable != null && !sourceTable.isBlank()) || sourceId != null) {
            return ApiResponse.success(etlService.listCleaningLogsBySource(sourceTable, sourceId));
        }
        return ApiResponse.success(etlService.listCleaningLogs());
    }
}
