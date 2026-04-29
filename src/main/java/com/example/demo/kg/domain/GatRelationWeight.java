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
@Table(name = "gat_relation_weight", schema = "kg")
public class GatRelationWeight {

    @Id
    private UUID weightId;

    @Column(nullable = false)
    private UUID gatTaskId;

    @Column
    private UUID relationId;

    @Column(nullable = false, precision = 12, scale = 8)
    private BigDecimal attentionWeight;

    @Column(nullable = false)
    private Boolean hiddenRelationFlag;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String explanation;

    @Column(nullable = false)
    private Instant createdAt;

    protected GatRelationWeight() {
    }

    public GatRelationWeight(UUID gatTaskId, UUID relationId, BigDecimal attentionWeight) {
        this.weightId = UUID.randomUUID();
        this.gatTaskId = gatTaskId;
        this.relationId = relationId;
        this.attentionWeight = attentionWeight;
        this.hiddenRelationFlag = false;
        this.createdAt = Instant.now();
    }

    public UUID getWeightId() { return weightId; }
    public UUID getGatTaskId() { return gatTaskId; }
    public UUID getRelationId() { return relationId; }
    public BigDecimal getAttentionWeight() { return attentionWeight; }
    public Boolean getHiddenRelationFlag() { return hiddenRelationFlag; }
    public Instant getCreatedAt() { return createdAt; }
}
