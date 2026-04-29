package com.example.demo.prod.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "process_run", schema = "prod")
public class ProcessRun {

    @Id
    private UUID runId;

    @Column(nullable = false)
    private UUID batchId;

    @Column
    private UUID unitId;

    @Column(nullable = false)
    private UUID stepId;

    @Column
    private UUID stationId;

    @Column
    private UUID equipmentId;

    @Column
    private UUID recipeId;

    @Column
    private UUID operatorId;

    @Column(length = 128)
    private String runNo;

    @Column
    private Instant startTime;

    @Column
    private Instant endTime;

    @Column(nullable = false, length = 32)
    private String runStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String contextJson;

    @Column(nullable = false)
    private Instant createdAt;

    protected ProcessRun() {
    }

    public ProcessRun(UUID batchId, UUID stepId) {
        this.runId = UUID.randomUUID();
        this.batchId = batchId;
        this.stepId = stepId;
        this.runStatus = "RUNNING";
        this.createdAt = Instant.now();
    }

    public UUID getRunId() { return runId; }
    public UUID getBatchId() { return batchId; }
    public UUID getUnitId() { return unitId; }
    public UUID getStepId() { return stepId; }
    public UUID getStationId() { return stationId; }
    public UUID getEquipmentId() { return equipmentId; }
    public UUID getRecipeId() { return recipeId; }
    public UUID getOperatorId() { return operatorId; }
    public String getRunNo() { return runNo; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getRunStatus() { return runStatus; }
    public Instant getCreatedAt() { return createdAt; }

    public void setUnitId(UUID unitId) { this.unitId = unitId; }
    public void setStationId(UUID stationId) { this.stationId = stationId; }
    public void setEquipmentId(UUID equipmentId) { this.equipmentId = equipmentId; }
    public void setRecipeId(UUID recipeId) { this.recipeId = recipeId; }
    public void setOperatorId(UUID operatorId) { this.operatorId = operatorId; }
    public void setRunNo(String runNo) { this.runNo = runNo; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public void setRunStatus(String runStatus) { this.runStatus = runStatus; }
}
