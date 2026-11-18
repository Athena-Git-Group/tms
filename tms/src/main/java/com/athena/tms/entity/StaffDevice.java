package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Generated;

@Entity
@Table(name="staff_devices")
public class StaffDevice {
    @Id
    private UUID id;
    private Long tenantId;
    private UUID staffId;
    private String deviceFingerprint;
    private String pushToken;
    private String platform;
    private OffsetDateTime registeredAt;
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
    public UUID getStaffId() {
        return this.staffId;
    }

    @Generated
    public String getDeviceFingerprint() {
        return this.deviceFingerprint;
    }

    @Generated
    public String getPushToken() {
        return this.pushToken;
    }

    public String getPlatform() {
        return this.platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    @Generated
    public OffsetDateTime getRegisteredAt() {
        return this.registeredAt;
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
    public void setStaffId(UUID staffId) {
        this.staffId = staffId;
    }

    @Generated
    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    @Generated
    public void setPushToken(String pushToken) {
        this.pushToken = pushToken;
    }

    @Generated
    public void setRegisteredAt(OffsetDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    @Generated
    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    @Generated
    public StaffDevice() {
    }
}
