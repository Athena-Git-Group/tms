/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.dao.EmptyResultDataAccessException
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.stereotype.Service
 *  org.springframework.transaction.annotation.Transactional
 *  tools.jackson.core.JacksonException
 *  tools.jackson.databind.ObjectMapper
 */
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
    /**
     * 中斷與完成共用的 SET 片段：把目前段落（segment_started_at 到傳入時刻）的秒數
     * 併進 worked_sec，並清空段落起點。佔一個參數位（段落結束時刻）。
     */
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
            throw new UnprocessableEntityException("\u4efb\u52d9\u8d77\u9ede\u72c0\u614b\u53ea\u80fd\u662f triage/pool/accepted\uff0c\u6536\u5230: " + command.initialState());
        }
        if ("guest".equals(command.source()) && command.reservationId() == null) {
            throw new UnprocessableEntityException("guest \u4f86\u6e90\u4efb\u52d9\u5fc5\u9808\u7d81\u5b9a\u8a02\u55ae\uff08reservation_id\uff09");
        }
        if ("cleaning".equals(command.type()) && command.workDate() == null) {
            throw new UnprocessableEntityException("\u6253\u6383\u4efb\u52d9\u5fc5\u586b work_date\uff08E-09 \u4e00\u623f\u4e00\u5929\u7d04\u675f\u7684\u4f9d\u64da\uff09");
        }
        assigned = command.assigneeId() != null;
        if (assigned != "accepted".equals(command.initialState())) {
            throw new UnprocessableEntityException("accepted \u8d77\u9ede\uff08\u6392\u623f\u767c\u4f48\uff09\u5fc5\u9808\u540c\u6642\u5e36 assignee\uff1btriage/pool \u8d77\u9ede\u4e0d\u5f97\u5e36");
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
        task = (Task)this.taskRepository.saveAndFlush(task);
        this.recordEvent(command.tenantId(), task.getId(), 1, "created", command.actor(), this.createdSnapshot(command));
        if (assigned) {
            this.recordEvent(command.tenantId(), task.getId(), 2, "assigned", command.actor(), TaskEngine.payload("assignee_id", command.assigneeId()));
        }
        return task;
    }

    @Transactional
    public int assignFromTriage(long tenantId, UUID taskId, String dept, Actor actor) {
        int seq = this.guard("\u6307\u6d3e\u90e8\u9580", "UPDATE tasks\n   SET state = 'pool', dept = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'triage'\nRETURNING event_seq\n", dept, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "triaged", actor, TaskEngine.payload("dept", dept));
    }

    @Transactional
    public int answer(long tenantId, UUID taskId, String answer, Actor actor) {
        int seq = this.guard("\u56de\u8986", "UPDATE tasks\n   SET state = 'done', note = ?, done_at = now(), event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'triage'\nRETURNING event_seq\n", answer, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "answered", actor, TaskEngine.payload("answer", answer));
    }

    @Transactional
    public int claim(long tenantId, UUID taskId, String claimerDept, Actor claimer) {
        if (claimer.id() == null) {
            throw new UnprocessableEntityException("\u8a8d\u9818\u8005\u5fc5\u9808\u662f\u5177\u540d\u54e1\u5de5");
        }
        int seq = this.guard("\u8a8d\u9818", "UPDATE tasks\n   SET state = 'accepted', assignee_id = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'pool' AND dept = ?\nRETURNING event_seq\n", claimer.id(), tenantId, taskId, claimerDept);
        return this.recordEvent(tenantId, taskId, seq, "claimed", claimer, TaskEngine.payload("assignee_id", claimer.id()));
    }

    /**
     * \u958b\u5de5\uff1a\u5f85\u958b\u59cb\uff08accepted\uff09\u8207\u672c\u4eba\u5ef6\u5f8c\uff08deferred \u4e14 deferred_by_self\uff09\u7684\u4efb\u52d9\u90fd\u80fd\u9032\u5165
     * \u9032\u884c\u4e2d\uff08doing\uff09\u3002started_at \u53ea\u8a18\u7b2c\u4e00\u6b21\u958b\u5de5\uff1b\u6bcf\u6b21\u9032 doing \u90fd\u91cd\u958b\u4e00\u6bb5
     * segment_started_at\uff1b\u96e2\u958b deferred \u6642\u6e05\u7a7a hold_reason\u3002
     * \u6642\u523b\u7528\u6ce8\u5165\u7684 {@link Clock}\uff08\u6e2c\u8a66\u4ee5\u53ef\u8abf\u6642\u9418\u8986\u84cb\uff09\uff0c\u4e0d\u7528\u8cc7\u6599\u5eab now()\u3002
     */
    @Transactional
    public int start(long tenantId, UUID taskId, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("\u958b\u59cb\u4efb\u52d9\u5fc5\u9808\u662f\u5177\u540d\u54e1\u5de5");
        }
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        // \u514d\u67e5\u8207\u5426\u5728\u300c\u958b\u5de5\u7576\u4e0b\u300d\u4f9d\u79df\u6236\u958b\u95dc\u9396\u5b9a\u5230 tasks.inspection_waived\uff1a\u4e4b\u5f8c\u4e3b\u7ba1\u6539\u8a2d\u5b9a
        // \u4e0d\u5f71\u97ff\u9019\u5f35\u55ae\uff08\u53ea\u6709\u6253\u6383\u4efb\u52d9\u6709\u67e5\u623f\u6982\u5ff5\uff0c\u5176\u4ed6\u7a2e\u985e\u4e00\u5f8b false\uff09\u3002
        boolean waiverEnabled = "true".equals(this.tenantConfigText(tenantId, "inspection_waived_enabled"));
        int seq = this.guard("\u958b\u59cb", "UPDATE tasks\n   SET state = 'doing', started_at = COALESCE(started_at, ?), segment_started_at = ?,\n       inspection_waived = (type = 'cleaning' AND ?),\n       hold_reason = NULL, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ?\n   AND (state = 'accepted' OR (state = 'deferred' AND deferred_by_self)) AND assignee_id = ?\nRETURNING event_seq\n", now, now, waiverEnabled, tenantId, taskId, actor.id());
        return this.recordEvent(tenantId, taskId, seq, "started", actor, null);
    }

    /** \u672c\u4eba\u6062\u5fa9\uff1ainterrupted\u2192doing\uff0c\u91cd\u958b\u4e00\u6bb5 segment_started_at \u4e26\u6e05\u7a7a\u505c\u4f4f\u539f\u56e0\u3002 */
    @Transactional
    public int resume(long tenantId, UUID taskId, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("\u6062\u5fa9\u4efb\u52d9\u5fc5\u9808\u662f\u5177\u540d\u54e1\u5de5");
        }
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        int seq = this.guard("\u6062\u5fa9", "UPDATE tasks\n   SET state = 'doing', segment_started_at = ?, hold_reason = NULL,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'interrupted' AND assignee_id = ?\nRETURNING event_seq\n", now, tenantId, taskId, actor.id());
        return this.recordEvent(tenantId, taskId, seq, "resumed", actor, null);
    }

    /**
     * \u672c\u4eba\u5b8c\u6210\uff1adoing\u2192done\u3002\u628a\u6700\u5f8c\u9019\u6bb5\uff08segment_started_at \u5230\u73fe\u5728\uff09\u7684\u79d2\u6578\u4f75\u9032
     * worked_sec \u4e26\u6e05\u7a7a\u6bb5\u843d\u8d77\u9ede\uff0c\u5be6\u969b\u5de5\u6642\u5c31\u7b49\u65bc\u5404\u6bb5 doing \u6642\u9593\u7684\u7e3d\u548c\u3001\u4e0d\u542b\u4e2d\u65b7\u671f\u9593\u3002
     * \u6253\u6383\u4efb\u52d9\u9084\u6c92\u52fe\u4e5f\u6c92\u8df3\u904e\u7684 SOP \u9805\u4e00\u5f8b\u8a18\u70ba\u8df3\u904e\uff08\u4e0d\u64cb\u5b8c\u6210\u4f46\u7559\u75d5\uff09\uff0c
     * \u4e26\u628a\u8df3\u904e\u9805\u7684\u540d\u7a31\u5beb\u9032 completed \u4e8b\u4ef6\u7684 payload\u3002
     */
    @Transactional
    public int complete(long tenantId, UUID taskId, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("\u5b8c\u6210\u4efb\u52d9\u5fc5\u9808\u662f\u5177\u540d\u54e1\u5de5");
        }
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        // \u958b\u5de5\u6642\u9396\u5b9a\u514d\u67e5\uff08inspection_waived\uff09\u7684\u6253\u6383\u4efb\u52d9\u76f4\u63a5\u514d\u67e5\u7d50\u6848\uff1a\u4e0d\u7d93\u300c\u5f85\u67e5\u623f\u300d\uff08done\uff09\uff0c
        // \u4e00\u6b65\u843d\u5728 inspected\u3001\u67e5\u623f\u7d50\u679c\u8a18 waived\uff1b\u5176\u4ed6\u4efb\u52d9\u7dad\u6301 done \u7b49\u67e5\u623f\u6216\u9001\u9054\u3002
        int seq = this.guard("\u5b8c\u6210", "UPDATE tasks\n   SET state = CASE WHEN type = 'cleaning' AND inspection_waived THEN 'inspected' ELSE 'done' END,\n       inspection = CASE WHEN type = 'cleaning' AND inspection_waived THEN 'waived' ELSE inspection END,\n       " + CLOSE_SEGMENT_SET + ", done_at = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'doing' AND assignee_id = ?\nRETURNING event_seq\n", now, now, tenantId, taskId, actor.id());
        List<String> skippedLabels = this.markUncheckedSopAsSkipped(tenantId, taskId, now);
        Map<String, Object> payload = skippedLabels == null ? null : TaskEngine.payload("skipped_items", skippedLabels);
        int completedSeq = this.recordEvent(tenantId, taskId, seq, "completed", actor, payload);
        if (this.isWaivedClosed(tenantId, taskId)) {
            this.writebackRoomStatusIfEnabled(tenantId, taskId, actor);
        }
        return completedSeq;
    }

    /** \u9019\u5f35\u55ae\u662f\u5426\u525b\u4ee5\u514d\u67e5\u7d50\u6848\u6536\u5c3e\uff08state=inspected \u4e14 inspection=waived\uff09\u3002 */
    private boolean isWaivedClosed(long tenantId, UUID taskId) {
        Map<String, Object> row = this.jdbcTemplate.queryForMap(
                "SELECT state, inspection FROM tasks WHERE tenant_id = ? AND id = ?", tenantId, taskId);
        return "inspected".equals(row.get("state")) && "waived".equals(row.get("inspection"));
    }

    /**
     * \u5b8c\u6210\u6253\u6383\u4efb\u52d9\u6642\uff0c\u628a\u9084\u6c92\u52fe\u4e5f\u6c92\u8df3\u904e\u7684 SOP \u9805\u88dc\u8a18\u70ba\u8df3\u904e\uff08task_sop_checks \u6295\u5f71\uff0c
     * \u4e0d\u8a18 checked_by\u2014\u2014\u8df3\u904e\u662f\u5b8c\u6210\u52d5\u4f5c\u7684\u63a8\u5b9a\u7d50\u679c\uff0c\u4e0d\u662f\u8ab0\u52fe\u7684\uff09\uff0c\u4e26\u56de\u50b3\u5b8c\u6210\u7576\u4e0b
     * \u6240\u6709\u8df3\u904e\u9805\u7684\u540d\u7a31\u4f9b completed \u4e8b\u4ef6\u7559\u75d5\u3002\u975e\u6253\u6383\u4efb\u52d9\u6c92\u6709 SOP \u6e05\u55ae\uff0c\u56de\u50b3 null\u3002
     */
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
        int seq = this.guard("\u67e5\u623f\u901a\u904e", "UPDATE tasks\n   SET state = 'inspected', inspection = 'passed', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'done'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "inspection_passed", actor, null);
    }

    /**
     * \u5408\u683c\u7d50\u6848\uff08\u514d\u67e5\u7d50\u6848\u6216\u67e5\u623f\u901a\u904e\uff09\u5f8c\u628a\u623f\u6cc1\u56de\u5beb PMS\uff1a\u5beb\u4e00\u7b46 outbox kind=pms_status
     * \uff08payload \u5f62\u72c0\u5c0d\u61c9 contracts shared.yaml \u7684 PmsRoomStatusWriteback\uff1aroomNo\uff0bstatus\uff0c
     * \u514d\u67e5\u7d50\u6848\u8207\u67e5\u623f\u901a\u904e\u90fd\u56de\u5beb inspected\uff08\u5df2\u6574\u7406\uff09\uff0c\u4e0d\u5206\u4f4f\u5ba2\u623f\u8207\u53ef\u552e\uff09\uff0c\u4e26\u5728\u4efb\u52d9\u4e8b\u4ef6
     * \u88dc\u4e00\u7b46 pms_writeback \u7559\u75d5\u3002\u300c\u56de\u5beb\u623f\u614b\u300d\u958b\u95dc\uff08pms.writeback.room_status\uff09\u95dc\u9589\u3001\u6216
     * PMS \u70ba\u624b\u52d5\u532f\u5165\u6a21\u5f0f\uff08pms.provider=manual\uff0c\u6c92\u6709\u56de\u5beb\u5c0d\u8c61\uff09\u6642\u7167\u6a23\u5408\u683c\u7d50\u6848\u4f46\u4e0d\u56de\u5beb\u3002
     * \u51aa\u7b49\u9375\u7d81\u300c\u4efb\u52d9\uff0b\u4e8b\u4ef6\u5e8f\u865f\u300d\uff0c\u540c\u4e00\u6b21\u7d50\u6848\u91cd\u653e\u4e0d\u6703\u5beb\u51fa\u7b2c\u4e8c\u7b46\u3002
     */
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

    /** \u8b80\u79df\u6236\u8a2d\u5b9a\u7684\u6587\u5b57\u503c\uff08jsonb \u4efb\u610f\u578b\u5225\u4e00\u5f8b\u4ee5\u6587\u5b57\u5448\u73fe\uff09\uff0c\u6c92\u6709\u9019\u500b\u9375\u6642\u56de null\u3002 */
    private String tenantConfigText(long tenantId, String key) {
        List<String> values = this.jdbcTemplate.queryForList(
                "SELECT value #>> '{}' FROM tenant_config WHERE tenant_id = ? AND key = ?", String.class, tenantId, key);
        return values.isEmpty() ? null : values.get(0);
    }

    @Transactional
    public int failInspection(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("\u67e5\u623f\u9000\u56de", "UPDATE tasks\n   SET state = 'accepted', started_at = NULL, done_at = NULL,\n       redo_count = redo_count + 1, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'done'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "inspection_failed", actor, TaskEngine.payload("note", note));
    }

    /**
     * 抽查退回：主管抽查已結案的打掃任務、任一項不合格時，把任務退回原負責人重做。
     * 和 {@link #failInspection} 的差別在起點與重做內容：一般查房退回的起點是 done
     * （還沒結案），抽查退回的起點是 inspected（已結案），而且重做要從頭來——已做
     * 時間直接歸零（worked_sec=0、清空進行中段落的起點），不是只退狀態。前提條件
     * 直接寫在 UPDATE 的 WHERE 裡，任務不在 inspected 時一列也不改並回 409，
     * 同一張單重送抽查退回不會重複加 redo_count。負責人（assignee_id）不動，
     * 原清房者接手重做。
     */
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
        int seq = this.guard("\u9000\u56de\u5f85\u63a5\u6c60", "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'accepted'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, TaskEngine.payload("note", note));
    }

    /**
     * \u672c\u4eba\u4e2d\u65b7\uff1adoing\u2192interrupted\u3002\u628a\u76ee\u524d\u9019\u6bb5\uff08segment_started_at \u5230\u73fe\u5728\uff09\u7684\u79d2\u6578
     * \u4f75\u9032 worked_sec \u4e26\u6e05\u7a7a\u6bb5\u843d\u8d77\u9ede\uff0c\u5df2\u505a\u6642\u9593\u5c31\u505c\u5728\u4e2d\u65b7\u7576\u4e0b\uff1b\u505c\u4f4f\u539f\u56e0\u8a18\u9032
     * hold_reason\u3002\u6642\u523b\u7528\u6ce8\u5165\u7684 {@link Clock}\uff0c\u548c\u5df2\u505a\u6642\u9593\u7684\u8b80\u53d6\u5171\u7528\u540c\u4e00\u500b\u6642\u9593\u4f86\u6e90\u3002
     */
    @Transactional
    public int interrupt(long tenantId, UUID taskId, String reason, Actor actor) {
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        int seq = this.guard("\u4e2d\u65b7", "UPDATE tasks\n   SET state = 'interrupted',\n       " + CLOSE_SEGMENT_SET + ", hold_reason = ?,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'doing'\nRETURNING event_seq\n", now, reason, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "interrupted", actor, TaskEngine.payload("reason", reason, "by_self", true));
    }

    /**
     * \u4e3b\u7ba1\u9644\u4ea4\u4ee3\u9000\u6c60\uff08contracts tasksRequeue\uff09\uff1ainterrupted\u2192pool\u3002\u4ea4\u4ee3\u5beb\u9032 requeue_note
     * \u8b93\u63a5\u624b\u7684\u4eba\u770b\u5f97\u5230\u9032\u5ea6\uff1bpriority_notify \u6a19\u6210 true\u3001pool_source \u8a18 manager_requeue\uff0c
     * \u5f85\u63a5\u6c60\u624d\u80fd\u628a\u9019\u5f35\u55ae\u6392\u5230\u524d\u9762\u4e26\u6a19\u793a\u4f86\u6e90\uff1b\u505c\u4f4f\u539f\u56e0\u5df2\u7d93\u8f49\u8ff0\u5728\u4ea4\u4ee3\u88e1\uff0c\u4e00\u4f75\u6e05\u7a7a\u3002
     */
    @Transactional
    public int requeueInterrupted(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("\u91cd\u65b0\u6392\u5165\u5f85\u63a5\u6c60", "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, pool_source = 'manager_requeue',\n       priority_notify = true, requeue_note = ?, hold_reason = NULL,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'interrupted'\nRETURNING event_seq\n", note, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, TaskEngine.payload("note", note));
    }

    /**
     * \u628a\u5f85\u63a5\u6c60\u7684\u4efb\u52d9\u5ef6\u5f8c\u3002\u5177\u540d\u54e1\u5de5\u7576\u547c\u53eb\u8005\u6642\u4efb\u52d9\u6703\u639b\u5230\u4ed6\u540d\u4e0b\uff0c\u4e4b\u5f8c\u672c\u4eba\u958b\u5de5
     * \u7684\u8ca0\u8cac\u4eba\u6838\u5c0d\u624d\u904e\u5f97\u4e86\uff1b\u7cfb\u7d71\u7576\u547c\u53eb\u8005\u6642\u4efb\u52d9\u7dad\u6301\u6c92\u6709\u8ca0\u8cac\u4eba\u3002
     */
    @Transactional
    public int defer(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard("\u66ab\u7de9", "UPDATE tasks\n   SET state = 'deferred', assignee_id = COALESCE(assignee_id, ?), event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'pool'\nRETURNING event_seq\n", actor.id(), tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "deferred", actor, null);
    }

    /**
     * \u8ca0\u8cac\u4eba\u5ef6\u5f8c\u81ea\u5df1\u540d\u4e0b\u9084\u6c92\u958b\u5de5\u7684\u4efb\u52d9\u3002\u4efb\u52d9\u4fdd\u7559\u8ca0\u8cac\u4eba\u4e26\u8a18\u4e0b\u539f\u56e0\uff0cdeferred_by_self
     * \u6a19\u6210 true\u3002\u5c11\u4e86\u9019\u500b\u6a19\u8a18\uff0c\u73ed\u672b\u9000\u6c60\u8207\u4e3b\u7ba1\u5ef6\u5f8c\u7684\u9694\u65e5\u56de\u6c60\u6703\u628a\u5169\u7a2e\u5ef6\u5f8c\u6df7\u5728\u4e00\u8d77\u8655\u7406\uff0c
     * \u672c\u4eba\u5ef6\u5f8c\u7684\u55ae\u5c31\u6703\u88ab\u932f\u8aa4\u5730\u9001\u56de\u5f85\u63a5\u6c60\u3002
     */
    @Transactional
    public int deferBySelf(long tenantId, UUID taskId, String reason, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("\u672c\u4eba\u5ef6\u5f8c\u5fc5\u9808\u662f\u5177\u540d\u54e1\u5de5");
        }
        int seq = this.guard("\u5ef6\u5f8c", "UPDATE tasks\n   SET state = 'deferred', deferred_by_self = true, hold_reason = ?,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'accepted' AND assignee_id = ?\nRETURNING event_seq\n", reason, tenantId, taskId, actor.id());
        return this.recordEvent(tenantId, taskId, seq, "deferred", actor, TaskEngine.payload("reason", reason, "by_self", true));
    }

    @Transactional
    public int undefer(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard("\u89e3\u9664\u66ab\u7de9", "UPDATE tasks\n   SET state = 'pool', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'deferred'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, null);
    }

    @Transactional
    public int cancelByGuest(long tenantId, UUID taskId) {
        int seq = this.guard("\u6536\u56de", "UPDATE tasks\n   SET state = 'cancelled', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state IN ('triage', 'pool')\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "cancelled", Actor.guest(), TaskEngine.payload("reason", "guest_withdrew"));
    }

    @Transactional
    public int cancel(long tenantId, UUID taskId, String reason, Actor actor) {
        if (reason == null || !TaskCancelReason.ALL.contains(reason)) {
            throw new UnprocessableEntityException("\u53d6\u6d88\u5fc5\u9644\u539f\u56e0 enum\uff08" + String.join((CharSequence)"/", TaskCancelReason.ALL) + "\uff09\uff0c\u6536\u5230: " + reason);
        }
        int seq = this.guard("\u53d6\u6d88", "UPDATE tasks\n   SET state = 'cancelled', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ?\n   AND state IN ('triage', 'pool', 'accepted', 'doing', 'deferred', 'interrupted')\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "cancelled", actor, TaskEngine.payload("reason", reason));
    }

    /**
     * 報修人在工務認領前撤回工務單：pool 直接轉 cancelled，取消原因固定記
     * staff_withdrew。前提條件直接寫在 UPDATE 的 WHERE 裡（state = 'pool'），
     * 條件不符就一列也不改並丟 409；已被認領的單若也能撤回，工務手上的工作
     * 會憑空消失。cancel_reason 與事件 payload 的 reason 同值，查任務列與查
     * 事件歷程得到同一個答案。
     */
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
            throw new UnprocessableEntityException("\u8a55\u5206\u5fc5\u9808\u662f 1\u20135 \u7684\u6574\u6578\uff0c\u6536\u5230: " + rating);
        }
        int seq = this.guard("\u8a55\u5206", "UPDATE tasks\n   SET rating = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state IN ('done', 'inspected') AND rating IS NULL\nRETURNING event_seq\n", rating, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "rated", Actor.guest(), TaskEngine.payload("rating", rating));
    }

    @Transactional
    public int sopCheck(long tenantId, UUID taskId, String itemKey, String label, boolean checked, boolean skipped, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("SOP \u52fe\u8a18\u5fc5\u9808\u662f\u5177\u540d\u54e1\u5de5");
        }
        int seq = this.guard("SOP \u52fe\u8a18", "UPDATE tasks\n   SET event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND assignee_id = ?\nRETURNING event_seq\n", tenantId, taskId, actor.id());
        // \u6295\u5f71\u5217\u8207\u4e8b\u4ef6\u540c\u4e00\u4ea4\u6613\u843d\u5730\uff1a\u540c\u4e00\u9805\u91cd\u52fe\u4ee5 (tenant_id, task_id, item_key) \u51aa\u7b49\u8986\u5beb\u6700\u65b0\u52fe\u8a18\u3002
        this.jdbcTemplate.update("INSERT INTO task_sop_checks (id, tenant_id, task_id, item_key, label, checked, skipped, checked_by, checked_at)\nVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)\nON CONFLICT (tenant_id, task_id, item_key)\nDO UPDATE SET label = EXCLUDED.label, checked = EXCLUDED.checked, skipped = EXCLUDED.skipped,\n              checked_by = EXCLUDED.checked_by, checked_at = EXCLUDED.checked_at\n", UuidV7.generate(), tenantId, taskId, itemKey, label, checked, skipped, actor.id(), OffsetDateTime.now(this.clock));
        return this.recordEvent(tenantId, taskId, seq, "sop_checked", actor, TaskEngine.payload("item_key", itemKey, "label", label, "checked", checked, "skipped", skipped));
    }

    private int guard(String operation, String sql, Object ... params) {
        try {
            Integer seq = (Integer)this.jdbcTemplate.queryForObject(sql, Integer.class, params);
            if (seq == null) {
                throw new IllegalStateException("RETURNING event_seq \u4e0d\u61c9\u70ba null");
            }
            return seq;
        }
        catch (EmptyResultDataAccessException zeroRows) {
            throw new StateConflictException("\u4efb\u52d9\u76ee\u524d\u72c0\u614b\u4e0d\u5141\u8a31\u300c" + operation + "\u300d");
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
        // 事件時間用注入的 Clock：工時斷言要拿 started/interrupted/resumed/completed
        // 的事件間隔重算各段 doing 時間，事件時間和 tasks 欄位必須同一個時間來源。
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
            throw new IllegalStateException("\u4e8b\u4ef6 payload \u5e8f\u5217\u5316\u5931\u6557", e);
        }
    }
}
