package com.example.demo.kg.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kg_entity", schema = "kg")
public class KgEntity {

    @Id
    private UUID entityId;

    @Column
    private UUID graphVersionId;

    @Column(nullable = false, length = 64)
    private String entityType;

    @Column(length = 64)
    private String refSchema;

    @Column(length = 64)
    private String refTable;

    @Column
    private UUID refId;

    @Column(length = 128)
    private String entityCode;

    @Column(nullable = false, length = 255)
    private String entityName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String properties;

    @Column(nullable = false)
    private Instant createdAt;

    protected KgEntity() {
    }

    public KgEntity(String entityType, String entityName) {
        this.entityId = UUID.randomUUID();
        this.entityType = entityType;
        this.entityName = entityName;
        this.createdAt = Instant.now();
    }

    public UUID getEntityId() { return entityId; }
    public UUID getGraphVersionId() { return graphVersionId; }
    public String getEntityType() { return entityType; }
    public String getRefSchema() { return refSchema; }
    public String getRefTable() { return refTable; }
    public UUID getRefId() { return refId; }
    public String getEntityCode() { return entityCode; }
    public String getEntityName() { return entityName; }
    public Instant getCreatedAt() { return createdAt; }

    public void setGraphVersionId(UUID graphVersionId) { this.graphVersionId = graphVersionId; }
    public void setRefId(UUID refId) { this.refId = refId; }
    public void setEntityCode(String entityCode) { this.entityCode = entityCode; }
}
