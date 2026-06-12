package com.example.demo.optimization.service;

import com.example.demo.optimization.domain.OptimizationObjective;
import com.example.demo.optimization.domain.OptimizationSolution;
import com.example.demo.optimization.domain.ProcessParameterSpace;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Seven-objective quality evaluator for many-objective process parameter optimization.
 *
 * This implementation is deterministic and can work with test data. The formulas combine:
 * - deviation from target parameter values;
 * - historical batch risk snapshot;
 * - cost/time/reliability proxies.
 *
 * Replace the proxy coefficients with calibrated process knowledge when real production data is ready.
 */
@Component
public class QualityManyObjectiveEvaluator {

    public Map<String, Double> evaluate(OptimizationSolution solution, BatchOptimizationContext context) {
        double normalizedDeviation = normalizedParameterDeviation(solution);
        double maxDeviation = maxNormalizedDeviation(solution);
        double thermalStress = stress(solution, "preheat_temp") * 0.35 + stress(solution, "reflow_temp") * 0.65;
        double speedStress = stress(solution, "belt_speed");
        double oxygenStress = stress(solution, "o2_ppm");
        double humidityStress = stress(solution, "humidity");
        double currentStress = stress(solution, "current");

        double defectSeverity = clamp01(0.45 * context.defectSeverityRisk()
                + 0.25 * thermalStress
                + 0.15 * currentStress
                + 0.15 * maxDeviation);

        double passRate = clamp01(context.passRateBaseline()
                - 0.25 * normalizedDeviation
                - 0.15 * context.defectCountRisk()
                - 0.10 * oxygenStress);

        double defectCount = clamp01(0.50 * context.defectCountRisk()
                + 0.20 * speedStress
                + 0.15 * humidityStress
                + 0.15 * normalizedDeviation);

        double defectSize = clamp01(0.45 * context.defectSizeRisk()
                + 0.25 * thermalStress
                + 0.20 * currentStress
                + 0.10 * humidityStress);

        double cost = clamp01(0.25 * stress(solution, "reflow_temp")
                + 0.20 * stress(solution, "preheat_temp")
                + 0.20 * currentStress
                + 0.15 * oxygenStress
                + 0.20 * normalizedDeviation);

        double computeTime = clamp01(0.20 * context.computeTimeBaseline()
                + 0.35 * (1.0 - normalized(solution, "belt_speed"))
                + 0.25 * normalizedDeviation
                + 0.20 * maxDeviation);

        double reliability = clamp01(context.reliabilityBaseline()
                - 0.30 * defectSeverity
                - 0.20 * defectCount
                - 0.20 * defectSize
                - 0.20 * maxDeviation);

        Map<String, Double> objectives = new LinkedHashMap<>();
        objectives.put(OptimizationObjective.DEFECT_SEVERITY.code(), defectSeverity);
        objectives.put(OptimizationObjective.NEGATIVE_PASS_RATE.code(), 1.0 - passRate);
        objectives.put(OptimizationObjective.DEFECT_COUNT.code(), defectCount);
        objectives.put(OptimizationObjective.DEFECT_SIZE.code(), defectSize);
        objectives.put(OptimizationObjective.COST.code(), cost);
        objectives.put(OptimizationObjective.COMPUTE_TIME.code(), computeTime);
        objectives.put(OptimizationObjective.NEGATIVE_RELIABILITY.code(), 1.0 - reliability);
        return objectives;
    }

    public Map<String, Object> objectiveMetadata() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("objectiveCount", OptimizationObjective.values().length);
        map.put("objectives", Arrays.stream(OptimizationObjective.values())
                .map(o -> Map.of("code", o.code(), "label", o.label(), "direction", o.direction()))
                .toList());
        return map;
    }

    private double normalizedParameterDeviation(OptimizationSolution solution) {
        double sum = 0.0;
        int count = 0;
        for (ProcessParameterSpace p : ProcessParameterSpace.ALL) {
            double range = Math.max(1e-9, p.upperBound() - p.lowerBound());
            sum += Math.abs(solution.getParameter(p.name()) - p.target()) / range;
            count++;
        }
        return clamp01(sum / Math.max(1, count) * 2.5);
    }

    private double maxNormalizedDeviation(OptimizationSolution solution) {
        double max = 0.0;
        for (ProcessParameterSpace p : ProcessParameterSpace.ALL) {
            double range = Math.max(1e-9, p.upperBound() - p.lowerBound());
            max = Math.max(max, Math.abs(solution.getParameter(p.name()) - p.target()) / range);
        }
        return clamp01(max * 2.5);
    }

    private double stress(OptimizationSolution solution, String name) {
        ProcessParameterSpace p = Arrays.stream(ProcessParameterSpace.ALL)
                .filter(x -> x.name().equals(name))
                .findFirst()
                .orElse(null);
        if (p == null) return 0.0;
        double range = Math.max(1e-9, p.upperBound() - p.lowerBound());
        return clamp01(Math.abs(solution.getParameter(name) - p.target()) / range * 2.5);
    }

    private double normalized(OptimizationSolution solution, String name) {
        ProcessParameterSpace p = Arrays.stream(ProcessParameterSpace.ALL)
                .filter(x -> x.name().equals(name))
                .findFirst()
                .orElse(null);
        if (p == null) return 0.5;
        return clamp01((solution.getParameter(name) - p.lowerBound()) / Math.max(1e-9, p.upperBound() - p.lowerBound()));
    }

    private double clamp01(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 1.0;
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** Historical/rule-derived context of current batch. */
    public record BatchOptimizationContext(
            double defectSeverityRisk,
            double passRateBaseline,
            double defectCountRisk,
            double defectSizeRisk,
            double computeTimeBaseline,
            double reliabilityBaseline
    ) {
        public static BatchOptimizationContext defaults() {
            return new BatchOptimizationContext(0.25, 0.88, 0.20, 0.22, 0.35, 0.86);
        }
    }
}
