package com.example.demo.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_resource", schema = "core")
public class FileResource {

    @Id
    private UUID fileId;

    @Column(nullable = false, length = 32)
    private String fileType;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(length = 128)
    private String mimeType;

    @Column
    private Long fileSize;

    @Column(length = 128)
    private String sha256;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected FileResource() {
    }

    public FileResource(String fileType, String fileName, String filePath) {
        this.fileId = UUID.randomUUID();
        this.fileType = fileType;
        this.fileName = fileName;
        this.filePath = filePath;
        this.uploadedAt = Instant.now();
    }

    public UUID getFileId() { return fileId; }
    public String getFileType() { return fileType; }
    public String getFileName() { return fileName; }
    public String getFilePath() { return filePath; }
    public String getMimeType() { return mimeType; }
    public Long getFileSize() { return fileSize; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }

    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }
}
