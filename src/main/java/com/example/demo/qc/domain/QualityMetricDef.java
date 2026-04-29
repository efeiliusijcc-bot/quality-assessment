package com.example.demo.qc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quality_metric_def", schema = "qc")
public class QualityMetricDef {

    @Id
    private UUID metricId;

    @Column
    private UUID stepId;

    @Column(nullable = false, length = 128)
    private String metricCode;

    @Column(nullable = false, length = 128)
    private String metricName;

    @Column(length = 32)
    private String unit;

    @Column(precision = 18, scale = 6)
    private BigDecimal lowerLimit;

    @Column(precision = 18, scale = 6)
    private BigDecimal upperLimit;

    @Column(precision = 18, scale = 6)
    private BigDecimal targetValue;

    @Column(columnDefinition = "TEXT")
    private String passRule;

    @Column(precision = 8, scale = 4)
    private BigDecimal severityWeight;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    protected QualityMetricDef() {
    }

    public QualityMetricDef(String metricCode, String metricName) {
        this.metricId = UUID.randomUUID();
        this.metricCode = metricCode;
        this.metricName = metricName;
        this.severityWeight = BigDecimal.ONE;
        this.createdAt = Instant.now();
    }

    public UUID getMetricId() { return metricId; }
    public UUID getStepId() { return stepId; }
    public String getMetricCode() { return metricCode; }
    public String getMetricName() { return metricName; }
    public String getUnit() { return unit; }
    public BigDecimal getLowerLimit() { return lowerLimit; }
    public BigDecimal getUpperLimit() { return upperLimit; }
    public BigDecimal getTargetValue() { return targetValue; }
    public String getPassRule() { return passRule; }
    public BigDecimal getSeverityWeight() { return severityWeight; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStepId(UUID stepId) { this.stepId = stepId; }
    public void setUnit(String unit) { this.unit = unit; }
    public void setLowerLimit(BigDecimal lowerLimit) { this.lowerLimit = lowerLimit; }
    public void setUpperLimit(BigDecimal upperLimit) { this.upperLimit = upperLimit; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }
    public void setPassRule(String passRule) { this.passRule = passRule; }
    public void setSeverityWeight(BigDecimal severityWeight) { this.severityWeight = severityWeight; }
    public void setDescription(String description) { this.description = description; }
}
