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
@Table(name = "production_batch", schema = "prod")
public class ProductionBatch {

    @Id
    private UUID batchId;

    @Column(nullable = false, unique = true, length = 64)
    private String batchNo;

    @Column(nullable = false)
    private UUID productTypeId;

    @Column
    private Integer planQty;

    @Column
    private Integer actualQty;

    @Column
    private Instant startTime;

    @Column
    private Instant endTime;

    @Column(nullable = false, length = 32)
    private String batchStatus;

    @Column
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    protected ProductionBatch() {
    }

    public ProductionBatch(String batchNo, UUID productTypeId) {
        this.batchId = UUID.randomUUID();
        this.batchNo = batchNo;
        this.productTypeId = productTypeId;
        this.batchStatus = "CREATED";
        this.createdAt = Instant.now();
    }

    public UUID getBatchId() { return batchId; }
    public String getBatchNo() { return batchNo; }
    public UUID getProductTypeId() { return productTypeId; }
    public Integer getPlanQty() { return planQty; }
    public Integer getActualQty() { return actualQty; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public String getBatchStatus() { return batchStatus; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPlanQty(Integer planQty) { this.planQty = planQty; }
    public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public void setBatchStatus(String batchStatus) { this.batchStatus = batchStatus; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
