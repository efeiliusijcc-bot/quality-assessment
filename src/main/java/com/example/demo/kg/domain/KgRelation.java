package com.example.demo.kg.domain;

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
@Table(name = "kg_relation", schema = "kg")
public class KgRelation {

    @Id
    private UUID relationId;

    @Column
    private UUID graphVersionId;

    @Column(nullable = false)
    private UUID sourceEntityId;

    @Column(nullable = false)
    private UUID targetEntityId;

    @Column(nullable = false, length = 128)
    private String relationType;

    @Column(precision = 12, scale = 8)
    private BigDecimal relationWeight;

    @Column(precision = 8, scale = 6)
    private BigDecimal confidence;

    @Column(length = 64)
    private String evidenceSource;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String evidencePayload;

    @Column(nullable = false)
    private Instant createdAt;

    protected KgRelation() {
    }

    public KgRelation(UUID sourceEntityId, UUID targetEntityId, String relationType) {
        this.relationId = UUID.randomUUID();
        this.sourceEntityId = sourceEntityId;
        this.targetEntityId = targetEntityId;
        this.relationType = relationType;
        this.createdAt = Instant.now();
    }

    public UUID getRelationId() { return relationId; }
    public UUID getGraphVersionId() { return graphVersionId; }
    public UUID getSourceEntityId() { return sourceEntityId; }
    public UUID getTargetEntityId() { return targetEntityId; }
    public String getRelationType() { return relationType; }
    public BigDecimal getRelationWeight() { return relationWeight; }
    public BigDecimal getConfidence() { return confidence; }
    public String getEvidenceSource() { return evidenceSource; }
    public Instant getCreatedAt() { return createdAt; }

    public void setRelationWeight(BigDecimal relationWeight) { this.relationWeight = relationWeight; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public void setEvidenceSource(String evidenceSource) { this.evidenceSource = evidenceSource; }
}
