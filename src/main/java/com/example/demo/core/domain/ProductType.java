package com.example.demo.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_type", schema = "core")
public class ProductType {

    @Id
    private UUID productTypeId;

    @Column(nullable = false, unique = true, length = 64)
    private String productCode;

    @Column(nullable = false, length = 128)
    private String productName;

    @Column(length = 64)
    private String materialSystem;

    @Column(columnDefinition = "TEXT")
    private String specification;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(nullable = false)
    private Instant createdAt;

    protected ProductType() {
    }

    public ProductType(String productCode, String productName) {
        this.productTypeId = UUID.randomUUID();
        this.productCode = productCode;
        this.productName = productName;
        this.materialSystem = "HTCC";
        this.createdAt = Instant.now();
    }

    public UUID getProductTypeId() { return productTypeId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public String getMaterialSystem() { return materialSystem; }
    public String getSpecification() { return specification; }
    public Instant getCreatedAt() { return createdAt; }
}
