/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.persistence.Entity
 *  jakarta.persistence.Id
 *  jakarta.persistence.Table
 *  lombok.Generated
 */
package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Generated;

@Entity
@Table(name="integration_api_keys")
public class IntegrationApiKey {
    @Id
    private UUID id;
    private Long tenantId;
    private String name;
    private String keyPrefix;
    private String keyHash;
    private OffsetDateTime createdAt;
    private OffsetDateTime revokedAt;

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
    public String getKeyPrefix() {
        return this.keyPrefix;
    }

    @Generated
    public String getKeyHash() {
        return this.keyHash;
    }

    @Generated
    public OffsetDateTime getCreatedAt() {
        return this.createdAt;
    }

    @Generated
    public OffsetDateTime getRevokedAt() {
        return this.revokedAt;
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
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    @Generated
    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    @Generated
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Generated
    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    @Generated
    public IntegrationApiKey() {
    }
}
