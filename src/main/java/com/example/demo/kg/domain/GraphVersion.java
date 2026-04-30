package com.example.demo.kg.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "graph_version", schema = "kg")
public class GraphVersion {

    @Id
    private UUID graphVersionId;

    @Column(nullable = false, length = 128)
    private String graphName;

    @Column(nullable = false, length = 64)
    private String versionNo;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    protected GraphVersion() {
    }

    public GraphVersion(String graphName, String versionNo) {
        this.graphVersionId = UUID.randomUUID();
        this.graphName = graphName;
        this.versionNo = versionNo;
        this.createdAt = Instant.now();
    }

    public UUID getGraphVersionId() { return graphVersionId; }
    public String getGraphName() { return graphName; }
    public String getVersionNo() { return versionNo; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
}
