package com.example.demo.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "process_step", schema = "core")
public class ProcessStep {

    @Id
    private UUID stepId;

    @Column(nullable = false, unique = true, length = 32)
    private String stepCode;

    @Column(nullable = false, length = 128)
    private String stepName;

    @Column(nullable = false)
    private Integer stepOrder;

    @Column(nullable = false)
    private Boolean isInspection;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    protected ProcessStep() {
    }

    public ProcessStep(String stepCode, String stepName, Integer stepOrder, Boolean isInspection) {
        this.stepId = UUID.randomUUID();
        this.stepCode = stepCode;
        this.stepName = stepName;
        this.stepOrder = stepOrder;
        this.isInspection = isInspection;
        this.createdAt = Instant.now();
    }

    public UUID getStepId() { return stepId; }
    public String getStepCode() { return stepCode; }
    public String getStepName() { return stepName; }
    public Integer getStepOrder() { return stepOrder; }
    public Boolean getIsInspection() { return isInspection; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
