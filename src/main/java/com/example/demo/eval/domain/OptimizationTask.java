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
@Table(name = "optimization_task", schema = "eval")
public class OptimizationTask {

    @Id
    private UUID optTaskId;

    @Column
    private UUID batchId;

    @Column
    private UUID stepId;

    @Column(nullable = false, length = 128)
    private String algorithmName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String objectives;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String constraints;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String inputParams;

    @Column(nullable = false, length = 32)
    private String optStatus;

    @Column
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant finishedAt;

    protected OptimizationTask() {
    }

    public OptimizationTask(UUID batchId, String algorithmName) {
        this.optTaskId = UUID.randomUUID();
        this.batchId = batchId;
        this.algorithmName = algorithmName;
        this.optStatus = "CREATED";
        this.createdAt = Instant.now();
    }

    public UUID getOptTaskId() { return optTaskId; }
    public UUID getBatchId() { return batchId; }
    public UUID getStepId() { return stepId; }
    public String getAlgorithmName() { return algorithmName; }
    public String getObjectives() { return objectives; }
    public String getConstraints() { return constraints; }
    public String getInputParams() { return inputParams; }
    public String getOptStatus() { return optStatus; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }

    public void setObjectives(String objectives) { this.objectives = objectives; }
    public void setConstraints(String constraints) { this.constraints = constraints; }
    public void setInputParams(String inputParams) { this.inputParams = inputParams; }
    public void setOptStatus(String optStatus) { this.optStatus = optStatus; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
