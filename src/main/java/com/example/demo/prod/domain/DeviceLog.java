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
@Table(name = "device_log", schema = "prod")
public class DeviceLog {

    @Id
    private UUID logId;

    @Column
    private UUID runId;

    @Column
    private UUID equipmentId;

    @Column(nullable = false)
    private Instant logTime;

    @Column(length = 32)
    private String logLevel;

    @Column(length = 64)
    private String alarmCode;

    @Column(length = 255)
    private String alarmName;

    @Column(columnDefinition = "TEXT")
    private String logContent;

    @Column
    private UUID sourceFileId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String rawPayload;

    protected DeviceLog() {
    }

    public DeviceLog(UUID equipmentId, String logContent) {
        this.logId = UUID.randomUUID();
        this.equipmentId = equipmentId;
        this.logContent = logContent;
        this.logTime = Instant.now();
    }

    public UUID getLogId() { return logId; }
    public UUID getRunId() { return runId; }
    public UUID getEquipmentId() { return equipmentId; }
    public Instant getLogTime() { return logTime; }
    public String getLogLevel() { return logLevel; }
    public String getAlarmCode() { return alarmCode; }
    public String getAlarmName() { return alarmName; }
    public String getLogContent() { return logContent; }

    public void setRunId(UUID runId) { this.runId = runId; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
}
