package com.example.demo.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workstation", schema = "core")
public class Workstation {

    @Id
    private UUID stationId;

    @Column(nullable = false)
    private UUID stepId;

    @Column(nullable = false, unique = true, length = 64)
    private String stationCode;

    @Column(nullable = false, length = 128)
    private String stationName;

    @Column(length = 128)
    private String location;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    protected Workstation() {
    }

    public Workstation(UUID stepId, String stationCode, String stationName) {
        this.stationId = UUID.randomUUID();
        this.stepId = stepId;
        this.stationCode = stationCode;
        this.stationName = stationName;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public UUID getStationId() { return stationId; }
    public UUID getStepId() { return stepId; }
    public String getStationCode() { return stationCode; }
    public String getStationName() { return stationName; }
    public String getLocation() { return location; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setLocation(String location) { this.location = location; }
}
