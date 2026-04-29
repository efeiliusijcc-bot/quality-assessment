package com.example.demo.etl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cleaning_log", schema = "etl")
public class CleaningLog {

    @Id
    private UUID cleaningLogId;

    @Column
    private UUID ruleId;

    @Column(length = 128)
    private String sourceTable;

    @Column
    private UUID sourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String afterValue;

    @Column(length = 32)
    private String actionResult;

    @Column(nullable = false)
    private Instant createdAt;

    protected CleaningLog() {
    }

    public CleaningLog(UUID ruleId, UUID sourceId) {
        this.cleaningLogId = UUID.randomUUID();
        this.ruleId = ruleId;
        this.sourceId = sourceId;
        this.createdAt = Instant.now();
    }

    public UUID getCleaningLogId() { return cleaningLogId; }
    public UUID getRuleId() { return ruleId; }
    public String getSourceTable() { return sourceTable; }
    public UUID getSourceId() { return sourceId; }
    public String getActionResult() { return actionResult; }
    public Instant getCreatedAt() { return createdAt; }

    public String getBeforeValue() { return beforeValue; }
    public String getAfterValue() { return afterValue; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }
    public void setBeforeValue(String beforeValue) { this.beforeValue = beforeValue; }
    public void setAfterValue(String afterValue) { this.afterValue = afterValue; }
    public void setActionResult(String actionResult) { this.actionResult = actionResult; }
}
