package com.example.demo.eval.controller;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.eval.dto.EvalDtos.*;
import com.example.demo.eval.service.EvalService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    // ──────────────────── Assessment Dashboard ────────────────────

    @GetMapping("/assessment/qualified")
    public ApiResponse<QualifiedDashboardData> getQualifiedDashboard(
            @RequestParam(required = false) UUID batchId) {
        return ApiResponse.success(evalService.getQualifiedDashboard(batchId));
    }

    @GetMapping("/assessment/judgment")
    public ApiResponse<JudgmentDashboardData> getJudgmentDashboard(
            @RequestParam(required = false) UUID batchId) {
        return ApiResponse.success(evalService.getJudgmentDashboard(batchId));
    }

    @GetMapping("/assessment/prediction")
    public ApiResponse<PredictionDashboardData> getPredictionDashboard(
            @RequestParam(required = false) UUID batchId) {
        return ApiResponse.success(evalService.getPredictionDashboard(batchId));
    }

    @GetMapping("/assessment/history")
    public ApiResponse<AssessmentHistoryPage> getHistory(
            @RequestParam(required = false) UUID batchId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(evalService.getAssessmentHistory(batchId, page, size));
    }

    @GetMapping("/assessment/stations")
    public ApiResponse<List<String>> getStations() {
        return ApiResponse.success(evalService.getStations());
    }

    @GetMapping("/assessment/batches")
    public ApiResponse<List<String>> getBatches() {
        return ApiResponse.success(evalService.getBatches());
    }

    // ──────────────────── Judgment & Prediction Streams ────────────────────

    @GetMapping("/assessment/judgment/stream")
    public ApiResponse<JudgmentStreamData> getJudgmentStream(
            @RequestParam(required = false) UUID batchId) {
        return ApiResponse.success(evalService.getJudgmentStream(batchId));
    }

    @GetMapping("/assessment/prediction/simulation")
    public ApiResponse<SimulationStreamData> getSimulationStream(
            @RequestParam(required = false) UUID batchId) {
        return ApiResponse.success(evalService.getSimulationStream(batchId));
    }

    // ──────────────────── AssessmentTask CRUD ────────────────────

    @PostMapping("/assessment/tasks")
    public ApiResponse<AssessmentTaskResponse> createTask(@RequestBody CreateAssessmentRequest request) {
        return ApiResponse.success(evalService.createTask(request));
    }

    @GetMapping("/assessment/tasks/{id}")
    public ApiResponse<AssessmentTaskResponse> getTask(@PathVariable UUID id) {
        return ApiResponse.success(evalService.getTaskById(id));
    }

    // ──────────────────── Optimization ────────────────────

    @PostMapping("/optimization/run/{batchId}")
    public ApiResponse<OptimizationResponse> runOptimization(@PathVariable String batchId) {
        return ApiResponse.success(evalService.runOptimization(batchId));
    }

    @GetMapping("/optimization/result/{batchId}")
    public ApiResponse<OptimizationResponse> getOptimizationResult(@PathVariable String batchId) {
        return ApiResponse.success(evalService.getOptimizationResult(batchId));
    }
}
