package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.dto.Actor;
import com.athena.tms.dto.GuestSessionTokens;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.TaskFixtures;
import com.athena.tms.integration.stepdefs.utils.Tenants;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.convert.DurationStyle;

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
                    """,
                    UUID.class,
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
                    """,
                    UUID.class,
                    UUID.randomUUID(), tenantOf(row), value(row, "name"), value(row, "login_id"),
                    "$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW",
                    value(row, "role"), value(row, "dept"));
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
            db.admin().update("""
                    UPDATE reservations_cache SET superseded_at = now()
                    WHERE tenant_id = ? AND room_id = ? AND reservation_ref <> ?
                      AND superseded_at IS NULL
                      AND tstzrange(checkin_at, checkout_at) && tstzrange(?, ?)
                    """,
                    tenantId, roomId, ref, checkinAt, checkoutAt);
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
                    """,
                    UUID.class,
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
                    """,
                    UUID.class,
                    UUID.randomUUID(), tenantOf(row), ctx.entityId(value(row, "room_id")),
                    value(row, "token"), Integer.parseInt(value(row, "version")), value(row, "status"),
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
                    """,
                    UUID.class,
                    UUID.randomUUID(), tenantOf(row), appliesTo, value(row, "item_key"),
                    value(row, "label"), Integer.parseInt(value(row, "sort_order")));
            ctx.registerEntity(value(row, "id"), id);
            ctx.registerSopItem(value(row, "label"), value(row, "item_key"));
            touchedInspection |= "inspection".equals(appliesTo);
        }
        if (touchedInspection) {
            ctx.put("inspectionKeys", db.admin().queryForList(
                    "SELECT item_key FROM sop_items WHERE tenant_id = 1 AND applies_to = 'inspection' AND active ORDER BY sort_order",
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
                    """,
                    tenantOf(row), value(row, "key"), value(row, "value"));
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
                    OffsetDateTime.parse(value(row, "from_at")), OffsetDateTime.parse(value(row, "to_at")),
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
                    """,
                    Long.class,
                    tenantOf(row), aggregateKey, value(row, "kind"), value(row, "payload"),
                    value(row, "idempotency_key"), value(row, "status"), attempts,
                    nextRetryAt == null ? null : parseRetryTime(nextRetryAt));
            keysById.put(id, aggregateKey);
            ctx.put("outbox:" + value(row, "idempotency_key"), id);
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
                UUID assignee = ctx.entityId(assigneeSymbol);
                ctx.put("cleanerId", assignee);
                ctx.put("cleanerActor", Actor.staff(roleFor(value(row, "dept")), assignee));
            }
        }
    }

    private UUID createTask(Map<String, String> row) {
        long tenantId = tenantOf(row);
        String source = value(row, "source");
        String state = value(row, "state");
        String dept = value(row, "dept");
        UUID reservationId = optionalEntity(value(row, "reservation_id"));
        if ("guest".equals(source) && reservationId == null) {
            return insertTaskDirectly(row, tenantId, dept, state);
        }
        UUID roomId = ctx.entityId(value(row, "room_id"));
        String estMin = value(row, "est_min");
        String workDate = value(row, "work_date");
        boolean beyondPool = !"triage".equals(state) && !"pool".equals(state);
        UUID assigneeId = optionalEntity(value(row, "assignee_id"));
        if (beyondPool && assigneeId == null) {
            assigneeId = anyStaffOfDept(tenantId, dept);
        }
        String initialState = beyondPool ? "accepted" : state;
        Actor creator;
        if ("guest".equals(source)) {
            creator = Actor.guest();
        } else if ("system".equals(source)) {
            creator = Actor.system();
        } else {
            creator = Actor.staff("frontdesk", anyStaffOfDept(tenantId, "frontdesk"));
        }
        TaskCreateCommand command = new TaskCreateCommand(
                tenantId, value(row, "type"), source, roomId, reservationId, dept, initialState,
                beyondPool ? assigneeId : null, value(row, "title"), null,
                estMin == null ? null : Integer.parseInt(estMin), null,
                workDate == null ? null : LocalDate.parse(workDate), creator);
        UUID taskId = Tenants.asTenant(tenantId, () -> fixtures.engine().create(command)).getId();
        if (beyondPool) {
            Actor worker = Actor.staff(roleFor(dept), assigneeId);
            Tenants.runAsTenant(tenantId, () -> {
                if ("doing".equals(state) || "done".equals(state) || "inspected".equals(state)) {
                    fixtures.engine().start(tenantId, taskId, worker);
                }
                if ("done".equals(state) || "inspected".equals(state)) {
                    fixtures.engine().complete(tenantId, taskId, worker);
                }
                if ("inspected".equals(state)) {
                    fixtures.engine().passInspection(tenantId, taskId, Actor.staff("manager", anyManager(tenantId)));
                }
            });
        }
        applyColumnOverrides(row, taskId);
        return taskId;
    }

    private UUID insertTaskDirectly(Map<String, String> row, long tenantId, String dept, String state) {
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
            boolean isCreated = "created".equals(type);
            String payload;
            if (isCreated) {
                payload = createdSnapshotJson(row, roomId);
            } else if ("claimed".equals(type) && assigneeId != null) {
                payload = "{\"assignee_id\": \"" + assigneeId + "\"}";
            } else {
                payload = null;
            }
            db.admin().update("""
                    INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id,
                                             payload, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, now())
                    """,
                    tenantId, taskId, seq, type,
                    isCreated ? "guest" : roleFor(dept),
                    isCreated ? null : assigneeId,
                    payload);
        }
        return taskId;
    }

    private static List<String> eventChainFor(String state) {
        return switch (state) {
            case "triage", "pool" -> List.of("created");
            case "accepted" -> List.of("created", "claimed");
            case "doing" -> List.of("created", "claimed", "started");
            case "done" -> List.of("created", "claimed", "started", "completed");
            case "inspected" -> List.of("created", "claimed", "started", "completed", "inspection_passed");
            default -> throw new IllegalArgumentException("前置任務不支援 state=" + state);
        };
    }

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
                "SELECT id FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND active ORDER BY login_id LIMIT 1",
                UUID.class, tenantId, dept);
    }

    private UUID anyManager(long tenantId) {
        return db.admin().queryForObject(
                "SELECT id FROM staff_accounts WHERE tenant_id = ? AND role = 'manager' AND active ORDER BY login_id LIMIT 1",
                UUID.class, tenantId);
    }

    private static String roleFor(String dept) {
        return switch (dept) {
            case "housekeeping" -> "housekeeping";
            case "engineering" -> "engineering";
            case "frontdesk" -> "frontdesk";
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

    private static String value(Map<String, String> row, String column) {
        String cell = row.get(column);
        return cell == null || cell.isBlank() ? null : cell;
    }
}
