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
@Table(name = "quality_measurement", schema = "qc")
public class QualityMeasurement {

    @Id
    private UUID measurementId;

    @Column(nullable = false)
    private UUID runId;

    @Column
    private UUID unitId;

    @Column(nullable = false)
    private UUID metricId;

    @Column(nullable = false)
    private Instant measuredAt;

    @Column(precision = 18, scale = 6)
    private BigDecimal valueNum;

    @Column(columnDefinition = "TEXT")
    private String valueText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String valueJson;

    @Column
    private Boolean isPass;

    @Column(precision = 18, scale = 6)
    private BigDecimal deviationValue;

    @Column(length = 64)
    private String measurementMethod;

    @Column
    private UUID sourceFileId;

    @Column(nullable = false)
    private Instant createdAt;

    protected QualityMeasurement() {
    }

    public QualityMeasurement(UUID runId, UUID metricId, BigDecimal valueNum) {
        this.measurementId = UUID.randomUUID();
        this.runId = runId;
        this.metricId = metricId;
        this.valueNum = valueNum;
        this.measuredAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public UUID getMeasurementId() { return measurementId; }
    public UUID getRunId() { return runId; }
    public UUID getUnitId() { return unitId; }
    public UUID getMetricId() { return metricId; }
    public Instant getMeasuredAt() { return measuredAt; }
    public BigDecimal getValueNum() { return valueNum; }
    public Boolean getIsPass() { return isPass; }
    public BigDecimal getDeviationValue() { return deviationValue; }
    public Instant getCreatedAt() { return createdAt; }

    public void setUnitId(UUID unitId) { this.unitId = unitId; }
    public void setIsPass(Boolean isPass) { this.isPass = isPass; }
    public void setDeviationValue(BigDecimal deviationValue) { this.deviationValue = deviationValue; }
    public void setMeasurementMethod(String measurementMethod) { this.measurementMethod = measurementMethod; }
    public void setSourceFileId(UUID sourceFileId) { this.sourceFileId = sourceFileId; }
}
