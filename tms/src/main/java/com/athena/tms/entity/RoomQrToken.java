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
 * 房間 QR 憑證。token 全域 UNIQUE（掃碼入口沒有租戶脈絡，token 本身是查找鍵）；
 * 一房同時僅一枚 active（partial unique）。輪替=同一交易舊 token→revoked＋
 * 新列 version+1＋該房全部未撤銷 guest_sessions 連坐撤銷；該業務邏輯在 RoomQrService。
 */
@Entity
@Table(name = "room_qr_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RoomQrToken {

    @Id
    private UUID id;

    private Long tenantId;

    private UUID roomId;

    /** ≥128 bit 隨機 base62 約 22 字元，不含房號資訊。 */
    private String token;

    /** 業務版本（重產 +1），不是 JPA 樂觀鎖欄位。 */
    private Integer version;

    /** 值域=common.codes.QrTokenStatus。 */
    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime revokedAt;
}
