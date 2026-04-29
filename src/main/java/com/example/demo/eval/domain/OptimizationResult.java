package com.example.demo.eval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "optimization_result", schema = "eval")
public class OptimizationResult {

    @Id
    private UUID optResultId;

    @Column(nullable = false)
    private UUID optTaskId;

    @Column
    private Integer paretoRank;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String parameterSolution;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String objectiveValues;

    @Column
    private Boolean feasibleFlag;

    @Column(length = 32)
    private String recommendationLevel;

    @Column(nullable = false)
    private Instant createdAt;

    protected OptimizationResult() {
    }

    public OptimizationResult(UUID optTaskId, Integer paretoRank) {
        this.optResultId = UUID.randomUUID();
        this.optTaskId = optTaskId;
        this.paretoRank = paretoRank;
        this.createdAt = Instant.now();
    }

    public UUID getOptResultId() { return optResultId; }
    public UUID getOptTaskId() { return optTaskId; }
    public Integer getParetoRank() { return paretoRank; }
    public String getParameterSolution() { return parameterSolution; }
    public String getObjectiveValues() { return objectiveValues; }
    public Boolean getFeasibleFlag() { return feasibleFlag; }
    public String getRecommendationLevel() { return recommendationLevel; }
    public Instant getCreatedAt() { return createdAt; }

    public void setParameterSolution(String parameterSolution) { this.parameterSolution = parameterSolution; }
    public void setObjectiveValues(String objectiveValues) { this.objectiveValues = objectiveValues; }
    public void setFeasibleFlag(Boolean feasibleFlag) { this.feasibleFlag = feasibleFlag; }
    public void setRecommendationLevel(String recommendationLevel) { this.recommendationLevel = recommendationLevel; }
}
