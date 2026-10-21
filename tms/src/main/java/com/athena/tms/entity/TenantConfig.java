package com.athena.tms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 租戶設定。DB 無獨立 PK，複合鍵 (tenant_id, key) 照 erm.dbml 原樣對映。
 * 鍵集封閉（DB CHECK chk_tenant_config_key）；inspection_items / sop_items
 * 兩鍵為 sop_items 表的門面，不種值（V2 seed 的設計決定）。
 */
@Entity
@Table(name = "tenant_config")
@IdClass(TenantConfig.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class TenantConfig {

    @Id
    private Long tenantId;

    @Id
    private String key;

    /** jsonb，結構依 key 而異；以原始 JSON 字串承載，解讀交給讀取端。 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String value;

    private OffsetDateTime updatedAt;

    /** → staff_accounts；NULL=系統初始化（seed 沒有操作者）。 */
    private UUID updatedBy;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private Long tenantId;
        private String key;
    }
}
