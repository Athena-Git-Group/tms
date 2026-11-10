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

    @Given(value="^系統中存在以下房間:$")
    public void roomsExist(DataTable table) {
        for (Map row : table.asMaps()) {
            UUID id = (UUID)this.db.admin().queryForObject("INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state,\n                   occupancy, updated_at)\nVALUES (?, ?, ?, ?, ?, ?, ?, now())\nON CONFLICT (tenant_id, room_no) DO UPDATE SET floor = excluded.floor,\n    room_type = excluded.room_type, hk_state = excluded.hk_state,\n    occupancy = excluded.occupancy, updated_at = now()\nRETURNING id\n", UUID.class, new Object[]{UUID.randomUUID(), EntityTableGivenSteps.tenantOf(row), EntityTableGivenSteps.value(row, "room_no"), Integer.parseInt(EntityTableGivenSteps.value(row, "floor")), EntityTableGivenSteps.value(row, "room_type"), EntityTableGivenSteps.value(row, "hk_state"), EntityTableGivenSteps.value(row, "occupancy")});
            this.ctx.registerEntity(EntityTableGivenSteps.value(row, "id"), id);
        }
    }

    @Given(value="^系統中存在以下員工:$")
    public void staffExist(DataTable table) {
        for (Map row : table.asMaps()) {
            UUID id = (UUID)this.db.admin().queryForObject("INSERT INTO staff_accounts (id, tenant_id, name, login_id, password_hash,\n                            role, dept, active, created_at, updated_at)\nVALUES (?, ?, ?, ?, ?, ?, ?, true, now(), now())\nON CONFLICT (tenant_id, login_id) DO UPDATE SET name = excluded.name,\n    role = excluded.role, dept = excluded.dept, active = true,\n    updated_at = now()\nRETURNING id\n", UUID.class, new Object[]{UUID.randomUUID(), EntityTableGivenSteps.tenantOf(row), EntityTableGivenSteps.value(row, "name"), EntityTableGivenSteps.value(row, "login_id"), "$2y$10$bQc3edVM.yCnLts.1Z1WyuSNWlF9T9..kLS2GRgBIpvsFYYlh.pjW", EntityTableGivenSteps.value(row, "role"), EntityTableGivenSteps.value(row, "dept")});
            this.ctx.registerEntity(EntityTableGivenSteps.value(row, "id"), id);
            this.ctx.registerStaffLogin(EntityTableGivenSteps.value(row, "name"), EntityTableGivenSteps.value(row, "login_id"));
        }
    }

    @Given(value="^系統中存在以下訂單:$")
    public void reservationsExist(DataTable table) {
        for (Map row : table.asMaps()) {
            long tenantId = EntityTableGivenSteps.tenantOf(row);
            UUID roomId = this.ctx.entityId(EntityTableGivenSteps.value(row, "room_id"));
            String ref = EntityTableGivenSteps.value(row, "reservation_ref");
            OffsetDateTime checkinAt = OffsetDateTime.parse(EntityTableGivenSteps.value(row, "checkin_at"));
            OffsetDateTime checkoutAt = OffsetDateTime.parse(EntityTableGivenSteps.value(row, "checkout_at"));
            this.db.admin().update("UPDATE reservations_cache SET superseded_at = now()\nWHERE tenant_id = ? AND room_id = ? AND reservation_ref <> ?\n  AND superseded_at IS NULL\n  AND tstzrange(checkin_at, checkout_at) && tstzrange(?, ?)\n", new Object[]{tenantId, roomId, ref, checkinAt, checkoutAt});
            UUID id = (UUID)this.db.admin().queryForObject("INSERT INTO reservations_cache (id, tenant_id, reservation_ref, room_id,\n                                attrs, checkin_at, checkout_at, source,\n                                synced_at, superseded_at)\nVALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, now(), NULL)\nON CONFLICT (tenant_id, reservation_ref) DO UPDATE SET\n    room_id = excluded.room_id, attrs = excluded.attrs,\n    checkin_at = excluded.checkin_at, checkout_at = excluded.checkout_at,\n    source = excluded.source, synced_at = now(), superseded_at = NULL\nRETURNING id\n", UUID.class, new Object[]{UUID.randomUUID(), tenantId, ref, roomId, EntityTableGivenSteps.value(row, "attrs"), checkinAt, checkoutAt, EntityTableGivenSteps.value(row, "source")});
            this.ctx.registerEntity(EntityTableGivenSteps.value(row, "id"), id);
        }
    }

    @Given(value="^系統中存在以下房間QR:$")
    public void qrTokensExist(DataTable table) {
        for (Map row : table.asMaps()) {
            String revokedAt = EntityTableGivenSteps.value(row, "revoked_at");
            UUID id = (UUID)this.db.admin().queryForObject("INSERT INTO room_qr_tokens (id, tenant_id, room_id, token, version, status,\n                            created_at, revoked_at)\nVALUES (?, ?, ?, ?, ?, ?, now(), ?)\nON CONFLICT (token) DO UPDATE SET room_id = excluded.room_id,\n    version = excluded.version, status = excluded.status,\n    revoked_at = excluded.revoked_at\nRETURNING id\n", UUID.class, new Object[]{UUID.randomUUID(), EntityTableGivenSteps.tenantOf(row), this.ctx.entityId(EntityTableGivenSteps.value(row, "room_id")), EntityTableGivenSteps.value(row, "token"), Integer.parseInt(EntityTableGivenSteps.value(row, "version")), EntityTableGivenSteps.value(row, "status"), revokedAt == null ? null : OffsetDateTime.parse(revokedAt)});
            this.ctx.registerEntity(EntityTableGivenSteps.value(row, "id"), id);
        }
    }

    @Given(value="^系統中存在以下住客session:$")
    public void guestSessionsExist(DataTable table) {
        for (Map row : table.asMaps()) {
            long tenantId = EntityTableGivenSteps.tenantOf(row);
            String token = GuestSessionTokens.issue((long)tenantId);
            String revokedAt = EntityTableGivenSteps.value(row, "revoked_at");
            UUID id = UUID.randomUUID();
            this.db.admin().update("INSERT INTO guest_sessions (id, tenant_id, session_token, room_id,\n                            reservation_id, expires_at, revoked_at, created_at)\nVALUES (?, ?, ?, ?, ?, ?, ?, now())\n", new Object[]{id, tenantId, token, this.ctx.entityId(EntityTableGivenSteps.value(row, "room_id")), this.ctx.entityId(EntityTableGivenSteps.value(row, "reservation_id")), OffsetDateTime.parse(EntityTableGivenSteps.value(row, "expires_at")), revokedAt == null ? null : OffsetDateTime.parse(revokedAt)});
            this.ctx.registerEntity(EntityTableGivenSteps.value(row, "id"), id);
            this.ctx.registerGuestSession(EntityTableGivenSteps.value(row, "session_token"), token);
            this.ctx.put("sessionToken", token);
        }
    }

    @Given(value="^系統中存在以下SOP項目:$")
    public void sopItemsExist(DataTable table) {
        boolean touchedInspection = false;
        for (Map row : table.asMaps()) {
            String appliesTo = EntityTableGivenSteps.value(row, "applies_to");
            UUID id = (UUID)this.db.admin().queryForObject("INSERT INTO sop_items (id, tenant_id, applies_to, item_key, label,\n                       sort_order, active, created_at)\nVALUES (?, ?, ?, ?, ?, ?, true, now())\nON CONFLICT (tenant_id, applies_to, item_key) DO UPDATE SET\n    label = excluded.label, sort_order = excluded.sort_order, active = true\nRETURNING id\n", UUID.class, new Object[]{UUID.randomUUID(), EntityTableGivenSteps.tenantOf(row), appliesTo, EntityTableGivenSteps.value(row, "item_key"), EntityTableGivenSteps.value(row, "label"), Integer.parseInt(EntityTableGivenSteps.value(row, "sort_order"))});
            this.ctx.registerEntity(EntityTableGivenSteps.value(row, "id"), id);
            this.ctx.registerSopItem(EntityTableGivenSteps.value(row, "label"), EntityTableGivenSteps.value(row, "item_key"));
            touchedInspection |= "inspection".equals(appliesTo);
        }
        if (touchedInspection) {
            this.ctx.put("inspectionKeys", this.db.admin().queryForList("SELECT item_key FROM sop_items WHERE tenant_id = 1 AND applies_to = 'inspection' AND active ORDER BY sort_order", String.class));
        }
    }

    @Given(value="^系統中存在以下租戶設定:$")
    public void tenantConfigExists(DataTable table) {
        for (Map row : table.asMaps()) {
            this.db.admin().update("INSERT INTO tenant_config (tenant_id, key, value, updated_at)\nVALUES (?, ?, ?::jsonb, now())\nON CONFLICT (tenant_id, key) DO UPDATE SET value = excluded.value,\n    updated_at = now()\n", new Object[]{EntityTableGivenSteps.tenantOf(row), EntityTableGivenSteps.value(row, "key"), EntityTableGivenSteps.value(row, "value")});
        }
    }

    @Given(value="^系統中存在以下請假:$")
    public void leavesExist(DataTable table) {
        for (Map row : table.asMaps()) {
            UUID id = UUID.randomUUID();
            this.db.admin().update("INSERT INTO leaves (id, tenant_id, staff_id, from_at, to_at, created_by,\n                    created_at)\nVALUES (?, ?, ?, ?, ?, ?, now())\n", new Object[]{id, EntityTableGivenSteps.tenantOf(row), this.ctx.entityId(EntityTableGivenSteps.value(row, "staff_id")), OffsetDateTime.parse(EntityTableGivenSteps.value(row, "from_at")), OffsetDateTime.parse(EntityTableGivenSteps.value(row, "to_at")), this.ctx.entityId(EntityTableGivenSteps.value(row, "created_by"))});
            this.ctx.registerEntity(EntityTableGivenSteps.value(row, "id"), id);
        }
    }

    @Given(value="^系統中存在以下SOP勾記:$")
    public void sopChecksExist(DataTable table) {
        for (Map row : table.asMaps()) {
            boolean checked = Boolean.parseBoolean(EntityTableGivenSteps.value(row, "checked"));
            UUID id = UUID.randomUUID();
            this.db.admin().update("INSERT INTO task_sop_checks (id, tenant_id, task_id, item_key, label,\n                             checked, skipped, checked_at)\nVALUES (?, ?, ?, ?, ?, ?, false, ?)\n", new Object[]{id, EntityTableGivenSteps.tenantOf(row), this.ctx.entityId(EntityTableGivenSteps.value(row, "task_id")), EntityTableGivenSteps.value(row, "item_key"), EntityTableGivenSteps.value(row, "label"), checked, checked ? OffsetDateTime.now() : null});
            this.ctx.registerEntity(EntityTableGivenSteps.value(row, "id"), id);
        }
    }

    @Given(value="^系統中存在以下待送匣:$")
    public void outboxRowsExist(DataTable table) {
        ArrayList freeIds = new ArrayList();
        ArrayList<String> blockedKeys = new ArrayList<String>();
        LinkedHashMap<Long, String> keysById = new LinkedHashMap<Long, String>();
        for (Map row : table.asMaps()) {
            String aggregateKey = this.translateAggregateKey(EntityTableGivenSteps.value(row, "aggregate_key"));
            String nextRetryAt = EntityTableGivenSteps.value(row, "next_retry_at");
            int attempts = Integer.parseInt(EntityTableGivenSteps.value(row, "attempts"));
            Long id2 = (Long)this.db.admin().queryForObject("INSERT INTO outbox (tenant_id, aggregate_key, kind, payload, idempotency_key,\n                    status, attempts, next_retry_at, created_at)\nVALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, now())\nRETURNING id\n", Long.class, new Object[]{EntityTableGivenSteps.tenantOf(row), aggregateKey, EntityTableGivenSteps.value(row, "kind"), EntityTableGivenSteps.value(row, "payload"), EntityTableGivenSteps.value(row, "idempotency_key"), EntityTableGivenSteps.value(row, "status"), attempts, nextRetryAt == null ? null : EntityTableGivenSteps.parseRetryTime(nextRetryAt)});
            keysById.put(id2, aggregateKey);
            this.ctx.put("outbox:" + EntityTableGivenSteps.value(row, "idempotency_key"), id2);
            if (attempts <= 0 || nextRetryAt == null) continue;
            blockedKeys.add(aggregateKey);
        }
        keysById.forEach((id, key) -> {
            if (!blockedKeys.contains(key)) {
                freeIds.add(id);
            }
        });
        this.ctx.put("outboxFreeIds", freeIds);
        this.ctx.put("outboxBlockedKeys", blockedKeys);
    }

    @Given(value="^系統中存在以下任務:$")
    public void tasksExist(DataTable table) {
        for (Map row : table.asMaps()) {
            UUID taskId = this.createTask(row);
            this.ctx.registerEntity(EntityTableGivenSteps.value(row, "id"), taskId);
            this.ctx.taskId(taskId);
            String assigneeSymbol = EntityTableGivenSteps.value(row, "assignee_id");
            if (assigneeSymbol == null) continue;
            UUID assignee = this.ctx.entityId(assigneeSymbol);
            this.ctx.put("cleanerId", assignee);
            this.ctx.put("cleanerActor", Actor.staff((String)EntityTableGivenSteps.roleFor(EntityTableGivenSteps.value(row, "dept")), (UUID)assignee));
        }
    }

    private UUID createTask(Map<String, String> row) {
        String initialState;
        long tenantId = EntityTableGivenSteps.tenantOf(row);
        String source = EntityTableGivenSteps.value(row, "source");
        String state = EntityTableGivenSteps.value(row, "state");
        String dept = EntityTableGivenSteps.value(row, "dept");
        UUID reservationId = this.optionalEntity(EntityTableGivenSteps.value(row, "reservation_id"));
        if ("guest".equals(source) && reservationId == null) {
            return this.insertTaskDirectly(row, tenantId, dept, state);
        }
        UUID roomId = this.ctx.entityId(EntityTableGivenSteps.value(row, "room_id"));
        String estMin = EntityTableGivenSteps.value(row, "est_min");
        String workDate = EntityTableGivenSteps.value(row, "work_date");
        boolean beyondPool = !"triage".equals(state) && !"pool".equals(state);
        UUID assigneeId = this.optionalEntity(EntityTableGivenSteps.value(row, "assignee_id"));
        if (beyondPool && assigneeId == null) {
            assigneeId = this.anyStaffOfDept(tenantId, dept);
        }
        String string = initialState = beyondPool ? "accepted" : state;
        Actor creator = "guest".equals(source) ? Actor.guest() : ("system".equals(source) ? Actor.system() : Actor.staff((String)"frontdesk", (UUID)this.anyStaffOfDept(tenantId, "frontdesk")));
        TaskCreateCommand command = new TaskCreateCommand(tenantId, EntityTableGivenSteps.value(row, "type"), source, roomId, reservationId, dept, initialState, beyondPool ? assigneeId : null, EntityTableGivenSteps.value(row, "title"), null, estMin == null ? null : Integer.valueOf(Integer.parseInt(estMin)), null, workDate == null ? null : LocalDate.parse(workDate), creator);
        UUID taskId = Tenants.asTenant(tenantId, () -> this.fixtures.engine().create(command)).getId();
        if (beyondPool) {
            Actor worker = Actor.staff((String)EntityTableGivenSteps.roleFor(dept), (UUID)assigneeId);
            Tenants.runAsTenant(tenantId, () -> {
                if ("doing".equals(state) || "done".equals(state) || "inspected".equals(state)) {
                    this.fixtures.engine().start(tenantId, taskId, worker);
                }
                if ("done".equals(state) || "inspected".equals(state)) {
                    this.fixtures.engine().complete(tenantId, taskId, worker);
                }
                if ("inspected".equals(state)) {
                    this.fixtures.engine().passInspection(tenantId, taskId, Actor.staff((String)"manager", (UUID)this.anyManager(tenantId)));
                }
            });
        }
        this.applyColumnOverrides(row, taskId);
        return taskId;
    }

    private UUID insertTaskDirectly(Map<String, String> row, long tenantId, String dept, String state) {
        UUID taskId = UUID.randomUUID();
        UUID roomId = this.ctx.entityId(EntityTableGivenSteps.value(row, "room_id"));
        UUID assigneeId = this.optionalEntity(EntityTableGivenSteps.value(row, "assignee_id"));
        List<String> chain = EntityTableGivenSteps.eventChainFor(state);
        this.db.admin().update("INSERT INTO tasks (id, tenant_id, type, source, room_id, dept, state, event_seq,\n                   assignee_id, title, est_min, started_at, done_at, rating,\n                   work_date, created_at, updated_at)\nVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())\n", new Object[]{taskId, tenantId, EntityTableGivenSteps.value(row, "type"), EntityTableGivenSteps.value(row, "source"), roomId, dept, state, chain.size(), assigneeId, EntityTableGivenSteps.value(row, "title"), EntityTableGivenSteps.parseIntOrNull(EntityTableGivenSteps.value(row, "est_min")), EntityTableGivenSteps.parseTimestampOrNull(EntityTableGivenSteps.value(row, "started_at")), EntityTableGivenSteps.parseTimestampOrNull(EntityTableGivenSteps.value(row, "done_at")), EntityTableGivenSteps.parseIntOrNull(EntityTableGivenSteps.value(row, "rating")), EntityTableGivenSteps.value(row, "work_date") == null ? null : LocalDate.parse(EntityTableGivenSteps.value(row, "work_date"))});
        for (int seq = 1; seq <= chain.size(); ++seq) {
            String type = chain.get(seq - 1);
            boolean isCreated = "created".equals(type);
            String payload = isCreated ? this.createdSnapshotJson(row, roomId) : ("claimed".equals(type) && assigneeId != null ? "{\"assignee_id\": \"" + String.valueOf(assigneeId) + "\"}" : null);
            this.db.admin().update("INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id,\n                         payload, created_at)\nVALUES (?, ?, ?, ?, ?, ?, ?::jsonb, now())\n", new Object[]{tenantId, taskId, seq, type, isCreated ? "guest" : EntityTableGivenSteps.roleFor(dept), isCreated ? null : assigneeId, payload});
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
        json.append("\"type\": \"").append(EntityTableGivenSteps.value(row, "type")).append("\"");
        json.append(", \"source\": \"").append(EntityTableGivenSteps.value(row, "source")).append("\"");
        json.append(", \"room_id\": \"").append(roomId).append("\"");
        json.append(", \"dept\": \"").append(EntityTableGivenSteps.value(row, "dept")).append("\"");
        json.append(", \"title\": \"").append(EntityTableGivenSteps.value(row, "title")).append("\"");
        json.append("}");
        return json.toString();
    }

    private void applyColumnOverrides(Map<String, String> row, UUID taskId) {
        Integer rating;
        OffsetDateTime doneAt;
        OffsetDateTime startedAt = EntityTableGivenSteps.parseTimestampOrNull(EntityTableGivenSteps.value(row, "started_at"));
        if (startedAt != null) {
            this.db.admin().update("UPDATE tasks SET started_at = ? WHERE id = ?", new Object[]{startedAt, taskId});
        }
        if ((doneAt = EntityTableGivenSteps.parseTimestampOrNull(EntityTableGivenSteps.value(row, "done_at"))) != null) {
            this.db.admin().update("UPDATE tasks SET done_at = ? WHERE id = ?", new Object[]{doneAt, taskId});
        }
        if ((rating = EntityTableGivenSteps.parseIntOrNull(EntityTableGivenSteps.value(row, "rating"))) != null) {
            this.db.admin().update("UPDATE tasks SET rating = ? WHERE id = ?", new Object[]{rating, taskId});
        }
    }

    private static OffsetDateTime parseRetryTime(String raw) {
        if (raw.startsWith("+") || raw.startsWith("-")) {
            Duration delta = DurationStyle.detectAndParse((String)raw.substring(1));
            return raw.startsWith("-") ? OffsetDateTime.now().minus(delta) : OffsetDateTime.now().plus(delta);
        }
        return OffsetDateTime.parse(raw);
    }

    private String translateAggregateKey(String key) {
        int lastColon = key.lastIndexOf(58);
        if (lastColon < 0) {
            return key;
        }
        String tail = key.substring(lastColon + 1);
        return this.ctx.hasEntity(tail) ? key.substring(0, lastColon + 1) + String.valueOf(this.ctx.entityId(tail)) : key;
    }

    private UUID optionalEntity(String symbol) {
        return symbol == null ? null : this.ctx.entityId(symbol);
    }

    private UUID anyStaffOfDept(long tenantId, String dept) {
        return (UUID)this.db.admin().queryForObject("SELECT id FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND active ORDER BY login_id LIMIT 1", UUID.class, new Object[]{tenantId, dept});
    }

    private UUID anyManager(long tenantId) {
        return (UUID)this.db.admin().queryForObject("SELECT id FROM staff_accounts WHERE tenant_id = ? AND role = 'manager' AND active ORDER BY login_id LIMIT 1", UUID.class, new Object[]{tenantId});
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
        return Long.parseLong(EntityTableGivenSteps.value(row, "tenant_id"));
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
