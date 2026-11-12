package com.athena.app.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BackendChannel {

    private static final String BASE_PATH = "/api/v1";

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    public static final String DEFAULT_PASSWORD = "demo1234";

    private static final byte[] PLACEHOLDER_JPEG = java.util.Base64.getDecoder().decode(
            "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRof"
            + "Hh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAAB"
            + "AAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==");

    private static final String TEST_TENANT_NAME = "測試飯店";

    private static final Map<String, String> TASK_STATE = Map.ofEntries(
            Map.entry("待認領", "pool"),
            Map.entry("待開始", "accepted"),
            Map.entry("進行中", "doing"),
            Map.entry("已延後", "deferred"),
            Map.entry("已中斷", "interrupted"),
            Map.entry("待查房", "done"),
            Map.entry("已送達", "done"),
            Map.entry("查房通過", "inspected"),
            Map.entry("完成 · 免查房", "inspected"),
            Map.entry("已取消", "cancelled"));

    private static final Map<String, String> DEPT = Map.ofEntries(
            Map.entry("房務", "housekeeping"),
            Map.entry("工務", "engineering"),
            Map.entry("櫃檯", "frontdesk"));

    private static final Map<String, String> ROLE = Map.ofEntries(
            Map.entry("主管", "manager"),
            Map.entry("值班經理", "duty_manager"));

    private static final Map<String, String> TASK_TYPE = Map.ofEntries(
            Map.entry("打掃", "cleaning"),
            Map.entry("備品", "amenity"),
            Map.entry("修繕", "repair"),
            Map.entry("其他", "other"));

    private static final Map<String, String> POOL_SOURCE = Map.ofEntries(
            Map.entry("住客掃碼", "guest_scan"),
            Map.entry("櫃檯轉單", "desk_forward"),
            Map.entry("主管退回", "manager_requeue"),
            Map.entry("請假退回", "leave_return"),
            Map.entry("班末退池", "shift_end_return"),
            Map.entry("查房退回", "inspection_return"),
            Map.entry("房務報修", "staff_report"),
            Map.entry("自動化規則", "system"));

    private static final Map<String, String> STAFF_STATE = Map.ofEntries(
            Map.entry("在職", "active"),
            Map.entry("待開通", "invited"),
            Map.entry("停用", "inactive"));

    private static final Map<String, String> REPAIR_TICKET_STATE = Map.ofEntries(
            Map.entry("待認領", "pool"),
            Map.entry("已接單", "accepted"),
            Map.entry("待開始", "accepted"),
            Map.entry("維修中", "doing"),
            Map.entry("已完修", "done"),
            Map.entry("已結案", "closed"),
            Map.entry("已撤回", "cancelled"));

    private static final Map<String, String> RESOLUTION_TYPE = Map.ofEntries(
            Map.entry("已修好", "fixed"),
            Map.entry("暫時處理", "temporary"),
            Map.entry("無法修復", "cannot_fix"),
            Map.entry("不需處理", "no_action_needed"));

    private static final Map<String, String> HK_STATE = Map.ofEntries(
            Map.entry("還沒打掃好", "dirty"),
            Map.entry("已打掃", "cleaned"),
            Map.entry("已查房", "inspected"),
            Map.entry("停用", "out_of_order"));

    private static final Map<String, String> REMINDER_STAGE = Map.ofEntries(
            Map.entry("已提醒同仁", "staff_reminded"),
            Map.entry("已通知主管", "manager_notified"));

    public static final String PUBLIC_AREA_ROOM_NO = "公共區域";

    private static final String PLAN_SHIFT_PLACEHOLDER = "default";

    private final String baseUrl;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private Connection connection;
    private Long cachedTenantId;
    private final Map<UUID, String> jwtByStaffId = new ConcurrentHashMap<>();

    public BackendChannel() {
        this.baseUrl = System.getProperty("backend.base.url", "http://127.0.0.1:8080");
        this.dbUrl = System.getProperty("backend.db.url", "jdbc:postgresql://127.0.0.1:5433/tms");
        this.dbUser = System.getProperty("backend.db.user", "tms");
        this.dbPassword = System.getProperty("backend.db.password", "tms");
    }

    public static String taskState(String chinese) {
        String value = TASK_STATE.get(chinese);
        if (value == null) {
            throw new IllegalArgumentException("找不到「" + chinese + "」在任務狀態對照表中的權威值");
        }
        return value;
    }

    public static String dept(String chinese) {
        String value = DEPT.get(chinese);
        if (value == null) {
            throw new IllegalArgumentException("找不到「" + chinese + "」在部門對照表中的權威值");
        }
        return value;
    }

    public static String role(String chinese) {
        String value = ROLE.get(chinese);
        if (value == null) {
            throw new IllegalArgumentException("找不到「" + chinese + "」在角色對照表中的權威值");
        }
        return value;
    }

    public static String taskType(String chinese) {
        String value = TASK_TYPE.get(chinese);
        if (value == null) {
            throw new IllegalArgumentException("找不到「" + chinese + "」在任務類別對照表中的權威值");
        }
        return value;
    }

    public static String poolSource(String chinese) {
        String value = POOL_SOURCE.get(chinese);
        if (value == null) {
            throw new IllegalArgumentException("找不到「" + chinese + "」在待認領來源對照表中的權威值");
        }
        return value;
    }

    public static String staffState(String chinese) {
        return lookupTerm(STAFF_STATE, chinese, "員工狀態");
    }

    public static String repairTicketState(String chinese) {
        return lookupTerm(REPAIR_TICKET_STATE, chinese, "工務單狀態");
    }

    public static String resolutionType(String chinese) {
        return lookupTerm(RESOLUTION_TYPE, chinese, "處理結果");
    }

    public static String hkState(String chinese) {
        return lookupTerm(HK_STATE, chinese, "房況");
    }

    private static String lookupTerm(Map<String, String> table, String chinese, String what) {
        String value = table.get(chinese);
        if (value == null) {
            throw new IllegalArgumentException("找不到「" + chinese + "」在" + what
                    + "對照表中的權威值（合法值：" + table.keySet() + "）");
        }
        return value;
    }

    public static String reminderStage(String chinese) {
        String value = REMINDER_STAGE.get(chinese);
        if (value == null) {
            throw new IllegalArgumentException("找不到「" + chinese + "」在未接提醒階段對照表中的權威值");
        }
        return value;
    }

    public record CreatedStaff(UUID id, String name, String loginId, String password, String role, String dept) {}

    public synchronized long ensureTenant() {
        if (cachedTenantId != null) {
            return cachedTenantId;
        }
        Optional<Object> existing = queryScalar("SELECT id FROM tenants WHERE name = ?", TEST_TENANT_NAME);
        if (existing.isPresent()) {
            cachedTenantId = ((Number) existing.get()).longValue();
            return cachedTenantId;
        }
        Object id = queryScalar("INSERT INTO tenants (name, created_at) VALUES (?, ?) RETURNING id",
                TEST_TENANT_NAME, OffsetDateTime.now())
                .orElseThrow(() -> new IllegalStateException("INSERT tenants 沒有回傳 id"));
        cachedTenantId = ((Number) id).longValue();
        return cachedTenantId;
    }

    public void resetBusinessData() {
        List<Map<String, Object>> rows = queryRows("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name NOT IN ('flyway_schema_history', 'tenants')
                """);
        if (rows.isEmpty()) {
            return;
        }
        List<String> tables = rows.stream().map(r -> (String) r.get("table_name")).toList();
        execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        jwtByStaffId.clear();
    }

    private static final String TEST_CLOCK_RESTART_HINT =
            "後端沒有開放撥時鐘端點（/api/v1/test/clock）。請用最新程式碼、帶旗標重啟後端："
                    + "TMS_DB_URL=jdbc:postgresql://localhost:5433/tms TMS_TEST_CLOCK_ENABLED=true "
                    + "./tms/mvnw -f tms/pom.xml spring-boot:run";

    public void setBackendClock(OffsetDateTime moment) {
        HttpResponse<String> response = send(request("POST", "/test/clock", null)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        toJson(Map.of("isoTime", moment.toString())), StandardCharsets.UTF_8)));
        requireTestClock(response, "撥後端時鐘到 " + moment);
    }

    public OffsetDateTime setBackendClockOutsideAllShifts() {
        long tenantId = ensureTenant();
        execute("INSERT INTO tenant_config (tenant_id, key, value, updated_at) "
                        + "VALUES (?, 'shift_times', ?::jsonb, now()) ON CONFLICT (tenant_id, key) DO NOTHING",
                tenantId,
                "{\"morning\": {\"start\": \"08:00\", \"end\": \"16:00\"}, "
                        + "\"afternoon\": {\"start\": \"16:00\", \"end\": \"23:00\"}}");
        Object raw = queryScalar(
                "SELECT value::text FROM tenant_config WHERE tenant_id = ? AND key = 'shift_times'", tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "測試租戶沒有 shift_times 設定，判不出「班別時段之外」；請先在情境裡設定飯店班別"));
        List<LocalTime[]> windows = new ArrayList<>();
        parseJson(raw.toString()).properties().forEach(shift -> {
            JsonNode window = shift.getValue();
            LocalTime start = parseShiftTime(window.get("start"));
            LocalTime end = parseShiftTime(window.get("end"));
            if (start != null && end != null) {
                windows.add(new LocalTime[] {start, end});
            }
        });
        OffsetDateTime candidate = OffsetDateTime.now().withSecond(0).withNano(0);
        for (int minute = 0; minute <= 24 * 60; minute++) {
            LocalTime at = candidate.toLocalTime();
            boolean onShift = windows.isEmpty() || windows.stream().anyMatch(w -> shiftCovers(w[0], w[1], at));
            if (!onShift) {
                setBackendClock(candidate);
                return candidate;
            }
            candidate = candidate.plusMinutes(1);
        }
        throw new IllegalStateException("飯店班別時段涵蓋全天或解析不出任何班別，找不到班別之外的時刻：" + raw);
    }

    private static LocalTime parseShiftTime(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        try {
            return LocalTime.parse(node.asText());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private static boolean shiftCovers(LocalTime start, LocalTime end, LocalTime at) {
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !at.isBefore(start) && at.isBefore(end);
        }
        return !at.isBefore(start) || at.isBefore(end);
    }

    public void resetBackendClock() {
        HttpResponse<String> response = send(request("DELETE", "/test/clock", null).DELETE());
        requireTestClock(response, "後端時鐘回到系統時間");
    }

    private static void requireTestClock(HttpResponse<String> response, String what) {
        int status = response.statusCode();
        if (status == 404 || status == 401 || status == 403) {
            throw new IllegalStateException(TEST_CLOCK_RESTART_HINT + "（HTTP " + status + "）");
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException(what + " 失敗：HTTP " + response.statusCode() + " " + response.body());
        }
    }

    public UUID ensureRoom(String roomNo, int floor) {
        long tenantId = ensureTenant();
        Optional<Object> existing = queryScalar(
                "SELECT id FROM rooms WHERE tenant_id = ? AND room_no = ?", tenantId, roomNo);
        if (existing.isPresent()) {
            return (UUID) existing.get();
        }
        UUID id = UUID.randomUUID();
        execute("""
                INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state, occupancy, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, roomNo, floor, "standard_double", "dirty", "vacant", OffsetDateTime.now());
        return id;
    }

    public UUID ensurePublicAreaRoom() {
        long tenantId = ensureTenant();
        Optional<Object> existing = queryScalar(
                "SELECT id FROM rooms WHERE tenant_id = ? AND room_no = ?", tenantId, PUBLIC_AREA_ROOM_NO);
        if (existing.isPresent()) {
            return (UUID) existing.get();
        }
        UUID id = UUID.randomUUID();
        execute("""
                INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state, occupancy, updated_at)
                VALUES (?, ?, ?, 0, 'public_area', 'cleaned', 'vacant', ?)
                """, id, tenantId, PUBLIC_AREA_ROOM_NO, OffsetDateTime.now());
        return id;
    }

    public void assignPlanRoom(UUID staffId, int floor) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom("PLANROOM-F" + floor, floor);
        OffsetDateTime now = OffsetDateTime.now();
        UUID planId = UUID.randomUUID();
        execute("""
                INSERT INTO cleaning_plans
                    (id, tenant_id, work_date, shift, status, version, generated_by, algo_version, generated_at)
                VALUES (?, ?, ?, ?, 'draft', 0, 'system', 'v1', ?)
                """, planId, tenantId, now.toLocalDate(), PLAN_SHIFT_PLACEHOLDER, now);
        execute("""
                INSERT INTO plan_rooms (id, tenant_id, plan_id, room_id, staff_id, est_min, route_seq)
                VALUES (?, ?, ?, ?, ?, 30, 1)
                """, UUID.randomUUID(), tenantId, planId, roomId, staffId);
    }

    public void deactivateStaff(UUID staffId) {
        execute("UPDATE staff_accounts SET active = false, status = 'inactive', updated_at = ?"
                        + " WHERE tenant_id = ? AND id = ?",
                OffsetDateTime.now(), ensureTenant(), staffId);
    }

    public UUID createHousekeepingPoolTask(
            String roomNo, Integer floor, String title, String typeCode, String poolSourceCode,
            boolean priorityNotify, String returnReason, String handoverNote, String ruleHit,
            String reminderStageCode) {
        long tenantId = ensureTenant();
        UUID roomId = roomNo == null
                ? ensurePublicAreaRoom()
                : ensureRoom(roomNo, floor != null ? floor : floorFromRoomNo(roomNo));
        LocalDate workDate = "cleaning".equals(typeCode) ? LocalDate.now() : null;

        UUID taskId = insertTaskAtCreatableState(
                tenantId, roomId, title, typeCode, "housekeeping", null, workDate, "pool", "desk");
        execute("""
                UPDATE tasks
                   SET pool_source = ?, priority_notify = ?, return_reason = ?, requeue_note = ?,
                       rule_hit = ?, reminder_stage = ?
                 WHERE tenant_id = ? AND id = ?
                """, poolSourceCode, priorityNotify, returnReason, handoverNote, ruleHit,
                reminderStageCode, tenantId, taskId);
        return taskId;
    }

    public static int floorFromRoomNo(String roomNo) {
        if (roomNo == null || roomNo.isEmpty() || !Character.isDigit(roomNo.charAt(0))) {
            return 0;
        }
        return Character.getNumericValue(roomNo.charAt(0));
    }

    public CreatedStaff createFieldStaff(String name, String deptCode, String stateCode) {
        return insertStaff(name, deptCode, deptCode, stateCode);
    }

    public CreatedStaff createManagerLikeStaff(String name, String roleCode, String stateCode) {
        return insertStaff(name, roleCode, null, stateCode);
    }

    private CreatedStaff insertStaff(String name, String roleCode, String deptCode, String stateCode) {
        boolean active = "active".equals(stateCode);
        long tenantId = ensureTenant();
        UUID id = UUID.randomUUID();
        String loginId = "fx-" + id.toString().replace("-", "").substring(0, 12);
        String passwordHash = passwordEncoder.encode(DEFAULT_PASSWORD);
        OffsetDateTime now = OffsetDateTime.now();
        execute("""
                INSERT INTO staff_accounts
                    (id, tenant_id, name, login_id, password_hash, role, dept, active, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, name, loginId, passwordHash, roleCode, deptCode, active, stateCode, now, now);
        return new CreatedStaff(id, name, loginId, DEFAULT_PASSWORD, roleCode, deptCode);
    }

    public void setCredentials(UUID staffId, String loginId, String rawPassword) {
        execute("UPDATE staff_accounts SET login_id = ?, password_hash = ?, updated_at = ? WHERE tenant_id = ? AND id = ?",
                loginId, passwordEncoder.encode(rawPassword), OffsetDateTime.now(), ensureTenant(), staffId);
        jwtByStaffId.remove(staffId);
    }

    public void setFailedLoginCount(UUID staffId, int count) {
        OffsetDateTime lockedUntil = count >= 5 ? OffsetDateTime.now().plusMinutes(15) : null;
        execute("UPDATE staff_accounts SET failed_login_count = ?, locked_until = ?, updated_at = ?"
                        + " WHERE tenant_id = ? AND id = ?",
                count, lockedUntil, OffsetDateTime.now(), ensureTenant(), staffId);
    }

    public void markMustChangePassword(UUID staffId) {
        execute("UPDATE staff_accounts SET must_change_password = true, updated_at = ? WHERE tenant_id = ? AND id = ?",
                OffsetDateTime.now(), ensureTenant(), staffId);
    }

    public void setRedoCount(UUID taskId, int redoCount) {
        execute("UPDATE tasks SET redo_count = ? WHERE tenant_id = ? AND id = ?",
                redoCount, ensureTenant(), taskId);
    }

    public void backdateWorkStart(UUID taskId, int workedMinutes) {
        execute("UPDATE tasks SET worked_sec = 0, segment_started_at = ? WHERE tenant_id = ? AND id = ?",
                OffsetDateTime.now().minusMinutes(workedMinutes), ensureTenant(), taskId);
    }

    public void backdateTaskTimeline(UUID taskId, int minutes) {
        long tenantId = ensureTenant();
        execute("UPDATE task_events SET created_at = created_at - make_interval(mins => ?)"
                + " WHERE tenant_id = ? AND task_id = ?", minutes, tenantId, taskId);
        execute("UPDATE tasks SET started_at = started_at - make_interval(mins => ?),"
                + " updated_at = updated_at - make_interval(mins => ?)"
                + " WHERE tenant_id = ? AND id = ?", minutes, minutes, tenantId, taskId);
    }

    public void freezeHandoverItem(
            String deptCode, OffsetDateTime frozenAt, String target, String content, String authorName) {
        String shift = frozenAt.toLocalTime().isBefore(LocalTime.NOON) ? "morning" : "evening";
        String snapshot = toJson(List.of(
                Map.of("target", target, "content", content, "authorName", authorName)));
        execute("""
                INSERT INTO handovers
                    (id, tenant_id, dept, shift, work_date, snapshot, generated_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, 'system', ?)
                """, UUID.randomUUID(), ensureTenant(), deptCode, shift, frozenAt.toLocalDate(),
                snapshot, frozenAt);
    }

    public void failInspection(UUID taskId, CreatedStaff inspector, String failedItemLabel) {
        long tenantId = ensureTenant();
        String failKey = ensureInspectionItem(tenantId, failedItemLabel);
        List<Map<String, Object>> items = activeInspectionItemKeys(tenantId).stream()
                .map(key -> Map.<String, Object>of("key", key, "pass", !failKey.equals(key)))
                .toList();
        postInspection(taskId, inspector, items);
    }

    private List<String> activeInspectionItemKeys(long tenantId) {
        return queryRows(
                "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection'"
                        + " AND active = true ORDER BY sort_order", tenantId)
                .stream()
                .map(row -> String.valueOf(row.get("item_key")))
                .toList();
    }

    private void postInspection(UUID taskId, CreatedStaff inspector, List<Map<String, Object>> items) {
        Map<String, Object> body = Map.of("taskId", taskId.toString(), "items", items);
        HttpResponse<String> response = send(request("POST", "/inspections", jwt(inspector))
                .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8)));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("fixture 查房判定失敗：POST /inspections -> HTTP "
                    + response.statusCode() + "，body=" + response.body());
        }
    }

    private String ensureInspectionItem(long tenantId, String label) {
        Optional<Object> existing = queryScalar(
                "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection' AND label = ?",
                tenantId, label);
        if (existing.isPresent()) {
            return String.valueOf(existing.get());
        }
        String itemKey = "adhoc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        execute("""
                INSERT INTO sop_items (id, tenant_id, applies_to, item_key, label, sort_order, active, created_at)
                VALUES (?, ?, 'inspection', ?, ?, 999, true, ?)
                """, UUID.randomUUID(), tenantId, itemKey, label, OffsetDateTime.now());
        return itemKey;
    }

    public void insertRoomMemory(
            String roomNo, String kind, String title, String excerpt, OffsetDateTime occurredAt) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom(roomNo, floorFromRoomNo(roomNo));
        execute("""
                INSERT INTO room_memory (id, tenant_id, room_id, kind, summary, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, roomId, kind, title + "：" + excerpt, occurredAt);
    }

    public void skipSopItem(UUID taskId, CreatedStaff staff, String label) {
        UUID sopItemId = ensureCleaningSopItemId(label);
        HttpResponse<String> response = send(
                request("POST", "/tasks/" + taskId + "/sop-check", jwt(staff))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                toJson(Map.of("sopItemId", sopItemId, "result", "skipped")),
                                StandardCharsets.UTF_8)));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("fixture 跳過 SOP 項失敗：POST /api/v1/tasks/" + taskId
                    + "/sop-check（sopItemId=" + sopItemId + "） -> HTTP " + response.statusCode()
                    + "，body=" + response.body());
        }
    }

    public UUID ensureCleaningSopItemId(String label) {
        long tenantId = ensureTenant();
        ensureCleaningSopItem(tenantId, label);
        return (UUID) queryScalar(
                "SELECT id FROM sop_items WHERE tenant_id = ? AND applies_to = 'cleaning' AND label = ?",
                tenantId, label)
                .orElseThrow(() -> new IllegalStateException("補不出打掃 SOP 項：" + label));
    }

    public void setTenantConfig(long tenantId, String key, Object value) {
        execute("""
                INSERT INTO tenant_config (tenant_id, key, value, updated_at)
                VALUES (?, ?, ?::jsonb, ?)
                ON CONFLICT (tenant_id, key) DO UPDATE SET value = EXCLUDED.value,
                                                           updated_at = EXCLUDED.updated_at
                """, tenantId, key, toJson(value), OffsetDateTime.now());
    }

    private void withInspectionWaivedDuringStart(
            long tenantId, boolean inspectionWaived, Runnable start) {
        if (!inspectionWaived) {
            start.run();
            return;
        }
        setTenantConfig(tenantId, "inspection_waived_enabled", true);
        try {
            start.run();
        } finally {
            clearTenantConfig(tenantId, "inspection_waived_enabled");
        }
    }

    public void clearTenantConfig(long tenantId, String key) {
        execute("DELETE FROM tenant_config WHERE tenant_id = ? AND key = ?", tenantId, key);
    }

    private String ensureCleaningSopItem(long tenantId, String label) {
        Optional<Object> existing = queryScalar(
                "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'cleaning' AND label = ?",
                tenantId, label);
        if (existing.isPresent()) {
            return String.valueOf(existing.get());
        }
        String itemKey = "adhoc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        execute("""
                INSERT INTO sop_items (id, tenant_id, applies_to, item_key, label, sort_order, active, created_at)
                VALUES (?, ?, 'cleaning', ?, ?, 999, true, ?)
                """, UUID.randomUUID(), tenantId, itemKey, label, OffsetDateTime.now());
        return itemKey;
    }

    public void reassignAfterShiftEnd(UUID taskId, CreatedStaff successor) {
        execute("UPDATE tasks SET state = 'accepted', assignee_id = ?, pool_source = 'shift_end_return',"
                        + " updated_at = ? WHERE tenant_id = ? AND id = ?",
                successor.id(), OffsetDateTime.now(), ensureTenant(), taskId);
    }

    public UUID createTask(
            String roomNo, String title, String typeCode, String deptCode, Integer estMin,
            String stateCode, CreatedStaff assignee, Integer workedMinutes, String holdReason) {
        return createTask(roomNo, title, typeCode, deptCode, estMin, stateCode, assignee,
                workedMinutes, holdReason, false);
    }

    public UUID createTask(
            String roomNo, String title, String typeCode, String deptCode, Integer estMin,
            String stateCode, CreatedStaff assignee, Integer workedMinutes, String holdReason,
            boolean inspectionWaived) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom(roomNo, floorFromRoomNo(roomNo));
        LocalDate workDate = "cleaning".equals(typeCode) ? LocalDate.now() : null;

        UUID taskId = insertTaskAtCreatableState(
                tenantId, roomId, title, typeCode, deptCode, estMin, workDate, "pool", "desk");

        switch (stateCode) {
            case "pool" -> { }
            case "accepted" -> claimAs(tenantId, taskId, requireAssignee(stateCode, assignee));
            case "doing" -> {
                claimAs(tenantId, taskId, requireAssignee(stateCode, assignee));
                withInspectionWaivedDuringStart(tenantId, inspectionWaived,
                        () -> postTaskAction(taskId, "start", assignee));
            }
            case "interrupted" -> {
                claimAs(tenantId, taskId, requireAssignee(stateCode, assignee));
                postTaskAction(taskId, "start", assignee);
                postTaskAction(taskId, "interrupt", assignee,
                        Map.of("reason", holdReason != null ? holdReason : "測試資料準備：中斷"));
            }
            case "done" -> {
                claimAs(tenantId, taskId, requireAssignee(stateCode, assignee));
                withInspectionWaivedDuringStart(tenantId, inspectionWaived,
                        () -> postTaskAction(taskId, "start", assignee));
                postTaskAction(taskId, "complete", assignee);
            }
            case "deferred" -> {
                claimAs(tenantId, taskId, requireAssignee(stateCode, assignee));
                postTaskAction(taskId, "defer", assignee,
                        Map.of("reason", holdReason != null ? holdReason : "測試資料準備：延後"));
            }
            case "cancelled" -> {
                claimAs(tenantId, taskId, requireAssignee(stateCode, assignee));
                postTaskAction(taskId, "cancel", assignee, Map.of("reason", "manager_void"));
            }
            case "inspected" -> {
                claimAs(tenantId, taskId, requireAssignee(stateCode, assignee));
                withInspectionWaivedDuringStart(tenantId, inspectionWaived,
                        () -> postTaskAction(taskId, "start", assignee));
                postTaskAction(taskId, "complete", assignee);
                if (!inspectionWaived) {
                    execute("UPDATE tasks SET state = 'inspected', inspection = 'passed'"
                            + " WHERE tenant_id = ? AND id = ?", tenantId, taskId);
                }
            }
            default -> throw new IllegalArgumentException(
                    "BackendChannel.createTask 目前支援 pool|accepted|doing|deferred|interrupted"
                            + "|cancelled|done|inspected，收到: "
                            + stateCode + "（其他狀態等對應情境實作時再擴充）");
        }

        if (workedMinutes != null) {
            if ("doing".equals(stateCode)) {
                execute("UPDATE tasks SET worked_sec = 0, segment_started_at = ? WHERE tenant_id = ? AND id = ?",
                        OffsetDateTime.now().minusMinutes(workedMinutes), tenantId, taskId);
            } else if (List.of("interrupted", "done", "inspected").contains(stateCode)) {
                execute("UPDATE tasks SET worked_sec = ?, segment_started_at = NULL WHERE tenant_id = ? AND id = ?",
                        workedMinutes * 60, tenantId, taskId);
            } else {
                throw new IllegalArgumentException(
                        "已做分鐘只在 doing|interrupted|done|inspected 有意義，收到 state=" + stateCode
                                + "。依契約（specs/truth/contracts/components/schemas/員工工作台.yaml"
                                + " 的 workedMinutes）accepted 與 deferred 的已做分鐘一律是 0，"
                                + "填非 0 是劇本寫錯");
            }
        }
        if (holdReason != null) {
            execute("UPDATE tasks SET hold_reason = ? WHERE tenant_id = ? AND id = ?",
                    holdReason, tenantId, taskId);
        }
        return taskId;
    }

    public UUID createRepairTicket(
            String ticketNo, String roomNo, String categoryCode, String stateCode,
            CreatedStaff reporter, CreatedStaff assignee, String description, boolean urgent) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom(roomNo, floorFromRoomNo(roomNo));

        String taskState = "done".equals(stateCode) ? "doing" : stateCode;
        UUID taskId = createTask(roomNo, categoryCode + " 工務單", "repair", "engineering", null,
                taskState, assignee, null, null);
        execute("UPDATE tasks SET source = 'staff' WHERE tenant_id = ? AND id = ?", tenantId, taskId);

        execute("UPDATE tasks SET urgent = ? WHERE tenant_id = ? AND id = ?", urgent, tenantId, taskId);
        execute("""
                INSERT INTO repair_tickets
                    (task_id, tenant_id, ticket_no, room_id, category, description, reporter_id,
                     source_task_id, created_at, is_urgent)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
                """, taskId, tenantId, ticketNo, roomId, categoryCode, description, reporter.id(),
                OffsetDateTime.now(), urgent);

        if ("done".equals(stateCode)) {
            completeRepairTask(taskId, requireAssignee(stateCode, assignee));
        }
        return taskId;
    }

    private List<UUID> parkOtherDoingTasks(long tenantId, CreatedStaff assignee) {
        if (assignee == null) {
            return List.of();
        }
        List<UUID> doing = new ArrayList<>();
        for (Map<String, Object> row : queryRows(
                "SELECT id FROM tasks WHERE tenant_id = ? AND assignee_id = ? AND state = 'doing'",
                tenantId, assignee.id())) {
            doing.add((UUID) row.get("id"));
        }
        for (UUID id : doing) {
            execute("UPDATE tasks SET state = 'accepted' WHERE tenant_id = ? AND id = ?", tenantId, id);
        }
        return doing;
    }

    public void downgradeRepairTicketUrgency(UUID ticketTaskId, CreatedStaff manager, String note) {
        postRepairTicketAction(ticketTaskId, "downgrade-urgency", manager, Map.of("note", note));
    }

    public void verifyRepairTicket(UUID ticketTaskId, CreatedStaff manager, String result, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", result);
        if (reason != null) {
            body.put("reason", reason);
        }
        postRepairTicketAction(ticketTaskId, "verify", manager, body);
    }

    public int verifyRepairTicketStatus(UUID ticketTaskId, CreatedStaff actor, String result, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", result);
        if (reason != null) {
            body.put("reason", reason);
        }
        return sendRepairTicketAction(ticketTaskId, "verify", actor, body).statusCode();
    }

    private void postRepairTicketAction(
            UUID ticketTaskId, String action, CreatedStaff actor, Map<String, Object> body) {
        String path = "/repair-tickets/" + ticketTaskId + "/" + action;
        HttpResponse<String> response = sendRepairTicketAction(ticketTaskId, action, actor, body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("fixture 工務單動作失敗：POST " + path + " -> HTTP "
                    + response.statusCode() + "，body=" + response.body());
        }
    }

    private HttpResponse<String> sendRepairTicketAction(
            UUID ticketTaskId, String action, CreatedStaff actor, Map<String, Object> body) {
        return send(request("POST", "/repair-tickets/" + ticketTaskId + "/" + action, jwt(actor))
                .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8)));
    }

    public void insertTodayReservation(String roomNo, String checkinHhmm, boolean vip) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom(roomNo, floorFromRoomNo(roomNo));
        LocalDate today = LocalDate.now();
        OffsetDateTime checkinAt = OffsetDateTime.of(
                today, LocalTime.parse(checkinHhmm), OffsetDateTime.now().getOffset());
        execute("""
                INSERT INTO reservations_cache
                    (id, tenant_id, reservation_ref, room_id, guest_name, lang, attrs,
                     checkin_at, checkout_at, source, synced_at, superseded_at)
                VALUES (?, ?, ?, ?, NULL, NULL, ?::jsonb, ?, ?, 'excel', ?, NULL)
                """, UUID.randomUUID(), tenantId, "excel:" + today + ":" + roomNo, roomId,
                vip ? "{\"vip\": true}" : "{}", checkinAt, checkinAt.plusDays(1).withHour(11),
                OffsetDateTime.now());
    }

    public void setTaskSource(UUID taskId, String source) {
        execute("UPDATE tasks SET source = ? WHERE tenant_id = ? AND id = ?",
                source, ensureTenant(), taskId);
    }

    public void closeRepairTicketAsVerified(UUID ticketTaskId, CreatedStaff verifier) {
        long tenantId = ensureTenant();
        OffsetDateTime now = OffsetDateTime.now();
        execute("UPDATE tasks SET state = 'closed', updated_at = ? WHERE tenant_id = ? AND id = ? AND state = 'done'",
                now, tenantId, ticketTaskId);
        execute("UPDATE repair_tickets SET verified_by = ?, verified_at = ? WHERE tenant_id = ? AND task_id = ?",
                verifier.id(), now, tenantId, ticketTaskId);
        appendTaskEvent(ticketTaskId, "repair_verified", "manager", verifier.id(), null, now);
    }

    private void appendTaskEvent(
            UUID taskId, String type, String actorRole, UUID actorId, Map<String, Object> payload,
            OffsetDateTime at) {
        long tenantId = ensureTenant();
        execute("UPDATE tasks SET event_seq = event_seq + 1 WHERE tenant_id = ? AND id = ?", tenantId, taskId);
        execute("""
                INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
                SELECT tenant_id, id, event_seq, ?, ?, ?, CAST(? AS jsonb), ?
                  FROM tasks WHERE tenant_id = ? AND id = ?
                """, type, actorRole, actorId, payload == null ? null : toJson(payload), at, tenantId, taskId);
    }

    public void replaceInspectionItems(List<String> labels) {
        long tenantId = ensureTenant();
        disableAllInspectionItems();
        int sortOrder = 0;
        for (String label : labels) {
            upsertInspectionItem(tenantId, label, sortOrder++);
        }
    }

    public void disableAllInspectionItems() {
        execute("UPDATE sop_items SET active = false WHERE tenant_id = ? AND applies_to = 'inspection'",
                ensureTenant());
    }

    public void appendInspectionItem(String label) {
        long tenantId = ensureTenant();
        int nextSortOrder = queryScalar(
                "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM sop_items"
                        + " WHERE tenant_id = ? AND applies_to = 'inspection' AND active = true", tenantId)
                .map(value -> ((Number) value).intValue())
                .orElse(0);
        upsertInspectionItem(tenantId, label, nextSortOrder);
    }

    private void upsertInspectionItem(long tenantId, String label, int sortOrder) {
        Optional<Object> existing = queryScalar(
                "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection' AND label = ?",
                tenantId, label);
        if (existing.isPresent()) {
            execute("UPDATE sop_items SET active = true, sort_order = ?"
                            + " WHERE tenant_id = ? AND applies_to = 'inspection' AND item_key = ?",
                    sortOrder, tenantId, String.valueOf(existing.get()));
            return;
        }
        String itemKey = "adhoc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        execute("""
                INSERT INTO sop_items (id, tenant_id, applies_to, item_key, label, sort_order, active, created_at)
                VALUES (?, ?, 'inspection', ?, ?, ?, true, ?)
                """, UUID.randomUUID(), tenantId, itemKey, label, sortOrder, OffsetDateTime.now());
    }

    public void passInspectionAs(UUID taskId, CreatedStaff inspector) {
        List<Map<String, Object>> items = activeInspectionItemKeys(ensureTenant()).stream()
                .map(key -> Map.<String, Object>of("key", key, "pass", true))
                .toList();
        postInspection(taskId, inspector, items);
    }

    public void setTaskDoneAt(UUID taskId, OffsetDateTime doneAt) {
        long tenantId = ensureTenant();
        execute("UPDATE tasks SET done_at = ?, work_date = ? WHERE tenant_id = ? AND id = ?",
                doneAt, doneAt.toLocalDate(), tenantId, taskId);
        execute("UPDATE task_events SET created_at = ? WHERE tenant_id = ? AND task_id = ? AND type = 'completed'",
                doneAt, tenantId, taskId);
    }

    public record RepairTicketSpec(
            String ticketNo,
            String roomNo,
            boolean publicArea,
            String categoryCode,
            String stateCode,
            CreatedStaff reporter,
            CreatedStaff assignee,
            String description,
            boolean urgent,
            String origin,
            String resolutionType,
            String resolutionNote,
            Integer completionPhotoCount,
            OffsetDateTime doneAt,
            CreatedStaff verifier,
            String verifyFailedReason,
            CreatedStaff verifyFailedBy,
            OffsetDateTime verifyFailedAt) {}

    public UUID createRepairTicket(RepairTicketSpec spec) {
        String state = spec.stateCode();
        boolean closed = "closed".equals(state);
        boolean finished = closed || "done".equals(state);
        if (!finished && spec.resolutionType() != null) {
            throw new IllegalArgumentException("工務單「" + spec.ticketNo()
                    + "」：處理結果只用於已完修與已結案的單，狀態=" + state);
        }
        String resolution = finished && spec.resolutionType() == null ? "fixed" : spec.resolutionType();
        boolean noActionNeeded = "no_action_needed".equals(resolution);
        if ("done".equals(state) && noActionNeeded) {
            throw new IllegalArgumentException("工務單「" + spec.ticketNo()
                    + "」：已完修的單不會是不需處理（不需處理完修當下就結案）");
        }
        if (closed && !noActionNeeded && spec.verifier() == null) {
            throw new IllegalArgumentException("工務單「" + spec.ticketNo()
                    + "」：已結案且處理結果不是不需處理時，驗收人必填");
        }
        if (spec.verifier() != null && (!closed || noActionNeeded)) {
            throw new IllegalArgumentException("工務單「" + spec.ticketNo()
                    + "」：驗收人只用於已結案、且處理結果不是不需處理的單");
        }
        if (spec.verifyFailedReason() == null
                ? spec.verifyFailedBy() != null || spec.verifyFailedAt() != null
                : spec.verifyFailedBy() == null) {
            throw new IllegalArgumentException("工務單「" + spec.ticketNo()
                    + "」：有不通過原因時不通過判定人必填；沒有不通過原因就不能寫判定人或時間");
        }

        CreatedStaff reporter = spec.reporter() != null ? spec.reporter() : spec.assignee();
        if (reporter == null) {
            throw new IllegalArgumentException("工務單「" + spec.ticketNo()
                    + "」沒有報修人也沒有處理人：repair_tickets.reporter_id 是 NOT NULL，定期保養的單至少要有處理人");
        }
        String roomNo = spec.roomNo();
        if (spec.publicArea()) {
            ensurePublicAreaRoom();
            roomNo = PUBLIC_AREA_ROOM_NO;
        }

        String baseState = finished ? "doing" : "cancelled".equals(state) ? "pool" : state;
        long tenantId = ensureTenant();
        List<UUID> parked = finished ? parkOtherDoingTasks(tenantId, spec.assignee()) : List.of();
        UUID taskId = createRepairTicket(spec.ticketNo(), roomNo, spec.categoryCode(), baseState,
                reporter, spec.assignee(), spec.description(), spec.urgent());

        if ("scheduled".equals(spec.origin())) {
            setTaskSource(taskId, "system");
        }
        if (finished) {
            int photos = spec.completionPhotoCount() != null
                    ? spec.completionPhotoCount() : (noActionNeeded ? 0 : 1);
            completeRepairTask(taskId, requireAssignee(state, spec.assignee()),
                    resolution, spec.resolutionNote(), photos);
            for (UUID doingId : parked) {
                execute("UPDATE tasks SET state = 'doing' WHERE tenant_id = ? AND id = ?", tenantId, doingId);
            }
            if ("cannot_fix".equals(resolution)) {
                setRoomHkState(roomNo, "out_of_order");
            }
            if (spec.doneAt() != null) {
                setTaskDoneAt(taskId, spec.doneAt());
            }
            if (closed && !noActionNeeded) {
                closeRepairTicketAsVerified(taskId, spec.verifier());
            }
        }
        if ("cancelled".equals(state)) {
            markWithdrawnByReporter(taskId, reporter);
        }
        if (spec.verifyFailedReason() != null) {
            OffsetDateTime failedAt = spec.verifyFailedAt();
            if (failedAt == null) {
                OffsetDateTime base = "done".equals(state) && spec.doneAt() != null ? spec.doneAt() : OffsetDateTime.now();
                failedAt = base.minusMinutes(30);
            }
            recordVerificationFailed(taskId, state, spec.assignee(),
                    spec.verifyFailedReason(), spec.verifyFailedBy(), failedAt);
        }
        return taskId;
    }

    private void markWithdrawnByReporter(UUID taskId, CreatedStaff reporter) {
        long tenantId = ensureTenant();
        OffsetDateTime now = OffsetDateTime.now();
        execute("UPDATE tasks SET state = 'cancelled', cancel_reason = 'staff_withdrew', updated_at = ?"
                        + " WHERE tenant_id = ? AND id = ? AND state = 'pool'",
                now, tenantId, taskId);
        String actorRole = reporter.dept() != null ? reporter.dept() : reporter.role();
        appendTaskEvent(taskId, "cancelled", actorRole, reporter.id(), Map.of("reason", "staff_withdrew"), now);
    }

    private void recordVerificationFailed(
            UUID taskId, String stateCode, CreatedStaff assignee, String reason, CreatedStaff failedBy,
            OffsetDateTime failedAt) {
        long tenantId = ensureTenant();
        boolean toPool = "pool".equals(stateCode);
        execute("""
                UPDATE repair_tickets
                   SET last_verify_failed_reason = ?, last_verify_failed_by = ?, last_verify_failed_at = ?
                 WHERE tenant_id = ? AND task_id = ?
                """, reason, failedBy.id(), failedAt, tenantId, taskId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", reason);
        payload.put("destination", toPool ? "pool" : "assignee");
        if (assignee != null) {
            payload.put("assigneeId", assignee.id().toString());
        }
        appendTaskEvent(taskId, "repair_verification_failed", "manager", failedBy.id(), payload, failedAt);
        if (toPool) {
            execute("""
                    UPDATE tasks SET pool_source = 'verification_return', assignee_id = NULL,
                           return_reason = '驗收不通過 · 原處理人已下班或請假'
                     WHERE tenant_id = ? AND id = ?
                    """, tenantId, taskId);
        }
    }

    public void completeRepairTask(
            UUID taskId, CreatedStaff assignee, String resolutionType, String resolutionNote, int photoCount) {
        List<String> photoIds = new ArrayList<>();
        for (int i = 0; i < photoCount; i++) {
            photoIds.add(createCompletionPhoto(assignee).toString());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("photoIds", photoIds);
        if (resolutionType != null) {
            body.put("resolutionType", resolutionType);
        }
        if (resolutionNote != null) {
            body.put("resolutionNote", resolutionNote);
        }
        postTaskAction(taskId, "complete", assignee, body);
    }

    public void insertReportPhotos(UUID ticketTaskId, UUID uploaderId, int count) {
        long tenantId = ensureTenant();
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            execute("""
                    INSERT INTO repair_photos
                        (id, tenant_id, ticket_task_id, purpose, status, storage_ref, taken_at,
                         uploaded_by, created_at, uploaded_at)
                    VALUES (?, ?, ?, 'report', 'uploaded', ?, ?, ?, ?, ?)
                    """, id, tenantId, ticketTaskId, "fixture/" + id + ".jpg", now, uploaderId, now, now);
        }
    }

    public void setRoomHkState(String roomNo, String hkState) {
        execute("UPDATE rooms SET hk_state = ?, updated_at = ? WHERE tenant_id = ? AND room_no = ?",
                hkState, OffsetDateTime.now(), ensureTenant(), roomNo);
    }

    public String roomHkState(String roomNo) {
        return String.valueOf(queryScalar(
                "SELECT hk_state FROM rooms WHERE tenant_id = ? AND room_no = ?", ensureTenant(), roomNo)
                .orElseThrow(() -> new IllegalStateException("後端沒有房號「" + roomNo + "」的房間")));
    }

    public List<String> pushTitlesSentTo(UUID staffId) {
        return queryRows("""
                SELECT COALESCE(payload->'payload'->>'title', payload->>'title') AS title FROM outbox
                 WHERE tenant_id = ? AND kind = 'push'
                   AND COALESCE(payload->'payload'->>'recipientStaffId', payload->>'recipientStaffId') = ?
                 ORDER BY id DESC
                """, ensureTenant(), staffId.toString())
                .stream().map(row -> String.valueOf(row.get("title"))).toList();
    }

    public JsonNode managerQueues(CreatedStaff manager) {
        return getJson("/me/manager-queues", manager);
    }

    public void assignPlanRoom(UUID staffId, int floor, String shiftEndHhmm) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom("PLANROOM-F" + floor, floor);
        OffsetDateTime now = OffsetDateTime.now();
        UUID planId = UUID.randomUUID();
        execute("""
                INSERT INTO cleaning_plans
                    (id, tenant_id, work_date, shift, status, version, generated_by, algo_version, generated_at)
                VALUES (?, ?, ?, ?, 'draft', 0, 'system', 'v1', ?)
                """, planId, tenantId, now.toLocalDate(), shiftEndHhmm, now);
        execute("""
                INSERT INTO plan_rooms (id, tenant_id, plan_id, room_id, staff_id, est_min, route_seq)
                VALUES (?, ?, ?, ?, ?, 30, 1)
                """, UUID.randomUUID(), tenantId, planId, roomId, staffId);
    }

    public CreatedStaff createStaff(String name, String roleCode, String deptCode, String stateCode) {
        return insertStaff(name, roleCode, deptCode, stateCode);
    }

    public void completeRepairTask(UUID taskId, CreatedStaff assignee) {
        UUID photoId = createCompletionPhoto(assignee);
        postTaskAction(taskId, "complete", assignee,
                Map.of("photoIds", List.of(photoId.toString())));
    }

    private UUID createCompletionPhoto(CreatedStaff uploader) {
        Map<String, Object> body = Map.of(
                "purpose", "completion",
                "takenAt", OffsetDateTime.now().toString());
        HttpResponse<String> created = send(request("POST", "/repair-photos", jwt(uploader))
                .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8)));
        if (created.statusCode() != 201 && created.statusCode() != 200) {
            throw new IllegalStateException("建立完修照片失敗：POST /repair-photos -> HTTP "
                    + created.statusCode() + "，body=" + created.body());
        }
        UUID photoId = UUID.fromString(parseJson(created.body()).path("id").asText());

        HttpResponse<String> uploaded = send(
                request("PUT", "/repair-photos/" + photoId + "/content", jwt(uploader))
                        .header("Content-Type", "image/jpeg")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(PLACEHOLDER_JPEG)));
        if (uploaded.statusCode() < 200 || uploaded.statusCode() >= 300) {
            throw new IllegalStateException("上傳完修照片失敗：PUT /repair-photos/" + photoId
                    + "/content -> HTTP " + uploaded.statusCode() + "，body=" + uploaded.body());
        }
        return photoId;
    }

    public int allocateNextRepairTicketNo() {
        Object lastNo = queryScalar("""
                INSERT INTO repair_ticket_counters (tenant_id, last_no) VALUES (?, 1)
                ON CONFLICT (tenant_id) DO UPDATE SET last_no = repair_ticket_counters.last_no + 1
                RETURNING last_no
                """, ensureTenant())
                .orElseThrow(() -> new IllegalStateException("repair_ticket_counters 取號沒有回傳 last_no"));
        return ((Number) lastNo).intValue();
    }

    public UUID createNotification(
            UUID recipientStaffId, String event, String title, OffsetDateTime createdAt, boolean read,
            Map<String, Object> target) {
        long tenantId = ensureTenant();
        UUID id = UUID.randomUUID();
        execute("""
                INSERT INTO notifications
                    (id, tenant_id, recipient_staff_id, event, title, target, created_at, read_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                """, id, tenantId, recipientStaffId, event, title, toJson(target),
                createdAt, read ? createdAt : null);
        return id;
    }

    private UUID insertTaskAtCreatableState(
            long tenantId, UUID roomId, String title, String typeCode, String deptCode,
            Integer estMin, LocalDate workDate, String initialState, String source) {
        UUID taskId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        execute("""
                INSERT INTO tasks
                    (id, tenant_id, type, source, room_id, dept, state, event_seq, title, est_min,
                     work_date, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?)
                """, taskId, tenantId, typeCode, source, roomId, deptCode, initialState, title, estMin,
                workDate, now, now);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", typeCode);
        snapshot.put("source", source);
        snapshot.put("room_id", roomId.toString());
        snapshot.put("dept", deptCode);
        snapshot.put("state", initialState);
        snapshot.put("title", title);
        snapshot.put("est_min", estMin);
        snapshot.put("work_date", workDate == null ? null : workDate.toString());
        execute("""
                INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
                VALUES (?, ?, 1, 'created', 'system', NULL, CAST(? AS jsonb), ?)
                """, tenantId, taskId, toJson(snapshot), now);
        return taskId;
    }

    private void claimAs(long tenantId, UUID taskId, CreatedStaff assignee) {
        if (!"manager".equals(assignee.role())) {
            postTaskAction(taskId, "claim", assignee);
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        execute("UPDATE tasks SET state = 'accepted', assignee_id = ?, event_seq = event_seq + 1, updated_at = ?"
                        + " WHERE tenant_id = ? AND id = ? AND state = 'pool'",
                assignee.id(), now, tenantId, taskId);
        execute("""
                INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
                SELECT tenant_id, id, event_seq, 'claimed', 'manager', ?, CAST(? AS jsonb), ?
                  FROM tasks WHERE tenant_id = ? AND id = ?
                """, assignee.id(), toJson(Map.of("assignee_id", assignee.id().toString())), now, tenantId, taskId);
    }

    private static CreatedStaff requireAssignee(String stateCode, CreatedStaff assignee) {
        if (assignee == null) {
            throw new IllegalArgumentException("state=" + stateCode + " 需要 assignee（呼叫端未提供）");
        }
        return assignee;
    }

    public String jwt(CreatedStaff staff) {
        return jwtByStaffId.computeIfAbsent(staff.id(), ignored -> login(staff.loginId(), staff.password()));
    }

    public String login(String loginId, String password) {
        Map<String, Object> body = Map.of("loginId", loginId, "password", password);
        HttpResponse<String> response = send(request("POST", "/staff-accounts/login", null)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8))
                .header("Content-Type", "application/json"));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("登入失敗：POST /staff-accounts/login -> HTTP "
                    + response.statusCode() + "，body=" + response.body());
        }
        JsonNode token = parseJson(response.body()).path("token");
        if (token.isMissingNode() || token.isNull()) {
            throw new IllegalStateException("登入回應沒有 token 欄位：" + response.body());
        }
        return token.asText();
    }

    public void postTaskAction(UUID taskId, String action, CreatedStaff actor) {
        postTaskAction(taskId, action, actor, null);
    }

    public void postTaskAction(UUID taskId, String action, CreatedStaff actor, Map<String, Object> body) {
        HttpRequest.Builder builder = request("POST", "/tasks/" + taskId + "/" + action, jwt(actor))
                .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
        if (body == null) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json");
        }
        HttpResponse<String> response = send(builder);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("fixture 狀態轉移失敗：POST /tasks/" + taskId + "/" + action
                    + " -> HTTP " + response.statusCode() + "，body=" + response.body());
        }
    }

    public JsonNode taskActionRejection(UUID taskId, String action, CreatedStaff actor) {
        HttpResponse<String> response = send(
                request("POST", "/tasks/" + taskId + "/" + action, jwt(actor))
                        .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        .POST(HttpRequest.BodyPublishers.noBody()));
        if (response.statusCode() < 400 || response.statusCode() >= 500) {
            throw new AssertionError("預期後端以 4xx 拒絕 POST /tasks/" + taskId + "/" + action
                    + "，實際 HTTP " + response.statusCode() + "，body=" + response.body());
        }
        return parseJson(response.body());
    }

    public JsonNode getJson(String path, CreatedStaff viewer) {
        HttpResponse<String> response = send(request("GET", path, jwt(viewer)).GET());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("權威讀回失敗：GET " + path + " -> HTTP "
                    + response.statusCode() + "，body=" + response.body());
        }
        return parseJson(response.body());
    }

    public Map<String, Object> taskRow(UUID taskId) {
        return singleRow("SELECT * FROM tasks WHERE tenant_id = ? AND id = ?",
                "tasks id=" + taskId, ensureTenant(), taskId);
    }

    public Map<String, Object> repairTicketRow(String ticketNo) {
        return singleRow("""
                SELECT rt.*, t.state AS task_state, t.urgent AS task_urgent
                FROM repair_tickets rt JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                WHERE rt.tenant_id = ? AND rt.ticket_no = ?
                """, "repair_tickets ticket_no=" + ticketNo, ensureTenant(), ticketNo);
    }

    public Map<String, Object> staffAccountRow(UUID staffId) {
        return singleRow("SELECT * FROM staff_accounts WHERE tenant_id = ? AND id = ?",
                "staff_accounts id=" + staffId, ensureTenant(), staffId);
    }

    public List<Map<String, Object>> queryRows(String sql, Object... params) {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                return toRows(resultSet);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("後端資料庫查詢失敗：" + sql, e);
        }
    }

    private Map<String, Object> singleRow(String sql, String what, Object... params) {
        List<Map<String, Object>> rows = queryRows(sql, params);
        if (rows.size() != 1) {
            throw new IllegalStateException("預期剛好一列（" + what + "），實際 " + rows.size() + " 列");
        }
        return rows.get(0);
    }

    private synchronized Connection connection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                connection.setAutoCommit(true);
            }
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "連不上後端資料庫（" + dbUrl + "）。docker compose 的 postgres 起了嗎？"
                            + "（對外埠 5433，見 docker-compose.yml）", e);
        }
    }

    private void execute(String sql, Object... params) {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("後端資料庫寫入失敗：" + sql, e);
        }
    }

    private Optional<Object> queryScalar(String sql, Object... params) {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(resultSet.getObject(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("後端資料庫查詢失敗：" + sql, e);
        }
    }

    private static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    private static List<Map<String, Object>> toRows(ResultSet resultSet) throws SQLException {
        ResultSetMetaData meta = resultSet.getMetaData();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                row.put(meta.getColumnLabel(i), resultSet.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    private HttpRequest.Builder request(String method, String path, String jwt) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + BASE_PATH + path))
                .timeout(Duration.ofSeconds(10));
        if (jwt != null) {
            builder.header("Authorization", "Bearer " + jwt);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            return HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException(
                    "連不上後端（" + baseUrl + "）。後端起了嗎？（docker compose up -d ＋ "
                            + "TMS_DB_URL=jdbc:postgresql://localhost:5433/tms ./mvnw -f tms/pom.xml spring-boot:run）", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 JSON 失敗", e);
        }
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("解析後端回應 JSON 失敗：" + body, e);
        }
    }
}
