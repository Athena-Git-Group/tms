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

    /** 待接池任務的簡便入口，等同 {@code builder().state("pool")...create()}。 */
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
        private Integer workedMinutes;
        private String holdReason;
        private int redoCount = 0;
        private String source = "desk";
        private UUID reservationId;
        private LocalDate workDate;
        private String cancelReason = "manager_void";
        private String poolSource;
        private Boolean priorityNotify;
        private String returnReason;
        private String requeueNote;
        private String ruleHit;
        private String dueAtHHmm;
        private Boolean deferredBySelf;
        private int escalateLevel = 0;

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

        public Builder workDate(LocalDate v) {
            this.workDate = v;
            return this;
        }

        public Builder cancelReason(String v) {
            this.cancelReason = v;
            return this;
        }

        /** 最近一次進池的來源標籤（tasks.pool_source 本 plan 新增投影欄位）。 */
        public Builder poolSource(String v) {
            this.poolSource = v;
            return this;
        }

        /** 待接池排序第二鍵（tasks.priority_notify 本 plan 新增投影欄位）。 */
        public Builder priorityNotify(boolean v) {
            this.priorityNotify = v;
            return this;
        }

        /** 最近一次退池的原因（tasks.return_reason 本 plan 新增投影欄位）。 */
        public Builder returnReason(String v) {
            this.returnReason = v;
            return this;
        }

        /** 接手進度交代（tasks.requeue_note 本 plan 新增投影欄位；PII 去識別化範圍）。 */
        public Builder requeueNote(String v) {
            this.requeueNote = v;
            return this;
        }

        /** 自動化規則命中的規則名（tasks.rule_hit，SLA/自動化規則增補）。 */
        public Builder ruleHit(String v) {
            this.ruleHit = v;
            return this;
        }

        /** 到期時間（tasks.due_at，SLA/自動化規則增補），今日的 {@code HH:mm}。 */
        public Builder dueAtToday(String hhmm) {
            this.dueAtHHmm = hhmm;
            return this;
        }

        /** state=deferred 時是否為本人延後（tasks.deferred_by_self，本 plan 新增）。 */
        public Builder deferredBySelf(boolean v) {
            this.deferredBySelf = v;
            return this;
        }

        /**
         * 補寫到第 N 層為止的逾時升級歷程（dsl.md「已升級」欄，L1/L2/L3 對應 1/2/3）。
         * 0＝未升級（預設）。
         */
        public Builder escalateToLevel(int level) {
            this.escalateLevel = level;
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
                // 這些都是本 plan／SLA 增補新增的投影欄位，TaskEngine 的狀態轉移不寫它們；
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
            if (escalateLevel > 0) {
                escalateTask(tenantId, taskId, escalateLevel, assigneeId, dept, roomNo, title);
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
                case "inspected": {
                    Task task = poolOrTriageCreate(tenantId, roomId, workDate, "pool", systemActor);
                    Actor actor = requireAssignee();
                    engine.claim(tenantId, task.getId(), dept, actor);
                    engine.start(tenantId, task.getId(), actor);
                    engine.complete(tenantId, task.getId(), actor);
                    Actor inspector = inspectorId != null ? Actor.staff("manager", inspectorId) : Actor.staff("manager", null);
                    engine.passInspection(tenantId, task.getId(), inspector);
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
                    // tasks.state 的 CHECK 在 V1 恰好就是這 9 個值，見 data-model.dbml Table tasks 的 Note。
                    throw new IllegalArgumentException(
                            "不支援的任務狀態代碼: " + state + "（V1 的 tasks.state CHECK 只有 9 個值）");
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
            times[n - 1] = lastEventTime;
            for (int i = n - 2; i >= 0; i--) {
                // 只需要嚴格遞增、維持事件先後順序；精確間距對任何斷言都不重要。
                times[i] = times[i + 1].minusSeconds(60);
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
         * dsl.md「已升級」欄：補寫到第 {@code level} 層為止的 {@code escalated} 事件與對應推播紀錄。
         * actor_role 固定 'system'（逾時升級是系統判定，不是哪個人做的），避開
         * task_events.actor_role 的 CHECK 值域裡沒有 duty_manager 這件事。
         * L1 收件人＝接單人（有 assignee 時）或部門在職成員（否則）；L2＝在職的 manager；
         * L3＝在職的 duty_manager——V1 schema 的 staff_accounts.role CHECK 還沒有這個值，
         * 查得到的清單必然是空的，這裡優雅跳過，不是本類別要蒙混過去的事。
         */
        private void escalateTask(
                long tenantId, UUID taskId, int level, UUID assigneeId, String dept, String roomNo, String title) {
            jdbcClient
                    .sql("UPDATE tasks SET esc_level = ? WHERE tenant_id = ? AND id = ?")
                    .params(level, tenantId, taskId)
                    .update();

            String label = (roomNo != null && !roomNo.isBlank()) ? roomNo : title;
            for (int l = 1; l <= level; l++) {
                Integer seq = jdbcClient
                        .sql("UPDATE tasks SET event_seq = event_seq + 1 WHERE tenant_id = ? AND id = ? RETURNING event_seq")
                        .params(tenantId, taskId)
                        .query(Integer.class)
                        .single();
                jdbcClient
                        .sql(
                                """
                                INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
                                VALUES (?, ?, ?, 'escalated', 'system', NULL, ?::jsonb, ?)
                                """)
                        .params(tenantId, taskId, seq, "{\"level\":" + l + "}", OffsetDateTime.ofInstant(clock.instant(), clock.getZone()))
                        .update();

                String pushTitle = l == 1 ? label + " 已逾時 · 升級 L1" : label + " 已升級 L" + l;
                String target = assigneeId != null
                        ? "{\"kind\":\"task\",\"taskId\":\"" + taskId + "\"}"
                        : "{\"kind\":\"pool\",\"dept\":\"" + dept + "\"}";
                for (UUID recipient : escalationRecipients(tenantId, l, assigneeId, dept)) {
                    insertEscalationPush(tenantId, taskId, recipient, pushTitle, target, l);
                }
            }
        }

        private List<UUID> escalationRecipients(long tenantId, int level, UUID assigneeId, String dept) {
            return switch (level) {
                case 1 -> assigneeId != null
                        ? List.of(assigneeId)
                        : jdbcClient
                                .sql("SELECT id FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND active = true")
                                .params(tenantId, dept)
                                .query(UUID.class)
                                .list();
                case 2 -> jdbcClient
                        .sql("SELECT id FROM staff_accounts WHERE tenant_id = ? AND role = 'manager' AND active = true")
                        .params(tenantId)
                        .query(UUID.class)
                        .list();
                case 3 -> jdbcClient
                        .sql("SELECT id FROM staff_accounts WHERE tenant_id = ? AND role = 'duty_manager' AND active = true")
                        .params(tenantId)
                        .query(UUID.class)
                        .list();
                default -> List.of();
            };
        }

        private void insertEscalationPush(
                long tenantId, UUID taskId, UUID recipient, String title, String targetJson, int level) {
            String payload = "{\"recipientStaffId\":\"" + recipient + "\",\"event\":\"escalated\",\"title\":\""
                    + escapeJson(title) + "\",\"target\":" + targetJson + "}";
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
                            "fx-escalate:" + taskId + ":L" + level + ":" + recipient,
                            OffsetDateTime.ofInstant(clock.instant(), clock.getZone()))
                    .update();
        }

        private String escapeJson(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
