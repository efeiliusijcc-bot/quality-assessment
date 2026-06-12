package com.example.demo.optimization.service;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.eval.domain.OptimizationResult;
import com.example.demo.eval.domain.OptimizationTask;
import com.example.demo.eval.dto.EvalDtos.OptimizationResponse;
import com.example.demo.eval.dto.EvalDtos.OptimizationStatisticsDto;
import com.example.demo.eval.dto.EvalDtos.ParetoSolutionDto;
import com.example.demo.eval.repository.OptimizationResultRepository;
import com.example.demo.eval.repository.OptimizationTaskRepository;
import com.example.demo.optimization.algorithm.Mansga3Optimizer;
import com.example.demo.optimization.algorithm.PushPullSearchOptimizer;
import com.example.demo.optimization.domain.OptimizationObjective;
import com.example.demo.optimization.domain.OptimizationSolution;
import com.example.demo.prod.domain.ProcessRun;
import com.example.demo.prod.repository.ProcessRunRepository;
import com.example.demo.qc.domain.DefectRecord;
import com.example.demo.qc.domain.InspectionTask;
import com.example.demo.qc.domain.QualityMeasurement;
import com.example.demo.qc.repository.DefectRecordRepository;
import com.example.demo.qc.repository.InspectionTaskRepository;
import com.example.demo.qc.repository.QualityMeasurementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Business runner for task 2: seven-objective process parameter optimization.
 * Supports MANSGA-III and Push-Pull constrained many-objective search.
 */
@Service
public class ManyObjectiveOptimizationRunner {

    private static final int OBJECTIVE_COUNT = 7;

    private final QualityManyObjectiveEvaluator evaluator;
    private final ProcessRunRepository processRunRepository;
    private final InspectionTaskRepository inspectionTaskRepository;
    private final DefectRecordRepository defectRecordRepository;
    private final QualityMeasurementRepository qualityMeasurementRepository;
    private final OptimizationTaskRepository optimizationTaskRepository;
    private final OptimizationResultRepository optimizationResultRepository;
    private final ObjectMapper objectMapper;

    public ManyObjectiveOptimizationRunner(QualityManyObjectiveEvaluator evaluator,
                                           ProcessRunRepository processRunRepository,
                                           InspectionTaskRepository inspectionTaskRepository,
                                           DefectRecordRepository defectRecordRepository,
                                           QualityMeasurementRepository qualityMeasurementRepository,
                                           OptimizationTaskRepository optimizationTaskRepository,
                                           OptimizationResultRepository optimizationResultRepository,
                                           ObjectMapper objectMapper) {
        this.evaluator = evaluator;
        this.processRunRepository = processRunRepository;
        this.inspectionTaskRepository = inspectionTaskRepository;
        this.defectRecordRepository = defectRecordRepository;
        this.qualityMeasurementRepository = qualityMeasurementRepository;
        this.optimizationTaskRepository = optimizationTaskRepository;
        this.optimizationResultRepository = optimizationResultRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OptimizationResponse run(UUID batchId, String algorithmName, int populationSize, int generations) {
        if (batchId == null) {
            throw new BusinessException(400, "batchId is required");
        }
        String algorithm = normalizeAlgorithm(algorithmName);
        populationSize = Math.max(64, populationSize);
        generations = Math.max(80, generations);

        long start = System.currentTimeMillis();
        QualityManyObjectiveEvaluator.BatchOptimizationContext context = loadContext(batchId);
        Function<OptimizationSolution, Map<String, Double>> objectiveFunction = solution -> evaluator.evaluate(solution, context);

        List<OptimizationSolution> paretoFront;
        if ("PUSH_PULL".equals(algorithm)) {
            PushPullSearchOptimizer optimizer = new PushPullSearchOptimizer(populationSize, generations, OBJECTIVE_COUNT, System.nanoTime());
            paretoFront = optimizer.optimize(objectiveFunction);
        } else {
            Mansga3Optimizer optimizer = new Mansga3Optimizer(populationSize, generations, OBJECTIVE_COUNT, System.nanoTime());
            paretoFront = optimizer.optimize(objectiveFunction);
        }

        paretoFront = paretoFront.stream()
                .sorted(Comparator.comparingDouble(this::totalObjectiveValue))
                .limit(50)
                .toList();

        OptimizationTask task = new OptimizationTask(batchId, algorithm);
        task.setObjectives(toJson(OptimizationObjective.codes()));
        task.setInputParams(toJson(Map.of(
                "populationSize", populationSize,
                "generations", generations,
                "objectiveCount", OBJECTIVE_COUNT
        )));
        task.setConstraints(toJson(Map.of(
                "algorithm", algorithm,
                "allObjectivesMinimized", true
        )));
        task.setOptStatus("COMPLETED");
        task.setFinishedAt(Instant.now());
        optimizationTaskRepository.save(task);

        List<ParetoSolutionDto> dtos = new ArrayList<>();
        for (int i = 0; i < paretoFront.size(); i++) {
            OptimizationSolution solution = paretoFront.get(i);
            OptimizationResult result = new OptimizationResult(task.getOptTaskId(), i);
            result.setParameterSolution(toJson(solution.getParameters()));
            result.setObjectiveValues(toJson(solution.getObjectives()));
            result.setFeasibleFlag(isFeasible(solution));
            result.setRecommendationLevel(i == 0 ? "RECOMMENDED" : "ALTERNATIVE");
            optimizationResultRepository.save(result);
            dtos.add(new ParetoSolutionDto(solution.getParameters(), solution.getObjectives(), solution.getCrowdingDistance()));
        }

        long elapsed = System.currentTimeMillis() - start;
        return new OptimizationResponse(
                batchId.toString(),
                algorithm,
                generations,
                dtos,
                dtos.isEmpty() ? null : dtos.get(0),
                new OptimizationStatisticsDto(elapsed, populationSize * generations, dtos.size())
        );
    }

    public Map<String, Object> objectiveMetadata() {
        Map<String, Object> meta = new LinkedHashMap<>(evaluator.objectiveMetadata());
        meta.put("supportedAlgorithms", List.of("MANSGA_III", "PUSH_PULL"));
        meta.put("note", "All objectives are minimized; pass rate and reliability are represented as losses.");
        return meta;
    }

    private QualityManyObjectiveEvaluator.BatchOptimizationContext loadContext(UUID batchId) {
        List<ProcessRun> runs = processRunRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        if (runs.isEmpty()) {
            return QualityManyObjectiveEvaluator.BatchOptimizationContext.defaults();
        }
        List<UUID> runIds = runs.stream().map(ProcessRun::getRunId).toList();
        List<InspectionTask> tasks = inspectionTaskRepository.findByRunIdIn(runIds);
        List<DefectRecord> defects = tasks.isEmpty() ? List.of() : defectRecordRepository.findByInspectionIdIn(tasks.stream().map(InspectionTask::getInspectionId).toList());
        List<QualityMeasurement> measurements = qualityMeasurementRepository.findByRunIdInOrderByMeasuredAtAsc(runIds);

        double maxSeverity = defects.stream()
                .map(DefectRecord::getSeverityLevel)
                .filter(v -> v != null)
                .mapToDouble(v -> Math.min(5, Math.max(1, v)) / 5.0)
                .max().orElse(0.20);
        double countRisk = Math.min(1.0, defects.stream().mapToInt(d -> d.getDefectCount() == null ? 1 : Math.max(1, d.getDefectCount())).sum() / 20.0);
        double sizeRisk = defects.stream()
                .map(DefectRecord::getConfidence)
                .filter(v -> v != null)
                .mapToDouble(v -> Math.max(0.0, Math.min(1.0, v.doubleValue())))
                .average().orElse(0.22);
        long passCount = measurements.stream().filter(m -> Boolean.TRUE.equals(m.getIsPass())).count();
        double passRate = measurements.isEmpty() ? 0.88 : passCount / (double) measurements.size();
        double reliability = Math.max(0.30, Math.min(0.98, passRate - 0.15 * countRisk - 0.10 * maxSeverity));
        return new QualityManyObjectiveEvaluator.BatchOptimizationContext(maxSeverity, passRate, countRisk, sizeRisk, 0.35, reliability);
    }

    private String normalizeAlgorithm(String algorithmName) {
        if (algorithmName == null || algorithmName.isBlank()) return "MANSGA_III";
        String alg = algorithmName.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (alg.contains("PUSH") || alg.contains("PULL")) return "PUSH_PULL";
        return "MANSGA_III";
    }

    private double totalObjectiveValue(OptimizationSolution solution) {
        if (solution.getObjectives() == null) return Double.MAX_VALUE;
        return solution.getObjectives().values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private boolean isFeasible(OptimizationSolution solution) {
        if (solution.getObjectives() == null) return false;
        double severity = solution.getObjectives().getOrDefault(OptimizationObjective.DEFECT_SEVERITY.code(), 1.0);
        double negativePassRate = solution.getObjectives().getOrDefault(OptimizationObjective.NEGATIVE_PASS_RATE.code(), 1.0);
        double negativeReliability = solution.getObjectives().getOrDefault(OptimizationObjective.NEGATIVE_RELIABILITY.code(), 1.0);
        return severity <= 0.60 && negativePassRate <= 0.35 && negativeReliability <= 0.40;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
