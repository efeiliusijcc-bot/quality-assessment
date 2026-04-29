package com.example.demo.kg.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gat_analysis_task", schema = "kg")
public class GatAnalysisTask {

    @Id
    private UUID gatTaskId;

    @Column
    private UUID graphVersionId;

    @Column(length = 128)
    private String modelName;

    @Column(length = 64)
    private String modelVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String inputScope;

    @Column(nullable = false, length = 32)
    private String taskStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant finishedAt;

    protected GatAnalysisTask() {
    }

    public GatAnalysisTask(UUID graphVersionId) {
        this.gatTaskId = UUID.randomUUID();
        this.graphVersionId = graphVersionId;
        this.modelName = "GAT";
        this.taskStatus = "CREATED";
        this.createdAt = Instant.now();
    }

    public UUID getGatTaskId() { return gatTaskId; }
    public UUID getGraphVersionId() { return graphVersionId; }
    public String getModelName() { return modelName; }
    public String getTaskStatus() { return taskStatus; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
