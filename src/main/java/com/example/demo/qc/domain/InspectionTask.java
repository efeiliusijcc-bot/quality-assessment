package com.example.demo.qc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_task", schema = "qc")
public class InspectionTask {

    @Id
    private UUID inspectionId;

    @Column(nullable = false)
    private UUID runId;

    @Column
    private UUID unitId;

    @Column(nullable = false)
    private UUID stepId;

    @Column(nullable = false, length = 64)
    private String inspectionType;

    @Column(length = 128)
    private String modelName;

    @Column(length = 64)
    private String modelVersion;

    @Column(length = 32)
    private String resultStatus;

    @Column(precision = 8, scale = 6)
    private BigDecimal confidence;

    @Column
    private UUID imageFileId;

    @Column
    private UUID videoFileId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String rawResult;

    @Column(nullable = false)
    private Instant inspectedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected InspectionTask() {
    }

    public InspectionTask(UUID runId, UUID stepId, String inspectionType) {
        this.inspectionId = UUID.randomUUID();
        this.runId = runId;
        this.stepId = stepId;
        this.inspectionType = inspectionType;
        this.inspectedAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public UUID getInspectionId() { return inspectionId; }
    public UUID getRunId() { return runId; }
    public UUID getUnitId() { return unitId; }
    public UUID getStepId() { return stepId; }
    public String getInspectionType() { return inspectionType; }
    public String getResultStatus() { return resultStatus; }
    public BigDecimal getConfidence() { return confidence; }
    public Instant getInspectedAt() { return inspectedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
}
