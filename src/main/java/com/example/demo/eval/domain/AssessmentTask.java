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
@Table(name = "assessment_task", schema = "eval")
public class AssessmentTask {

    @Id
    private UUID taskId;

    @Column(nullable = false, length = 32)
    private String taskType;

    @Column
    private UUID batchId;

    @Column
    private UUID unitId;

    @Column
    private UUID stepId;

    @Column(length = 128)
    private String modelName;

    @Column(length = 64)
    private String modelVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String inputSnapshot;

    @Column(nullable = false, length = 32)
    private String taskStatus;

    @Column
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant finishedAt;

    protected AssessmentTask() {
    }

    public AssessmentTask(String taskType, UUID batchId) {
        this.taskId = UUID.randomUUID();
        this.taskType = taskType;
        this.batchId = batchId;
        this.taskStatus = "CREATED";
        this.createdAt = Instant.now();
    }

    public UUID getTaskId() { return taskId; }
    public String getTaskType() { return taskType; }
    public UUID getBatchId() { return batchId; }
    public UUID getStepId() { return stepId; }
    public String getModelName() { return modelName; }
    public String getTaskStatus() { return taskStatus; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }

    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
