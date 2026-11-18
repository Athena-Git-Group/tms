package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Generated;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name="inspections")
public class Inspection {
    @Id
    private UUID id;
    private Long tenantId;
    private UUID taskId;
    private UUID inspectorId;
    private String result;
    @JdbcTypeCode(value=3001)
    private String items;
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
    public UUID getTaskId() {
        return this.taskId;
    }

    @Generated
    public UUID getInspectorId() {
        return this.inspectorId;
    }

    @Generated
    public String getResult() {
        return this.result;
    }

    @Generated
    public String getItems() {
        return this.items;
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
    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    @Generated
    public void setInspectorId(UUID inspectorId) {
        this.inspectorId = inspectorId;
    }

    @Generated
    public void setResult(String result) {
        this.result = result;
    }

    @Generated
    public void setItems(String items) {
        this.items = items;
    }

    @Generated
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Generated
    public Inspection() {
    }
}
