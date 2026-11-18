package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Generated;

@Entity
@Table(name="rooms")
public class Room {
    @Id
    private UUID id;
    private Long tenantId;
    private String roomNo;
    private Integer floor;
    private String roomType;
    private String hkState;
    private String occupancy;
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
    public String getRoomNo() {
        return this.roomNo;
    }

    @Generated
    public Integer getFloor() {
        return this.floor;
    }

    @Generated
    public String getRoomType() {
        return this.roomType;
    }

    @Generated
    public String getHkState() {
        return this.hkState;
    }

    @Generated
    public String getOccupancy() {
        return this.occupancy;
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
    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
    }

    @Generated
    public void setFloor(Integer floor) {
        this.floor = floor;
    }

    @Generated
    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    @Generated
    public void setHkState(String hkState) {
        this.hkState = hkState;
    }

    @Generated
    public void setOccupancy(String occupancy) {
        this.occupancy = occupancy;
    }

    @Generated
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Generated
    public Room() {
    }
}
