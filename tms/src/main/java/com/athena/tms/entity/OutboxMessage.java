/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.persistence.Entity
 *  jakarta.persistence.GeneratedValue
 *  jakarta.persistence.GenerationType
 *  jakarta.persistence.Id
 *  jakarta.persistence.Table
 *  lombok.Generated
 *  org.hibernate.annotations.JdbcTypeCode
 */
package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Generated;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name="outbox")
public class OutboxMessage {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    private Long tenantId;
    private String aggregateKey;
    private String kind;
    @JdbcTypeCode(value=3001)
    private String payload;
    private String idempotencyKey;
    private String status = "pending";
    private int attempts = 0;
    private OffsetDateTime nextRetryAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime sentAt;

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public Long getTenantId() {
        return this.tenantId;
    }

    @Generated
    public String getAggregateKey() {
        return this.aggregateKey;
    }

    @Generated
    public String getKind() {
        return this.kind;
    }

    @Generated
    public String getPayload() {
        return this.payload;
    }

    @Generated
    public String getIdempotencyKey() {
        return this.idempotencyKey;
    }

    @Generated
    public String getStatus() {
        return this.status;
    }

    @Generated
    public int getAttempts() {
        return this.attempts;
    }

    @Generated
    public OffsetDateTime getNextRetryAt() {
        return this.nextRetryAt;
    }

    @Generated
    public OffsetDateTime getCreatedAt() {
        return this.createdAt;
    }

    @Generated
    public OffsetDateTime getSentAt() {
        return this.sentAt;
    }

    @Generated
    public void setId(Long id) {
        this.id = id;
    }

    @Generated
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    @Generated
    public void setAggregateKey(String aggregateKey) {
        this.aggregateKey = aggregateKey;
    }

    @Generated
    public void setKind(String kind) {
        this.kind = kind;
    }

    @Generated
    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Generated
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    @Generated
    public void setStatus(String status) {
        this.status = status;
    }

    @Generated
    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    @Generated
    public void setNextRetryAt(OffsetDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    @Generated
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Generated
    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }

    @Generated
    public OutboxMessage() {
    }
}
