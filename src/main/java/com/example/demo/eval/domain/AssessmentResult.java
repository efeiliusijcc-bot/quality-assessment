package com.example.demo.eval.domain;

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
@Table(name = "assessment_result", schema = "eval")
public class AssessmentResult {

    @Id
    private UUID resultId;

    @Column(nullable = false)
    private UUID taskId;

    @Column(precision = 10, scale = 6)
    private BigDecimal assessmentScore;

    @Column(precision = 8, scale = 6)
    private BigDecimal passProbability;

    @Column
    private Boolean isPass;

    @Column(length = 32)
    private String riskLevel;

    @Column(columnDefinition = "TEXT")
    private String conclusion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String problemParams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String suggestions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String outputPayload;

    @Column(nullable = false)
    private Instant createdAt;

    protected AssessmentResult() {
    }

    public AssessmentResult(UUID taskId, BigDecimal assessmentScore, Boolean isPass) {
        this.resultId = UUID.randomUUID();
        this.taskId = taskId;
        this.assessmentScore = assessmentScore;
        this.isPass = isPass;
        this.createdAt = Instant.now();
    }

    public UUID getResultId() { return resultId; }
    public UUID getTaskId() { return taskId; }
    public BigDecimal getAssessmentScore() { return assessmentScore; }
    public BigDecimal getPassProbability() { return passProbability; }
    public Boolean getIsPass() { return isPass; }
    public String getRiskLevel() { return riskLevel; }
    public String getConclusion() { return conclusion; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPassProbability(BigDecimal passProbability) { this.passProbability = passProbability; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
}
