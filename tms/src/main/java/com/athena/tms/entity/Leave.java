package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Generated;

@Entity
@Table(name="leaves")
public class Leave {
    @Id
    private UUID id;
    private Long tenantId;
    private UUID staffId;
    private OffsetDateTime fromAt;
    private OffsetDateTime toAt;
    private UUID createdBy;
    private OffsetDateTime createdAt;

    @Generated
    public UUID getId() {
        return this.id;
    }

    @Generated
    public Long getTenantId() {
        return this.tenantId;
    }

    @Generated
    public UUID getStaffId() {
        return this.staffId;
    }

    @Generated
    public OffsetDateTime getFromAt() {
        return this.fromAt;
    }

    @Generated
    public OffsetDateTime getToAt() {
        return this.toAt;
    }

    @Generated
    public UUID getCreatedBy() {
        return this.createdBy;
    }

    @Generated
    public OffsetDateTime getCreatedAt() {
        return this.createdAt;
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
    public void setStaffId(UUID staffId) {
        this.staffId = staffId;
    }

    @Generated
    public void setFromAt(OffsetDateTime fromAt) {
        this.fromAt = fromAt;
    }

    @Generated
    public void setToAt(OffsetDateTime toAt) {
        this.toAt = toAt;
    }

    @Generated
    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    @Generated
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Generated
    public Leave() {
    }
}
