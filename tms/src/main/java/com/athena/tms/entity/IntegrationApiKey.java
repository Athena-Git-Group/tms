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
 * PMS 整合用的 per-租戶 M2M API key。只存 SHA-256 hash，明文僅開立當下出現一次，
 * 資料庫外洩不可反推明文。
 *
 * <p>生命週期只有兩態：active（revokedAt 為 NULL）→ revoked（落印 revokedAt），
 * 單向且不可逆。不硬刪，撤銷列永久保留當稽核軌跡；同租戶可同時有多把 active key，
 * 供輪替時新舊 key 重疊使用。
 *
 * <p>寫入限制：應用資料庫角色對本表只有 SELECT 與 revoked_at 單欄的 UPDATE 權限，
 * 整列 UPDATE 會被資料庫擋下。撤銷必須走只更新 revoked_at 的語句，不能用
 * repository 的 save 回寫整個 entity；開立（INSERT）由營運以管理者角色執行。
 */
@Entity
@Table(name = "integration_api_keys")
@Getter
@Setter
@NoArgsConstructor
public class IntegrationApiKey {

    @Id
    private UUID id;

    private Long tenantId;

    /** 營運命名；同租戶可重名（輪替重疊期新舊 key 必然同名）。 */
    private String name;

    /** 明文前 16 字，僅供營運肉眼對帳，不參與驗證。 */
    private String keyPrefix;

    /** SHA-256(明文全字串) 的 64 字 hex；全域 UNIQUE，是驗證時的唯一熱查詢鍵。 */
    private String keyHash;

    private OffsetDateTime createdAt;

    /** NULL = active；非空 = 已撤銷（單向、不可逆、不復活）。 */
    private OffsetDateTime revokedAt;
}
