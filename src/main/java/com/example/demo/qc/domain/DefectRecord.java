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
@Table(name = "defect_record", schema = "qc")
public class DefectRecord {

    @Id
    private UUID defectId;

    @Column(nullable = false)
    private UUID inspectionId;

    @Column
    private UUID unitId;

    @Column(nullable = false)
    private UUID defectTypeId;

    @Column
    private Integer defectCount;

    @Column(precision = 18, scale = 6)
    private BigDecimal defectSize;

    @Column(precision = 18, scale = 6)
    private BigDecimal defectArea;

    @Column(precision = 18, scale = 6)
    private BigDecimal locationX;

    @Column(precision = 18, scale = 6)
    private BigDecimal locationY;

    @Column(precision = 18, scale = 6)
    private BigDecimal locationZ;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String bboxJson;

    @Column
    private UUID defectImageId;

    @Column(precision = 8, scale = 6)
    private BigDecimal confidence;

    @Column
    private Integer severityLevel;

    @Column
    private Boolean isCritical;

    @Column(nullable = false)
    private Instant createdAt;

    protected DefectRecord() {
    }

    public DefectRecord(UUID inspectionId, UUID defectTypeId) {
        this.defectId = UUID.randomUUID();
        this.inspectionId = inspectionId;
        this.defectTypeId = defectTypeId;
        this.defectCount = 1;
        this.isCritical = false;
        this.createdAt = Instant.now();
    }

    public UUID getDefectId() { return defectId; }
    public UUID getInspectionId() { return inspectionId; }
    public UUID getUnitId() { return unitId; }
    public UUID getDefectTypeId() { return defectTypeId; }
    public Integer getDefectCount() { return defectCount; }
    public BigDecimal getConfidence() { return confidence; }
    public Integer getSeverityLevel() { return severityLevel; }
    public Boolean getIsCritical() { return isCritical; }
    public Instant getCreatedAt() { return createdAt; }

    public void setSeverityLevel(Integer severityLevel) { this.severityLevel = severityLevel; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public void setIsCritical(Boolean isCritical) { this.isCritical = isCritical; }
}
