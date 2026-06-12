package com.example.demo.eval.service;

import com.example.demo.common.exception.BusinessException;
import com.example.demo.core.domain.ParameterDef;
import com.example.demo.core.domain.Workstation;
import com.example.demo.core.repository.ParameterDefRepository;
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
import com.example.demo.optimization.domain.ProcessParameterSpace;
import com.example.demo.optimization.service.ManyObjectiveOptimizationRunner;
import com.example.demo.prod.domain.ParameterValue;
import com.example.demo.prod.domain.ProductionBatch;
import com.example.demo.prod.domain.ProcessRun;
import com.example.demo.prod.repository.ParameterValueRepository;
import com.example.demo.prod.repository.ProductionBatchRepository;
import com.example.demo.prod.repository.ProcessRunRepository;
import com.example.demo.qc.domain.DefectRecord;
import com.example.demo.qc.domain.InspectionTask;
import com.example.demo.qc.domain.QualityMeasurement;
import com.example.demo.qc.repository.DefectRecordRepository;
import com.example.demo.qc.repository.InspectionTaskRepository;
import com.example.demo.qc.repository.QualityMeasurementRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final ParameterDefRepository parameterDefRepo;
    private final ProductionBatchRepository batchRepo;
    private final ProcessRunRepository processRunRepo;
    private final ParameterValueRepository parameterValueRepo;
    private final QualityMeasurementRepository qualityMeasurementRepo;
    private final InspectionTaskRepository inspectionTaskRepo;
    private final DefectRecordRepository defectRecordRepo;
    private final GraphReasoningService graphReasoningService;
    private final ManyObjectiveOptimizationRunner manyObjectiveOptimizationRunner;

    public EvalService(AssessmentTaskRepository assessmentTaskRepo,
                       AssessmentResultRepository assessmentResultRepo,
                       OptimizationTaskRepository optimizationTaskRepo,
                       OptimizationResultRepository optimizationResultRepo,
                       WorkstationRepository workstationRepo,
                       ParameterDefRepository parameterDefRepo,
                       ProductionBatchRepository batchRepo,
                       ProcessRunRepository processRunRepo,
                       ParameterValueRepository parameterValueRepo,
                       QualityMeasurementRepository qualityMeasurementRepo,
                       InspectionTaskRepository inspectionTaskRepo,
                       DefectRecordRepository defectRecordRepo,
                       GraphReasoningService graphReasoningService,
                       ManyObjectiveOptimizationRunner manyObjectiveOptimizationRunner) {
        this.assessmentTaskRepo = assessmentTaskRepo;
        this.assessmentResultRepo = assessmentResultRepo;
        this.optimizationTaskRepo = optimizationTaskRepo;
        this.optimizationResultRepo = optimizationResultRepo;
        this.workstationRepo = workstationRepo;
        this.parameterDefRepo = parameterDefRepo;
        this.batchRepo = batchRepo;
        this.processRunRepo = processRunRepo;
        this.parameterValueRepo = parameterValueRepo;
        this.qualityMeasurementRepo = qualityMeasurementRepo;
        this.inspectionTaskRepo = inspectionTaskRepo;
        this.defectRecordRepo = defectRecordRepo;
        this.graphReasoningService = graphReasoningService;
        this.manyObjectiveOptimizationRunner = manyObjectiveOptimizationRunner;
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

    public QualifiedDashboardData getQualifiedDashboard(String batchNoOrId) {
        ProductionBatch batch = resolveBatch(batchNoOrId).orElse(null);
        String reasoningKey = batch != null ? batch.getBatchNo() : batchNoOrId;
        GraphReasoning reasoning = resolveGraphReasoning(reasoningKey);
        BatchQualitySnapshot snapshot = batch != null ? loadBatchSnapshot(batch.getBatchId()) : BatchQualitySnapshot.empty();

        return new QualifiedDashboardData(
            buildQualifiedMetrics(snapshot),
            snapshot.timeAxis(),
            snapshot.seriesAt(0),
            snapshot.seriesAt(1),
            snapshot.seriesAt(5),
            buildQualifiedResultCards(snapshot, reasoning),
            buildStreamMetrics(snapshot),
            reasoning
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  Dashboard – Judgment
    // ════════════════════════════════════════════════════════════════

    public JudgmentDashboardData getJudgmentDashboard(String batchNoOrId) {
        ProductionBatch batch = resolveBatch(batchNoOrId).orElse(null);
        String reasoningKey = batch != null ? batch.getBatchNo() : batchNoOrId;
        GraphReasoning reasoning = resolveGraphReasoning(reasoningKey);
        BatchQualitySnapshot snapshot = batch != null ? loadBatchSnapshot(batch.getBatchId()) : BatchQualitySnapshot.empty();
        List<Double> currentParameters = snapshot.latestParameterValues(5);
        List<Double> targetParameters = ProcessParameterSpace.ALL.length >= 5
                ? Arrays.stream(ProcessParameterSpace.ALL).limit(5).map(ProcessParameterSpace::target).toList()
                : List.of();

        return new JudgmentDashboardData(
            buildJudgmentMetrics(snapshot, reasoning),
            buildRadarIndicators(),
            buildAbnormalSampleValues(snapshot, reasoning),
            List.of(100.0, 100.0, 100.0, 100.0),
            parameterNames(5),
            currentParameters,
            targetParameters,
            buildCoreConclusion(snapshot, reasoning),
            buildCoreDescription(snapshot, reasoning),
            buildDiagnosisItems(snapshot, reasoning),
            buildActionItems(snapshot, reasoning),
            reasoning
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  Dashboard – Prediction
    // ════════════════════════════════════════════════════════════════

    public PredictionDashboardData getPredictionDashboard(String batchNoOrId) {
        ProductionBatch batch = resolveBatch(batchNoOrId).orElse(null);
        String reasoningKey = batch != null ? batch.getBatchNo() : batchNoOrId;
        GraphReasoning reasoning = resolveGraphReasoning(reasoningKey);
        BatchQualitySnapshot snapshot = batch != null ? loadBatchSnapshot(batch.getBatchId()) : BatchQualitySnapshot.empty();
        double predictedProbability = estimateDefectProbability(snapshot, reasoning);

        return new PredictionDashboardData(
            buildPredictionMetrics(snapshot, predictedProbability),
            predictedProbability,
            0.5,  // threshold
            buildTriggerCards(snapshot, reasoning, predictedProbability),
            buildPredictionOptimizationRows(snapshot),
            buildOptimizationSummary(snapshot, predictedProbability),
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

        List<UUID> runIds = runs.stream().map(ProcessRun::getRunId).toList();
        Map<UUID, List<ParameterValue>> valuesByRun = batchLoadParameterValues(runIds);
        Map<UUID, ParameterSignal> signalByParamId = loadParameterSignals(valuesByRun);
        Map<UUID, String> stationNameById = stationNameMap(runs);
        String batchLabel = batchRepo.findById(batchId).map(ProductionBatch::getBatchNo).orElse(batchId.toString());

        List<AssessmentHistoryItem> allItems = new ArrayList<>();
        for (ProcessRun run : runs) {
            List<ParameterValue> values = valuesByRun.getOrDefault(run.getRunId(), List.of());
            RunSignalValues signals = extractSignalValues(values, signalByParamId);
            allItems.add(new AssessmentHistoryItem(
                values.isEmpty() ? run.getRunId().toString() : values.get(0).getValueId().toString(),
                batchLabel,
                run.getStationId() != null ? stationNameById.getOrDefault(run.getStationId(), run.getStationId().toString()) : "",
                signals.temperature(),
                signals.pressure(),
                signals.current(),
                signals.sampledAt() != null ? signals.sampledAt().toString() : formatTime(run)
            ));
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

        List<UUID> runIds = runs.stream().map(ProcessRun::getRunId).toList();
        Map<UUID, List<ParameterValue>> valuesByRun = batchLoadParameterValues(runIds);
        Map<UUID, ParameterSignal> signalByParamId = loadParameterSignals(valuesByRun);

        List<String> timeAxis = new ArrayList<>();
        List<Double> temperature = new ArrayList<>();
        List<Double> beltSpeed = new ArrayList<>();
        List<Double> o2Ppm = new ArrayList<>();
        List<Double> humidity = new ArrayList<>();
        List<Double> current = new ArrayList<>();

        for (ProcessRun run : runs) {
            List<ParameterValue> values = valuesByRun.getOrDefault(run.getRunId(), List.of());
            RunSignalValues signals = extractSignalValues(values, signalByParamId);
            timeAxis.add(formatTime(run));
            temperature.add(signals.temperature());
            beltSpeed.add(signals.beltSpeed());
            o2Ppm.add(signals.o2Ppm());
            humidity.add(signals.humidity());
            current.add(signals.current());
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

        BatchQualitySnapshot snapshot = loadBatchSnapshot(batchId);
        List<ProcessRun> runs = processRunRepo.findByBatchIdOrderByCreatedAtAsc(batchId);
        if (runs.isEmpty()) {
            return new SimulationStreamData(List.of());
        }

        List<UUID> runIds = runs.stream().map(ProcessRun::getRunId).toList();
        Map<UUID, List<ParameterValue>> valuesByRun = batchLoadParameterValues(runIds);
        Map<UUID, ParameterSignal> signalByParamId = loadParameterSignals(valuesByRun);

        List<SimulationDataPoint> points = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            ProcessRun run = runs.get(i);
            List<ParameterValue> values = valuesByRun.getOrDefault(run.getRunId(), List.of());
            RunSignalValues signals = extractSignalValues(values, signalByParamId);
            double drift = 0.0;
            List<Double> orderedSignals = signals.toSeries();
            for (int j = 0; j < Math.min(orderedSignals.size(), ProcessParameterSpace.ALL.length); j++) {
                ProcessParameterSpace space = ProcessParameterSpace.ALL[j];
                drift += Math.abs(orderedSignals.get(j) - space.target()) / Math.max(1.0, space.upperBound() - space.lowerBound());
            }
            double baseProbability = estimateDefectProbability(snapshot, resolveGraphReasoning(batchId.toString()));
            double trendProbability = Math.min(0.98, baseProbability + drift * 0.12 + i * 0.005);
            points.add(new SimulationDataPoint(
                formatTime(run),
                signals.temperature(),
                signals.pressure(),
                signals.beltSpeed(),
                round(trendProbability)
            ));
        }

        return new SimulationStreamData(points);
    }

    // ════════════════════════════════════════════════════════════════
    //  Optimization
    // ════════════════════════════════════════════════════════════════

    public OptimizationResponse runOptimization(String batchId) {
        UUID batchUuid = resolveBatch(batchId)
            .map(ProductionBatch::getBatchId)
            .orElseGet(() -> {
                try {
                    return UUID.fromString(batchId);
                } catch (IllegalArgumentException e) {
                    throw new BusinessException(400, "invalid batchId format");
                }
            });
        return manyObjectiveOptimizationRunner.run(batchUuid, "MANSGA_III", 96, 120);
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

    private Optional<ProductionBatch> resolveBatch(String batchNoOrId) {
        if (batchNoOrId != null && !batchNoOrId.isBlank()) {
            try {
                return batchRepo.findById(UUID.fromString(batchNoOrId));
            } catch (IllegalArgumentException ignored) {
                return batchRepo.findByBatchNo(batchNoOrId);
            }
        }
        return batchRepo.findAll().stream()
                .max(Comparator.comparing(ProductionBatch::getCreatedAt));
    }

    private BatchQualitySnapshot loadBatchSnapshot(UUID batchId) {
        List<ProcessRun> runs = processRunRepo.findByBatchIdOrderByCreatedAtAsc(batchId);
        if (runs.isEmpty()) {
            return BatchQualitySnapshot.empty();
        }

        List<UUID> runIds = runs.stream().map(ProcessRun::getRunId).toList();
        Map<UUID, List<ParameterValue>> valuesByRun = batchLoadParameterValues(runIds);
        Map<UUID, ParameterSignal> signalByParamId = loadParameterSignals(valuesByRun);
        List<QualityMeasurement> measurements = qualityMeasurementRepo.findByRunIdInOrderByMeasuredAtAsc(runIds);
        Map<UUID, List<QualityMeasurement>> measurementsByRun = measurements.stream()
                .collect(LinkedHashMap::new,
                        (map, measurement) -> map.computeIfAbsent(measurement.getRunId(), ignored -> new ArrayList<>()).add(measurement),
                        Map::putAll);
        List<InspectionTask> inspections = inspectionTaskRepo.findByRunIdIn(runIds);
        Map<UUID, List<InspectionTask>> inspectionsByRun = inspections.stream()
                .collect(LinkedHashMap::new,
                        (map, inspection) -> map.computeIfAbsent(inspection.getRunId(), ignored -> new ArrayList<>()).add(inspection),
                        Map::putAll);
        List<DefectRecord> defects = inspections.isEmpty()
                ? List.of()
                : defectRecordRepo.findByInspectionIdIn(inspections.stream().map(InspectionTask::getInspectionId).toList());

        List<String> timeAxis = new ArrayList<>();
        List<List<Double>> parameterSeries = new ArrayList<>();
        for (int i = 0; i < ParameterSignal.ordered().size(); i++) {
            parameterSeries.add(new ArrayList<>());
        }
        int passedMeasurements = 0;
        int failedMeasurements = 0;
        double qualityScoreSum = 0.0;
        int scoredRuns = 0;

        for (ProcessRun run : runs) {
            timeAxis.add(formatTime(run));
            List<ParameterValue> values = valuesByRun.getOrDefault(run.getRunId(), List.of());
            RunSignalValues signals = extractSignalValues(values, signalByParamId);
            List<Double> orderedSignals = signals.toSeries();
            for (int i = 0; i < parameterSeries.size(); i++) {
                parameterSeries.get(i).add(orderedSignals.get(i));
            }

            List<QualityMeasurement> runMeasurements = measurementsByRun.getOrDefault(run.getRunId(), List.of());
            long runPassed = runMeasurements.stream().filter(m -> Boolean.TRUE.equals(m.getIsPass())).count();
            long runFailed = runMeasurements.stream().filter(m -> Boolean.FALSE.equals(m.getIsPass())).count();
            passedMeasurements += (int) runPassed;
            failedMeasurements += (int) runFailed;
            int measured = (int) (runPassed + runFailed);
            if (measured > 0) {
                qualityScoreSum += runPassed * 100.0 / measured;
                scoredRuns++;
            } else {
                List<InspectionTask> runInspections = inspectionsByRun.getOrDefault(run.getRunId(), List.of());
                boolean failedByInspection = runInspections.stream()
                        .anyMatch(task -> task.getResultStatus() != null
                                && !Set.of("PASS", "OK", "COMPLETED", "NORMAL").contains(task.getResultStatus().toUpperCase(Locale.ROOT)));
                qualityScoreSum += failedByInspection ? 60.0 : 90.0;
                scoredRuns++;
            }
        }

        int defectCount = defects.stream()
                .map(DefectRecord::getDefectCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int severeDefectCount = (int) defects.stream()
                .filter(defect -> Boolean.TRUE.equals(defect.getIsCritical())
                        || Optional.ofNullable(defect.getSeverityLevel()).orElse(0) >= 3)
                .count();
        double avgInspectionConfidence = inspections.stream()
                .filter(task -> task.getConfidence() != null)
                .mapToDouble(task -> task.getConfidence().doubleValue())
                .average()
                .orElse(0.0);

        double passRate = passedMeasurements + failedMeasurements == 0
                ? (defectCount == 0 ? 1.0 : Math.max(0.0, 1.0 - defectCount / (double) Math.max(1, runs.size())))
                : passedMeasurements / (double) (passedMeasurements + failedMeasurements);
        double averageScore = scoredRuns == 0 ? passRate * 100.0 : qualityScoreSum / scoredRuns;

        return new BatchQualitySnapshot(
                runs,
                timeAxis,
                parameterSeries,
                passedMeasurements,
                failedMeasurements,
                defectCount,
                severeDefectCount,
                avgInspectionConfidence,
                round(passRate),
                round(averageScore));
    }

    private List<IntroMetric> buildQualifiedMetrics(BatchQualitySnapshot snapshot) {
        if (snapshot.runCount() == 0) {
            return buildDefaultMetrics("qualified");
        }
        return List.of(
                new IntroMetric("Total Runs", String.valueOf(snapshot.runCount()), "process runs"),
                new IntroMetric("Pass Rate", percent(snapshot.passRate()), snapshot.failedMeasurements() + " failed metrics"),
                new IntroMetric("Average Score", String.valueOf(snapshot.averageScore()), "quality score"));
    }

    private List<QualifiedResultCard> buildQualifiedResultCards(BatchQualitySnapshot snapshot, GraphReasoning reasoning) {
        if (snapshot.runCount() == 0) {
            return List.of();
        }
        double riskScore = reasoning != null ? reasoning.riskScore() : 0.0;
        return List.of(
                new QualifiedResultCard("Quality Pass", snapshot.passRate() >= 0.9 ? "PASS" : "NG",
                        "Pass rate " + percent(snapshot.passRate()), snapshot.passRate() >= 0.9),
                new QualifiedResultCard("Defect Control", snapshot.defectCount() == 0 ? "PASS" : "CHECK",
                        snapshot.defectCount() + " defects recorded", snapshot.defectCount() == 0),
                new QualifiedResultCard("Graph Risk", riskScore < 60 ? "PASS" : "RISK",
                        "Risk score " + round(riskScore), riskScore < 60));
    }

    private List<StreamMetric> buildStreamMetrics(BatchQualitySnapshot snapshot) {
        if (snapshot.runCount() == 0) {
            return List.of();
        }
        return List.of(
                new StreamMetric("Pass Rate", percent(snapshot.passRate()), snapshot.passRate() * 100.0, "#16a34a"),
                new StreamMetric("Defect Load", String.valueOf(snapshot.defectCount()),
                        Math.min(100.0, snapshot.defectCount() * 10.0), "#dc2626"),
                new StreamMetric("Confidence", percent(snapshot.avgInspectionConfidence()),
                        snapshot.avgInspectionConfidence() * 100.0, "#2563eb"));
    }

    private List<IntroMetric> buildJudgmentMetrics(BatchQualitySnapshot snapshot, GraphReasoning reasoning) {
        if (snapshot.runCount() == 0) {
            return buildDefaultMetrics("judgment");
        }
        int abnormalCount = snapshot.failedMeasurements() + snapshot.defectCount();
        double riskScore = reasoning != null ? reasoning.riskScore() : 0.0;
        return List.of(
                new IntroMetric("Abnormal Count", String.valueOf(abnormalCount), "metrics + defects"),
                new IntroMetric("Risk Level", riskScore >= 70 ? "HIGH" : riskScore >= 40 ? "MEDIUM" : "LOW", "score " + round(riskScore)),
                new IntroMetric("Diagnosis Accuracy", percent(snapshot.avgInspectionConfidence()), "model confidence"));
    }

    private List<RadarIndicator> buildRadarIndicators() {
        return List.of(
                new RadarIndicator("Defect", 100),
                new RadarIndicator("Process Drift", 100),
                new RadarIndicator("Pass Rate", 100),
                new RadarIndicator("Graph Risk", 100));
    }

    private List<Double> buildAbnormalSampleValues(BatchQualitySnapshot snapshot, GraphReasoning reasoning) {
        double riskScore = reasoning != null ? reasoning.riskScore() : 0.0;
        return List.of(
                Math.min(100.0, snapshot.defectCount() * 12.0),
                Math.min(100.0, averageParameterDeviation(snapshot) * 100.0),
                round((1.0 - snapshot.passRate()) * 100.0),
                round(riskScore));
    }

    private String buildCoreConclusion(BatchQualitySnapshot snapshot, GraphReasoning reasoning) {
        if (snapshot.runCount() == 0) {
            return "No batch data";
        }
        double riskScore = reasoning != null ? reasoning.riskScore() : 0.0;
        if (snapshot.passRate() < 0.85 || riskScore >= 70 || snapshot.severeDefectCount() > 0) {
            return "Process risk requires adjustment";
        }
        if (snapshot.defectCount() > 0 || snapshot.passRate() < 0.95) {
            return "Process window needs review";
        }
        return "Process quality is stable";
    }

    private String buildCoreDescription(BatchQualitySnapshot snapshot, GraphReasoning reasoning) {
        if (snapshot.runCount() == 0) {
            return "Import production, process parameter and quality measurement data before judgment.";
        }
        String mainDefect = reasoning != null ? reasoning.mainDefect() : "No Defect";
        return "Current batch has pass rate " + percent(snapshot.passRate())
                + ", defect count " + snapshot.defectCount()
                + ", dominant graph defect " + mainDefect + ".";
    }

    private List<JudgmentDiagnosisItem> buildDiagnosisItems(BatchQualitySnapshot snapshot, GraphReasoning reasoning) {
        if (snapshot.runCount() == 0) {
            return List.of();
        }
        List<JudgmentDiagnosisItem> items = new ArrayList<>();
        if (snapshot.failedMeasurements() > 0) {
            items.add(new JudgmentDiagnosisItem("Quality limit deviation",
                    snapshot.failedMeasurements() + " quality measurements are marked as failed."));
        }
        if (snapshot.defectCount() > 0) {
            items.add(new JudgmentDiagnosisItem("Defect concentration",
                    snapshot.defectCount() + " defects were recorded in the selected batch."));
        }
        if (reasoning != null && !reasoning.parameterChain().isEmpty()) {
            items.add(new JudgmentDiagnosisItem("Graph factor chain",
                    "Key related parameters: " + String.join(", ", reasoning.parameterChain().stream().limit(4).toList())));
        }
        if (items.isEmpty()) {
            items.add(new JudgmentDiagnosisItem("Stable process", "No active quality anomaly was found from current records."));
        }
        return items;
    }

    private List<JudgmentActionItem> buildActionItems(BatchQualitySnapshot snapshot, GraphReasoning reasoning) {
        if (snapshot.runCount() == 0) {
            return List.of();
        }
        List<JudgmentActionItem> items = new ArrayList<>();
        items.add(new JudgmentActionItem("Review", snapshot.defectCount() > 0 ? "Inspect defect source station" : "Continue monitoring"));
        List<String> hints = reasoning != null ? reasoning.optimizationHints() : List.of();
        hints.stream().limit(3).forEach(hint -> items.add(new JudgmentActionItem("Hint", hint)));
        return items;
    }

    private List<IntroMetric> buildPredictionMetrics(BatchQualitySnapshot snapshot, double predictedProbability) {
        if (snapshot.runCount() == 0) {
            return buildDefaultMetrics("prediction");
        }
        return List.of(
                new IntroMetric("Defect Probability", percent(predictedProbability), predictedProbability >= 0.5 ? "above threshold" : "below threshold"),
                new IntroMetric("Triggered Rules", String.valueOf(snapshot.failedMeasurements() + snapshot.severeDefectCount()), "quality triggers"),
                new IntroMetric("Optimization Gain", percent(Math.max(0.0, 1.0 - predictedProbability)), "expected risk reduction"));
    }

    private List<TriggerCard> buildTriggerCards(BatchQualitySnapshot snapshot, GraphReasoning reasoning, double probability) {
        if (snapshot.runCount() == 0) {
            return List.of();
        }
        List<TriggerCard> cards = new ArrayList<>();
        cards.add(new TriggerCard("Pass Threshold", percent(snapshot.passRate()), snapshot.passRate() >= 0.9 ? "normal" : "below control target"));
        cards.add(new TriggerCard("Prediction", percent(probability), probability >= 0.5 ? "adjust parameters" : "continue production"));
        if (reasoning != null && reasoning.riskScore() > 0) {
            cards.add(new TriggerCard("Graph Risk", String.valueOf(round(reasoning.riskScore())), reasoning.mainDefect()));
        }
        return cards;
    }

    private List<PredictionOptimizationRow> buildPredictionOptimizationRows(BatchQualitySnapshot snapshot) {
        if (snapshot.runCount() == 0) {
            return List.of();
        }
        List<Double> current = snapshot.latestParameterValues(ProcessParameterSpace.ALL.length);
        List<PredictionOptimizationRow> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(current.size(), ProcessParameterSpace.ALL.length); i++) {
            ProcessParameterSpace space = ProcessParameterSpace.ALL[i];
            double currentValue = current.get(i);
            double recommended = space.clip(currentValue + (space.target() - currentValue) * 0.6);
            double effect = Math.abs(currentValue - space.target()) - Math.abs(recommended - space.target());
            rows.add(new PredictionOptimizationRow(
                    space.name(),
                    String.valueOf(round(currentValue)),
                    String.valueOf(round(recommended)),
                    effect > 0 ? "deviation -" + round(effect) : "keep"));
        }
        return rows;
    }

    private List<OptimizationSummaryItem> buildOptimizationSummary(BatchQualitySnapshot snapshot, double probability) {
        if (snapshot.runCount() == 0) {
            return List.of();
        }
        return List.of(
                new OptimizationSummaryItem("Recommended Action", probability >= 0.5 ? "Adjust process parameters" : "Continue production"),
                new OptimizationSummaryItem("Current Pass Rate", percent(snapshot.passRate())),
                new OptimizationSummaryItem("Severe Defects", String.valueOf(snapshot.severeDefectCount())));
    }

    private double estimateDefectProbability(BatchQualitySnapshot snapshot, GraphReasoning reasoning) {
        if (snapshot.runCount() == 0) {
            return 0.0;
        }
        double defectTerm = Math.min(0.35, snapshot.defectCount() / (double) Math.max(1, snapshot.runCount()) * 0.18);
        double failTerm = Math.min(0.30, (1.0 - snapshot.passRate()) * 0.7);
        double riskTerm = reasoning != null ? Math.min(0.30, reasoning.riskScore() / 100.0 * 0.30) : 0.0;
        double severeTerm = snapshot.severeDefectCount() > 0 ? 0.15 : 0.0;
        return round(Math.min(0.98, defectTerm + failTerm + riskTerm + severeTerm));
    }

    private double averageParameterDeviation(BatchQualitySnapshot snapshot) {
        List<Double> current = snapshot.latestParameterValues(ProcessParameterSpace.ALL.length);
        if (current.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < Math.min(current.size(), ProcessParameterSpace.ALL.length); i++) {
            ProcessParameterSpace space = ProcessParameterSpace.ALL[i];
            double range = Math.max(1.0, space.upperBound() - space.lowerBound());
            sum += Math.abs(current.get(i) - space.target()) / range;
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private List<String> parameterNames(int limit) {
        return Arrays.stream(ProcessParameterSpace.ALL)
                .limit(limit)
                .map(ProcessParameterSpace::name)
                .toList();
    }

    private String formatTime(ProcessRun run) {
        Instant time = run.getStartTime() != null ? run.getStartTime() : run.getCreatedAt();
        return time != null ? time.toString() : "";
    }

    private String percent(double value) {
        return BigDecimal.valueOf(value * 100.0)
                .setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    private double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record BatchQualitySnapshot(
            List<ProcessRun> runs,
            List<String> timeAxis,
            List<List<Double>> parameterSeries,
            int passedMeasurements,
            int failedMeasurements,
            int defectCount,
            int severeDefectCount,
            double avgInspectionConfidence,
            double passRate,
            double averageScore) {

        private static BatchQualitySnapshot empty() {
            return new BatchQualitySnapshot(List.of(), List.of(), List.of(), 0, 0, 0, 0, 0.0, 0.0, 0.0);
        }

        private int runCount() {
            return runs.size();
        }

        private List<Double> seriesAt(int index) {
            if (index < 0 || index >= parameterSeries.size()) {
                return List.of();
            }
            return parameterSeries.get(index);
        }

        private List<Double> latestParameterValues(int limit) {
            if (parameterSeries.isEmpty()) {
                return List.of();
            }
            List<Double> values = new ArrayList<>();
            for (List<Double> series : parameterSeries.stream().limit(limit).toList()) {
                values.add(series.isEmpty() ? 0.0 : series.get(series.size() - 1));
            }
            return values;
        }
    }

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

    private GraphReasoning resolveGraphReasoning(String batchNoOrId) {
        if (batchNoOrId == null || batchNoOrId.isBlank()) {
            return defaultGraphReasoning();
        }
        try {
            return graphReasoningService.evaluateBatch(batchNoOrId);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EvalService.class)
                    .warn("Graph reasoning degraded for batchId={}: {}", batchNoOrId, e.getMessage());
            return defaultGraphReasoning();
        }
    }

    private UUID resolveBatchUuid(String batchNoOrId) {
        if (batchNoOrId == null || batchNoOrId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(batchNoOrId);
        } catch (IllegalArgumentException ignored) {
            return batchRepo.findByBatchNo(batchNoOrId)
                    .map(ProductionBatch::getBatchId)
                    .orElse(null);
        }
    }

    private double extractDouble(List<ParameterValue> values, int index) {
        if (index < 0 || index >= values.size()) return 0.0;
        BigDecimal bd = values.get(index).getValueNum();
        return bd != null ? bd.doubleValue() : 0.0;
    }

    private Map<UUID, String> stationNameMap(List<ProcessRun> runs) {
        Set<UUID> stationIds = new LinkedHashSet<>();
        for (ProcessRun run : runs) {
            if (run.getStationId() != null) {
                stationIds.add(run.getStationId());
            }
        }
        if (stationIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new HashMap<>();
        workstationRepo.findAllById(stationIds).forEach(station -> result.put(station.getStationId(), station.getStationName()));
        return result;
    }

    private Map<UUID, ParameterSignal> loadParameterSignals(Map<UUID, List<ParameterValue>> valuesByRun) {
        Set<UUID> paramIds = new LinkedHashSet<>();
        valuesByRun.values().forEach(values -> values.forEach(value -> {
            if (value.getParamId() != null) {
                paramIds.add(value.getParamId());
            }
        }));
        if (paramIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ParameterSignal> result = new HashMap<>();
        for (ParameterDef def : parameterDefRepo.findAllById(paramIds)) {
            detectSignal(def).ifPresent(signal -> result.put(def.getParamId(), signal));
        }
        return result;
    }

    private Optional<ParameterSignal> detectSignal(ParameterDef def) {
        String text = String.join(" ",
                Optional.ofNullable(def.getParamCode()).orElse(""),
                Optional.ofNullable(def.getParamName()).orElse(""),
                Optional.ofNullable(def.getParamCategory()).orElse("")).toLowerCase(Locale.ROOT);
        if (containsAny(text, "temp", "temperature", "热", "温度")) {
            return Optional.of(ParameterSignal.TEMPERATURE);
        }
        if (containsAny(text, "press", "pressure", "压力", "压合")) {
            return Optional.of(ParameterSignal.PRESSURE);
        }
        if (containsAny(text, "belt", "speed", "速度", "带速", "传送")) {
            return Optional.of(ParameterSignal.BELT_SPEED);
        }
        if (containsAny(text, "o2", "oxygen", "氧")) {
            return Optional.of(ParameterSignal.O2_PPM);
        }
        if (containsAny(text, "humid", "humidity", "湿度")) {
            return Optional.of(ParameterSignal.HUMIDITY);
        }
        if (containsAny(text, "current", "amp", "电流")) {
            return Optional.of(ParameterSignal.CURRENT);
        }
        return Optional.empty();
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private RunSignalValues extractSignalValues(List<ParameterValue> values, Map<UUID, ParameterSignal> signalByParamId) {
        EnumMap<ParameterSignal, Double> bySignal = new EnumMap<>(ParameterSignal.class);
        Instant sampledAt = null;
        for (ParameterValue value : values) {
            if (sampledAt == null || (value.getMeasuredAt() != null && value.getMeasuredAt().isAfter(sampledAt))) {
                sampledAt = value.getMeasuredAt();
            }
            ParameterSignal signal = signalByParamId.get(value.getParamId());
            if (signal == null || value.getValueNum() == null) {
                continue;
            }
            bySignal.put(signal, value.getValueNum().doubleValue());
        }

        List<ParameterValue> numericValues = values.stream()
                .filter(value -> value.getValueNum() != null)
                .toList();
        List<ParameterSignal> ordered = ParameterSignal.ordered();
        int fallbackIndex = 0;
        for (ParameterSignal signal : ordered) {
            if (bySignal.containsKey(signal)) {
                continue;
            }
            while (fallbackIndex < numericValues.size()
                    && signalByParamId.containsKey(numericValues.get(fallbackIndex).getParamId())) {
                fallbackIndex++;
            }
            if (fallbackIndex < numericValues.size()) {
                bySignal.put(signal, numericValues.get(fallbackIndex).getValueNum().doubleValue());
                fallbackIndex++;
            } else {
                bySignal.put(signal, 0.0);
            }
        }

        return new RunSignalValues(
                bySignal.get(ParameterSignal.TEMPERATURE),
                bySignal.get(ParameterSignal.PRESSURE),
                bySignal.get(ParameterSignal.BELT_SPEED),
                bySignal.get(ParameterSignal.O2_PPM),
                bySignal.get(ParameterSignal.HUMIDITY),
                bySignal.get(ParameterSignal.CURRENT),
                sampledAt);
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

    private enum ParameterSignal {
        TEMPERATURE,
        PRESSURE,
        BELT_SPEED,
        O2_PPM,
        HUMIDITY,
        CURRENT;

        private static List<ParameterSignal> ordered() {
            return List.of(TEMPERATURE, PRESSURE, BELT_SPEED, O2_PPM, HUMIDITY, CURRENT);
        }
    }

    private record RunSignalValues(
            double temperature,
            double pressure,
            double beltSpeed,
            double o2Ppm,
            double humidity,
            double current,
            Instant sampledAt) {

        private List<Double> toSeries() {
            return List.of(temperature, pressure, beltSpeed, o2Ppm, humidity, current);
        }
    }
}
