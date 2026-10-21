package com.athena.tms.service;

import com.athena.tms.common.UuidV7;
import com.athena.tms.common.codes.TaskCancelReason;
import com.athena.tms.common.codes.TaskEventType;
import com.athena.tms.common.codes.TaskSource;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.common.codes.TaskType;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.entity.Task;
import com.athena.tms.entity.TaskEvent;
import com.athena.tms.dao.TaskEventRepository;
import com.athena.tms.dao.TaskRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;

/**
 * 任務狀態變更的唯一入口。所有業務模組一律經本類改任務狀態，不自己
 * UPDATE tasks。轉移合法性只在這一處定義，才守得住「事件流＝唯一真相」。
 *
 * <p>每一次轉移都把前提條件（目前狀態、操作者是誰等）直接寫進一句條件式
 * UPDATE 的 WHERE，同句用 RETURNING 拿到遞增後的 event_seq，而不是先
 * SELECT 判斷再 UPDATE。先查詢再更新的寫法，查詢與更新之間任務可能被
 * 另一個請求改掉：兩人同秒認領，兩邊都查到「還在 pool」就會雙贏；seq
 * 如果另外查 max(seq)+1，同一段間隙也會讓兩個事件搶到同一個號碼。把
 * 取號與條件判斷壓進同一句 SQL，原子性直接由 DB 的列鎖保證，程式層
 * 不必再發明鎖。
 *
 * <p>每次狀態變更在同一筆 Postgres 交易完成三件事：這句條件式 UPDATE、
 * append task_events、落 outbox。要嘛三者皆在、要嘛皆無，不會出現
 * 「狀態改了但事件掉了」或「事件上了 Kafka 但 DB 沒這回事」。
 *
 * <p>條件式 UPDATE 改動 0 列一律回 409 狀態衝突，不區分「單不存在」與
 * 「狀態不符」。要區分就得先 SELECT，又會重新打開查詢與更新之間可能被
 * 改掉的那段間隔。跨租戶或不存在的單該回 404，由 API 層先以
 * findByTenantIdAndId 判定再呼叫本類。
 */
@Service
public class TaskEngine {

    /** 狀態機合法起點（spec §5.1：選單→pool、自由文字→pool/triage、排房發佈→accepted）。 */
    private static final Set<String> CREATABLE_STATES =
            Set.of(TaskState.TRIAGE, TaskState.POOL, TaskState.ACCEPTED);

    private final JdbcTemplate jdbcTemplate;
    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public TaskEngine(JdbcTemplate jdbcTemplate,
                      TaskRepository taskRepository,
                      TaskEventRepository taskEventRepository,
                      OutboxWriter outboxWriter,
                      ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    /**
     * 建單＋created 事件。建立不是轉移、沒有條件式更新要過，但事件與 outbox 義務相同。
     * created 快照必填種類、來源、房號、訂單、標題、預估分鐘，並多帶 dept、
     * 起點狀態與計畫欄位，重放時才能重建整列而不只是部分欄位。排房發佈走
     * accepted 起點時補一筆 assigned 事件，spec §5.2 的事件範例就是這個形狀。
     */
    @Transactional
    public Task create(TaskCreateCommand command) {
        if (!CREATABLE_STATES.contains(command.initialState())) {
            throw new UnprocessableEntityException(
                    "任務起點狀態只能是 triage/pool/accepted，收到: " + command.initialState());
        }
        if (TaskSource.GUEST.equals(command.source()) && command.reservationId() == null) {
            throw new UnprocessableEntityException("guest 來源任務必須綁定訂單（reservation_id）");
        }
        if (TaskType.CLEANING.equals(command.type()) && command.workDate() == null) {
            throw new UnprocessableEntityException("打掃任務必填 work_date（E-09 一房一天約束的依據）");
        }
        boolean assigned = command.assigneeId() != null;
        if (assigned != TaskState.ACCEPTED.equals(command.initialState())) {
            throw new UnprocessableEntityException(
                    "accepted 起點（排房發佈）必須同時帶 assignee；triage/pool 起點不得帶");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Task task = new Task();
        task.setId(UuidV7.generate());
        task.setTenantId(command.tenantId());
        task.setType(command.type());
        task.setSource(command.source());
        task.setRoomId(command.roomId());
        task.setReservationId(command.reservationId());
        task.setDept(command.dept());
        task.setState(command.initialState());
        task.setAssigneeId(command.assigneeId());
        task.setTitle(command.title());
        task.setNote(command.note());
        task.setEstMin(command.estMin());
        task.setPlanId(command.planId());
        task.setWorkDate(command.workDate());
        task.setEventSeq(assigned ? 2 : 1);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        // saveAndFlush 而非 save：TaskEvent 是 identity 主鍵、save 即刻 INSERT，
        // tasks 的 INSERT 若還躺在 Hibernate 待 flush 佇列，fk_task_events_task 會先炸
        task = taskRepository.saveAndFlush(task);

        recordEvent(command.tenantId(), task.getId(), 1, TaskEventType.CREATED,
                command.actor(), createdSnapshot(command));
        if (assigned) {
            recordEvent(command.tenantId(), task.getId(), 2, TaskEventType.ASSIGNED,
                    command.actor(), payload("assignee_id", command.assigneeId()));
        }
        return task;
    }

    /** triage→pool：櫃檯判讀自由文字後指派部門。payload 帶 dept——它改了 tasks 欄位，重放要靠它。 */
    @Transactional
    public int assignFromTriage(long tenantId, UUID taskId, String dept, Actor actor) {
        int seq = guard("指派部門", """
                UPDATE tasks
                   SET state = 'pool', dept = ?, event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'triage'
                RETURNING event_seq
                """, dept, tenantId, taskId);
        return recordEvent(tenantId, taskId, seq, TaskEventType.TRIAGED, actor, payload("dept", dept));
    }

    /**
     * triage→done：櫃檯直接回覆住客、不派工（done_at＝事件時刻）。回覆同句寫進
     * tasks.note——spec 要求「回覆存任務 note、住客端可見」，住客查進度
     * 讀的是 tasks 這張表，回覆只進 answered 事件 payload 的話住客看不到。
     */
    @Transactional
    public int answer(long tenantId, UUID taskId, String answer, Actor actor) {
        int seq = guard("回覆", """
                UPDATE tasks
                   SET state = 'done', note = ?, done_at = now(), event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'triage'
                RETURNING event_seq
                """, answer, tenantId, taskId);
        return recordEvent(tenantId, taskId, seq, TaskEventType.ANSWERED, actor, payload("answer", answer));
    }

    /**
     * pool→accepted：認領。認領限同部門，部門（dept 欄位）相符與狀態檢查一起
     * 壓進同一句條件式 UPDATE：跨部門、已被認領都是 0 列回 409。兩執行緒同秒
     * 搶同單時，後到者在列鎖上排隊、鎖放開後條件重估為 0 列，恰一人成功不靠
     * 程式層互斥。
     */
    @Transactional
    public int claim(long tenantId, UUID taskId, String claimerDept, Actor claimer) {
        if (claimer.id() == null) {
            throw new UnprocessableEntityException("認領者必須是具名員工");
        }
        int seq = guard("認領", """
                UPDATE tasks
                   SET state = 'accepted', assignee_id = ?, event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'pool' AND dept = ?
                RETURNING event_seq
                """, claimer.id(), tenantId, taskId, claimerDept);
        return recordEvent(tenantId, taskId, seq, TaskEventType.CLAIMED, claimer,
                payload("assignee_id", claimer.id()));
    }

    /**
     * accepted→doing：開工（started_at＝事件時刻）。兩段回報只有認領者本人
     * 能按，assignee_id＝操作者壓進同一句條件式 UPDATE，別人按一律 0 列回 409。
     * 部門相符不另驗，認領人歸屬已涵蓋，因為認領時那句條件式更新就擋過了。
     */
    @Transactional
    public int start(long tenantId, UUID taskId, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("開始任務必須是具名員工");
        }
        int seq = guard("開始", """
                UPDATE tasks
                   SET state = 'doing', started_at = now(), event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'accepted' AND assignee_id = ?
                RETURNING event_seq
                """, tenantId, taskId, actor.id());
        return recordEvent(tenantId, taskId, seq, TaskEventType.STARTED, actor, null);
    }

    /**
     * doing→done：完成。實際分鐘＝done_at−started_at（derived，不落欄）。
     * 與 {@link #start} 同款條件式更新：限認領者本人，非本人 0 列回 409。
     */
    @Transactional
    public int complete(long tenantId, UUID taskId, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("完成任務必須是具名員工");
        }
        int seq = guard("完成", """
                UPDATE tasks
                   SET state = 'done', done_at = now(), event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'doing' AND assignee_id = ?
                RETURNING event_seq
                """, tenantId, taskId, actor.id());
        return recordEvent(tenantId, taskId, seq, TaskEventType.COMPLETED, actor, null);
    }

    /** done→inspected：查房全過（終點之一）。 */
    @Transactional
    public int passInspection(long tenantId, UUID taskId, Actor actor) {
        int seq = guard("查房通過", """
                UPDATE tasks
                   SET state = 'inspected', event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'done'
                RETURNING event_seq
                """, tenantId, taskId);
        return recordEvent(tenantId, taskId, seq, TaskEventType.INSPECTION_PASSED, actor, null);
    }

    /** done→accepted：查房退回原清房者重做。started_at/done_at 清空重計（工時統計只認重做那輪）。 */
    @Transactional
    public int failInspection(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = guard("查房退回", """
                UPDATE tasks
                   SET state = 'accepted', started_at = NULL, done_at = NULL,
                       redo_count = redo_count + 1, event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'done'
                RETURNING event_seq
                """, tenantId, taskId);
        return recordEvent(tenantId, taskId, seq, TaskEventType.INSPECTION_FAILED, actor,
                payload("note", note));
    }

    /** accepted→pool：退回待接池（請假自動退、班末掃描、查房退回連動），assignee 清空。 */
    @Transactional
    public int returnToPool(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = guard("退回待接池", """
                UPDATE tasks
                   SET state = 'pool', assignee_id = NULL, event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'accepted'
                RETURNING event_seq
                """, tenantId, taskId);
        return recordEvent(tenantId, taskId, seq, TaskEventType.RETURNED_TO_POOL, actor,
                payload("note", note));
    }

    /** doing→interrupted：請假中斷。assignee 保留——去向（續做或 requeue）是主管之後的決定。 */
    @Transactional
    public int interrupt(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = guard("中斷", """
                UPDATE tasks
                   SET state = 'interrupted', event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'doing'
                RETURNING event_seq
                """, tenantId, taskId);
        return recordEvent(tenantId, taskId, seq, TaskEventType.INTERRUPTED, actor, payload("note", note));
    }

    /** interrupted→pool：主管 requeue，交代進事件 payload（接手的人只有這條線索）。 */
    @Transactional
    public int requeueInterrupted(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = guard("重新排入待接池", """
                UPDATE tasks
                   SET state = 'pool', assignee_id = NULL, event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'interrupted'
                RETURNING event_seq
                """, tenantId, taskId);
        return recordEvent(tenantId, taskId, seq, TaskEventType.RETURNED_TO_POOL, actor,
                payload("note", note));
    }

    /** pool→deferred：主管暫緩。 */
    @Transactional
    public int defer(long tenantId, UUID taskId, Actor actor) {
        int seq = guard("暫緩", """
                UPDATE tasks
                   SET state = 'deferred', event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'pool'
                RETURNING event_seq
                """, tenantId, taskId);
        return recordEvent(tenantId, taskId, seq, TaskEventType.DEFERRED, actor, null);
    }

    /** deferred→pool：解除暫緩（undefer／隔日自動）。事件沿用 returned_to_pool——18 值集合沒有 undeferred。 */
    @Transactional
    public int undefer(long tenantId, UUID taskId, Actor actor) {
        int seq = guard("解除暫緩", """
                UPDATE tasks
                   SET state = 'pool', event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'deferred'
                RETURNING event_seq
                """, tenantId, taskId);
        return recordEvent(tenantId, taskId, seq, TaskEventType.RETURNED_TO_POOL, actor, null);
    }

    /** triage|pool→cancelled：住客收回。收回只限還沒人認領；accepted 起已有人在動，條件式更新改動 0 列回 409。 */
    @Transactional
    public int cancelByGuest(long tenantId, UUID taskId) {
        int seq = guard("收回", """
                UPDATE tasks
                   SET state = 'cancelled', event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state IN ('triage', 'pool')
                RETURNING event_seq
                """, tenantId, taskId);
        return recordEvent(tenantId, taskId, seq, TaskEventType.CANCELLED, Actor.guest(),
                payload("reason", TaskCancelReason.GUEST_WITHDREW));
    }

    /**
     * 任何未完成態→cancelled：主管取消，必附原因 enum（spec §5.1）。
     * 未完成態＝九態扣掉 done/inspected/cancelled，與「一房一天僅一筆未完成
     * 打掃單」約束用同一套定義，才不會出現「統計當它完成、取消卻說它未完成」的分歧。
     */
    @Transactional
    public int cancel(long tenantId, UUID taskId, String reason, Actor actor) {
        if (reason == null || !TaskCancelReason.ALL.contains(reason)) {
            throw new UnprocessableEntityException(
                    "取消必附原因 enum（" + String.join("/", TaskCancelReason.ALL) + "），收到: " + reason);
        }
        int seq = guard("取消", """
                UPDATE tasks
                   SET state = 'cancelled', event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ?
                   AND state IN ('triage', 'pool', 'accepted', 'doing', 'deferred', 'interrupted')
                RETURNING event_seq
                """, tenantId, taskId);
        return recordEvent(tenantId, taskId, seq, TaskEventType.CANCELLED, actor,
                payload("reason", reason));
    }

    /**
     * 評分不改 state，但 tasks 欄位變更仍走同一套條件式更新＋事件＋outbox。
     * 否則住客模組就得自己 UPDATE tasks，唯一入口破功。這句條件式 UPDATE
     * 同時涵蓋評分規則：僅 done/inspected 後可評，rating IS NULL 擋重評，
     * 0 列回 409。
     */
    @Transactional
    public int rate(long tenantId, UUID taskId, int rating) {
        if (rating < 1 || rating > 5) {
            throw new UnprocessableEntityException("評分必須是 1–5 的整數，收到: " + rating);
        }
        int seq = guard("評分", """
                UPDATE tasks
                   SET rating = ?, event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state IN ('done', 'inspected') AND rating IS NULL
                RETURNING event_seq
                """, rating, tenantId, taskId);
        return recordEvent(tenantId, taskId, seq, TaskEventType.RATED, Actor.guest(),
                payload("rating", rating));
    }

    /**
     * SOP 勾記不改 state，但與評分同款理由走條件式更新取號＋事件＋outbox
     * 三件套：事件 seq 必須對 tasks.event_seq 原子取號（UPDATE…RETURNING），
     * 而 UPDATE tasks 只有本類有權做。員工模組把勾記寫進 task_sop_checks，
     * 那張表是從事件整理出來、方便查詢的目前狀態副本，權威的勾記歷史在這裡
     * 產生的 sop_checked 事件。這句條件式 UPDATE 不限狀態，因為 spec 未要求，
     * 且下架項目的歷史單仍可補記；但限認領者本人，assignee_id＝操作者壓進
     * 同一句條件式，非本人或單不存在都是 0 列回 409，404 照例由 API 層前置
     * 判定。
     */
    @Transactional
    public int sopCheck(long tenantId, UUID taskId, String itemKey, String label,
                        boolean checked, boolean skipped, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("SOP 勾記必須是具名員工");
        }
        int seq = guard("SOP 勾記", """
                UPDATE tasks
                   SET event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND assignee_id = ?
                RETURNING event_seq
                """, tenantId, taskId, actor.id());
        return recordEvent(tenantId, taskId, seq, TaskEventType.SOP_CHECKED, actor,
                payload("item_key", itemKey, "label", label, "checked", checked, "skipped", skipped));
    }

    /**
     * 條件式更新的執行器。UPDATE…RETURNING 走 executeQuery：有列＝轉移
     * 成功且同句拿到 seq；0 列＝這句條件判斷擋下更新。queryForObject 對
     * 0 列固定丟 EmptyResultDataAccessException，拿它當「被擋下」的訊號
     * 是契約不是 hack。
     */
    private int guard(String operation, String sql, Object... params) {
        try {
            Integer seq = jdbcTemplate.queryForObject(sql, Integer.class, params);
            if (seq == null) {
                throw new IllegalStateException("RETURNING event_seq 不應為 null");
            }
            return seq;
        } catch (EmptyResultDataAccessException zeroRows) {
            throw new StateConflictException("任務目前狀態不允許「" + operation + "」");
        }
    }

    /**
     * 同交易三件套的後兩件：append task_events＋落 outbox。對 task_events
     * 只 INSERT。事件列不可改不可刪，UPDATE/DELETE 由 DB 權限擋，程式面也不給入口。
     * TaskEvent/OutboxMessage 不 setId，bigint identity 主鍵一律由 DB 取號。
     */
    private int recordEvent(long tenantId, UUID taskId, int seq, String type,
                            Actor actor, Map<String, Object> payload) {
        TaskEvent event = new TaskEvent();
        event.setTenantId(tenantId);
        event.setTaskId(taskId);
        event.setSeq(seq);
        event.setType(type);
        event.setActorRole(actor.role());
        event.setActorId(actor.id());
        event.setPayload(toJson(payload));
        event.setCreatedAt(OffsetDateTime.now());
        event = taskEventRepository.save(event);
        outboxWriter.enqueueTaskEvent(event);
        return seq;
    }

    private Map<String, Object> createdSnapshot(TaskCreateCommand command) {
        return payload(
                "type", command.type(),
                "source", command.source(),
                "room_id", command.roomId(),
                "reservation_id", command.reservationId(),
                "dept", command.dept(),
                "state", command.initialState(),
                "title", command.title(),
                "note", command.note(),
                "est_min", command.estMin(),
                "plan_id", command.planId(),
                "work_date", command.workDate());
    }

    /** 偶數位鍵、奇數位值；null 值的鍵不落 payload——重放端把「缺鍵」讀成 NULL，少一種空值表示法。 */
    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                map.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return map;
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("事件 payload 序列化失敗", e);
        }
    }
}
