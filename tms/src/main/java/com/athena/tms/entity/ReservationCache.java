package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 訂房檔唯讀副本（真相在 PMS）。作廢=superseded_at 標上不刪列；
 * 當前訂單查詢一律 superseded_at IS NULL。同房住期重疊由 DB EXCLUDE 約束擋。
 */
@Entity
@Table(name = "reservations_cache")
@Getter
@Setter
@NoArgsConstructor
public class ReservationCache {

    @Id
    private UUID id;

    private Long tenantId;

    /** PMS 訂單編號；Excel 匯入列合成 excel:日期:房號。 */
    private String reservationRef;

    private UUID roomId;

    /** PII：去識別化範圍。 */
    private String guestName;

    /** 住客端語言。 */
    private String lang;

    /** jsonb：VIP/親子/寵物/紀念日——自動開準備工單依據。 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String attrs;

    private OffsetDateTime checkinAt;

    private OffsetDateTime checkoutAt;

    /** 值域=common.codes.ReservationSource。 */
    private String source;

    private OffsetDateTime syncedAt;

    private OffsetDateTime supersededAt;
}
