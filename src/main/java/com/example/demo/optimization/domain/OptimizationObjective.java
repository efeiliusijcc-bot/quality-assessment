package com.example.demo.optimization.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Seven minimization objectives used by the many-objective optimizer.
 * Pass rate and reliability are represented as losses.
 */
public enum OptimizationObjective {
    DEFECT_SEVERITY("defect_severity", "缺陷严重程度", "min"),
    NEGATIVE_PASS_RATE("negative_pass_rate", "合格率损失", "min"),
    DEFECT_COUNT("defect_count", "缺陷数量", "min"),
    DEFECT_SIZE("defect_size", "缺陷大小", "min"),
    COST("cost", "成本", "min"),
    COMPUTE_TIME("compute_time", "计算时间", "min"),
    NEGATIVE_RELIABILITY("negative_reliability", "可靠性损失", "min");

    private final String code;
    private final String label;
    private final String direction;

    OptimizationObjective(String code, String label, String direction) {
        this.code = code;
        this.label = label;
        this.direction = direction;
    }

    public String code() { return code; }
    public String label() { return label; }
    public String direction() { return direction; }

    public static List<String> codes() {
        return Arrays.stream(values()).map(OptimizationObjective::code).toList();
    }
}
