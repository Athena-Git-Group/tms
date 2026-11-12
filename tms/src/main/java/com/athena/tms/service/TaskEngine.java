package com.athena.tms.service;

import com.athena.tms.common.UuidV7;
import com.athena.tms.common.codes.TaskCancelReason;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.TaskEventRepository;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.entity.Task;
import com.athena.tms.entity.TaskEvent;
import com.athena.tms.service.OutboxWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class TaskEngine {
    private static final Set<String> CREATABLE_STATES = Set.of("triage", "pool", "accepted");
    private static final String CLOSE_SEGMENT_SET = "worked_sec = worked_sec + COALESCE(CAST(EXTRACT(EPOCH FROM (? - segment_started_at)) AS int), 0),\n       segment_started_at = NULL";
    private final JdbcTemplate jdbcTemplate;
    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TaskEngine(JdbcTemplate jdbcTemplate, TaskRepository taskRepository, TaskEventRepository taskEventRepository, OutboxWriter outboxWriter, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public Task create(TaskCreateCommand command) {
        boolean assigned;
        if (!CREATABLE_STATES.contains(command.initialState())) {
            throw new UnprocessableEntityException("任務起點狀態只能是 triage/pool/accepted，收到: " + command.initialState());
        }
        if ("guest".equals(command.source()) && command.reservationId() == null) {
            throw new UnprocessableEntityException("guest 來源任務必須綁定訂單（reservation_id）");
        }
        if ("cleaning".equals(command.type()) && command.workDate() == null) {
            throw new UnprocessableEntityException("打掃任務必填 work_date（E-09 一房一天約束的依據）");
        }
        assigned = command.assigneeId() != null;
        if (assigned != "accepted".equals(command.initialState())) {
            throw new UnprocessableEntityException("accepted 起點（排房發佈）必須同時帶 assignee；triage/pool 起點不得帶");
        }
        OffsetDateTime now = OffsetDateTime.now(this.clock);
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
        task.setUrgent(command.urgent());
        task.setEventSeq(assigned ? 2 : 1);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task = (Task)this.taskRepository.saveAndFlush(task);
        this.recordEvent(command.tenantId(), task.getId(), 1, "created", command.actor(), this.createdSnapshot(command));
        if (assigned) {
            this.recordEvent(command.tenantId(), task.getId(), 2, "assigned", command.actor(), TaskEngine.payload("assignee_id", command.assigneeId()));
        }
        return task;
    }

    @Transactional
    public int assignFromTriage(long tenantId, UUID taskId, String dept, Actor actor) {
        int seq = this.guard("指派部門", "UPDATE tasks\n   SET state = 'pool', dept = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'triage'\nRETURNING event_seq\n", dept, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "triaged", actor, TaskEngine.payload("dept", dept));
    }

    @Transactional
    public int answer(long tenantId, UUID taskId, String answer, Actor actor) {
        int seq = this.guard("回覆", "UPDATE tasks\n   SET state = 'done', note = ?, done_at = now(), event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'triage'\nRETURNING event_seq\n", answer, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "answered", actor, TaskEngine.payload("answer", answer));
    }

    @Transactional
    public int claim(long tenantId, UUID taskId, String claimerDept, Actor claimer) {
        if (claimer.id() == null) {
            throw new UnprocessableEntityException("認領者必須是具名員工");
        }
        int seq = this.guard("認領", "UPDATE tasks\n   SET state = 'accepted', assignee_id = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'pool' AND dept = ?\nRETURNING event_seq\n", claimer.id(), tenantId, taskId, claimerDept);
        return this.recordEvent(tenantId, taskId, seq, "claimed", claimer, TaskEngine.payload("assignee_id", claimer.id()));
    }

    @Transactional
    public int start(long tenantId, UUID taskId, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("開始任務必須是具名員工");
        }
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        boolean waiverEnabled = "true".equals(this.tenantConfigText(tenantId, "inspection_waived_enabled"));
        int seq = this.guard("開始", "UPDATE tasks\n   SET state = 'doing', started_at = COALESCE(started_at, ?), segment_started_at = ?,\n       inspection_waived = (type = 'cleaning' AND ?),\n       hold_reason = NULL, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ?\n   AND (state = 'accepted' OR (state = 'deferred' AND deferred_by_self)) AND assignee_id = ?\nRETURNING event_seq\n", now, now, waiverEnabled, tenantId, taskId, actor.id());
        return this.recordEvent(tenantId, taskId, seq, "started", actor, null);
    }

    @Transactional
    public int resume(long tenantId, UUID taskId, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("恢復任務必須是具名員工");
        }
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        int seq = this.guard("恢復", "UPDATE tasks\n   SET state = 'doing', segment_started_at = ?, hold_reason = NULL,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'interrupted' AND assignee_id = ?\nRETURNING event_seq\n", now, tenantId, taskId, actor.id());
        return this.recordEvent(tenantId, taskId, seq, "resumed", actor, null);
    }

    @Transactional
    public int complete(long tenantId, UUID taskId, Actor actor) {
        return this.complete(tenantId, taskId, actor, null, false);
    }

    @Transactional
    public int complete(long tenantId, UUID taskId, Actor actor, Map<String, Object> extraPayload, boolean closeRepair) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("完成任務必須是具名員工");
        }
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        int seq = this.guard("完成", "UPDATE tasks\n   SET state = CASE WHEN type = 'cleaning' AND inspection_waived THEN 'inspected'\n                    WHEN type = 'repair' AND ? THEN 'closed' ELSE 'done' END,\n       inspection = CASE WHEN type = 'cleaning' AND inspection_waived THEN 'waived' ELSE inspection END,\n       " + CLOSE_SEGMENT_SET + ", done_at = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'doing' AND assignee_id = ?\nRETURNING event_seq\n", closeRepair, now, now, tenantId, taskId, actor.id());
        List<String> skippedLabels = this.markUncheckedSopAsSkipped(tenantId, taskId, now);
        Map<String, Object> payload = skippedLabels == null ? null : TaskEngine.payload("skipped_items", skippedLabels);
        if (extraPayload != null && !extraPayload.isEmpty()) {
            payload = payload == null ? new LinkedHashMap<>() : payload;
            payload.putAll(extraPayload);
        }
        int completedSeq = this.recordEvent(tenantId, taskId, seq, "completed", actor, payload);
        if (this.isWaivedClosed(tenantId, taskId)) {
            this.writebackRoomStatusIfEnabled(tenantId, taskId, actor);
        }
        return completedSeq;
    }

    private boolean isWaivedClosed(long tenantId, UUID taskId) {
        Map<String, Object> row = this.jdbcTemplate.queryForMap(
                "SELECT state, inspection FROM tasks WHERE tenant_id = ? AND id = ?", tenantId, taskId);
        return "inspected".equals(row.get("state")) && "waived".equals(row.get("inspection"));
    }

    private List<String> markUncheckedSopAsSkipped(long tenantId, UUID taskId, OffsetDateTime now) {
        String type = (String)this.jdbcTemplate.queryForObject("SELECT type FROM tasks WHERE tenant_id = ? AND id = ?", String.class, tenantId, taskId);
        if (!"cleaning".equals(type)) {
            return null;
        }
        List<Map<String, Object>> missing = this.jdbcTemplate.queryForList("SELECT s.item_key, s.label\n  FROM sop_items s\n WHERE s.tenant_id = ? AND s.applies_to = 'cleaning' AND s.active\n   AND NOT EXISTS (SELECT 1 FROM task_sop_checks c\n                    WHERE c.tenant_id = s.tenant_id AND c.task_id = ? AND c.item_key = s.item_key)\n ORDER BY s.sort_order\n", tenantId, taskId);
        for (Map<String, Object> item : missing) {
            this.jdbcTemplate.update("INSERT INTO task_sop_checks (id, tenant_id, task_id, item_key, label, checked, skipped, checked_by, checked_at)\nVALUES (?, ?, ?, ?, ?, false, true, NULL, ?)\nON CONFLICT (tenant_id, task_id, item_key) DO NOTHING\n", UuidV7.generate(), tenantId, taskId, item.get("item_key"), item.get("label"), now);
        }
        return this.jdbcTemplate.queryForList("SELECT label FROM task_sop_checks WHERE tenant_id = ? AND task_id = ? AND skipped", String.class, tenantId, taskId);
    }

    @Transactional
    public int passInspection(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard("查房通過", "UPDATE tasks\n   SET state = 'inspected', inspection = 'passed', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'done'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "inspection_passed", actor, null);
    }

    @Transactional
    public void writebackRoomStatusIfEnabled(long tenantId, UUID taskId, Actor actor) {
        if ("false".equals(this.tenantConfigText(tenantId, "pms.writeback.room_status"))
                || "manual".equals(this.tenantConfigText(tenantId, "pms.provider"))) {
            return;
        }
        Map<String, Object> room = this.jdbcTemplate.queryForMap("SELECT r.id AS room_id, r.room_no\n  FROM tasks t\n  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id\n WHERE t.tenant_id = ? AND t.id = ?\n", tenantId, taskId);
        String roomNo = String.valueOf(room.get("room_no"));
        Integer seq = (Integer)this.jdbcTemplate.queryForObject("UPDATE tasks SET event_seq = event_seq + 1, updated_at = now() WHERE tenant_id = ? AND id = ? RETURNING event_seq", Integer.class, tenantId, taskId);
        String payload = this.toJson(TaskEngine.payload("roomNo", roomNo, "status", "inspected"));
        this.jdbcTemplate.update("INSERT INTO outbox (tenant_id, aggregate_key, kind, payload, idempotency_key, status, attempts, created_at)\nVALUES (?, ?, 'pms_status', ?::jsonb, ?, 'pending', 0, ?)\nON CONFLICT (tenant_id, kind, idempotency_key) DO NOTHING\n", tenantId, "room:" + tenantId + ":" + String.valueOf(room.get("room_id")), payload, "pms_status:task:" + String.valueOf(taskId) + ":seq:" + seq, OffsetDateTime.now(this.clock));
        this.recordEvent(tenantId, taskId, seq, "pms_writeback", actor, TaskEngine.payload("roomNo", roomNo, "status", "inspected"));
    }

    private String tenantConfigText(long tenantId, String key) {
        List<String> values = this.jdbcTemplate.queryForList(
                "SELECT value #>> '{}' FROM tenant_config WHERE tenant_id = ? AND key = ?", String.class, tenantId, key);
        return values.isEmpty() ? null : values.get(0);
    }

    @Transactional
    public int failInspection(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("查房退回", "UPDATE tasks\n   SET state = 'accepted', started_at = NULL, done_at = NULL,\n       redo_count = redo_count + 1, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'done'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "inspection_failed", actor, TaskEngine.payload("note", note));
    }

    @Transactional
    public int failSpotCheck(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("抽查退回", """
                UPDATE tasks
                   SET state = 'accepted', started_at = NULL, done_at = NULL,
                       worked_sec = 0, segment_started_at = NULL,
                       inspection = 'failed', redo_count = redo_count + 1,
                       event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'inspected'
                RETURNING event_seq
                """, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "inspection_failed", actor, TaskEngine.payload("note", note));
    }

    @Transactional
    public int returnToPool(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("退回待接池", "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, pool_source = 'inspection_return', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'accepted'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, TaskEngine.payload("note", note));
    }

    @Transactional
    public int interrupt(long tenantId, UUID taskId, String reason, Actor actor) {
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        int seq = this.guard("中斷", "UPDATE tasks\n   SET state = 'interrupted',\n       " + CLOSE_SEGMENT_SET + ", hold_reason = ?,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'doing'\nRETURNING event_seq\n", now, reason, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "interrupted", actor, TaskEngine.payload("reason", reason, "by_self", true));
    }

    @Transactional
    public int requeueToPool(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("重新排入待接池", "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, pool_source = 'manager_requeue',\n       priority_notify = true, requeue_note = ?, hold_reason = NULL,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state IN ('interrupted', 'accepted')\nRETURNING event_seq\n", note, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, TaskEngine.payload("note", note));
    }

    @Transactional
    public int defer(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard("暫緩", "UPDATE tasks\n   SET state = 'deferred', assignee_id = COALESCE(assignee_id, ?), event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'pool'\nRETURNING event_seq\n", actor.id(), tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "deferred", actor, null);
    }

    @Transactional
    public int deferBySelf(long tenantId, UUID taskId, String reason, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("本人延後必須是具名員工");
        }
        int seq = this.guard("延後", "UPDATE tasks\n   SET state = 'deferred', deferred_by_self = true, hold_reason = ?,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'accepted' AND assignee_id = ?\nRETURNING event_seq\n", reason, tenantId, taskId, actor.id());
        return this.recordEvent(tenantId, taskId, seq, "deferred", actor, TaskEngine.payload("reason", reason, "by_self", true));
    }

    @Transactional
    public int undefer(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard("解除暫緩", "UPDATE tasks\n   SET state = 'pool', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'deferred'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, null);
    }

    @Transactional
    public int reassign(long tenantId, UUID taskId, UUID newAssigneeId, Actor actor) {
        if (newAssigneeId == null) {
            throw new UnprocessableEntityException("改派必須指定新負責人（assigneeId）");
        }
        int seq = this.guard("改派", "UPDATE tasks\n   SET assignee_id = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'accepted'\nRETURNING event_seq\n", newAssigneeId, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "reassigned", actor, TaskEngine.payload("assignee_id", newAssigneeId));
    }

    @Transactional
    public int releaseToPool(long tenantId, UUID taskId, Actor actor) {
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        int seq = this.guard(
                "放回工務待認領",
                "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, pool_source = NULL,\n       "
                        + CLOSE_SEGMENT_SET
                        + ",\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state IN ('accepted', 'doing')\nRETURNING event_seq\n",
                now, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, null);
    }

    @Transactional
    public int returnToPoolForLeave(long tenantId, UUID taskId, Actor actor) {
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        int seq = this.guard("請假退池", "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, pool_source = 'leave_return',\n       priority_notify = true, " + CLOSE_SEGMENT_SET + ",\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ?\n   AND state IN ('accepted', 'doing', 'interrupted', 'deferred')\nRETURNING event_seq\n", now, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, TaskEngine.payload("reason", "leave_return"));
    }

    @Transactional
    public int cancelByGuest(long tenantId, UUID taskId) {
        int seq = this.guard("收回", "UPDATE tasks\n   SET state = 'cancelled', cancel_reason = 'guest_withdrew', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state IN ('triage', 'pool')\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "cancelled", Actor.guest(), TaskEngine.payload("reason", "guest_withdrew"));
    }

    @Transactional
    public int cancel(long tenantId, UUID taskId, String reason, Actor actor) {
        if (reason == null || !TaskCancelReason.ALL.contains(reason)) {
            throw new UnprocessableEntityException("取消必附原因 enum（" + String.join((CharSequence)"/", TaskCancelReason.ALL) + "），收到: " + reason);
        }
        int seq = this.guard("取消", "UPDATE tasks\n   SET state = 'cancelled', cancel_reason = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ?\n   AND state IN ('triage', 'pool', 'accepted', 'doing', 'deferred', 'interrupted')\nRETURNING event_seq\n", reason, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "cancelled", actor, TaskEngine.payload("reason", reason));
    }

    @Transactional
    public int withdrawFromPool(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard(
                "撤回",
                """
                UPDATE tasks
                   SET state = 'cancelled', cancel_reason = 'staff_withdrew', event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'pool'
                RETURNING event_seq
                """,
                tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "cancelled", actor, TaskEngine.payload("reason", "staff_withdrew"));
    }

    @Transactional
    public int rate(long tenantId, UUID taskId, int rating) {
        if (rating < 1 || rating > 5) {
            throw new UnprocessableEntityException("評分必須是 1–5 的整數，收到: " + rating);
        }
        int seq = this.guard("評分", "UPDATE tasks\n   SET rating = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state IN ('done', 'inspected') AND rating IS NULL\nRETURNING event_seq\n", rating, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "rated", Actor.guest(), TaskEngine.payload("rating", rating));
    }

    @Transactional
    public int sopCheck(long tenantId, UUID taskId, String itemKey, String label, boolean checked, boolean skipped, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("SOP 勾記必須是具名員工");
        }
        int seq = this.guard("SOP 勾記", "UPDATE tasks\n   SET event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND assignee_id = ?\nRETURNING event_seq\n", tenantId, taskId, actor.id());
        this.jdbcTemplate.update("INSERT INTO task_sop_checks (id, tenant_id, task_id, item_key, label, checked, skipped, checked_by, checked_at)\nVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)\nON CONFLICT (tenant_id, task_id, item_key)\nDO UPDATE SET label = EXCLUDED.label, checked = EXCLUDED.checked, skipped = EXCLUDED.skipped,\n              checked_by = EXCLUDED.checked_by, checked_at = EXCLUDED.checked_at\n", UuidV7.generate(), tenantId, taskId, itemKey, label, checked, skipped, actor.id(), OffsetDateTime.now(this.clock));
        return this.recordEvent(tenantId, taskId, seq, "sop_checked", actor, TaskEngine.payload("item_key", itemKey, "label", label, "checked", checked, "skipped", skipped));
    }

    @Transactional
    public int recordUrgentDowngraded(long tenantId, UUID taskId, String downgradeReason, Actor actor) {
        int seq = this.guard("急件降級", "UPDATE tasks\n   SET event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ?\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "urgent_downgraded", actor, TaskEngine.payload("downgradeReason", downgradeReason));
    }

    @Transactional
    public int verifyRepairTicket(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard(
                "驗收",
                "UPDATE tasks\n   SET state = 'closed', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND type = 'repair' AND state = 'done'\nRETURNING event_seq\n",
                tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "repair_verified", actor, null);
    }

    @Transactional
    public int failRepairVerification(long tenantId, UUID taskId, Actor actor, String reason, UUID assigneeId, boolean toPool) {
        String sql = toPool
                ? "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, pool_source = 'verification_return',\n       return_reason = '驗收不通過 · 原處理人已下班或請假', done_at = NULL,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND type = 'repair' AND state = 'done'\nRETURNING event_seq\n"
                : "UPDATE tasks\n   SET state = 'accepted', done_at = NULL, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND type = 'repair' AND state = 'done'\nRETURNING event_seq\n";
        int seq = this.guard("驗收不通過", sql, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "repair_verification_failed", actor,
                TaskEngine.payload("reason", reason, "destination", toPool ? "pool" : "assignee", "assigneeId", assigneeId));
    }

    private int guard(String operation, String sql, Object ... params) {
        try {
            Integer seq = (Integer)this.jdbcTemplate.queryForObject(sql, Integer.class, params);
            if (seq == null) {
                throw new IllegalStateException("RETURNING event_seq 不應為 null");
            }
            return seq;
        }
        catch (EmptyResultDataAccessException zeroRows) {
            throw new StateConflictException("任務目前狀態不允許「" + operation + "」");
        }
    }

    private int recordEvent(long tenantId, UUID taskId, int seq, String type, Actor actor, Map<String, Object> payload) {
        TaskEvent event = new TaskEvent();
        event.setTenantId(tenantId);
        event.setTaskId(taskId);
        event.setSeq(seq);
        event.setType(type);
        event.setActorRole(actor.role());
        event.setActorId(actor.id());
        event.setPayload(this.toJson(payload));
        event.setCreatedAt(OffsetDateTime.now(this.clock));
        event = (TaskEvent)this.taskEventRepository.save(event);
        this.outboxWriter.enqueueTaskEvent(event);
        return seq;
    }

    private Map<String, Object> createdSnapshot(TaskCreateCommand command) {
        return TaskEngine.payload("type", command.type(), "source", command.source(), "room_id", command.roomId(), "reservation_id", command.reservationId(), "dept", command.dept(), "state", command.initialState(), "title", command.title(), "note", command.note(), "est_min", command.estMin(), "plan_id", command.planId(), "work_date", command.workDate());
    }

    private static Map<String, Object> payload(Object ... keyValues) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] == null) continue;
            map.put((String)keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return this.objectMapper.writeValueAsString(payload);
        }
        catch (JacksonException e) {
            throw new IllegalStateException("事件 payload 序列化失敗", e);
        }
    }
}
