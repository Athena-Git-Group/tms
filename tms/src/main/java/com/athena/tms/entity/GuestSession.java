package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Generated;

@Entity
@Table(name="guest_sessions")
public class GuestSession {
    @Id
    private UUID id;
    private Long tenantId;
    private String sessionToken;
    private UUID roomId;
    private UUID reservationId;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastSeenAt;

    @Generated
    public UUID getId() {
        return this.id;
    }

    @Generated
    public Long getTenantId() {
        return this.tenantId;
    }

    @Generated
    public String getSessionToken() {
        return this.sessionToken;
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
    public OffsetDateTime getExpiresAt() {
        return this.expiresAt;
    }

    @Generated
    public OffsetDateTime getRevokedAt() {
        return this.revokedAt;
    }

    @Generated
    public OffsetDateTime getCreatedAt() {
        return this.createdAt;
    }

    @Generated
    public OffsetDateTime getLastSeenAt() {
        return this.lastSeenAt;
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
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
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
    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Generated
    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    @Generated
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Generated
    public void setLastSeenAt(OffsetDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    @Generated
    public GuestSession() {
    }
}
