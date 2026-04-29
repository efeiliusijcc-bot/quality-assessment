package com.example.demo.prod.domain;

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
@Table(name = "parameter_value", schema = "prod")
public class ParameterValue {

    @Id
    private UUID valueId;

    @Column(nullable = false)
    private UUID runId;

    @Column(nullable = false)
    private UUID paramId;

    @Column(nullable = false)
    private Instant measuredAt;

    @Column(precision = 18, scale = 6)
    private BigDecimal valueNum;

    @Column(columnDefinition = "TEXT")
    private String valueText;

    @Column
    private Boolean valueBool;

    @Column
    private Instant valueTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String valueJson;

    @Column
    private UUID fileId;

    @Column(length = 32)
    private String qualityFlag;

    @Column(columnDefinition = "TEXT")
    private String sourceRef;

    @Column(nullable = false)
    private Instant createdAt;

    protected ParameterValue() {
    }

    public ParameterValue(UUID runId, UUID paramId, BigDecimal valueNum) {
        this.valueId = UUID.randomUUID();
        this.runId = runId;
        this.paramId = paramId;
        this.valueNum = valueNum;
        this.measuredAt = Instant.now();
        this.qualityFlag = "RAW";
        this.createdAt = Instant.now();
    }

    public UUID getValueId() { return valueId; }
    public UUID getRunId() { return runId; }
    public UUID getParamId() { return paramId; }
    public Instant getMeasuredAt() { return measuredAt; }
    public BigDecimal getValueNum() { return valueNum; }
    public String getValueText() { return valueText; }
    public Boolean getValueBool() { return valueBool; }
    public String getQualityFlag() { return qualityFlag; }
    public Instant getCreatedAt() { return createdAt; }

    public void setValueText(String valueText) { this.valueText = valueText; }
    public void setQualityFlag(String qualityFlag) { this.qualityFlag = qualityFlag; }
}
