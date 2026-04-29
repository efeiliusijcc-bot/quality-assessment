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
@Table(name = "product_unit", schema = "prod")
public class ProductUnit {

    @Id
    private UUID unitId;

    @Column(nullable = false)
    private UUID batchId;

    @Column(nullable = false, length = 128)
    private String serialNo;

    @Column
    private UUID currentStepId;

    @Column(nullable = false, length = 32)
    private String unitStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    protected ProductUnit() {
    }

    public ProductUnit(UUID batchId, String serialNo) {
        this.unitId = UUID.randomUUID();
        this.batchId = batchId;
        this.serialNo = serialNo;
        this.unitStatus = "IN_PROCESS";
        this.createdAt = Instant.now();
    }

    public UUID getUnitId() { return unitId; }
    public UUID getBatchId() { return batchId; }
    public String getSerialNo() { return serialNo; }
    public UUID getCurrentStepId() { return currentStepId; }
    public String getUnitStatus() { return unitStatus; }
    public Instant getCreatedAt() { return createdAt; }

    public void setCurrentStepId(UUID currentStepId) { this.currentStepId = currentStepId; }
    public void setUnitStatus(String unitStatus) { this.unitStatus = unitStatus; }
}
