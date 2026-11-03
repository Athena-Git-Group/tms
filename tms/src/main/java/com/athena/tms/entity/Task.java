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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Generated;

@Entity
@Table(name="tasks")
public class Task {
    @Id
    private UUID id;
    private Long tenantId;
    private String type;
    private String source;
    private UUID roomId;
    private UUID reservationId;
    private String dept;
    private String state;
    private int eventSeq = 0;
    private UUID assigneeId;
    private String title;
    private String note;
    private Integer estMin;
    private OffsetDateTime startedAt;
    private OffsetDateTime doneAt;
    private int redoCount = 0;
    private Integer rating;
    private UUID planId;
    private LocalDate workDate;
    private Integer slaMin;
    private Integer escLevel;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Generated
    public UUID getId() {
        return this.id;
    }

    @Generated
    public Long getTenantId() {
        return this.tenantId;
    }

    @Generated
    public String getType() {
        return this.type;
    }

    @Generated
    public String getSource() {
        return this.source;
    }

    @Generated
    public UUID getRoomId() {
        return this.roomId;
    }

    @Generated
    public UUID getReservationId() {
        return this.reservationId;
    }

    @Generated
    public String getDept() {
        return this.dept;
    }

    @Generated
    public String getState() {
        return this.state;
    }

    @Generated
    public int getEventSeq() {
        return this.eventSeq;
    }

    @Generated
    public UUID getAssigneeId() {
        return this.assigneeId;
    }

    @Generated
    public String getTitle() {
        return this.title;
    }

    @Generated
    public String getNote() {
        return this.note;
    }

    @Generated
    public Integer getEstMin() {
        return this.estMin;
    }

    @Generated
    public OffsetDateTime getStartedAt() {
        return this.startedAt;
    }

    @Generated
    public OffsetDateTime getDoneAt() {
        return this.doneAt;
    }

    @Generated
    public int getRedoCount() {
        return this.redoCount;
    }

    @Generated
    public Integer getRating() {
        return this.rating;
    }

    @Generated
    public UUID getPlanId() {
        return this.planId;
    }

    @Generated
    public LocalDate getWorkDate() {
        return this.workDate;
    }

    @Generated
    public Integer getSlaMin() {
        return this.slaMin;
    }

    @Generated
    public Integer getEscLevel() {
        return this.escLevel;
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
    public void setType(String type) {
        this.type = type;
    }

    @Generated
    public void setSource(String source) {
        this.source = source;
    }

    @Generated
    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
    }

    @Generated
    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    @Generated
    public void setDept(String dept) {
        this.dept = dept;
    }

    @Generated
    public void setState(String state) {
        this.state = state;
    }

    @Generated
    public void setEventSeq(int eventSeq) {
        this.eventSeq = eventSeq;
    }

    @Generated
    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
    }

    @Generated
    public void setTitle(String title) {
        this.title = title;
    }

    @Generated
    public void setNote(String note) {
        this.note = note;
    }

    @Generated
    public void setEstMin(Integer estMin) {
        this.estMin = estMin;
    }

    @Generated
    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    @Generated
    public void setDoneAt(OffsetDateTime doneAt) {
        this.doneAt = doneAt;
    }

    @Generated
    public void setRedoCount(int redoCount) {
        this.redoCount = redoCount;
    }

    @Generated
    public void setRating(Integer rating) {
        this.rating = rating;
    }

    @Generated
    public void setPlanId(UUID planId) {
        this.planId = planId;
    }

    @Generated
    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    @Generated
    public void setSlaMin(Integer slaMin) {
        this.slaMin = slaMin;
    }

    @Generated
    public void setEscLevel(Integer escLevel) {
        this.escLevel = escLevel;
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
    public Task() {
    }
}
