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

/**
 * 任務測試資料入口（{@code tasks}／{@code task_events}，欄位依 data-model.dbml
 * {@code Table tasks}、{@code Table task_events}）。
 *
 * <p>任務的狀態轉移一律透過 {@link TaskEngine} 執行，不直接寫 tasks.state：
 * 這樣每個目標狀態背後都會留下對應的 task_events 歷程（created/claimed/started/…），
 * 跟正式流程產生的資料形狀一致，之後查 task_events 斷言事件歷程時不會因為是測試資料
 * 而多出例外分支。只有 tasks.redo_count 這類「只是一個計數器、本身不代表任何事件」
 * 的欄位，才在狀態轉移跑完之後用 UPDATE 直接灌值。
 *
 * <p>{@link TaskEngine} 的每一句 UPDATE 都用資料庫的 {@code now()}，不是注入的
 * {@link MutableClock}，所以狀態轉移一結束，時間還停在測試執行當下的真實時刻。
 * {@link Builder#create()} 跑完整個狀態轉移之後，會把這個任務的 task_events.created_at
 * 與 tasks 對應欄位整批改寫成以 {@link MutableClock} 目前讀數回推出來的時間，
 * 讓「已做 N 分鐘」這類相對於測試時鐘的斷言在 V1 schema（還沒有 tasks.worked_sec）下
 * 也能成立：此時「已做分鐘」等於 {@code 目前時鐘 − tasks.started_at}。
 */
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

        /**
         * 只在 state=inspected 時有意義：是否以「開工當下鎖定免查」收尾（對應「完成 ·
         * 免查房」，tasks.inspection=waived），而不是主管查房通過（tasks.inspection=passed）。
         * 兩者在 {@code 狀態用語對照} 都對應同一個 tasks.state=inspected，差別只在
         * tasks.inspection，見 {@link com.athena.tms.bdd.support.Terms#taskState}。
         */
        public Builder inspectionWaived(boolean v) {
            this.inspectionWaived = v;
            return this;
        }

        /** 只在 state=doing 時有意義：backdate 成「目前時鐘往前推這麼多分鐘」開工。 */
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

        /**
         * dsl.md「完成時間」：已完成狀態（done／inspected／closed）的 {@code tasks.done_at} 與
         * {@code completed} 事件時間。事件歷程以這個時刻為錨點重排：completed 之前的事件往前
         * 每筆 60 秒，之後的（inspection_passed／inspection_waived）往後每筆 60 秒。
         */
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

        /** 最近一次進池的來源標籤（tasks.pool_source 本 plan 新增衍生欄位）。 */
        public Builder poolSource(String v) {
            this.poolSource = v;
            return this;
        }

        /** 待認領排序第二鍵（tasks.priority_notify 本 plan 新增衍生欄位）。 */
        public Builder priorityNotify(boolean v) {
            this.priorityNotify = v;
            return this;
        }

        /** 最近一次退池的原因（tasks.return_reason 本 plan 新增衍生欄位）。 */
        public Builder returnReason(String v) {
            this.returnReason = v;
            return this;
        }

        /** 接手進度交代（tasks.requeue_note 本 plan 新增衍生欄位；PII 去識別化範圍）。 */
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

        /**
         * 直接把待認領任務 staging 到未接提醒的某個階段（dsl.md「提醒狀態」欄：
         * {@code staff_reminded}＝已提醒同仁、{@code manager_notified}＝已通知主管），
         * 補寫到該階段為止的 push outbox 紀錄，不需要真的把 {@link MutableClock} 推進到
         * 門檻分鐘數。只在 state=pool 時有意義（tasks.reminder_stage 的設計只在池中才有意義，
         * 見 data-model.dbml Table tasks 的 reminder_stage note）。
         *
         * <p>只負責「staging 到某個階段」這個資料落地動作，不判斷任何門檻比對規則
         * （一般單/急件各自的分鐘數門檻由 Phase 2 的排程掃描 stepdef 負責）。
         */
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
                // redo_count 只是一個計數器，這裡直接灌值而不是真的跑 N 次查房退回／重做循環；
                // 需要驗證「第 N 次退回」事件歷程本身的情境，應該改呼叫 TaskEngine.failInspection。
                jdbcClient
                        .sql("UPDATE tasks SET redo_count = ? WHERE tenant_id = ? AND id = ?")
                        .params(redoCount, tenantId, taskId)
                        .update();
            }

            if (holdReason != null || poolSource != null || priorityNotify != null
                    || returnReason != null || requeueNote != null || ruleHit != null
                    || dueAtHHmm != null || deferredBySelf != null) {
                // 這些都是本 plan／SLA 增補新增的衍生欄位，TaskEngine 的狀態轉移不寫它們；
                // COALESCE 只覆寫呼叫端有給值的欄位，沒給的維持 NULL／預設值，不假裝幫情境
                // 決定沒要求的值。
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

        /** 只在 state=doing／interrupted 時落地 worked_sec／segment_started_at（見 workedMinutes）。 */
        private void applyWorkedSeconds(long tenantId, UUID taskId) {
            if (workedMinutes == null) {
                return;
            }
            if ("doing".equals(state)) {
                // doing：已做分鐘＝目前這段 now－segment_started_at（worked_sec 保持 0，
                // 代表這是唯一一段、還沒有任何已結束的段落）。
                Instant segmentStart = clock.instant().minusSeconds(workedMinutes * 60L);
                jdbcClient
                        .sql(
                                "UPDATE tasks SET worked_sec = 0, segment_started_at = ? WHERE tenant_id = ? AND id = ?")
                        .params(OffsetDateTime.ofInstant(segmentStart, clock.getZone()), tenantId, taskId)
                        .update();
            } else if ("interrupted".equals(state)) {
                // interrupted：沒有進行中的段落，已做分鐘全部落在 worked_sec。
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
                    // 工務單「已結案」：重演到 done（完修）後把 tasks.state 收到 closed。結案事件由呼叫端
                    // 依結案方式補：驗收通過補 repair_verified；不需處理時 completed 事件本身就是結案事件。
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
                        // 「完成 · 免查房」：比照正式流程在開工當下鎖定免查（tasks.inspection_waived），
                        // 這裡用 UPDATE 直接補這個衍生欄位（不是事件本身，本身也不代表任何事件），
                        // 讓接下來的 complete() 照正式的 CASE 邏輯把狀態收在 inspected、
                        // inspection 記為 waived，事件歷程跟正式流程一致。
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
                    // tasks.state 的 CHECK 見 V23（10 個值），對照 data-model.dbml Table tasks 的 Note。
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
            // 錨點預設是最後一筆事件＝目前 Clock；有給「完成時間」時錨點改成 completed 事件，
            // 之後的查房事件往後排，讓 done_at 與 completed 事件時間都落在指定時刻。
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
                // 只需要嚴格遞增、維持事件先後順序；精確間距對任何斷言都不重要。
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

        /**
         * dsl.md「提醒狀態」欄：補寫到 {@code targetStage} 為止的未接提醒 push outbox 紀錄，
         * 最後把 tasks.reminder_stage 寫到 {@code targetStage}。「已通知主管」隱含「已提醒同仁」
         * 已經先發生過，所以兩段都在門檻之內的情境會補兩筆推播，不是只補最終那一段。
         *
         * <p>收件人慣例：提醒同仁＝接單人（有 assignee 時）或部門在職成員；通知主管＝在職的
         * manager。兩段都只各發一次（冪等鍵帶 stage）。
         */
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
