package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 推播地址簿：一列=一台裝置，多機合法。fingerprint 只做去重不做綁定。
 * 外鍵照全檔慣例以純量欄位承載（DB 複合外鍵 (tenant_id, staff_id) 保證一致性）。
 */
@Entity
@Table(name = "staff_devices")
@Getter
@Setter
@NoArgsConstructor
public class StaffDevice {

    @Id
    private UUID id;

    private Long tenantId;

    private UUID staffId;

    private String deviceFingerprint;

    /** APNS/FCM 地址；權限被關即空。 */
    private String pushToken;

    private OffsetDateTime registeredAt;

    /** 登出或推播服務回報失效時標記。 */
    private OffsetDateTime revokedAt;
}
