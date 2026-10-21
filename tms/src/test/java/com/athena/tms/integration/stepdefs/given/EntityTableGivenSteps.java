package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.dto.GuestSessionTokens;
import com.athena.tms.common.codes.ActorRole;
import com.athena.tms.common.codes.Dept;
import com.athena.tms.common.codes.TaskEventType;
import com.athena.tms.common.codes.TaskSource;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.TaskFixtures;
import com.athena.tms.integration.stepdefs.utils.Tenants;
import com.athena.tms.integration.stepdefs.utils.TestData;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import org.springframework.boot.convert.DurationStyle;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「系統中存在以下〈實體〉」前置資料步驟的共同落點。表格的欄名即資料庫欄名
 * （V1 schema），id 類欄位放人讀得懂的代號，落庫時換成真 UUID 並登記到
 * {@link ScenarioContext}，後續步驟憑代號找回實際資料列。
 *
 * <p>種子 migration 已有的資料列（房間、員工、訂單等主檔）不重複插入：
 * 一律以自然鍵 upsert（房號、login_id、訂單編號……），表格值覆蓋可變欄位、
 * 既有 UUID 保留。這讓場景可以重複執行，也不會與種子資料的唯一約束相撞。
 *
 * <p>兩個欄位不照表格字面落庫：員工的 password_hash 一律寫入種子共用的
 * bcrypt（demo1234）——登入步驟要走真驗證，表格裡的「hash-hk1」只是示意；
 * 住客 session 的 session_token 實際簽發帶租戶前綴的權杖，表格值只當代號。
 */
public class EntityTableGivenSteps {

    private final ScenarioContext ctx;
    private final DbSupport db;
    private final TaskFixtures fixtures;

    public EntityTableGivenSteps(ScenarioContext ctx, DbSupport db, TaskFixtures fixtures) {
        this.ctx = ctx;
        this.db = db;
        this.fixtures = fixtures;
    }

    @Given("^系統中存在以下房間:$")
    public void roomsExist(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            UUID id = db.admin().queryForObject("""
                    INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state,
                                       occupancy, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, now())
                    ON CONFLICT (tenant_id, room_no) DO UPDATE SET floor = excluded.floor,
                        room_type = excluded.room_type, hk_state = excluded.hk_state,
                        occupancy = excluded.occupancy, updated_at = now()
                    RETURNING id
                    """, UUID.class,
                    UUID.randomUUID(), tenantOf(row), value(row, "room_no"),
                    Integer.parseInt(value(row, "floor")), value(row, "room_type"),
                    value(row, "hk_state"), value(row, "occupancy"));
            ctx.registerEntity(value(row, "id"), id);
        }
    }

    @Given("^系統中存在以下員工:$")
    public void staffExist(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            UUID id = db.admin().queryForObject("""
                    INSERT INTO staff_accounts (id, tenant_id, name, login_id, password_hash,
                                                role, dept, active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, true, now(), now())
                    ON CONFLICT (tenant_id, login_id) DO UPDATE SET name = excluded.name,
                        role = excluded.role, dept = excluded.dept, active = true,
                        updated_at = now()
                    RETURNING id
                    """, UUID.class,
                    UUID.randomUUID(), tenantOf(row), value(row, "name"), value(row, "login_id"),
                    TestData.SEED_BCRYPT, value(row, "role"), value(row, "dept"));
            ctx.registerEntity(value(row, "id"), id);
            ctx.registerStaffLogin(value(row, "name"), value(row, "login_id"));
        }
    }

    @Given("^系統中存在以下訂單:$")
    public void reservationsExist(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            long tenantId = tenantOf(row);
            UUID roomId = ctx.entityId(value(row, "room_id"));
            String ref = value(row, "reservation_ref");
            OffsetDateTime checkinAt = OffsetDateTime.parse(value(row, "checkin_at"));
            OffsetDateTime checkoutAt = OffsetDateTime.parse(value(row, "checkout_at"));
            // 同房住期重疊有 EXCLUDE 約束擋著：先把擋路的舊訂單標成被取代，
            // 語意等同 PMS（飯店既有的訂房與房況系統）重新同步——新資料取代舊資料，不硬刪
            db.admin().update("""
                    UPDATE reservations_cache SET superseded_at = now()
                    WHERE tenant_id = ? AND room_id = ? AND reservation_ref <> ?
                      AND superseded_at IS NULL
                      AND tstzrange(checkin_at, checkout_at) && tstzrange(?, ?)
                    """, tenantId, roomId, ref, checkinAt, checkoutAt);
            UUID id = db.admin().queryForObject("""
                    INSERT INTO reservations_cache (id, tenant_id, reservation_ref, room_id,
                                                    attrs, checkin_at, checkout_at, source,
                                                    synced_at, superseded_at)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, now(), NULL)
                    ON CONFLICT (tenant_id, reservation_ref) DO UPDATE SET
                        room_id = excluded.room_id, attrs = excluded.attrs,
                        checkin_at = excluded.checkin_at, checkout_at = excluded.checkout_at,
                        source = excluded.source, synced_at = now(), superseded_at = NULL
                    RETURNING id
                    """, UUID.class,
                    UUID.randomUUID(), tenantId, ref, roomId, value(row, "attrs"),
                    checkinAt, checkoutAt, value(row, "source"));
            ctx.registerEntity(value(row, "id"), id);
        }
    }

    @Given("^系統中存在以下房間QR:$")
    public void qrTokensExist(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            String revokedAt = value(row, "revoked_at");
            UUID id = db.admin().queryForObject("""
                    INSERT INTO room_qr_tokens (id, tenant_id, room_id, token, version, status,
                                                created_at, revoked_at)
                    VALUES (?, ?, ?, ?, ?, ?, now(), ?)
                    ON CONFLICT (token) DO UPDATE SET room_id = excluded.room_id,
                        version = excluded.version, status = excluded.status,
                        revoked_at = excluded.revoked_at
                    RETURNING id
                    """, UUID.class,
                    UUID.randomUUID(), tenantOf(row), ctx.entityId(value(row, "room_id")),
                    value(row, "token"), Integer.parseInt(value(row, "version")),
                    value(row, "status"),
                    revokedAt == null ? null : OffsetDateTime.parse(revokedAt));
            ctx.registerEntity(value(row, "id"), id);
        }
    }

    @Given("^系統中存在以下住客session:$")
    public void guestSessionsExist(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            long tenantId = tenantOf(row);
            String token = GuestSessionTokens.issue(tenantId);
            String revokedAt = value(row, "revoked_at");
            UUID id = UUID.randomUUID();
            db.admin().update("""
                    INSERT INTO guest_sessions (id, tenant_id, session_token, room_id,
                                                reservation_id, expires_at, revoked_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, now())
                    """,
                    id, tenantId, token, ctx.entityId(value(row, "room_id")),
                    ctx.entityId(value(row, "reservation_id")),
                    OffsetDateTime.parse(value(row, "expires_at")),
                    revokedAt == null ? null : OffsetDateTime.parse(revokedAt));
            ctx.registerEntity(value(row, "id"), id);
            ctx.registerGuestSession(value(row, "session_token"), token);
            // 單一住客的場景（如 QR 輪替連坐）靠它拿「這位住客」的權杖
            ctx.put("sessionToken", token);
        }
    }

    @Given("^系統中存在以下SOP項目:$")
    public void sopItemsExist(DataTable table) {
        boolean touchedInspection = false;
        for (Map<String, String> row : table.asMaps()) {
            String appliesTo = value(row, "applies_to");
            UUID id = db.admin().queryForObject("""
                    INSERT INTO sop_items (id, tenant_id, applies_to, item_key, label,
                                           sort_order, active, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, true, now())
                    ON CONFLICT (tenant_id, applies_to, item_key) DO UPDATE SET
                        label = excluded.label, sort_order = excluded.sort_order, active = true
                    RETURNING id
                    """, UUID.class,
                    UUID.randomUUID(), tenantOf(row), appliesTo, value(row, "item_key"),
                    value(row, "label"), Integer.parseInt(value(row, "sort_order")));
            ctx.registerEntity(value(row, "id"), id);
            ctx.registerSopItem(value(row, "label"), value(row, "item_key"));
            touchedInspection |= "inspection".equals(appliesTo);
        }
        if (touchedInspection) {
            // 查房步驟要「租戶的判定全集」；全集=租戶所有啟用中的查房項目，
            // 不只這次表格列（種子可能已有同鍵項目）
            ctx.put("inspectionKeys", db.admin().queryForList(
                    "SELECT item_key FROM sop_items WHERE tenant_id = 1"
                            + " AND applies_to = 'inspection' AND active ORDER BY sort_order",
                    String.class));
        }
    }

    @Given("^系統中存在以下租戶設定:$")
    public void tenantConfigExists(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            db.admin().update("""
                    INSERT INTO tenant_config (tenant_id, key, value, updated_at)
                    VALUES (?, ?, ?::jsonb, now())
                    ON CONFLICT (tenant_id, key) DO UPDATE SET value = excluded.value,
                        updated_at = now()
                    """, tenantOf(row), value(row, "key"), value(row, "value"));
        }
    }

    @Given("^系統中存在以下請假:$")
    public void leavesExist(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            UUID id = UUID.randomUUID();
            db.admin().update("""
                    INSERT INTO leaves (id, tenant_id, staff_id, from_at, to_at, created_by,
                                        created_at)
                    VALUES (?, ?, ?, ?, ?, ?, now())
                    """,
                    id, tenantOf(row), ctx.entityId(value(row, "staff_id")),
                    OffsetDateTime.parse(value(row, "from_at")),
                    OffsetDateTime.parse(value(row, "to_at")),
                    ctx.entityId(value(row, "created_by")));
            ctx.registerEntity(value(row, "id"), id);
        }
    }

    @Given("^系統中存在以下SOP勾記:$")
    public void sopChecksExist(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            boolean checked = Boolean.parseBoolean(value(row, "checked"));
            UUID id = UUID.randomUUID();
            db.admin().update("""
                    INSERT INTO task_sop_checks (id, tenant_id, task_id, item_key, label,
                                                 checked, skipped, checked_at)
                    VALUES (?, ?, ?, ?, ?, ?, false, ?)
                    """,
                    id, tenantOf(row), ctx.entityId(value(row, "task_id")),
                    value(row, "item_key"), value(row, "label"), checked,
                    checked ? OffsetDateTime.now() : null);
            ctx.registerEntity(value(row, "id"), id);
        }
    }

    @Given("^系統中存在以下待送匣:$")
    public void outboxRowsExist(DataTable table) {
        List<Long> freeIds = new ArrayList<>();
        List<String> blockedKeys = new ArrayList<>();
        Map<Long, String> keysById = new LinkedHashMap<>();
        for (Map<String, String> row : table.asMaps()) {
            String aggregateKey = translateAggregateKey(value(row, "aggregate_key"));
            String nextRetryAt = value(row, "next_retry_at");
            int attempts = Integer.parseInt(value(row, "attempts"));
            Long id = db.admin().queryForObject("""
                    INSERT INTO outbox (tenant_id, aggregate_key, kind, payload, idempotency_key,
                                        status, attempts, next_retry_at, created_at)
                    VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, now())
                    RETURNING id
                    """, Long.class,
                    tenantOf(row), aggregateKey, value(row, "kind"), value(row, "payload"),
                    value(row, "idempotency_key"), value(row, "status"), attempts,
                    nextRetryAt == null ? null : parseRetryTime(nextRetryAt));
            keysById.put(id, aggregateKey);
            ctx.put("outbox:" + value(row, "idempotency_key"), id);
            // attempts>0 且排了重試時間＝「送失敗、等重試」的列；同鍵後續列會被它擋住
            if (attempts > 0 && nextRetryAt != null) {
                blockedKeys.add(aggregateKey);
            }
        }
        keysById.forEach((id, key) -> {
            if (!blockedKeys.contains(key)) {
                freeIds.add(id);
            }
        });
        ctx.put("outboxFreeIds", freeIds);
        ctx.put("outboxBlockedKeys", blockedKeys);
    }

    @Given("^系統中存在以下任務:$")
    public void tasksExist(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            UUID taskId = createTask(row);
            ctx.registerEntity(value(row, "id"), taskId);
            ctx.taskId(taskId);
            String assigneeSymbol = value(row, "assignee_id");
            if (assigneeSymbol != null) {
                // 查房場景要知道「原清房者」是誰（退回歸屬、重掃重交的操作者）
                UUID assignee = ctx.entityId(assigneeSymbol);
                ctx.put("cleanerId", assignee);
                ctx.put("cleanerActor", Actor.staff(roleFor(value(row, "dept")), assignee));
            }
        }
    }

    /**
     * 造出表格描述的任務。能走 TaskEngine 就走（建單＋狀態轉移產生與正式流程
     * 一致的事件、seq 與 outbox 列），唯一例外是「guest 來源但表格沒給訂單」：
     * 引擎的驗證不收，退回直插資料列並補一筆 created 事件，事件序號照樣成立。
     */
    private UUID createTask(Map<String, String> row) {
        long tenantId = tenantOf(row);
        String source = value(row, "source");
        String state = value(row, "state");
        String dept = value(row, "dept");
        UUID reservationId = optionalEntity(value(row, "reservation_id"));
        if (TaskSource.GUEST.equals(source) && reservationId == null) {
            return insertTaskDirectly(row, tenantId, dept, state);
        }
        UUID roomId = ctx.entityId(value(row, "room_id"));
        String estMin = value(row, "est_min");
        String workDate = value(row, "work_date");
        boolean beyondPool = !TaskState.TRIAGE.equals(state) && !TaskState.POOL.equals(state);
        UUID assigneeId = optionalEntity(value(row, "assignee_id"));
        if (beyondPool && assigneeId == null) {
            assigneeId = anyStaffOfDept(tenantId, dept);
        }
        String initialState = beyondPool ? TaskState.ACCEPTED : state;
        Actor creator = TaskSource.GUEST.equals(source) ? Actor.guest()
                : TaskSource.SYSTEM.equals(source) ? Actor.system()
                : Actor.staff(ActorRole.FRONTDESK, anyStaffOfDept(tenantId, Dept.FRONTDESK));
        TaskCreateCommand command = new TaskCreateCommand(
                tenantId, value(row, "type"), source, roomId, reservationId, dept, initialState,
                beyondPool ? assigneeId : null, value(row, "title"), null,
                estMin == null ? null : Integer.parseInt(estMin), null,
                workDate == null ? null : LocalDate.parse(workDate), creator);
        UUID taskId = Tenants.asTenant(tenantId, () -> fixtures.engine().create(command)).getId();
        if (beyondPool) {
            Actor worker = Actor.staff(roleFor(dept), assigneeId);
            Tenants.runAsTenant(tenantId, () -> {
                if (TaskState.DOING.equals(state) || TaskState.DONE.equals(state)
                        || TaskState.INSPECTED.equals(state)) {
                    fixtures.engine().start(tenantId, taskId, worker);
                }
                if (TaskState.DONE.equals(state) || TaskState.INSPECTED.equals(state)) {
                    fixtures.engine().complete(tenantId, taskId, worker);
                }
                if (TaskState.INSPECTED.equals(state)) {
                    fixtures.engine().passInspection(tenantId, taskId,
                            Actor.staff(ActorRole.MANAGER, anyManager(tenantId)));
                }
            });
        }
        applyColumnOverrides(row, taskId);
        return taskId;
    }

    /** 引擎建不了的列直插 tasks＋created 事件；狀態超過 pool 時補齊轉移事件，seq 無洞。 */
    private UUID insertTaskDirectly(Map<String, String> row, long tenantId, String dept,
                                    String state) {
        UUID taskId = UUID.randomUUID();
        UUID roomId = ctx.entityId(value(row, "room_id"));
        UUID assigneeId = optionalEntity(value(row, "assignee_id"));
        List<String> chain = eventChainFor(state);
        db.admin().update("""
                INSERT INTO tasks (id, tenant_id, type, source, room_id, dept, state, event_seq,
                                   assignee_id, title, est_min, started_at, done_at, rating,
                                   work_date, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                taskId, tenantId, value(row, "type"), value(row, "source"), roomId, dept, state,
                chain.size(), assigneeId, value(row, "title"),
                parseIntOrNull(value(row, "est_min")),
                parseTimestampOrNull(value(row, "started_at")),
                parseTimestampOrNull(value(row, "done_at")),
                parseIntOrNull(value(row, "rating")),
                value(row, "work_date") == null ? null : LocalDate.parse(value(row, "work_date")));
        for (int seq = 1; seq <= chain.size(); seq++) {
            String type = chain.get(seq - 1);
            boolean isCreated = TaskEventType.CREATED.equals(type);
            String payload = isCreated
                    ? createdSnapshotJson(row, roomId)
                    : TaskEventType.CLAIMED.equals(type) && assigneeId != null
                            ? "{\"assignee_id\": \"" + assigneeId + "\"}"
                            : null;
            db.admin().update("""
                    INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id,
                                             payload, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, now())
                    """,
                    tenantId, taskId, seq, type,
                    isCreated ? ActorRole.GUEST : roleFor(dept),
                    isCreated ? null : assigneeId, payload);
        }
        return taskId;
    }

    /** 到達某狀態該存在的事件歷史（與引擎正常流程同形）。 */
    private static List<String> eventChainFor(String state) {
        return switch (state) {
            case TaskState.TRIAGE, TaskState.POOL -> List.of(TaskEventType.CREATED);
            case TaskState.ACCEPTED -> List.of(TaskEventType.CREATED, TaskEventType.CLAIMED);
            case TaskState.DOING -> List.of(TaskEventType.CREATED, TaskEventType.CLAIMED,
                    TaskEventType.STARTED);
            case TaskState.DONE -> List.of(TaskEventType.CREATED, TaskEventType.CLAIMED,
                    TaskEventType.STARTED, TaskEventType.COMPLETED);
            case TaskState.INSPECTED -> List.of(TaskEventType.CREATED, TaskEventType.CLAIMED,
                    TaskEventType.STARTED, TaskEventType.COMPLETED,
                    TaskEventType.INSPECTION_PASSED);
            default -> throw new IllegalArgumentException("前置任務不支援 state=" + state);
        };
    }

    /** created 快照（鍵名比照引擎的 createdSnapshot；null 欄不落鍵）。 */
    private String createdSnapshotJson(Map<String, String> row, UUID roomId) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"type\": \"").append(value(row, "type")).append("\"");
        json.append(", \"source\": \"").append(value(row, "source")).append("\"");
        json.append(", \"room_id\": \"").append(roomId).append("\"");
        json.append(", \"dept\": \"").append(value(row, "dept")).append("\"");
        json.append(", \"title\": \"").append(value(row, "title")).append("\"");
        json.append("}");
        return json.toString();
    }

    /**
     * 引擎走出來的時間欄是 now()；表格有字面值時以表格為準覆寫，讓「資料應為」
     * 類斷言對得上。rating 走引擎要多一次 API 呼叫，直接補欄位即可——
     * 「已評過」這個條件（rating IS NULL）本身就是讀 tasks 這一列的欄位，
     * 不是讀事件，直接灌值一樣能讓這個判定生效。
     */
    private void applyColumnOverrides(Map<String, String> row, UUID taskId) {
        OffsetDateTime startedAt = parseTimestampOrNull(value(row, "started_at"));
        if (startedAt != null) {
            db.admin().update("UPDATE tasks SET started_at = ? WHERE id = ?", startedAt, taskId);
        }
        OffsetDateTime doneAt = parseTimestampOrNull(value(row, "done_at"));
        if (doneAt != null) {
            db.admin().update("UPDATE tasks SET done_at = ? WHERE id = ?", doneAt, taskId);
        }
        Integer rating = parseIntOrNull(value(row, "rating"));
        if (rating != null) {
            db.admin().update("UPDATE tasks SET rating = ? WHERE id = ?", rating, taskId);
        }
    }

    /**
     * aggregate_key 的尾段若是已登記的代號就換成真 UUID
     * （「room:1:r-305」→「room:1:&lt;房 uuid&gt;」），與正式分區鍵同形。
     */
    /**
     * 待送匣表格的 next_retry_at 支援相對時間："+1h" 表示「從現在起 1 小時後」
     * （還在等、整鍵仍被擋）、"-1h" 表示「1 小時前就該重試了」（租約／退避已過期，
     * relay 這輪就會撈到）。寫死絕對時間一旦跨過那個日期就全部失效（fixture 曾經
     * 真的因為這樣掛過）；相對時間永遠以場景實際執行的那一刻為準，不會過期。
     * 沒有正負號前綴的字串仍當絕對 ISO 時間解析，保留舊寫法的退路。
     */
    private static OffsetDateTime parseRetryTime(String raw) {
        if (raw.startsWith("+") || raw.startsWith("-")) {
            Duration delta = DurationStyle.detectAndParse(raw.substring(1));
            return raw.startsWith("-") ? OffsetDateTime.now().minus(delta) : OffsetDateTime.now().plus(delta);
        }
        return OffsetDateTime.parse(raw);
    }

    private String translateAggregateKey(String key) {
        int lastColon = key.lastIndexOf(':');
        if (lastColon < 0) {
            return key;
        }
        String tail = key.substring(lastColon + 1);
        return ctx.hasEntity(tail) ? key.substring(0, lastColon + 1) + ctx.entityId(tail) : key;
    }

    private UUID optionalEntity(String symbol) {
        return symbol == null ? null : ctx.entityId(symbol);
    }

    private UUID anyStaffOfDept(long tenantId, String dept) {
        return db.admin().queryForObject(
                "SELECT id FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND active"
                        + " ORDER BY login_id LIMIT 1", UUID.class, tenantId, dept);
    }

    private UUID anyManager(long tenantId) {
        return db.admin().queryForObject(
                "SELECT id FROM staff_accounts WHERE tenant_id = ? AND role = 'manager' AND active"
                        + " ORDER BY login_id LIMIT 1", UUID.class, tenantId);
    }

    private static String roleFor(String dept) {
        return switch (dept) {
            case Dept.HOUSEKEEPING -> ActorRole.HOUSEKEEPING;
            case Dept.ENGINEERING -> ActorRole.ENGINEERING;
            case Dept.FRONTDESK -> ActorRole.FRONTDESK;
            default -> throw new IllegalArgumentException("沒有部門 " + dept + " 的角色對照");
        };
    }

    private static long tenantOf(Map<String, String> row) {
        return Long.parseLong(value(row, "tenant_id"));
    }

    private static Integer parseIntOrNull(String cell) {
        return cell == null ? null : Integer.valueOf(cell);
    }

    private static OffsetDateTime parseTimestampOrNull(String cell) {
        return cell == null ? null : OffsetDateTime.parse(cell);
    }

    /** 空字串與缺欄一視同仁回 null（DataTable 的空 cell 讀出來是空字串）。 */
    private static String value(Map<String, String> row, String column) {
        String cell = row.get(column);
        return cell == null || cell.isBlank() ? null : cell;
    }
}
