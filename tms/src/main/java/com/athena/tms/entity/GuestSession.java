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
 * 住客工作階段。驗證=expires_at > now() AND revoked_at IS NULL，缺一即 401。
 * 綁簽發當下訂單（reservation_id）——換住客舊 session 自然失效。
 */
@Entity
@Table(name = "guest_sessions")
@Getter
@Setter
@NoArgsConstructor
public class GuestSession {

    @Id
    private UUID id;

    private Long tenantId;

    /** 住客瀏覽器憑證；全域 UNIQUE（住客請求沒有租戶脈絡）。 */
    private String sessionToken;

    private UUID roomId;

    private UUID reservationId;

    /** =退房時間。 */
    private OffsetDateTime expiresAt;

    /** QR 輪替/提前退房/訂單取消時整批標上。 */
    private OffsetDateTime revokedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime lastSeenAt;
}
