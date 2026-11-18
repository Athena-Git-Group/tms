package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Generated;

@Entity
@Table(name="task_sop_checks")
public class TaskSopCheck {
    @Id
    private UUID id;
    private Long tenantId;
    private UUID taskId;
    private String itemKey;
    private String label;
    private boolean checked = false;
    private boolean skipped = false;
    private UUID checkedBy;
    private OffsetDateTime checkedAt;

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
    public String getItemKey() {
        return this.itemKey;
    }

    @Generated
    public String getLabel() {
        return this.label;
    }

    @Generated
    public boolean isChecked() {
        return this.checked;
    }

    @Generated
    public boolean isSkipped() {
        return this.skipped;
    }

    @Generated
    public UUID getCheckedBy() {
        return this.checkedBy;
    }

    @Generated
    public OffsetDateTime getCheckedAt() {
        return this.checkedAt;
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
    public void setItemKey(String itemKey) {
        this.itemKey = itemKey;
    }

    @Generated
    public void setLabel(String label) {
        this.label = label;
    }

    @Generated
    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    @Generated
    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    @Generated
    public void setCheckedBy(UUID checkedBy) {
        this.checkedBy = checkedBy;
    }

    @Generated
    public void setCheckedAt(OffsetDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }

    @Generated
    public TaskSopCheck() {
    }
}
