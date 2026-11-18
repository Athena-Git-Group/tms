package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Generated;

@Entity
@Table(name="sop_items")
public class SopItem {
    @Id
    private UUID id;
    private Long tenantId;
    private String appliesTo;
    private String itemKey;
    private String label;
    private Integer sortOrder;
    private boolean active = true;
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
    public String getAppliesTo() {
        return this.appliesTo;
    }

    @Generated
    public String getItemKey() {
        return this.itemKey;
    }

    @Generated
    public String getLabel() {
        return this.label;
    }

    @Generated
    public Integer getSortOrder() {
        return this.sortOrder;
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
    public void setId(UUID id) {
        this.id = id;
    }

    @Generated
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    @Generated
    public void setAppliesTo(String appliesTo) {
        this.appliesTo = appliesTo;
    }

    @Generated
    public void setItemKey(String itemKey) {
        this.itemKey = itemKey;
    }

    @Generated
    public void setLabel(String label) {
        this.label = label;
    }

    @Generated
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
    public SopItem() {
    }
}
