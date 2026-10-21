package com.athena.tms.entity;

import com.athena.tms.common.codes.OutboxStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 待送匣（表名 outbox）＝交易護欄：與狀態變更同交易落庫。
 * relay 依 (aggregate_key, id) partial index 輪詢、同鍵訊息依序送出。
 * id=bigint identity，是「主鍵一律 UUIDv7」慣例在資料模型文件明列的例外。
 */
@Entity
@Table(name = "outbox")
@Getter
@Setter
@NoArgsConstructor
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tenantId;

    /** Kafka 分區鍵：task_event/push 用 task:<uuid>；pms_status 用 room:<tenant_id>:<uuid>。 */
    private String aggregateKey;

    /** 值域=common.codes.OutboxKind。 */
    private String kind;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    /** 下游去重：重送不重做；(tenant, kind, idempotency_key) 唯一。 */
    private String idempotencyKey;

    /** 值域=common.codes.OutboxStatus（DB DEFAULT 'pending'）。 */
    private String status = OutboxStatus.PENDING;

    private int attempts = 0;

    /** 指數退避。 */
    private OffsetDateTime nextRetryAt;

    private OffsetDateTime createdAt;

    /** sent 保留 3 天後排程清除。 */
    private OffsetDateTime sentAt;
}
