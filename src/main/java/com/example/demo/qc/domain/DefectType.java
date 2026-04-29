package com.example.demo.qc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "defect_type", schema = "qc")
public class DefectType {

    @Id
    private UUID defectTypeId;

    @Column
    private UUID stepId;

    @Column(nullable = false, length = 128)
    private String defectCode;

    @Column(nullable = false, length = 128)
    private String defectName;

    @Column(length = 64)
    private String defectCategory;

    @Column
    private Integer defaultSeverity;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    protected DefectType() {
    }

    public DefectType(UUID stepId, String defectCode, String defectName, String defectCategory, Integer defaultSeverity) {
        this.defectTypeId = UUID.randomUUID();
        this.stepId = stepId;
        this.defectCode = defectCode;
        this.defectName = defectName;
        this.defectCategory = defectCategory;
        this.defaultSeverity = defaultSeverity;
        this.createdAt = Instant.now();
    }

    public UUID getDefectTypeId() { return defectTypeId; }
    public UUID getStepId() { return stepId; }
    public String getDefectCode() { return defectCode; }
    public String getDefectName() { return defectName; }
    public String getDefectCategory() { return defectCategory; }
    public Integer getDefaultSeverity() { return defaultSeverity; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDefectCategory(String defectCategory) { this.defectCategory = defectCategory; }
    public void setDefaultSeverity(Integer defaultSeverity) { this.defaultSeverity = defaultSeverity; }
    public void setDescription(String description) { this.description = description; }
}
