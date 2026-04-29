package com.example.demo.optimization.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class ProcessParameterSpace {

    public static final ProcessParameterSpace[] ALL = {
        new ProcessParameterSpace("preheat_temp", 120.0, 200.0, 155.0),
        new ProcessParameterSpace("reflow_temp", 200.0, 280.0, 238.0),
        new ProcessParameterSpace("belt_speed", 50.0, 120.0, 90.0),
        new ProcessParameterSpace("o2_ppm", 0.0, 500.0, 85.0),
        new ProcessParameterSpace("humidity", 20.0, 80.0, 45.0),
        new ProcessParameterSpace("current", 0.5, 5.0, 1.2),
    };

    private final String name;
    private final double lowerBound;
    private final double upperBound;
    private final double target;

    public ProcessParameterSpace(String name, double lowerBound, double upperBound, double target) {
        this.name = name;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.target = target;
    }

    public String name() { return name; }
    public double lowerBound() { return lowerBound; }
    public double upperBound() { return upperBound; }
    public double target() { return target; }

    public double random(Random rng) {
        return lowerBound + rng.nextDouble() * (upperBound - lowerBound);
    }

    public double clip(double value) {
        return Math.max(lowerBound, Math.min(upperBound, value));
    }

    public static Map<String, Double> targetValues() {
        Map<String, Double> targets = new LinkedHashMap<>();
        for (ProcessParameterSpace space : ALL) {
            targets.put(space.name(), space.target());
        }
        return targets;
    }
}
