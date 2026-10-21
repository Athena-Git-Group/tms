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

/** 弱網重送的冪等去重簿。PK=(tenant_id, op_id)；7 天後排程清。 */
@Entity
@Table(name = "processed_ops")
@IdClass(ProcessedOp.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class ProcessedOp {

    @Id
    private Long tenantId;

    /** 客戶端 Idempotency-Key（UUID 字串）。 */
    @Id
    private String opId;

    /** jsonb：狀態碼＋精簡回應，重送原樣回放。 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String resultRef;

    private OffsetDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private Long tenantId;
        private String opId;
    }
}
