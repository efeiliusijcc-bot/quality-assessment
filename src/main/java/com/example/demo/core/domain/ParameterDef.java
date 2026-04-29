package com.example.demo.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parameter_def", schema = "core")
public class ParameterDef {

    @Id
    private UUID paramId;

    @Column
    private UUID stepId;

    @Column(nullable = false, length = 128)
    private String paramCode;

    @Column(nullable = false, length = 128)
    private String paramName;

    @Column(nullable = false, length = 32)
    private String paramCategory;

    @Column(nullable = false, length = 32)
    private String dataType;

    @Column(length = 32)
    private String unit;

    @Column(nullable = false, length = 32)
    private String sourceType;

    @Column(precision = 18, scale = 6)
    private BigDecimal lowerLimit;

    @Column(precision = 18, scale = 6)
    private BigDecimal upperLimit;

    @Column(precision = 18, scale = 6)
    private BigDecimal standardValue;

    @Column(nullable = false)
    private Boolean requiredFlag;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    protected ParameterDef() {
    }

    public ParameterDef(String paramCode, String paramName, String paramCategory, String dataType) {
        this.paramId = UUID.randomUUID();
        this.paramCode = paramCode;
        this.paramName = paramName;
        this.paramCategory = paramCategory;
        this.dataType = dataType;
        this.sourceType = "MANUAL";
        this.requiredFlag = false;
        this.createdAt = Instant.now();
    }

    public UUID getParamId() { return paramId; }
    public UUID getStepId() { return stepId; }
    public String getParamCode() { return paramCode; }
    public String getParamName() { return paramName; }
    public String getParamCategory() { return paramCategory; }
    public String getDataType() { return dataType; }
    public String getUnit() { return unit; }
    public BigDecimal getLowerLimit() { return lowerLimit; }
    public BigDecimal getUpperLimit() { return upperLimit; }
    public BigDecimal getStandardValue() { return standardValue; }
    public Boolean getRequiredFlag() { return requiredFlag; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStepId(UUID stepId) { this.stepId = stepId; }
    public void setUnit(String unit) { this.unit = unit; }
    public void setLowerLimit(BigDecimal lowerLimit) { this.lowerLimit = lowerLimit; }
    public void setUpperLimit(BigDecimal upperLimit) { this.upperLimit = upperLimit; }
    public void setStandardValue(BigDecimal standardValue) { this.standardValue = standardValue; }
}
