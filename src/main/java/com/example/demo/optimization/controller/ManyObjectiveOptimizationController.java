package com.example.demo.optimization.controller;

import com.example.demo.common.api.ApiResponse;
import com.example.demo.eval.dto.EvalDtos.OptimizationResponse;
import com.example.demo.optimization.service.ManyObjectiveOptimizationRunner;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/optimization")
public class ManyObjectiveOptimizationController {

    private final ManyObjectiveOptimizationRunner runner;

    public ManyObjectiveOptimizationController(ManyObjectiveOptimizationRunner runner) {
        this.runner = runner;
    }

    @PostMapping("/many-objective/run/{batchId}")
    public ApiResponse<OptimizationResponse> runManyObjective(
            @PathVariable UUID batchId,
            @RequestParam(defaultValue = "MANSGA_III") String algorithm,
            @RequestParam(defaultValue = "96") int populationSize,
            @RequestParam(defaultValue = "120") int generations) {
        return ApiResponse.success(runner.run(batchId, algorithm, populationSize, generations));
    }

    @GetMapping("/many-objective/objectives")
    public ApiResponse<Map<String, Object>> objectiveMetadata() {
        return ApiResponse.success(runner.objectiveMetadata());
    }
}
