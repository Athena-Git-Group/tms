package com.athena.tms.entity;

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
import java.util.UUID;

/**
 * 事件流唯一真相，只進不改：tms_app 角色在 DB 層沒有 UPDATE/DELETE 權限，
 * 應用層對本 entity 只能新增——改既有列會在 DB 炸 42501，這是刻意的。
 * id=bigint identity，是「主鍵一律 UUIDv7」慣例的例外；它不當訂閱書籤用，增量一律走 Kafka。
 */
@Entity
@Table(name = "task_events")
@Getter
@Setter
@NoArgsConstructor
public class TaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tenantId;

    private UUID taskId;

    /** 這個任務的第幾步；取自 tasks.event_seq 的 RETURNING，(tenant, task, seq) 唯一。 */
    private int seq;

    /** 值域=common.codes.TaskEventType（18 值；DB 不設 CHECK，可擴充）。 */
    private String type;

    /** 值域=common.codes.ActorRole。 */
    private String actorRole;

    /** 住客與系統為空。 */
    private UUID actorId;

    /** jsonb：會改 tasks 欄位的事件必填帶齊（created 帶建立快照）。 */
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private OffsetDateTime createdAt;
}
