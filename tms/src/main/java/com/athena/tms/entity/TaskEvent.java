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
import java.util.UUID;
import lombok.Generated;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name="task_events")
public class TaskEvent {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    private Long tenantId;
    private UUID taskId;
    private int seq;
    private String type;
    private String actorRole;
    private UUID actorId;
    @JdbcTypeCode(value=3001)
    private String payload;
    private OffsetDateTime createdAt;

    @Generated
    public Long getId() {
        return this.id;
    }

    @Generated
    public Long getTenantId() {
        return this.tenantId;
    }

    @Generated
    public UUID getTaskId() {
        return this.taskId;
    }

    @Generated
    public int getSeq() {
        return this.seq;
    }

    @Generated
    public String getType() {
        return this.type;
    }

    @Generated
    public String getActorRole() {
        return this.actorRole;
    }

    @Generated
    public UUID getActorId() {
        return this.actorId;
    }

    @Generated
    public String getPayload() {
        return this.payload;
    }

    @Generated
    public OffsetDateTime getCreatedAt() {
        return this.createdAt;
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
    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    @Generated
    public void setSeq(int seq) {
        this.seq = seq;
    }

    @Generated
    public void setType(String type) {
        this.type = type;
    }

    @Generated
    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    @Generated
    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    @Generated
    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Generated
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Generated
    public TaskEvent() {
    }
}
