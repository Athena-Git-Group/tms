package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Generated;

@Entity
@Table(name="room_qr_tokens")
public class RoomQrToken {
    @Id
    private UUID id;
    private Long tenantId;
    private UUID roomId;
    private String token;
    private Integer version;
    private String status;
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
    public UUID getRoomId() {
        return this.roomId;
    }

    @Generated
    public String getToken() {
        return this.token;
    }

    @Generated
    public Integer getVersion() {
        return this.version;
    }

    @Generated
    public String getStatus() {
        return this.status;
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
    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
    }

    @Generated
    public void setToken(String token) {
        this.token = token;
    }

    @Generated
    public void setVersion(Integer version) {
        this.version = version;
    }

    @Generated
    public void setStatus(String status) {
        this.status = status;
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
    public RoomQrToken() {
    }
}
