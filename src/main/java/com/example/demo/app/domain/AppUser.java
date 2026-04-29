package com.example.demo.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user", schema = "app")
public class AppUser {

    @Id
    private UUID userId;

    @Column(nullable = false, length = 64, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "real_name", length = 64)
    private String realName;

    @Column(name = "role_code", nullable = false, length = 32)
    private String roleCode;

    @Column(length = 32)
    private String phone;

    @Column(length = 128)
    private String email;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, String realName, String roleCode) {
        this.userId = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
        this.realName = realName;
        this.roleCode = roleCode;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRealName() { return realName; }
    public String getRoleCode() { return roleCode; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setPhone(String phone) { this.phone = phone; }
    public void setEmail(String email) { this.email = email; }
    public void setStatus(String status) { this.status = status; this.updatedAt = Instant.now(); }
}
