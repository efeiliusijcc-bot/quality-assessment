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
@Table(name = "import_job", schema = "etl")
public class ImportJob {

    @Id
    private UUID importId;

    @Column(nullable = false, length = 32)
    private String sourceType;

    @Column(length = 255)
    private String sourceName;

    @Column
    private UUID fileId;

    @Column(length = 128)
    private String targetTable;

    @Column(nullable = false, length = 32)
    private String importStatus;

    @Column
    private Integer totalRows;

    @Column
    private Integer successRows;

    @Column
    private Integer errorRows;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String errorLog;

    @Column
    private UUID importedBy;

    @Column(nullable = false)
    private Instant startedAt;

    @Column
    private Instant finishedAt;

    protected ImportJob() {
    }

    public ImportJob(String sourceType, String sourceName) {
        this.importId = UUID.randomUUID();
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.importStatus = "CREATED";
        this.totalRows = 0;
        this.successRows = 0;
        this.errorRows = 0;
        this.errorLog = "[]";
        this.startedAt = Instant.now();
    }

    public UUID getImportId() { return importId; }
    public String getSourceType() { return sourceType; }
    public String getSourceName() { return sourceName; }
    public UUID getFileId() { return fileId; }
    public String getImportStatus() { return importStatus; }
    public Integer getTotalRows() { return totalRows; }
    public Integer getSuccessRows() { return successRows; }
    public Integer getErrorRows() { return errorRows; }
    public UUID getImportedBy() { return importedBy; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }

    public void setFileId(UUID fileId) { this.fileId = fileId; }
    public void setImportStatus(String importStatus) { this.importStatus = importStatus; }
    public void setTotalRows(Integer totalRows) { this.totalRows = totalRows; }
    public void setSuccessRows(Integer successRows) { this.successRows = successRows; }
    public void setErrorRows(Integer errorRows) { this.errorRows = errorRows; }
    public void setImportedBy(UUID importedBy) { this.importedBy = importedBy; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
    public void setErrorLog(String errorLog) { this.errorLog = errorLog; }
    public String getTargetTable() { return targetTable; }
    public String getErrorLog() { return errorLog; }
}
