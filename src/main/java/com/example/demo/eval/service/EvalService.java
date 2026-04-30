package com.example.demo.eval.service;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.core.domain.Workstation;
import com.example.demo.core.repository.WorkstationRepository;
import com.example.demo.eval.domain.AssessmentResult;
import com.example.demo.eval.domain.AssessmentTask;
import com.example.demo.eval.domain.OptimizationResult;
import com.example.demo.eval.domain.OptimizationTask;
import com.example.demo.eval.dto.EvalDtos.*;
import com.example.demo.eval.repository.AssessmentResultRepository;
import com.example.demo.eval.repository.AssessmentTaskRepository;
import com.example.demo.eval.repository.OptimizationResultRepository;
import com.example.demo.eval.repository.OptimizationTaskRepository;
import com.example.demo.optimization.algorithm.Mansga3Optimizer;
import com.example.demo.optimization.domain.OptimizationSolution;
import com.example.demo.optimization.domain.ProcessParameterSpace;
import com.example.demo.prod.domain.ParameterValue;
import com.example.demo.prod.domain.ProductionBatch;
import com.example.demo.prod.domain.ProcessRun;
import com.example.demo.prod.repository.ParameterValueRepository;
import com.example.demo.prod.repository.ProductionBatchRepository;
import com.example.demo.prod.repository.ProcessRunRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class EvalService {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final AssessmentTaskRepository assessmentTaskRepo;
    private final AssessmentResultRepository assessmentResultRepo;
    private final OptimizationTaskRepository optimizationTaskRepo;
    private final OptimizationResultRepository optimizationResultRepo;
    private final WorkstationRepository workstationRepo;
    private final ProductionBatchRepository batchRepo;
    private final ProcessRunRepository processRunRepo;
    private final ParameterValueRepository parameterValueRepo;

    public EvalService(AssessmentTaskRepository assessmentTaskRepo,
                       AssessmentResultRepository assessmentResultRepo,
                       OptimizationTaskRepository optimizationTaskRepo,
                       OptimizationResultRepository optimizationResultRepo,
                       WorkstationRepository workstationRepo,
                       ProductionBatchRepository batchRepo,
                       ProcessRunRepository processRunRepo,
                       ParameterValueRepository parameterValueRepo) {
        this.assessmentTaskRepo = assessmentTaskRepo;
        this.assessmentResultRepo = assessmentResultRepo;
        this.optimizationTaskRepo = optimizationTaskRepo;
        this.optimizationResultRepo = optimizationResultRepo;
        this.workstationRepo = workstationRepo;
        this.batchRepo = batchRepo;
        this.processRunRepo = processRunRepo;
        this.parameterValueRepo = parameterValueRepo;
    }

    // ════════════════════════════════════════════════════════════════
    //  AssessmentTask CRUD
    // ════════════════════════════════════════════════════════════════

    public AssessmentTaskResponse createTask(CreateAssessmentRequest request) {
        AssessmentTask task = new AssessmentTask(request.taskType(), request.batchId());
        assessmentTaskRepo.save(task);
        return toTaskResponse(task);
    }

    public AssessmentTaskResponse getTaskById(UUID taskId) {
        AssessmentTask task = assessmentTaskRepo.findById(taskId)
            .orElseThrow(() -> new BusinessException(404, "assessment task not found"));
        return toTaskResponse(task);
    }

    public List<AssessmentTaskResponse> listAllTasks() {
        return assessmentTaskRepo.findAll().stream()
            .map(this::toTaskResponse)
            .toList();
    }

    // ════════════════════════════════════════════════════════════════
    //  AssessmentResult
    // ════════════════════════════════════════════════════════════════

    public List<AssessmentResult> listResultsByTask(UUID taskId) {
        return assessmentResultRepo.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    // ════════════════════════════════════════════════════════════════
    //  OptimizationTask CRUD
    // ════════════════════════════════════════════════════════════════

    public OptimizationTask createOptTask(UUID batchId, String algorithmName) {
        OptimizationTask task = new OptimizationTask(batchId, algorithmName);
        optimizationTaskRepo.save(task);
        return task;
    }

    public OptimizationTask getOptTaskById(UUID optTaskId) {
        return optimizationTaskRepo.findById(optTaskId)
            .orElseThrow(() -> new BusinessException(404, "optimization task not found"));
    }

    // ════════════════════════════════════════════════════════════════
    //  OptimizationResult
    // ════════════════════════════════════════════════════════════════

    public List<OptimizationResult> listOptResultsByTask(UUID optTaskId) {
        return optimizationResultRepo.findByOptTaskIdOrderByParetoRankAsc(optTaskId);
    }

    // ════════════════════════════════════════════════════════════════
    //  Dashboard – Qualified
    // ════════════════════════════════════════════════════════════════

    public QualifiedDashboardData getQualifiedDashboard(UUID batchId) {
        List<IntroMetric> metrics = buildDefaultMetrics("qualified");
        GraphReasoning reasoning = defaultGraphReasoning();

        if (batchId != null) {
            List<ProcessRun> runs = processRunRepo.findByBatchIdOrderByCreatedAtAsc(batchId);
            if (!runs.isEmpty()) {
                metrics = buildMetricsFromRuns(runs);
            }
        }

        return new QualifiedDashboardData(
            metrics,
            List.of(),  // timeAxis
            List.of(),  // temperatureData
            List.of(),  // pressureData
            List.of(),  // currentData
            List.of(),  // resultCards
            List.of(),  // streamMetrics
            reasoning
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  Dashboard – Judgment
    // ════════════════════════════════════════════════════════════════

    public JudgmentDashboardData getJudgmentDashboard(UUID batchId) {
        List<IntroMetric> metrics = buildDefaultMetrics("judgment");
        GraphReasoning reasoning = defaultGraphReasoning();

        return new JudgmentDashboardData(
            metrics,
            List.of(),  // radarIndicators
            List.of(),  // abnormalSampleValues
            List.of(),  // targetValues
            List.of(),  // compareCategories
            List.of(),  // currentParameters
            List.of(),  // targetParameters
            "",  // coreConclusion
            "",  // coreDescription
            List.of(),  // diagnosisItems
            List.of(),  // actionItems
            reasoning
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  Dashboard – Prediction
    // ════════════════════════════════════════════════════════════════

    public PredictionDashboardData getPredictionDashboard(UUID batchId) {
        List<IntroMetric> metrics = buildDefaultMetrics("prediction");
        GraphReasoning reasoning = defaultGraphReasoning();

        return new PredictionDashboardData(
            metrics,
            0.0,  // predictedProbability
            0.5,  // threshold
            List.of(),  // triggerCards
            List.of(),  // optimizationTable
            List.of(),  // optimizationSummary
            reasoning
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  History
    // ════════════════════════════════════════════════════════════════

    public AssessmentHistoryPage getAssessmentHistory(UUID batchId, int page, int size) {
        if (batchId == null) {
            return new AssessmentHistoryPage(List.of(), 0);
        }

        List<ProcessRun> runs = processRunRepo.findByBatchIdOrderByCreatedAtAsc(batchId);
        if (runs.isEmpty()) {
            return new AssessmentHistoryPage(List.of(), 0);
        }

        // Batch-load all ParameterValues for all runs
        List<UUID> runIds = runs.stream().map(ProcessRun::getRunId).toList();
        Map<UUID, List<ParameterValue>> valuesByRun = batchLoadParameterValues(runIds);

        List<AssessmentHistoryItem> allItems = new ArrayList<>();
        for (ProcessRun run : runs) {
            List<ParameterValue> values = valuesByRun.getOrDefault(run.getRunId(), List.of());
            if (values.isEmpty()) {
                // Create a placeholder item from the run itself
                allItems.add(new AssessmentHistoryItem(
                    run.getRunId().toString(),
                    batchId.toString(),
                    run.getStationId() != null ? run.getStationId().toString() : "",
                    0.0, 0.0, 0.0,
                    run.getStartTime() != null ? run.getStartTime().toString() : ""
                ));
            } else {
                for (ParameterValue pv : values) {
                    allItems.add(new AssessmentHistoryItem(
                        pv.getValueId().toString(),
                        batchId.toString(),
                        run.getStationId() != null ? run.getStationId().toString() : "",
                        extractDouble(values, 0),  // temperature placeholder
                        extractDouble(values, 1),  // pressure placeholder
                        pv.getValueNum() != null ? pv.getValueNum().doubleValue() : 0.0,
                        pv.getMeasuredAt() != null ? pv.getMeasuredAt().toString() : ""
                    ));
                    break; // One item per run
                }
            }
        }

        int total = allItems.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        List<AssessmentHistoryItem> paged = allItems.subList(from, to);

        return new AssessmentHistoryPage(paged, total);
    }

    // ════════════════════════════════════════════════════════════════
    //  Stations & Batches
    // ════════════════════════════════════════════════════════════════

    public List<String> getStations() {
        return workstationRepo.findAll().stream()
            .map(Workstation::getStationName)
            .toList();
    }

    public List<String> getBatches() {
        return batchRepo.findAll().stream()
            .map(ProductionBatch::getBatchNo)
            .toList();
    }

    // ════════════════════════════════════════════════════════════════
    //  Judgment Stream
    // ════════════════════════════════════════════════════════════════

    public JudgmentStreamData getJudgmentStream(UUID batchId) {
        if (batchId == null) {
            return new JudgmentStreamData(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        List<ProcessRun> runs = processRunRepo.findByBatchIdOrderByCreatedAtAsc(batchId);
        if (runs.isEmpty()) {
            return new JudgmentStreamData(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        // Batch-load all ParameterValues for all runs
        List<UUID> runIds = runs.stream().map(ProcessRun::getRunId).toList();
        Map<UUID, List<ParameterValue>> valuesByRun = batchLoadParameterValues(runIds);

        List<String> timeAxis = new ArrayList<>();
        List<Double> temperature = new ArrayList<>();
        List<Double> beltSpeed = new ArrayList<>();
        List<Double> o2Ppm = new ArrayList<>();
        List<Double> humidity = new ArrayList<>();
        List<Double> current = new ArrayList<>();

        for (ProcessRun run : runs) {
            List<ParameterValue> values = valuesByRun.getOrDefault(run.getRunId(), List.of());
            timeAxis.add(run.getStartTime() != null ? run.getStartTime().toString() : "");
            temperature.add(extractDouble(values, 0));
            beltSpeed.add(extractDouble(values, 1));
            o2Ppm.add(extractDouble(values, 2));
            humidity.add(extractDouble(values, 3));
            current.add(extractDouble(values, 4));
        }

        return new JudgmentStreamData(timeAxis, temperature, beltSpeed, o2Ppm, humidity, current);
    }

    // ════════════════════════════════════════════════════════════════
    //  Simulation Stream
    // ════════════════════════════════════════════════════════════════

    public SimulationStreamData getSimulationStream(UUID batchId) {
        if (batchId == null) {
            return new SimulationStreamData(List.of());
        }

        List<ProcessRun> runs = processRunRepo.findByBatchIdOrderByCreatedAtAsc(batchId);
        if (runs.isEmpty()) {
            return new SimulationStreamData(List.of());
        }

        // Batch-load all ParameterValues for all runs
        List<UUID> runIds = runs.stream().map(ProcessRun::getRunId).toList();
        Map<UUID, List<ParameterValue>> valuesByRun = batchLoadParameterValues(runIds);

        List<SimulationDataPoint> points = new ArrayList<>();
        for (ProcessRun run : runs) {
            List<ParameterValue> values = valuesByRun.getOrDefault(run.getRunId(), List.of());
            points.add(new SimulationDataPoint(
                run.getStartTime() != null ? run.getStartTime().toString() : "",
                extractDouble(values, 0),  // temperature
                extractDouble(values, 1),  // pressure
                extractDouble(values, 2),  // beltSpeed
                0.0  // probability
            ));
        }

        return new SimulationStreamData(points);
    }

    // ════════════════════════════════════════════════════════════════
    //  Optimization
    // ════════════════════════════════════════════════════════════════

    public OptimizationResponse runOptimization(String batchId) {
        UUID batchUuid;
        try {
            batchUuid = UUID.fromString(batchId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "invalid batchId format");
        }

        long startTime = System.currentTimeMillis();
        int populationSize = 50;
        int generations = 100;
        int objectiveCount = 2;

        // Use Mansga3Optimizer as default
        Mansga3Optimizer optimizer = new Mansga3Optimizer(populationSize, generations, objectiveCount, System.nanoTime());

        // Simple evaluator: minimize deviation from target values
        Map<String, Double> targets = ProcessParameterSpace.targetValues();
        java.util.function.Function<OptimizationSolution, Map<String, Double>> evaluator = solution -> {
            Map<String, Double> objectives = new LinkedHashMap<>();
            double totalDeviation = 0.0;
            double maxDeviation = 0.0;
            for (Map.Entry<String, Double> entry : solution.getParameters().entrySet()) {
                double target = targets.getOrDefault(entry.getKey(), entry.getValue());
                double dev = Math.abs(entry.getValue() - target);
                totalDeviation += dev;
                maxDeviation = Math.max(maxDeviation, dev);
            }
            objectives.put("total_deviation", totalDeviation);
            objectives.put("max_deviation", maxDeviation);
            return objectives;
        };

        List<OptimizationSolution> paretoFront = optimizer.optimize(evaluator);
        long elapsed = System.currentTimeMillis() - startTime;

        // Convert to DTOs
        List<ParetoSolutionDto> paretoDtos = paretoFront.stream()
            .map(sol -> new ParetoSolutionDto(
                sol.getParameters(),
                sol.getObjectives(),
                sol.getCrowdingDistance()
            ))
            .toList();

        // Pick recommended solution (first in Pareto front)
        ParetoSolutionDto recommended = paretoDtos.isEmpty() ? null : paretoDtos.get(0);

        OptimizationStatisticsDto stats = new OptimizationStatisticsDto(
            elapsed,
            populationSize * generations,
            paretoFront.size()
        );

        // Persist results
        OptimizationTask optTask = createOptTask(batchUuid, "MANSga3");
        optTask.setOptStatus("COMPLETED");
        optTask.setFinishedAt(Instant.now());
        optimizationTaskRepo.save(optTask);

        for (int i = 0; i < paretoFront.size(); i++) {
            OptimizationSolution sol = paretoFront.get(i);
            OptimizationResult result = new OptimizationResult(optTask.getOptTaskId(), i);
            try {
                // Store parameter solution and objective values as JSON strings
                // These will be persisted via the JSONB columns
                result.setParameterSolution(MAPPER.writeValueAsString(sol.getParameters()));
                result.setObjectiveValues(MAPPER.writeValueAsString(sol.getObjectives()));
                result.setFeasibleFlag(true);
                result.setRecommendationLevel(i == 0 ? "RECOMMENDED" : "ALTERNATIVE");
                optimizationResultRepo.save(result);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(EvalService.class).warn("Failed to persist optimization result {}: {}", i, e.getMessage());
            }
        }

        return new OptimizationResponse(
            batchId,
            "MANSga3",
            generations,
            paretoDtos,
            recommended,
            stats
        );
    }

    public OptimizationResponse getOptimizationResult(String batchId) {
        UUID batchUuid;
        try {
            batchUuid = UUID.fromString(batchId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "invalid batchId format");
        }

        List<OptimizationTask> tasks = optimizationTaskRepo.findByBatchIdOrderByCreatedAtDesc(batchUuid);
        if (tasks.isEmpty()) {
            throw new BusinessException(404, "no optimization result found for batch");
        }

        OptimizationTask task = tasks.get(0);
        List<OptimizationResult> results = optimizationResultRepo.findByOptTaskIdOrderByParetoRankAsc(task.getOptTaskId());

        List<ParetoSolutionDto> paretoDtos = results.stream()
            .map(r -> {
                Map<String, Double> params = parseJsonMap(r.getParameterSolution());
                Map<String, Double> objs = parseJsonMap(r.getObjectiveValues());
                return new ParetoSolutionDto(params, objs, 0.0);
            })
            .toList();

        ParetoSolutionDto recommended = paretoDtos.isEmpty() ? null : paretoDtos.get(0);

        OptimizationStatisticsDto stats = new OptimizationStatisticsDto(
            task.getFinishedAt() != null && task.getCreatedAt() != null
                ? task.getFinishedAt().toEpochMilli() - task.getCreatedAt().toEpochMilli()
                : 0,
            results.size(),
            paretoDtos.size()
        );

        return new OptimizationResponse(
            batchId,
            task.getAlgorithmName(),
            0,
            paretoDtos,
            recommended,
            stats
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════

    private AssessmentTaskResponse toTaskResponse(AssessmentTask t) {
        return new AssessmentTaskResponse(
            t.getTaskId(),
            t.getTaskType(),
            t.getBatchId(),
            t.getTaskStatus(),
            t.getCreatedAt() != null ? t.getCreatedAt().toString() : null,
            t.getFinishedAt() != null ? t.getFinishedAt().toString() : null
        );
    }

    private List<IntroMetric> buildDefaultMetrics(String type) {
        return switch (type) {
            case "qualified" -> List.of(
                new IntroMetric("Total Inspected", "0", ""),
                new IntroMetric("Pass Rate", "0%", ""),
                new IntroMetric("Average Score", "0", "")
            );
            case "judgment" -> List.of(
                new IntroMetric("Abnormal Count", "0", ""),
                new IntroMetric("Risk Level", "N/A", ""),
                new IntroMetric("Diagnosis Accuracy", "0%", "")
            );
            case "prediction" -> List.of(
                new IntroMetric("Defect Probability", "0%", ""),
                new IntroMetric("Triggered Rules", "0", ""),
                new IntroMetric("Optimization Gain", "0%", "")
            );
            default -> List.of();
        };
    }

    private List<IntroMetric> buildMetricsFromRuns(List<ProcessRun> runs) {
        return List.of(
            new IntroMetric("Total Runs", String.valueOf(runs.size()), ""),
            new IntroMetric("Batch ID", runs.get(0).getBatchId().toString(), ""),
            new IntroMetric("Status", runs.get(0).getRunStatus(), "")
        );
    }

    private GraphReasoning defaultGraphReasoning() {
        return new GraphReasoning(
            0.0,
            "",
            List.of(),
            List.of(),
            List.of(),
            "",
            List.of(),
            new ReasoningStatistics(0, 0, 0, 0, 0, 0)
        );
    }

    private double extractDouble(List<ParameterValue> values, int index) {
        if (index < 0 || index >= values.size()) return 0.0;
        BigDecimal bd = values.get(index).getValueNum();
        return bd != null ? bd.doubleValue() : 0.0;
    }

    /**
     * Batch-load ParameterValues for multiple runs and group by runId.
     * Replaces N individual queries with a single IN query.
     */
    private Map<UUID, List<ParameterValue>> batchLoadParameterValues(List<UUID> runIds) {
        if (runIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ParameterValue>> result = new LinkedHashMap<>();
        for (UUID runId : runIds) {
            result.put(runId, new ArrayList<>());
        }
        for (ParameterValue pv : parameterValueRepo.findByRunIdInOrderByMeasuredAtAsc(runIds)) {
            result.get(pv.getRunId()).add(pv);
        }
        return result;
    }

    private Map<String, Double> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
