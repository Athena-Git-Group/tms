package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Generated;

@Entity
@Table(name="staff_accounts")
public class StaffAccount {
    @Id
    private UUID id;
    private Long tenantId;
    private String name;
    private String loginId;
    private String passwordHash;
    private String role;
    private String dept;
    private boolean active = true;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private boolean mustChangePassword = false;
    private int failedLoginCount = 0;
    private OffsetDateTime lockedUntil;

    @Generated
    public UUID getId() {
        return this.id;
    }

    @Generated
    public Long getTenantId() {
        return this.tenantId;
    }

    @Generated
    public String getName() {
        return this.name;
    }

    @Generated
    public String getLoginId() {
        return this.loginId;
    }

    @Generated
    public String getPasswordHash() {
        return this.passwordHash;
    }

    @Generated
    public String getRole() {
        return this.role;
    }

    @Generated
    public String getDept() {
        return this.dept;
    }

    @Generated
    public boolean isActive() {
        return this.active;
    }

    @Generated
    public OffsetDateTime getCreatedAt() {
        return this.createdAt;
    }

    @Generated
    public OffsetDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    @Generated
    public void setId(UUID id) {
        this.id = id;
    }

    @Generated
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    @Generated
    public void setName(String name) {
        this.name = name;
    }

    @Generated
    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    @Generated
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @Generated
    public void setRole(String role) {
        this.role = role;
    }

    @Generated
    public void setDept(String dept) {
        this.dept = dept;
    }

    @Generated
    public void setActive(boolean active) {
        this.active = active;
    }

    @Generated
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Generated
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Generated
    public boolean isMustChangePassword() {
        return this.mustChangePassword;
    }

    @Generated
    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    @Generated
    public int getFailedLoginCount() {
        return this.failedLoginCount;
    }

    @Generated
    public void setFailedLoginCount(int failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    @Generated
    public OffsetDateTime getLockedUntil() {
        return this.lockedUntil;
    }

    @Generated
    public void setLockedUntil(OffsetDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    @Generated
    public StaffAccount() {
    }
}
