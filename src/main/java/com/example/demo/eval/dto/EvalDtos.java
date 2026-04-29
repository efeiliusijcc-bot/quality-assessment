package com.example.demo.eval.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EvalDtos {

    private EvalDtos() {}

    // ──────────────────── Common ────────────────────

    public record IntroMetric(String label, String value, String extra) {}

    public record QualifiedResultCard(String label, String value, String tip, boolean pass) {}

    public record StreamMetric(String label, String value, double percentage, String color) {}

    public record ReasoningStatistics(
        int nodeCount,
        int relationCount,
        int ruleRelationCount,
        int aprioriRelationCount,
        int defectCount,
        int parameterCount
    ) {}

    public record GraphReasoning(
        double riskScore,
        String mainDefect,
        List<String> defectChain,
        List<String> parameterChain,
        List<String> stepChain,
        String reasoningSummary,
        List<String> optimizationHints,
        ReasoningStatistics statistics
    ) {}

    // ──────────────────── Qualified Dashboard ────────────────────

    public record QualifiedDashboardData(
        List<IntroMetric> metrics,
        List<String> timeAxis,
        List<Double> temperatureData,
        List<Double> pressureData,
        List<Double> currentData,
        List<QualifiedResultCard> resultCards,
        List<StreamMetric> streamMetrics,
        GraphReasoning graphReasoning
    ) {}

    // ──────────────────── Judgment Dashboard ────────────────────

    public record RadarIndicator(String name, int max) {}

    public record JudgmentDiagnosisItem(String title, String content) {}

    public record JudgmentActionItem(String label, String value) {}

    public record JudgmentDashboardData(
        List<IntroMetric> metrics,
        List<RadarIndicator> radarIndicators,
        List<Double> abnormalSampleValues,
        List<Double> targetValues,
        List<String> compareCategories,
        List<Double> currentParameters,
        List<Double> targetParameters,
        String coreConclusion,
        String coreDescription,
        List<JudgmentDiagnosisItem> diagnosisItems,
        List<JudgmentActionItem> actionItems,
        GraphReasoning graphReasoning
    ) {}

    public record JudgmentStreamData(
        List<String> timeAxis,
        List<Double> temperature,
        List<Double> beltSpeed,
        List<Double> o2Ppm,
        List<Double> humidity,
        List<Double> current
    ) {}

    // ──────────────────── Prediction Dashboard ────────────────────

    public record TriggerCard(String label, String value, String tip) {}

    public record PredictionOptimizationRow(String parameter, String current, String recommended, String effect) {}

    public record OptimizationSummaryItem(String label, String value) {}

    public record PredictionDashboardData(
        List<IntroMetric> metrics,
        double predictedProbability,
        double threshold,
        List<TriggerCard> triggerCards,
        List<PredictionOptimizationRow> optimizationTable,
        List<OptimizationSummaryItem> optimizationSummary,
        GraphReasoning graphReasoning
    ) {}

    public record SimulationDataPoint(String time, double temperature, double pressure, double beltSpeed, double probability) {}

    public record SimulationStreamData(List<SimulationDataPoint> points) {}

    // ──────────────────── History ────────────────────

    public record AssessmentHistoryItem(
        String id,
        String batchId,
        String station,
        double temperature,
        double pressure,
        double currentValue,
        String sampledAt
    ) {}

    public record AssessmentHistoryPage(List<AssessmentHistoryItem> records, int total) {}

    // ──────────────────── Optimization ────────────────────

    public record ParetoSolutionDto(
        Map<String, Double> parameters,
        Map<String, Double> objectiveValues,
        double crowdingDistance
    ) {}

    public record OptimizationStatisticsDto(
        long elapsedTimeMs,
        int totalEvaluations,
        int paretoFrontSize
    ) {}

    public record OptimizationResponse(
        String batchId,
        String algorithm,
        int generations,
        List<ParetoSolutionDto> paretoFront,
        ParetoSolutionDto recommendedSolution,
        OptimizationStatisticsDto statistics
    ) {}

    // ──────────────────── AssessmentTask CRUD ────────────────────

    public record CreateAssessmentRequest(
        @NotBlank String taskType,
        UUID batchId,
        UUID stepId
    ) {}

    public record AssessmentTaskResponse(
        UUID taskId,
        String taskType,
        UUID batchId,
        String taskStatus,
        String createdAt,
        String finishedAt
    ) {}
}
