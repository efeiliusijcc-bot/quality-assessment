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
@Table(name = "process_recipe", schema = "prod")
public class ProcessRecipe {

    @Id
    private UUID recipeId;

    @Column
    private UUID productTypeId;

    @Column(nullable = false)
    private UUID stepId;

    @Column(nullable = false, length = 64)
    private String recipeCode;

    @Column(nullable = false, length = 128)
    private String recipeName;

    @Column(nullable = false, length = 32)
    private String versionNo;

    @Column(nullable = false)
    private Boolean isActive;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String parameterJson;

    @Column(nullable = false)
    private Instant createdAt;

    protected ProcessRecipe() {
    }

    public ProcessRecipe(UUID stepId, String recipeCode, String recipeName) {
        this.recipeId = UUID.randomUUID();
        this.stepId = stepId;
        this.recipeCode = recipeCode;
        this.recipeName = recipeName;
        this.versionNo = "V1.0";
        this.isActive = true;
        this.createdAt = Instant.now();
    }

    public UUID getRecipeId() { return recipeId; }
    public UUID getProductTypeId() { return productTypeId; }
    public UUID getStepId() { return stepId; }
    public String getRecipeCode() { return recipeCode; }
    public String getRecipeName() { return recipeName; }
    public String getVersionNo() { return versionNo; }
    public Boolean getIsActive() { return isActive; }
    public Instant getCreatedAt() { return createdAt; }

    public void setProductTypeId(UUID productTypeId) { this.productTypeId = productTypeId; }
}
