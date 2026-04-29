package com.example.demo.etl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cleaning_rule", schema = "etl")
public class CleaningRule {

    @Id
    private UUID ruleId;

    @Column(nullable = false, unique = true, length = 128)
    private String ruleCode;

    @Column(nullable = false, length = 128)
    private String ruleName;

    @Column(length = 64)
    private String targetCategory;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String conditionExpr;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String actionExpr;

    @Column
    private Integer priorityNo;

    @Column(nullable = false)
    private Boolean enabledFlag;

    @Column(nullable = false)
    private Instant createdAt;

    protected CleaningRule() {
    }

    public CleaningRule(String ruleCode, String ruleName, String conditionExpr, String actionExpr) {
        this.ruleId = UUID.randomUUID();
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.conditionExpr = conditionExpr;
        this.actionExpr = actionExpr;
        this.priorityNo = 100;
        this.enabledFlag = true;
        this.createdAt = Instant.now();
    }

    public UUID getRuleId() { return ruleId; }
    public String getRuleCode() { return ruleCode; }
    public String getRuleName() { return ruleName; }
    public String getTargetCategory() { return targetCategory; }
    public Boolean getEnabledFlag() { return enabledFlag; }
    public Instant getCreatedAt() { return createdAt; }
}
