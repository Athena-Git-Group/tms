package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Generated;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name="reservations_cache")
public class ReservationCache {
    @Id
    private UUID id;
    private Long tenantId;
    private String reservationRef;
    private UUID roomId;
    private String guestName;
    private String lang;
    @JdbcTypeCode(value=3001)
    private String attrs;
    private OffsetDateTime checkinAt;
    private OffsetDateTime checkoutAt;
    private String source;
    private OffsetDateTime syncedAt;
    private OffsetDateTime supersededAt;

    @Generated
    public UUID getId() {
        return this.id;
    }

    @Generated
    public Long getTenantId() {
        return this.tenantId;
    }

    @Generated
    public String getReservationRef() {
        return this.reservationRef;
    }

    @Generated
    public UUID getRoomId() {
        return this.roomId;
    }

    @Generated
    public String getGuestName() {
        return this.guestName;
    }

    @Generated
    public String getLang() {
        return this.lang;
    }

    @Generated
    public String getAttrs() {
        return this.attrs;
    }

    @Generated
    public OffsetDateTime getCheckinAt() {
        return this.checkinAt;
    }

    @Generated
    public OffsetDateTime getCheckoutAt() {
        return this.checkoutAt;
    }

    @Generated
    public String getSource() {
        return this.source;
    }

    @Generated
    public OffsetDateTime getSyncedAt() {
        return this.syncedAt;
    }

    @Generated
    public OffsetDateTime getSupersededAt() {
        return this.supersededAt;
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
    public void setReservationRef(String reservationRef) {
        this.reservationRef = reservationRef;
    }

    @Generated
    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
    }

    @Generated
    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    @Generated
    public void setLang(String lang) {
        this.lang = lang;
    }

    @Generated
    public void setAttrs(String attrs) {
        this.attrs = attrs;
    }

    @Generated
    public void setCheckinAt(OffsetDateTime checkinAt) {
        this.checkinAt = checkinAt;
    }

    @Generated
    public void setCheckoutAt(OffsetDateTime checkoutAt) {
        this.checkoutAt = checkoutAt;
    }

    @Generated
    public void setSource(String source) {
        this.source = source;
    }

    @Generated
    public void setSyncedAt(OffsetDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }

    @Generated
    public void setSupersededAt(OffsetDateTime supersededAt) {
        this.supersededAt = supersededAt;
    }

    @Generated
    public ReservationCache() {
    }
}
