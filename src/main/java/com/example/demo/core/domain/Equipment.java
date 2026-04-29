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
@Table(name = "equipment", schema = "core")
public class Equipment {

    @Id
    private UUID equipmentId;

    @Column
    private UUID stationId;

    @Column(nullable = false, unique = true, length = 64)
    private String equipmentCode;

    @Column(nullable = false, length = 128)
    private String equipmentName;

    @Column(length = 64)
    private String equipmentType;

    @Column(length = 128)
    private String manufacturer;

    @Column(length = 128)
    private String modelNo;

    @Column(nullable = false, length = 16)
    private String status;

    @Column
    private java.time.LocalDate installedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(nullable = false)
    private Instant createdAt;

    protected Equipment() {
    }

    public Equipment(String equipmentCode, String equipmentName) {
        this.equipmentId = UUID.randomUUID();
        this.equipmentCode = equipmentCode;
        this.equipmentName = equipmentName;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public UUID getEquipmentId() { return equipmentId; }
    public UUID getStationId() { return stationId; }
    public String getEquipmentCode() { return equipmentCode; }
    public String getEquipmentName() { return equipmentName; }
    public String getEquipmentType() { return equipmentType; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStationId(UUID stationId) { this.stationId = stationId; }
    public void setEquipmentType(String equipmentType) { this.equipmentType = equipmentType; }
}
