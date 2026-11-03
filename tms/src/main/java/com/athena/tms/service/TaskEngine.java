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
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
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
    private final JdbcTemplate jdbcTemplate;
    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public TaskEngine(JdbcTemplate jdbcTemplate, TaskRepository taskRepository, TaskEventRepository taskEventRepository, OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
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
        boolean bl = assigned = command.assigneeId() != null;
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

    @Transactional
    public int start(long tenantId, UUID taskId, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("\u958b\u59cb\u4efb\u52d9\u5fc5\u9808\u662f\u5177\u540d\u54e1\u5de5");
        }
        int seq = this.guard("\u958b\u59cb", "UPDATE tasks\n   SET state = 'doing', started_at = now(), event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'accepted' AND assignee_id = ?\nRETURNING event_seq\n", tenantId, taskId, actor.id());
        return this.recordEvent(tenantId, taskId, seq, "started", actor, null);
    }

    @Transactional
    public int complete(long tenantId, UUID taskId, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("\u5b8c\u6210\u4efb\u52d9\u5fc5\u9808\u662f\u5177\u540d\u54e1\u5de5");
        }
        int seq = this.guard("\u5b8c\u6210", "UPDATE tasks\n   SET state = 'done', done_at = now(), event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'doing' AND assignee_id = ?\nRETURNING event_seq\n", tenantId, taskId, actor.id());
        return this.recordEvent(tenantId, taskId, seq, "completed", actor, null);
    }

    @Transactional
    public int passInspection(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard("\u67e5\u623f\u901a\u904e", "UPDATE tasks\n   SET state = 'inspected', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'done'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "inspection_passed", actor, null);
    }

    @Transactional
    public int failInspection(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("\u67e5\u623f\u9000\u56de", "UPDATE tasks\n   SET state = 'accepted', started_at = NULL, done_at = NULL,\n       redo_count = redo_count + 1, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'done'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "inspection_failed", actor, TaskEngine.payload("note", note));
    }

    @Transactional
    public int returnToPool(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("\u9000\u56de\u5f85\u63a5\u6c60", "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'accepted'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, TaskEngine.payload("note", note));
    }

    @Transactional
    public int interrupt(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("\u4e2d\u65b7", "UPDATE tasks\n   SET state = 'interrupted', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'doing'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "interrupted", actor, TaskEngine.payload("note", note));
    }

    @Transactional
    public int requeueInterrupted(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("\u91cd\u65b0\u6392\u5165\u5f85\u63a5\u6c60", "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'interrupted'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, TaskEngine.payload("note", note));
    }

    @Transactional
    public int defer(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard("\u66ab\u7de9", "UPDATE tasks\n   SET state = 'deferred', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'pool'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "deferred", actor, null);
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
        event.setCreatedAt(OffsetDateTime.now());
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
