package com.athena.tms.bdd.support;

import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.entity.Task;
import com.athena.tms.service.TaskEngine;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class TaskFixtures {

    private static final Set<String> SUPPORTED_SOURCES = Set.of("guest", "desk", "system", "staff");

    private final TaskEngine engine;
    private final JdbcClient jdbcClient;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final MutableClock clock;

    public TaskFixtures(
            TaskEngine engine,
            JdbcClient jdbcClient,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            MutableClock clock) {
        this.engine = engine;
        this.jdbcClient = jdbcClient;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.clock = clock;
    }

    public Builder builder() {
        return new Builder();
    }

    public UUID createPoolTask(String roomNo, String title, String typeCode, String deptCode, Integer estMin) {
        return builder().roomNo(roomNo).title(title).type(typeCode).dept(deptCode).estMin(estMin).state("pool").create();
    }

    public final class Builder {
        private String roomNo;
        private String title;
        private String type;
        private String dept;
        private Integer estMin;
        private String state;
        private UUID assigneeId;
        private UUID inspectorId;
        private boolean inspectionWaived = false;
        private Integer workedMinutes;
        private String holdReason;
        private int redoCount = 0;
        private String source = "desk";
        private UUID reservationId;
        private LocalDate workDate;
        private Instant doneAt;
        private String cancelReason = "manager_void";
        private String poolSource;
        private Boolean priorityNotify;
        private String returnReason;
        private String requeueNote;
        private String ruleHit;
        private String dueAtHHmm;
        private Boolean deferredBySelf;
        private String reminderStage;

        public Builder roomNo(String v) {
            this.roomNo = v;
            return this;
        }

        public Builder title(String v) {
            this.title = v;
            return this;
        }

        public Builder type(String v) {
            this.type = v;
            return this;
        }

        public Builder dept(String v) {
            this.dept = v;
            return this;
        }

        public Builder estMin(Integer v) {
            this.estMin = v;
            return this;
        }

        public Builder state(String v) {
            this.state = v;
            return this;
        }

        public Builder assignee(UUID v) {
            this.assigneeId = v;
            return this;
        }

        public Builder inspector(UUID v) {
            this.inspectorId = v;
            return this;
        }

        public Builder inspectionWaived(boolean v) {
            this.inspectionWaived = v;
            return this;
        }

        public Builder workedMinutes(Integer v) {
            this.workedMinutes = v;
            return this;
        }

        public Builder holdReason(String v) {
            this.holdReason = v;
            return this;
        }

        public Builder redoCount(int v) {
            this.redoCount = v;
            return this;
        }

        public Builder source(String v) {
            this.source = v;
            return this;
        }

        public Builder reservationId(UUID v) {
            this.reservationId = v;
            return this;
        }

        public Builder doneAt(Instant v) {
            this.doneAt = v;
            return this;
        }

        public Builder workDate(LocalDate v) {
            this.workDate = v;
            return this;
        }

        public Builder cancelReason(String v) {
            this.cancelReason = v;
            return this;
        }

        public Builder poolSource(String v) {
            this.poolSource = v;
            return this;
        }

        public Builder priorityNotify(boolean v) {
            this.priorityNotify = v;
            return this;
        }

        public Builder returnReason(String v) {
            this.returnReason = v;
            return this;
        }

        public Builder requeueNote(String v) {
            this.requeueNote = v;
            return this;
        }

        public Builder ruleHit(String v) {
            this.ruleHit = v;
            return this;
        }

        public Builder dueAtToday(String hhmm) {
            this.dueAtHHmm = hhmm;
            return this;
        }

        public Builder deferredBySelf(boolean v) {
            this.deferredBySelf = v;
            return this;
        }

        public Builder reminderStage(String stage) {
            this.reminderStage = stage;
            return this;
        }

        public UUID create() {
            Objects.requireNonNull(roomNo, "roomNo");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(dept, "dept");
            Objects.requireNonNull(state, "state");
            if (!SUPPORTED_SOURCES.contains(source)) {
                throw new IllegalArgumentException(
                        "tasks.source 只支援 guest|desk|system|staff，收到: " + source);
            }
            if (workedMinutes != null && !"doing".equals(state) && !"interrupted".equals(state)) {
                throw new IllegalArgumentException(
                        "workedMinutes 代表「已做分鐘」，只在 state=doing 或 interrupted 時有意義，收到 state="
                                + state);
            }
            if (doneAt != null && !"done".equals(state) && !"inspected".equals(state) && !"closed".equals(state)) {
                throw new IllegalArgumentException(
                        "doneAt 代表「完成時間」，只在 state=done、inspected 或 closed 時有意義，收到 state=" + state);
            }
            if (reminderStage != null && !"pool".equals(state)) {
                throw new IllegalArgumentException(
                        "tasks.reminder_stage 只在 state=pool 時有意義，收到 state=" + state);
            }

            long tenantId = tenantFixtures.testTenantId();
            UUID roomId = roomFixtures.roomId(roomNo);
            LocalDate effectiveWorkDate = workDate;
            if (effectiveWorkDate == null && "cleaning".equals(type)) {
                effectiveWorkDate = clock.instant().atZone(clock.getZone()).toLocalDate();
            }

            UUID taskId;
            TenantContext.set(tenantId);
            try {
                taskId = createAtState(tenantId, roomId, effectiveWorkDate).getId();
            } finally {
                TenantContext.clear();
            }

            if (redoCount > 0) {
                jdbcClient
                        .sql("UPDATE tasks SET redo_count = ? WHERE tenant_id = ? AND id = ?")
                        .params(redoCount, tenantId, taskId)
                        .update();
            }

            if (holdReason != null || poolSource != null || priorityNotify != null
                    || returnReason != null || requeueNote != null || ruleHit != null
                    || dueAtHHmm != null || deferredBySelf != null) {
                jdbcClient
                        .sql(
                                """
                                UPDATE tasks
                                   SET hold_reason = COALESCE(?, hold_reason),
                                       pool_source = COALESCE(?, pool_source),
                                       priority_notify = COALESCE(?, priority_notify),
                                       return_reason = COALESCE(?, return_reason),
                                       requeue_note = COALESCE(?, requeue_note),
                                       rule_hit = COALESCE(?, rule_hit),
                                       due_at = COALESCE(?, due_at),
                                       deferred_by_self = COALESCE(?, deferred_by_self)
                                 WHERE tenant_id = ? AND id = ?
                                """)
                        .params(
                                holdReason, poolSource, priorityNotify, returnReason, requeueNote, ruleHit,
                                dueAtHHmm == null ? null : todayAt(dueAtHHmm),
                                deferredBySelf, tenantId, taskId)
                        .update();
            }

            backdateTimeline(tenantId, taskId);
            applyWorkedSeconds(tenantId, taskId);
            if (reminderStage != null) {
                stageReminder(tenantId, taskId, reminderStage, assigneeId, dept, roomNo, title);
            }
            return taskId;
        }

        private void applyWorkedSeconds(long tenantId, UUID taskId) {
            if (workedMinutes == null) {
                return;
            }
            if ("doing".equals(state)) {
                Instant segmentStart = clock.instant().minusSeconds(workedMinutes * 60L);
                jdbcClient
                        .sql(
                                "UPDATE tasks SET worked_sec = 0, segment_started_at = ? WHERE tenant_id = ? AND id = ?")
                        .params(OffsetDateTime.ofInstant(segmentStart, clock.getZone()), tenantId, taskId)
                        .update();
            } else if ("interrupted".equals(state)) {
                jdbcClient
                        .sql(
                                "UPDATE tasks SET worked_sec = ?, segment_started_at = NULL WHERE tenant_id = ? AND id = ?")
                        .params(workedMinutes * 60, tenantId, taskId)
                        .update();
            }
        }

        private OffsetDateTime todayAt(String hhmm) {
            LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
            return ZonedDateTime.of(today, LocalTime.parse(hhmm), clock.getZone()).toOffsetDateTime();
        }

        private Task createAtState(long tenantId, UUID roomId, LocalDate workDate) {
            Actor systemActor = Actor.system();
            switch (state) {
                case "triage":
                    return poolOrTriageCreate(tenantId, roomId, workDate, "triage", systemActor);
                case "pool":
                    return poolOrTriageCreate(tenantId, roomId, workDate, "pool", systemActor);
                case "accepted": {
                    Task task = poolOrTriageCreate(tenantId, roomId, workDate, "pool", systemActor);
                    engine.claim(tenantId, task.getId(), dept, requireAssignee());
                    return task;
                }
                case "doing": {
                    Task task = poolOrTriageCreate(tenantId, roomId, workDate, "pool", systemActor);
                    Actor actor = requireAssignee();
                    engine.claim(tenantId, task.getId(), dept, actor);
                    engine.start(tenantId, task.getId(), actor);
                    return task;
                }
                case "done": {
                    Task task = poolOrTriageCreate(tenantId, roomId, workDate, "pool", systemActor);
                    Actor actor = requireAssignee();
                    engine.claim(tenantId, task.getId(), dept, actor);
                    engine.start(tenantId, task.getId(), actor);
                    engine.complete(tenantId, task.getId(), actor);
                    return task;
                }
                case "closed": {
                    Task task = poolOrTriageCreate(tenantId, roomId, workDate, "pool", systemActor);
                    Actor actor = requireAssignee();
                    engine.claim(tenantId, task.getId(), dept, actor);
                    engine.start(tenantId, task.getId(), actor);
                    engine.complete(tenantId, task.getId(), actor);
                    jdbcClient
                            .sql("UPDATE tasks SET state = 'closed' WHERE tenant_id = ? AND id = ?")
                            .params(tenantId, task.getId())
                            .update();
                    return task;
                }
                case "inspected": {
                    Task task = poolOrTriageCreate(tenantId, roomId, workDate, "pool", systemActor);
                    Actor actor = requireAssignee();
                    engine.claim(tenantId, task.getId(), dept, actor);
                    engine.start(tenantId, task.getId(), actor);
                    if (inspectionWaived) {
                        jdbcClient
                                .sql("UPDATE tasks SET inspection_waived = true WHERE tenant_id = ? AND id = ?")
                                .params(tenantId, task.getId())
                                .update();
                        engine.complete(tenantId, task.getId(), actor);
                    } else {
                        engine.complete(tenantId, task.getId(), actor);
                        Actor inspector = inspectorId != null ? Actor.staff("manager", inspectorId) : Actor.staff("manager", null);
                        engine.passInspection(tenantId, task.getId(), inspector);
                    }
                    return task;
                }
                case "deferred": {
                    Task task = poolOrTriageCreate(tenantId, roomId, workDate, "pool", systemActor);
                    Actor actor = assigneeId != null ? Actor.staff(dept, assigneeId) : systemActor;
                    engine.defer(tenantId, task.getId(), actor);
                    return task;
                }
                case "interrupted": {
                    Task task = poolOrTriageCreate(tenantId, roomId, workDate, "pool", systemActor);
                    Actor actor = requireAssignee();
                    engine.claim(tenantId, task.getId(), dept, actor);
                    engine.start(tenantId, task.getId(), actor);
                    engine.interrupt(tenantId, task.getId(), null, actor);
                    return task;
                }
                case "cancelled": {
                    Task task = poolOrTriageCreate(tenantId, roomId, workDate, "pool", systemActor);
                    Actor actor = assigneeId != null ? Actor.staff(dept, assigneeId) : Actor.staff("manager", null);
                    engine.cancel(tenantId, task.getId(), cancelReason, actor);
                    return task;
                }
                default:
                    throw new IllegalArgumentException("不支援的任務狀態代碼: " + state);
            }
        }

        private Task poolOrTriageCreate(
                long tenantId, UUID roomId, LocalDate workDate, String initialState, Actor actor) {
            return engine.create(
                    new TaskCreateCommand(
                            tenantId,
                            type,
                            source,
                            roomId,
                            reservationId,
                            dept,
                            initialState,
                            null,
                            title,
                            null,
                            estMin,
                            null,
                            workDate,
                            actor));
        }

        private Actor requireAssignee() {
            if (assigneeId == null) {
                throw new IllegalArgumentException("state=" + state + " 需要先指定 assignee（呼叫端未提供 assigneeId）");
            }
            return Actor.staff(dept, assigneeId);
        }

        private void backdateTimeline(long tenantId, UUID taskId) {
            List<Map<String, Object>> events = jdbcClient
                    .sql("SELECT id, seq, type FROM task_events WHERE tenant_id = ? AND task_id = ? ORDER BY seq")
                    .params(tenantId, taskId)
                    .query()
                    .listOfRows();
            if (events.isEmpty()) {
                return;
            }

            Instant now = clock.instant();
            Instant lastEventTime = workedMinutes != null ? now.minusSeconds(workedMinutes * 60L) : now;
            int n = events.size();
            Instant[] times = new Instant[n];
            int anchor = n - 1;
            Instant anchorTime = lastEventTime;
            if (doneAt != null) {
                for (int i = 0; i < n; i++) {
                    if ("completed".equals(events.get(i).get("type"))) {
                        anchor = i;
                        anchorTime = doneAt;
                        break;
                    }
                }
            }
            times[anchor] = anchorTime;
            for (int i = anchor - 1; i >= 0; i--) {
                times[i] = times[i + 1].minusSeconds(60);
            }
            for (int i = anchor + 1; i < n; i++) {
                times[i] = times[i - 1].plusSeconds(60);
            }

            for (int i = 0; i < n; i++) {
                jdbcClient
                        .sql("UPDATE task_events SET created_at = ? WHERE id = ?")
                        .params(OffsetDateTime.ofInstant(times[i], clock.getZone()), events.get(i).get("id"))
                        .update();
            }

            Instant startedInstant = timeOfEvent(events, times, "started");
            Instant doneInstant = timeOfEvent(events, times, "completed");
            jdbcClient
                    .sql(
                            """
                            UPDATE tasks
                               SET created_at = ?,
                                   updated_at = ?,
                                   started_at = COALESCE(?, started_at),
                                   done_at = COALESCE(?, done_at)
                             WHERE tenant_id = ? AND id = ?
                            """)
                    .params(
                            OffsetDateTime.ofInstant(times[0], clock.getZone()),
                            OffsetDateTime.ofInstant(times[n - 1], clock.getZone()),
                            startedInstant == null ? null : OffsetDateTime.ofInstant(startedInstant, clock.getZone()),
                            doneInstant == null ? null : OffsetDateTime.ofInstant(doneInstant, clock.getZone()),
                            tenantId,
                            taskId)
                    .update();
        }

        private Instant timeOfEvent(List<Map<String, Object>> events, Instant[] times, String type) {
            for (int i = 0; i < events.size(); i++) {
                if (type.equals(events.get(i).get("type"))) {
                    return times[i];
                }
            }
            return null;
        }

        private void stageReminder(
                long tenantId, UUID taskId, String targetStage, UUID assigneeId, String dept, String roomNo,
                String title) {
            List<String> stages = List.of("staff_reminded", "manager_notified");
            int targetIndex = stages.indexOf(targetStage);
            if (targetIndex < 0) {
                throw new IllegalArgumentException(
                        "tasks.reminder_stage 只支援 staff_reminded|manager_notified，收到: " + targetStage);
            }

            String label = (roomNo != null && !roomNo.isBlank()) ? roomNo : title;
            String targetJson = assigneeId != null
                    ? "{\"kind\":\"task\",\"taskId\":\"" + taskId + "\"}"
                    : "{\"kind\":\"pool\",\"dept\":\"" + dept + "\"}";

            for (int i = 0; i <= targetIndex; i++) {
                String stage = stages.get(i);
                boolean isStaffStage = "staff_reminded".equals(stage);
                String pushEvent = isStaffStage ? "unclaimed_reminder_staff" : "unclaimed_reminder_manager";
                String pushTitle = isStaffStage ? label + " 已等提醒，還沒人接" : label + " 已通知主管";
                List<UUID> recipients = isStaffStage
                        ? reminderRecipients(tenantId, "staff", assigneeId, dept)
                        : reminderRecipients(tenantId, "manager", assigneeId, dept);
                for (UUID recipient : recipients) {
                    insertReminderPush(tenantId, taskId, recipient, pushEvent, pushTitle, targetJson, stage);
                }
            }

            jdbcClient
                    .sql("UPDATE tasks SET reminder_stage = ? WHERE tenant_id = ? AND id = ?")
                    .params(targetStage, tenantId, taskId)
                    .update();
        }

        private List<UUID> reminderRecipients(long tenantId, String kind, UUID assigneeId, String dept) {
            return switch (kind) {
                case "staff" -> assigneeId != null
                        ? List.of(assigneeId)
                        : jdbcClient
                                .sql("SELECT id FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND active = true")
                                .params(tenantId, dept)
                                .query(UUID.class)
                                .list();
                case "manager" -> jdbcClient
                        .sql("SELECT id FROM staff_accounts WHERE tenant_id = ? AND role = 'manager' AND active = true")
                        .params(tenantId)
                        .query(UUID.class)
                        .list();
                default -> List.of();
            };
        }

        private void insertReminderPush(
                long tenantId, UUID taskId, UUID recipient, String event, String title, String targetJson,
                String stage) {
            String payload = "{\"recipientStaffId\":\"" + recipient + "\",\"event\":\"" + event
                    + "\",\"title\":\"" + escapeJson(title) + "\",\"target\":" + targetJson + "}";
            jdbcClient
                    .sql(
                            """
                            INSERT INTO outbox (tenant_id, aggregate_key, kind, payload, idempotency_key, status, attempts, created_at)
                            VALUES (?, ?, 'push', ?::jsonb, ?, 'pending', 0, ?)
                            """)
                    .params(
                            tenantId,
                            "task:" + taskId,
                            payload,
                            "fx-reminder:" + taskId + ":" + stage + ":" + recipient,
                            OffsetDateTime.ofInstant(clock.instant(), clock.getZone()))
                    .update();
        }

        private String escapeJson(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
