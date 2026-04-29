package com.example.demo.optimization.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public class OptimizationSolution {

    private final Map<String, Double> parameters;
    private Map<String, Double> objectives;
    private int rank;
    private double crowdingDistance;

    public OptimizationSolution(Map<String, Double> parameters) {
        this.parameters = new LinkedHashMap<>(parameters);
        this.rank = 0;
        this.crowdingDistance = 0.0;
    }

    public double getParameter(String name) { return parameters.getOrDefault(name, 0.0); }
    public void setParameter(String name, double value) { parameters.put(name, value); }
    public Map<String, Double> getParameters() { return parameters; }

    public int getObjectiveCount() { return objectives != null ? objectives.size() : 0; }
    public double getObjective(int index) {
        if (objectives == null) return 0.0;
        int i = 0;
        for (double v : objectives.values()) { if (i == index) return v; i++; }
        return 0.0;
    }
    public Map<String, Double> getObjectives() { return objectives; }
    public void setObjectives(Map<String, Double> objectives) { this.objectives = objectives; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public double getCrowdingDistance() { return crowdingDistance; }
    public void setCrowdingDistance(double crowdingDistance) { this.crowdingDistance = crowdingDistance; }

    public boolean dominates(OptimizationSolution other) {
        if (objectives == null || other.objectives == null) return false;
        boolean atLeastOneBetter = false;
        for (Map.Entry<String, Double> entry : objectives.entrySet()) {
            Double otherVal = other.objectives.get(entry.getKey());
            if (otherVal == null) return false;
            if (entry.getValue() > otherVal) return false;
            if (entry.getValue() < otherVal) atLeastOneBetter = true;
        }
        return atLeastOneBetter;
    }

    public OptimizationSolution copy() {
        return new OptimizationSolution(new LinkedHashMap<>(parameters));
    }
}
