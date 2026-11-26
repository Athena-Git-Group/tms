package com.athena.bddsupport;

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
import java.util.Collections;
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

    private static final String TEST_TENANT_CODE = "test-hotel";

    public static final String FRONTDESK_TENANT_NAME = "雅典娜酒店";

    public static final String FRONTDESK_TENANT_CODE = "athena";

    public static final String FRONTDESK_PASSWORD = "Tms12345";

    private static final int FRONTDESK_FIRST_FLOOR = 2;

    private static final int FRONTDESK_LAST_FLOOR = 5;

    private static final int FRONTDESK_ROOMS_PER_FLOOR = 16;

    private record DefaultStaff(String name, String loginId, String role, String dept) {}

    private static final List<DefaultStaff> FRONTDESK_STAFF = List.of(
            new DefaultStaff("林佩君", "F0101", "frontdesk", "frontdesk"),
            new DefaultStaff("何志偉", "F0102", "frontdesk", "frontdesk"),
            new DefaultStaff("阮氏香", "F0103", "frontdesk", "frontdesk"),
            new DefaultStaff("許雅婷", "F0199", "frontdesk", "frontdesk"),
            new DefaultStaff("張主管", "M0001", "manager", null),
            new DefaultStaff("吳家豪", "D0001", "duty_manager", null),
            new DefaultStaff("小美", "H0201", "housekeeping", "housekeeping"),
            new DefaultStaff("小林", "H0202", "housekeeping", "housekeeping"),
            new DefaultStaff("小陳", "H0203", "housekeeping", "housekeeping"),
            new DefaultStaff("阿美", "H0204", "housekeeping", "housekeeping"),
            new DefaultStaff("阿強", "E0301", "engineering", "engineering"),
            new DefaultStaff("陳老闆", "O0001", "owner", null));

    private static final List<Map<String, String>> FRONTDESK_INFO_PAGES = List.of(
            infoPage("breakfast", "早餐時間", "早餐 07:00–10:00，在 2 樓自助餐廳，憑房卡入場。"),
            infoPage("wifi", "Wi-Fi", "Wi-Fi 名稱 Athena-Guest，密碼是房號加姓氏拼音。"),
            infoPage("checkout", "退房時間", "退房時間是中午 12:00。"),
            infoPage("gym", "健身房", "健身房在 B1，06:00–22:00 開放。"),
            infoPage("taxi", "叫車", "需要叫車請告訴櫃檯時間與目的地，我們幫您安排。"));

    private static final List<Map<String, String>> GUEST_INFO_PAGES = List.of(
            infoPage("breakfast", "早餐", "一樓餐廳 06:30–10:00"),
            infoPage("wifi", "Wi‑Fi", "Wi‑Fi 名稱 Athena-Guest，密碼是房號加姓氏拼音。"),
            infoPage("checkout", "退房時間", "中午 12:00 前"));

    private static final String GUEST_STAYING_ROOM_NO = "311";

    private static final String GUEST_STAYING_NAME = "測試住客";

    private static final String MANAGER_NAME = "張主管";

    private static final String DESK_CLERK_NAME = "林佩君";

    private static Map<String, String> infoPage(String key, String title, String body) {
        Map<String, String> page = new LinkedHashMap<>();
        page.put("key", key);
        page.put("title", title);
        page.put("body", body);
        return page;
    }

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
            Map.entry("值班經理", "duty_manager"),
            Map.entry("值班主管", "duty_manager"),
            Map.entry("老闆", "owner"));

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

    private static final String FAULT_TRIGGER_PREFIX = "bdd_fault_";

    private Connection connection;
    private Long cachedTenantId;
    private String frontdeskPasswordHash;
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
        cachedTenantId = insertTenant(TEST_TENANT_NAME, TEST_TENANT_CODE);
        return cachedTenantId;
    }

    private long insertTenant(String name, String code) {
        Object id = queryScalar("INSERT INTO tenants (name, code, created_at) VALUES (?, ?, ?) RETURNING id",
                name, code, OffsetDateTime.now())
                .orElseThrow(() -> new IllegalStateException("INSERT tenants 沒有回傳 id"));
        return ((Number) id).longValue();
    }

    public record FrontdeskDefaults(long tenantId, String tenantCode, Map<String, CreatedStaff> staffByName) {

        public CreatedStaff staff(String name) {
            CreatedStaff staff = staffByName.get(name);
            if (staff == null) {
                throw new IllegalArgumentException(
                        "「" + name + "」不是櫃檯 Web 的預設帳號（有：" + staffByName.keySet() + "）");
            }
            return staff;
        }
    }

    public synchronized FrontdeskDefaults ensureFrontdeskDefaults() {
        long tenantId = ensureFrontdeskTenant();
        cachedTenantId = tenantId;
        try {
            Connection db = connection();
            insertFrontdeskRooms(db, tenantId, "DO NOTHING");
            Map<String, CreatedStaff> staffByName = upsertFrontdeskStaff(db, tenantId);
            setTenantConfig(tenantId, "guest_info_pages", FRONTDESK_INFO_PAGES);
            return new FrontdeskDefaults(tenantId, FRONTDESK_TENANT_CODE, Collections.unmodifiableMap(staffByName));
        } catch (SQLException e) {
            throw new IllegalStateException("建立櫃檯 Web 的預設房間與帳號失敗", e);
        }
    }

    /**
     * 把資料庫恢復成櫃檯 Web 每個場景開頭的樣子，效果等同 {@link #resetBusinessData()} 之後再 {@link #ensureFrontdeskDefaults()}。
     *
     * <p>房間與員工帳號是每個場景都一樣的基準資料，所以不清空重建，只把場景改過的部分蓋回去：
     * 不屬於預設的房間與帳號刪掉，預設的那幾列除了 id 之外整列寫回建立時的值。
     * 只蓋部分欄位的話，前一個場景改過的房況、帳號狀態或密碼會留到下一個場景。
     * 其餘資料表一律清空。整件事和 {@link #resetBusinessData()} 一樣先鎖好所有表再動手，理由寫在那裡。
     */
    public synchronized FrontdeskDefaults resetToFrontdeskDefaults() {
        long tenantId = ensureFrontdeskTenant();
        cachedTenantId = tenantId;
        List<String> tables = resettableTables();
        List<String> cleared = tables.stream().filter(t -> !BASELINE_TABLES.contains(t)).toList();
        Map<String, CreatedStaff> staffByName = new LinkedHashMap<>();
        lockAllThen("LOCK TABLE " + String.join(", ", tables) + " IN ACCESS EXCLUSIVE MODE", db -> {
            try (java.sql.Statement statement = db.createStatement()) {
                statement.execute("TRUNCATE TABLE " + String.join(", ", cleared) + " RESTART IDENTITY CASCADE");
            }
            deleteRowsOutsideFrontdeskDefaults(db, tenantId);
            insertFrontdeskRooms(db, tenantId, """
                    DO UPDATE SET floor = EXCLUDED.floor, room_type = EXCLUDED.room_type, hk_state = EXCLUDED.hk_state,
                        occupancy = EXCLUDED.occupancy, updated_at = EXCLUDED.updated_at""");
            staffByName.clear();
            staffByName.putAll(upsertFrontdeskStaff(db, tenantId));
            try (PreparedStatement statement = db.prepareStatement(
                    "INSERT INTO tenant_config (tenant_id, key, value, updated_at) VALUES (?, ?, ?::jsonb, ?)")) {
                bind(statement, tenantId, "guest_info_pages", toJson(FRONTDESK_INFO_PAGES), OffsetDateTime.now());
                statement.executeUpdate();
            }
        }, "還原櫃檯 Web 的預設資料");
        jwtByStaffId.clear();
        return new FrontdeskDefaults(tenantId, FRONTDESK_TENANT_CODE, Collections.unmodifiableMap(staffByName));
    }

    private static final List<String> BASELINE_TABLES = List.of("rooms", "staff_accounts");

    private List<String> resettableTables;

    private List<String> resettableTables() {
        if (resettableTables == null) {
            resettableTables = queryRows("""
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                      AND table_name NOT IN ('flyway_schema_history', 'tenants')
                    ORDER BY table_name
                    """).stream().map(r -> (String) r.get("table_name")).toList();
        }
        return resettableTables;
    }

    private void deleteRowsOutsideFrontdeskDefaults(Connection db, long tenantId) throws SQLException {
        try (PreparedStatement rooms = db.prepareStatement(
                "DELETE FROM rooms WHERE tenant_id <> ? OR NOT (room_no = ANY (?))");
             PreparedStatement staff = db.prepareStatement(
                     "DELETE FROM staff_accounts WHERE tenant_id <> ? OR NOT (login_id = ANY (?))")) {
            rooms.setLong(1, tenantId);
            rooms.setArray(2, db.createArrayOf("text", frontdeskRoomNumbers().toArray()));
            rooms.executeUpdate();
            staff.setLong(1, tenantId);
            staff.setArray(2, db.createArrayOf("text", FRONTDESK_STAFF.stream().map(DefaultStaff::loginId).toArray()));
            staff.executeUpdate();
        }
    }

    private static List<String> frontdeskRoomNumbers() {
        List<String> roomNumbers = new ArrayList<>();
        for (int floor = FRONTDESK_FIRST_FLOOR; floor <= FRONTDESK_LAST_FLOOR; floor++) {
            for (int seq = 1; seq <= FRONTDESK_ROOMS_PER_FLOOR; seq++) {
                roomNumbers.add(String.format("%d%02d", floor, seq));
            }
        }
        return roomNumbers;
    }

    private void insertFrontdeskRooms(Connection db, long tenantId, String onConflict) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement("""
                INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state, occupancy, updated_at)
                SELECT gen_random_uuid(), ?, room_no, room_no::int / 100, 'standard_double', 'cleaned', 'vacant', ?
                FROM unnest(?::text[]) AS room_no
                ON CONFLICT (tenant_id, room_no)
                """ + onConflict)) {
            statement.setLong(1, tenantId);
            statement.setObject(2, OffsetDateTime.now());
            statement.setArray(3, db.createArrayOf("text", frontdeskRoomNumbers().toArray()));
            statement.executeUpdate();
        }
    }

    public synchronized void ensureGuestDefaults() {
        long tenantId = ensureFrontdeskTenant();
        if (!Long.valueOf(tenantId).equals(cachedTenantId)) {
            throw new IllegalStateException("住客掃碼頁的預設資料要建在「" + FRONTDESK_TENANT_NAME
                    + "」，先呼叫 ensureFrontdeskDefaults()");
        }
        OffsetDateTime now = backendNow();
        OffsetDateTime startOfToday = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        createReservation(GUEST_STAYING_ROOM_NO, GUEST_STAYING_NAME, false,
                startOfToday, startOfToday.plusDays(2).withHour(12));
        setTenantConfig(tenantId, "guest_info_pages", GUEST_INFO_PAGES);
    }

    private long ensureFrontdeskTenant() {
        List<Map<String, Object>> rows = queryRows(
                "SELECT id, name FROM tenants WHERE code = ?", FRONTDESK_TENANT_CODE);
        if (rows.isEmpty()) {
            return insertTenant(FRONTDESK_TENANT_NAME, FRONTDESK_TENANT_CODE);
        }
        Map<String, Object> row = rows.get(0);
        if (!FRONTDESK_TENANT_NAME.equals(row.get("name"))) {
            throw new IllegalStateException("飯店代碼 " + FRONTDESK_TENANT_CODE + " 已經給了「" + row.get("name")
                    + "」，不能當成櫃檯 Web 的預設飯店「" + FRONTDESK_TENANT_NAME + "」");
        }
        return ((Number) row.get("id")).longValue();
    }

    private Map<String, CreatedStaff> upsertFrontdeskStaff(Connection db, long tenantId) throws SQLException {
        if (frontdeskPasswordHash == null) {
            frontdeskPasswordHash = passwordEncoder.encode(FRONTDESK_PASSWORD);
        }
        Map<String, UUID> idByLoginId = new LinkedHashMap<>();
        try (PreparedStatement statement = db.prepareStatement("""
                INSERT INTO staff_accounts
                    (id, tenant_id, name, login_id, password_hash, role, dept, active, status,
                     must_change_password, failed_login_count, locked_until, created_at, updated_at)
                SELECT gen_random_uuid(), ?, s.name, s.login_id, ?, s.role, s.dept, true, 'active', false, 0, NULL, ?, ?
                FROM unnest(?::text[], ?::text[], ?::text[], ?::text[]) AS s(name, login_id, role, dept)
                ON CONFLICT (tenant_id, login_id) DO UPDATE
                    SET name = EXCLUDED.name, password_hash = EXCLUDED.password_hash,
                        role = EXCLUDED.role, dept = EXCLUDED.dept, active = true, status = 'active',
                        must_change_password = false, failed_login_count = 0, locked_until = NULL,
                        created_at = EXCLUDED.created_at, updated_at = EXCLUDED.updated_at
                RETURNING id, login_id
                """)) {
            OffsetDateTime now = OffsetDateTime.now();
            bind(statement, tenantId, frontdeskPasswordHash, now, now);
            statement.setArray(5, db.createArrayOf("text", FRONTDESK_STAFF.stream().map(DefaultStaff::name).toArray()));
            statement.setArray(6, db.createArrayOf("text", FRONTDESK_STAFF.stream().map(DefaultStaff::loginId).toArray()));
            statement.setArray(7, db.createArrayOf("text", FRONTDESK_STAFF.stream().map(DefaultStaff::role).toArray()));
            statement.setArray(8, db.createArrayOf("text", FRONTDESK_STAFF.stream().map(DefaultStaff::dept).toArray()));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    idByLoginId.put(resultSet.getString("login_id"), (UUID) resultSet.getObject("id"));
                }
            }
        }
        Map<String, CreatedStaff> staffByName = new LinkedHashMap<>();
        for (DefaultStaff staff : FRONTDESK_STAFF) {
            UUID id = idByLoginId.get(staff.loginId());
            if (id == null) {
                throw new IllegalStateException("寫入預設帳號 " + staff.loginId() + " 沒有回傳 id");
            }
            staffByName.put(staff.name(), new CreatedStaff(id, staff.name(), staff.loginId(), FRONTDESK_PASSWORD,
                    staff.role(), staff.dept()));
        }
        return staffByName;
    }

    /**
     * 清空業務資料，每個場景開頭呼叫。後端這時候可能還在讀寫這些資料表，例如看板查詢、班末掃描或即時訊號組卡片。
     *
     * <p>這裡要保證清空不會和後端互相卡死，也不會害後端的查詢被資料庫中止。
     * TRUNCATE 要一張一張拿到每個資料表的獨占鎖，後端的查詢也是一張一張鎖它讀到的表，
     * 兩邊拿鎖的先後不同時，可能各自握著對方要的那張表互相等待。資料庫等滿 deadlock_timeout（預設 1 秒）
     * 會挑一邊中止（SQLState 40P01）：中止的是清空，整個場景還沒開始就失敗；中止的是後端，畫面那一次讀取就失敗。
     *
     * <p>做法是在同一個交易裡先用 LOCK TABLE 把所有要清的表鎖好，再 TRUNCATE。鎖表這一步限時 {@link #RESET_LOCK_WAIT}，
     * 比 deadlock_timeout 短：真的和後端互相等待時，清空這一方先時間到、放掉手上所有的鎖、稍等再整批重來，
     * 互相等待在資料庫判定之前就解開，後端的查詢不會被中止。鎖是排隊等的，不是拿不到就放棄，
     * 後端之後才來的查詢會排在清空後面，所以後端再忙也輪得到清空。全部鎖到手之後 TRUNCATE 不必再等任何人。
     */
    public void resetBusinessData() {
        List<String> tables = resettableTables();
        if (tables.isEmpty()) {
            return;
        }
        String joined = String.join(", ", tables);
        String truncateSql = "TRUNCATE TABLE " + joined + " RESTART IDENTITY CASCADE";
        lockAllThen("LOCK TABLE " + joined + " IN ACCESS EXCLUSIVE MODE", db -> {
            try (java.sql.Statement statement = db.createStatement()) {
                statement.execute(truncateSql);
            }
        }, "清空業務資料");
        jwtByStaffId.clear();
    }

    @FunctionalInterface
    private interface LockedWork {
        void run(Connection db) throws SQLException;
    }

    private static final Duration RESET_LOCK_WAIT = Duration.ofMillis(400);

    private static final Duration RESET_PATIENCE = Duration.ofSeconds(30);

    private static final long RESET_RETRY_PAUSE_MS = 20;

    private static final List<String> RESET_RETRYABLE_STATES = List.of("57014", "55P03", "40P01");

    private void lockAllThen(String lockSql, LockedWork work, String what) {
        Connection db = connection();
        long deadline = System.nanoTime() + RESET_PATIENCE.toNanos();
        int attempts = 0;
        try {
            db.setAutoCommit(false);
            while (true) {
                attempts++;
                try (java.sql.Statement statement = db.createStatement()) {
                    statement.execute("SET LOCAL statement_timeout = " + RESET_LOCK_WAIT.toMillis());
                    statement.execute(lockSql);
                    statement.execute("SET LOCAL statement_timeout = 0");
                    work.run(db);
                    db.commit();
                    return;
                } catch (SQLException e) {
                    db.rollback();
                    if (!RESET_RETRYABLE_STATES.contains(e.getSQLState()) || System.nanoTime() > deadline) {
                        throw new IllegalStateException(what + "失敗（試了 " + attempts + " 次）", e);
                    }
                } catch (RuntimeException e) {
                    db.rollback();
                    throw e;
                }
                Thread.sleep(RESET_RETRY_PAUSE_MS);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(what + "時交易控制失敗", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(what + "時被中斷", e);
        } finally {
            try {
                db.setAutoCommit(true);
            } catch (SQLException e) {
                connection = null;
            }
        }
    }

    private static final String TEST_CLOCK_RESTART_HINT =
            "後端沒有開放測試端點（/api/v1/test/**）。請用最新程式碼、帶 test-support profile 重啟後端："
                    + "TMS_DB_URL=jdbc:postgresql://localhost:5433/tms SPRING_PROFILES_ACTIVE=test-support "
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

    public OffsetDateTime backendNow() {
        HttpResponse<String> response = send(request("GET", "/test/clock", null).GET());
        int status = response.statusCode();
        if (status == 404 || status == 401 || status == 403) {
            return OffsetDateTime.now();
        }
        if (status != 200) {
            throw new IllegalStateException("讀後端時鐘失敗：HTTP " + status + " " + response.body());
        }
        JsonNode clock = parseJson(response.body());
        if (!clock.path("fixed").asBoolean(false)) {
            return OffsetDateTime.now();
        }
        return OffsetDateTime.parse(clock.path("isoTime").asText());
    }

    private static void requireTestClock(HttpResponse<String> response, String what) {
        requireTestSupport(response, 200, what);
    }

    private static void requireTestSupport(HttpResponse<String> response, int expected, String what) {
        int status = response.statusCode();
        if (status == 404 || status == 401 || status == 403) {
            throw new IllegalStateException(TEST_CLOCK_RESTART_HINT + "（HTTP " + status + "）");
        }
        if (status != expected) {
            throw new IllegalStateException(what + " 失敗：HTTP " + status + " " + response.body());
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
                backendNow().minusMinutes(workedMinutes), ensureTenant(), taskId);
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
                        backendNow().minusMinutes(workedMinutes), tenantId, taskId);
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

    private Map<String, String> repairCategoryLabels;

    /** 報修類別代碼對應的中文名稱，照後端 {@code GET /api/v1/repair-categories} 的順序；同一個 channel 只讀一次。 */
    public Map<String, String> repairCategoryLabels(CreatedStaff reader) {
        if (repairCategoryLabels == null) {
            HttpResponse<String> response = send(request("GET", "/repair-categories", jwt(reader)).GET());
            requireSuccess(response, "讀報修類別：GET /repair-categories");
            Map<String, String> labels = new LinkedHashMap<>();
            for (JsonNode category : parseJson(response.body())) {
                labels.put(category.path("code").asText(), category.path("label").asText());
            }
            repairCategoryLabels = Collections.unmodifiableMap(labels);
        }
        return repairCategoryLabels;
    }

    public UUID createRepairTicket(
            String ticketNo, String roomNo, String categoryCode, String stateCode,
            CreatedStaff reporter, CreatedStaff assignee, String description, boolean urgent) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom(roomNo, floorFromRoomNo(roomNo));

        String taskState = "done".equals(stateCode) ? "doing" : stateCode;
        UUID taskId = createTask(roomNo, repairCategoryLabels(reporter).getOrDefault(categoryCode, categoryCode) + " 工務單",
                "repair", "engineering", null,
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

    private static final int TIME_CREATED = 0;
    private static final int TIME_TRIAGED = 1;
    private static final int TIME_ACCEPTED = 2;
    private static final int TIME_STARTED = 3;
    private static final int TIME_DONE = 4;
    private static final int TIME_FINAL = 5;

    private static final List<String> SITUATION_TIME_NAMES =
            List.of("建立", "派出", "到手", "開工", "完成", "進入這個狀況");

    private static final java.util.regex.Pattern TASK_NO_FORMAT = java.util.regex.Pattern.compile("T-(\\d{4,})");

    private static final java.util.regex.Pattern REPAIR_TICKET_NO_FORMAT =
            java.util.regex.Pattern.compile("W-(\\d{4,})");

    private static final int VACANT_ROOM_STAY_DAYS_AGO = 30;

    private static final String GUEST_SESSION_HEADER = "X-Guest-Session";
    private static final String GUEST_SESSION_ISSUER_NAME = "測試資料櫃檯";

    public synchronized UUID createTaskInSituation(TaskSituation situation, TaskSituationSpec spec) {
        long tenantId = ensureTenant();
        OffsetDateTime[] times = resolveSituationTimes(situation, spec, this::backendNow);
        return inTransaction(() -> new SituationTaskWriter(tenantId, situation, spec, times).write());
    }

    private static OffsetDateTime[] resolveSituationTimes(
            TaskSituation situation, TaskSituationSpec spec,
            java.util.function.Supplier<OffsetDateTime> backendNow) {
        OffsetDateTime[] times = {
            spec.createdAt, spec.triagedAt, spec.acceptedAt, spec.startedAt, spec.doneAt, null
        };
        boolean returnedToPool = isReturnedToPool(situation, spec);
        int anchor = returnedToPool ? TIME_FINAL : situationTimeAnchor(situation);
        if (times[anchor] != null && spec.at != null && !times[anchor].isEqual(spec.at)) {
            throw new IllegalArgumentException("「" + situation.term() + "」的計時起點就是"
                    + SITUATION_TIME_NAMES.get(anchor) + "的時刻，兩邊給了不同的值：" + spec.at + " 與 " + times[anchor]);
        }
        if (times[anchor] == null) {
            times[anchor] = spec.at != null ? spec.at : backendNow.get();
        }
        for (int i = anchor + 1; i < times.length; i++) {
            boolean triagedWhileWaiting = situation == TaskSituation.POOL && i == TIME_TRIAGED;
            if (times[i] != null && !triagedWhileWaiting) {
                throw new IllegalArgumentException("「" + situation.term() + "」的單還沒走到"
                        + SITUATION_TIME_NAMES.get(i) + "，不能給這個時刻");
            }
            if (times[i] == null) {
                times[i] = times[i - 1];
            }
        }
        for (int i = anchor - 1; i >= 0; i--) {
            if (times[i] == null) {
                times[i] = times[i + 1];
            }
        }
        for (int i = 0; i < times.length; i++) {
            times[i] = times[i].truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            if (i > 0 && times[i].isBefore(times[i - 1])) {
                throw new IllegalArgumentException("「" + situation.term() + "」的時刻前後顛倒："
                        + SITUATION_TIME_NAMES.get(i) + "（" + times[i] + "）早於"
                        + SITUATION_TIME_NAMES.get(i - 1) + "（" + times[i - 1] + "）");
            }
        }
        return times;
    }

    private static final List<String> RETURN_POOL_SOURCES =
            List.of("manager_requeue", "leave_return", "shift_end_return");

    private static boolean isReturnedToPool(TaskSituation situation, TaskSituationSpec spec) {
        if (situation != TaskSituation.POOL) {
            return false;
        }
        boolean waitingStartedAfterCreation =
                spec.createdAt != null && spec.at != null && spec.at.isAfter(spec.createdAt);
        boolean returnSourceGiven = spec.poolSource != null && RETURN_POOL_SOURCES.contains(spec.poolSource);
        return waitingStartedAfterCreation || spec.returnedBy != null || spec.previousAssignee != null
                || returnSourceGiven;
    }

    private static int situationTimeAnchor(TaskSituation situation) {
        return switch (situation) {
            case TRIAGE, POOL -> TIME_CREATED;
            case ACCEPTED_BY_PLAN, ACCEPTED_BY_CLAIM, ACCEPTED_BY_MANAGER, ACCEPTED_BY_REASSIGN -> TIME_ACCEPTED;
            case DOING -> TIME_STARTED;
            case DONE_AWAITING_INSPECTION, DONE_AWAITING_VERIFICATION, DONE, INSPECTION_WAIVED,
                    CLOSED_NO_ACTION_NEEDED -> TIME_DONE;
            case INTERRUPTED_BY_LEAVE, INTERRUPTED_BY_SELF, DEFERRED_BY_MANAGER, DEFERRED_BY_SELF,
                    INSPECTION_PASSED, CLOSED_VERIFIED, CANCELLED_BY_GUEST, CANCELLED_BY_MANAGER,
                    CANCELLED_PLAN_SUPERSEDED, CANCELLED_BY_REPORTER, ANSWERED -> TIME_FINAL;
        };
    }

    private record PendingEvent(
            String type, String actorRole, UUID actorId, Map<String, Object> payload, OffsetDateTime at) {}

    private final class SituationTaskWriter {

        private final long tenantId;
        private final TaskSituation situation;
        private final TaskSituationSpec spec;
        private final OffsetDateTime[] times;
        private final boolean repair;
        private final boolean cleaning;
        private final UUID taskId = UUID.randomUUID();
        private final Map<String, Object> columns = new LinkedHashMap<>();
        private final List<PendingEvent> events = new ArrayList<>();
        private final List<Object[]> sopChecks = new ArrayList<>();
        private final Map<String, Object> repairTicket = new LinkedHashMap<>();

        SituationTaskWriter(
                long tenantId, TaskSituation situation, TaskSituationSpec spec, OffsetDateTime[] times) {
            this.tenantId = tenantId;
            this.situation = situation;
            this.spec = spec;
            this.times = times;
            this.repair = "repair".equals(spec.type);
            this.cleaning = "cleaning".equals(spec.type);
        }

        UUID write() {
            validate();
            TaskSituationSpec.AcceptedVia acceptedVia = acceptedVia();
            boolean startsInTriage = staysInTriage() || spec.triagedBy != null;
            boolean planAssigned = acceptedVia == TaskSituationSpec.AcceptedVia.PLAN_ASSIGNED;
            String initialState = startsInTriage ? "triage" : planAssigned ? "accepted" : "pool";
            String initialDept = startsInTriage ? "frontdesk" : spec.dept;

            create(initialState, initialDept);
            if (spec.triagedBy != null) {
                triage();
            }
            if (acceptedVia != null) {
                accept(acceptedVia);
            }
            switch (situation) {
                case TRIAGE -> { }
                case POOL -> {
                    if (isReturnedToPool(situation, spec)) {
                        returnToPool();
                    }
                }
                case ACCEPTED_BY_PLAN, ACCEPTED_BY_CLAIM, ACCEPTED_BY_MANAGER, ACCEPTED_BY_REASSIGN ->
                        checkSopItems(times[TIME_ACCEPTED]);
                case DOING -> start();
                case DONE_AWAITING_INSPECTION, DONE_AWAITING_VERIFICATION, DONE, INSPECTION_WAIVED,
                        CLOSED_NO_ACTION_NEEDED -> {
                    start();
                    complete();
                }
                case INSPECTION_PASSED -> {
                    start();
                    complete();
                    passInspection();
                }
                case CLOSED_VERIFIED -> {
                    start();
                    complete();
                    verifyRepair();
                }
                case INTERRUPTED_BY_LEAVE, INTERRUPTED_BY_SELF -> {
                    start();
                    interrupt();
                }
                case DEFERRED_BY_MANAGER, DEFERRED_BY_SELF -> defer();
                case CANCELLED_BY_GUEST, CANCELLED_BY_MANAGER, CANCELLED_PLAN_SUPERSEDED,
                        CANCELLED_BY_REPORTER -> cancel();
                case ANSWERED -> answer();
            }
            insert();
            return taskId;
        }

        private void create(String initialState, String initialDept) {
            UUID roomId = ensureRoom(spec.roomNo, floorFromRoomNo(spec.roomNo));
            String taskNo = repair ? null : takeDisplayNo("task_counters", "T-", TASK_NO_FORMAT, spec.displayNo);
            String title = spec.title != null ? spec.title : titleFromGuestText();
            String note = repair && spec.guestText == null && spec.menuKey == null ? null : taskNote();
            LocalDate workDate = cleaning ? times[TIME_CREATED].toLocalDate() : null;
            boolean planAssigned = "accepted".equals(initialState);
            if (spec.urgent && !repair) {
                throw new IllegalArgumentException("只有修繕單可以是急件，" + spec.roomNo + " 的類型是 " + spec.type);
            }

            columns.put("id", taskId);
            columns.put("tenant_id", tenantId);
            columns.put("task_no", taskNo);
            columns.put("type", spec.type);
            columns.put("source", spec.source);
            columns.put("room_id", roomId);
            columns.put("reservation_id", spec.reservationId);
            columns.put("dept", initialDept);
            columns.put("state", initialState);
            columns.put("title", title);
            columns.put("note", note);
            columns.put("est_min", spec.estMin);
            columns.put("work_date", workDate);
            columns.put("urgent", spec.urgent);
            columns.put("created_at", times[TIME_CREATED]);
            if ("pool".equals(initialState)) {
                boolean explicitEntry = spec.poolSource != null && !RETURN_POOL_SOURCES.contains(spec.poolSource);
                if (explicitEntry) {
                    columns.put("pool_source", spec.poolSource);
                } else if ("guest".equals(spec.source)) {
                    columns.put("pool_source", "guest_scan");
                } else if ("system".equals(spec.source)) {
                    columns.put("pool_source", "system");
                } else if (repair && "desk".equals(spec.source)) {
                    columns.put("pool_source", "desk_forward");
                }
            }

            Map<String, Object> snapshot = new LinkedHashMap<>();
            putIfPresent(snapshot, "type", spec.type);
            putIfPresent(snapshot, "source", spec.source);
            putIfPresent(snapshot, "room_id", roomId.toString());
            putIfPresent(snapshot, "reservation_id", spec.reservationId == null ? null : spec.reservationId.toString());
            putIfPresent(snapshot, "dept", initialDept);
            putIfPresent(snapshot, "state", initialState);
            putIfPresent(snapshot, "title", title);
            putIfPresent(snapshot, "note", note);
            putIfPresent(snapshot, "est_min", spec.estMin);
            putIfPresent(snapshot, "work_date", workDate == null ? null : workDate.toString());
            putIfPresent(snapshot, "task_no", taskNo);

            boolean byStaff = "desk".equals(spec.source) || "staff".equals(spec.source);
            String creatorRole = byStaff ? actorRole(spec.createdBy) : spec.source;
            UUID creatorId = byStaff ? spec.createdBy.id() : null;
            event("created", creatorRole, creatorId, snapshot, times[TIME_CREATED]);

            if (repair) {
                prepareRepairTicket(roomId);
            }
            if (planAssigned) {
                columns.put("assignee_id", spec.assignee.id());
                event("assigned", creatorRole, creatorId,
                        Map.of("assignee_id", spec.assignee.id().toString()), times[TIME_ACCEPTED]);
            }
        }

        private void prepareRepairTicket(UUID roomId) {
            CreatedStaff reporter = spec.createdBy != null ? spec.createdBy : spec.assignee;
            repairTicket.put("task_id", taskId);
            repairTicket.put("tenant_id", tenantId);
            repairTicket.put("ticket_no",
                    takeDisplayNo("repair_ticket_counters", "W-", REPAIR_TICKET_NO_FORMAT, spec.displayNo));
            repairTicket.put("room_id", roomId);
            repairTicket.put("category", spec.repairCategory != null ? spec.repairCategory : "other");
            repairTicket.put("description", spec.note);
            repairTicket.put("reporter_id", reporter.id());
            repairTicket.put("is_urgent", spec.urgent);
            repairTicket.put("created_at", times[TIME_CREATED]);
        }

        private void triage() {
            columns.put("state", "pool");
            columns.put("dept", spec.dept);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("dept", spec.dept);
            payload.put("via", spec.triageVia != null ? spec.triageVia : "button");
            event("triaged", actorRole(spec.triagedBy), spec.triagedBy.id(), payload, times[TIME_TRIAGED]);
        }

        private void accept(TaskSituationSpec.AcceptedVia via) {
            Map<String, Object> toAssignee = Map.of("assignee_id", spec.assignee.id().toString());
            switch (via) {
                case PLAN_ASSIGNED -> { }
                case SELF_CLAIMED -> event("claimed", actorRole(spec.assignee), spec.assignee.id(),
                        toAssignee, times[TIME_ACCEPTED]);
                case MANAGER_ASSIGNED -> event("assigned", actorRole(spec.assignedBy), spec.assignedBy.id(),
                        toAssignee, times[TIME_ACCEPTED]);
                case REASSIGNED -> {
                    event("claimed", actorRole(spec.previousAssignee), spec.previousAssignee.id(),
                            Map.of("assignee_id", spec.previousAssignee.id().toString()), times[TIME_TRIAGED]);
                    event("reassigned", actorRole(spec.assignedBy), spec.assignedBy.id(),
                            toAssignee, times[TIME_ACCEPTED]);
                }
            }
            columns.put("state", "accepted");
            columns.put("assignee_id", spec.assignee.id());
        }

        private void returnToPool() {
            CreatedStaff holder = spec.previousAssignee != null ? spec.previousAssignee : anyStaffOfDept();
            event("claimed", actorRole(holder), holder.id(),
                    Map.of("assignee_id", holder.id().toString()), times[TIME_ACCEPTED]);

            String poolSource = returnPoolSource();
            columns.put("state", "pool");
            columns.put("assignee_id", null);
            columns.put("pool_source", poolSource);
            switch (poolSource) {
                case "manager_requeue" -> {
                    columns.put("priority_notify", true);
                    columns.put("requeue_note", spec.handoverNote);
                    event("returned_to_pool", actorRole(spec.returnedBy), spec.returnedBy.id(),
                            Map.of("note", spec.handoverNote), times[TIME_FINAL]);
                }
                case "leave_return" -> {
                    columns.put("priority_notify", true);
                    event("returned_to_pool", actorRole(spec.returnedBy), spec.returnedBy.id(),
                            Map.of("reason", "leave_return"), times[TIME_FINAL]);
                }
                default -> {
                    columns.put("return_reason", "班末退池 · 交給下一班");
                    event("returned_to_pool", "system", null,
                            Map.of("reason", "shift_end_return"), times[TIME_FINAL]);
                }
            }
        }

        private String returnPoolSource() {
            if (spec.poolSource != null) {
                return spec.poolSource;
            }
            return spec.returnedBy != null ? "manager_requeue" : "shift_end_return";
        }

        private CreatedStaff anyStaffOfDept() {
            List<Map<String, Object>> rows = queryRows("SELECT id, name, login_id, role, dept FROM staff_accounts"
                    + " WHERE tenant_id = ? AND dept = ? AND active ORDER BY login_id LIMIT 1", tenantId, spec.dept);
            require(!rows.isEmpty(), "被退回的單要有退回之前的負責人，但 " + spec.dept + " 沒有任何在職的同仁可以當");
            Map<String, Object> row = rows.get(0);
            return new CreatedStaff((UUID) row.get("id"), (String) row.get("name"), (String) row.get("login_id"),
                    null, (String) row.get("role"), (String) row.get("dept"));
        }

        private void start() {
            columns.put("state", "doing");
            columns.put("started_at", times[TIME_STARTED]);
            columns.put("segment_started_at", times[TIME_STARTED]);
            columns.put("inspection_waived", situation == TaskSituation.INSPECTION_WAIVED);
            event("started", actorRole(spec.assignee), spec.assignee.id(), null, times[TIME_STARTED]);
            checkSopItems(times[TIME_STARTED]);
        }

        private void closeWorkSegment(OffsetDateTime at) {
            columns.put("worked_sec", (int) Duration.between(times[TIME_STARTED], at).getSeconds());
            columns.put("segment_started_at", null);
        }

        private void complete() {
            OffsetDateTime doneAt = times[TIME_DONE];
            closeWorkSegment(doneAt);
            columns.put("done_at", doneAt);
            Map<String, Object> payload = null;
            if (cleaning) {
                payload = new LinkedHashMap<>();
                payload.put("skipped_items", skipUncheckedSopItems(doneAt));
            }
            if (repair) {
                String resolution = resolutionType();
                payload = new LinkedHashMap<>();
                payload.put("resolutionType", resolution);
                repairTicket.put("resolution_type", resolution);
            }
            switch (situation) {
                case INSPECTION_WAIVED -> {
                    columns.put("state", "inspected");
                    columns.put("inspection", "waived");
                    columns.put("ended_at", doneAt);
                }
                case CLOSED_NO_ACTION_NEEDED -> {
                    columns.put("state", "closed");
                    columns.put("ended_at", doneAt);
                }
                case DONE -> {
                    columns.put("state", "done");
                    columns.put("ended_at", doneAt);
                }
                default -> columns.put("state", "done");
            }
            event("completed", actorRole(spec.assignee), spec.assignee.id(), payload, doneAt);
        }

        private String resolutionType() {
            if (situation == TaskSituation.CLOSED_NO_ACTION_NEEDED) {
                return "no_action_needed";
            }
            return spec.resolutionType != null ? spec.resolutionType : "fixed";
        }

        private void passInspection() {
            columns.put("state", "inspected");
            columns.put("inspection", "passed");
            columns.put("ended_at", times[TIME_FINAL]);
            event("inspection_passed", actorRole(spec.actedBy), spec.actedBy.id(), null, times[TIME_FINAL]);
        }

        private void verifyRepair() {
            columns.put("state", "closed");
            columns.put("ended_at", times[TIME_FINAL]);
            repairTicket.put("verified_by", spec.actedBy.id());
            repairTicket.put("verified_at", times[TIME_FINAL]);
            event("repair_verified", actorRole(spec.actedBy), spec.actedBy.id(), null, times[TIME_FINAL]);
        }

        private void interrupt() {
            boolean bySelf = situation == TaskSituation.INTERRUPTED_BY_SELF;
            closeWorkSegment(times[TIME_FINAL]);
            columns.put("state", "interrupted");
            columns.put("hold_reason", spec.reason);
            columns.put("requeue_note", spec.handoverNote);
            Map<String, Object> payload = new LinkedHashMap<>();
            putIfPresent(payload, "reason", spec.reason);
            payload.put("by_self", bySelf);
            if (bySelf) {
                event("interrupted", actorRole(spec.assignee), spec.assignee.id(), payload, times[TIME_FINAL]);
            } else if (spec.actedBy != null) {
                event("interrupted", actorRole(spec.actedBy), spec.actedBy.id(), payload, times[TIME_FINAL]);
            } else {
                event("interrupted", "system", null, payload, times[TIME_FINAL]);
            }
        }

        private void defer() {
            boolean bySelf = situation == TaskSituation.DEFERRED_BY_SELF;
            CreatedStaff actor = bySelf ? spec.assignee : spec.actedBy;
            columns.put("state", "deferred");
            columns.put("deferred_by_self", bySelf);
            Map<String, Object> payload = null;
            if (bySelf) {
                columns.put("hold_reason", spec.reason);
                payload = new LinkedHashMap<>();
                putIfPresent(payload, "reason", spec.reason);
                payload.put("by_self", true);
            }
            event("deferred", actorRole(actor), actor.id(), payload, times[TIME_FINAL]);
        }

        private void cancel() {
            String reason = switch (situation) {
                case CANCELLED_BY_GUEST -> "guest_withdrew";
                case CANCELLED_BY_MANAGER -> "manager_void";
                case CANCELLED_PLAN_SUPERSEDED -> "plan_superseded";
                default -> "staff_withdrew";
            };
            columns.put("state", "cancelled");
            columns.put("cancel_reason", reason);
            columns.put("ended_at", times[TIME_FINAL]);
            Map<String, Object> payload = Map.of("reason", reason);
            switch (situation) {
                case CANCELLED_BY_GUEST -> event("cancelled", "guest", null, payload, times[TIME_FINAL]);
                case CANCELLED_PLAN_SUPERSEDED -> event("cancelled", "system", null, payload, times[TIME_FINAL]);
                case CANCELLED_BY_MANAGER -> event("cancelled", actorRole(spec.actedBy), spec.actedBy.id(),
                        payload, times[TIME_FINAL]);
                default -> event("cancelled", actorRole(spec.createdBy), spec.createdBy.id(),
                        payload, times[TIME_FINAL]);
            }
        }

        private void answer() {
            String answer = spec.answer.strip();
            columns.put("state", "done");
            columns.put("answer", answer);
            columns.put("done_at", times[TIME_FINAL]);
            columns.put("ended_at", times[TIME_FINAL]);
            event("answered", actorRole(spec.actedBy), spec.actedBy.id(),
                    Map.of("answer", answer), times[TIME_FINAL]);
        }

        private void checkSopItems(OffsetDateTime at) {
            if (spec.sopCheckedCount == null || spec.sopCheckedCount == 0) {
                return;
            }
            List<Map<String, Object>> items = activeCleaningSopItems();
            require(items.size() >= spec.sopCheckedCount, "要勾 " + spec.sopCheckedCount
                    + " 項清潔 SOP，但飯店啟用中的清潔 SOP 只有 " + items.size() + " 項");
            for (Map<String, Object> item : items.subList(0, spec.sopCheckedCount)) {
                sopChecks.add(new Object[] {item.get("item_key"), item.get("label"), true, false,
                        spec.assignee.id(), at});
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("item_key", item.get("item_key"));
                payload.put("label", item.get("label"));
                payload.put("checked", true);
                payload.put("skipped", false);
                event("sop_checked", actorRole(spec.assignee), spec.assignee.id(), payload, at);
            }
        }

        private List<String> skipUncheckedSopItems(OffsetDateTime at) {
            List<String> skipped = new ArrayList<>();
            List<Map<String, Object>> items = activeCleaningSopItems();
            for (Map<String, Object> item : items.subList(sopChecks.size(), items.size())) {
                sopChecks.add(new Object[] {item.get("item_key"), item.get("label"), false, true, null, at});
                skipped.add(String.valueOf(item.get("label")));
            }
            return skipped;
        }

        private List<Map<String, Object>> activeCleaningSopItems() {
            return queryRows("SELECT item_key, label FROM sop_items WHERE tenant_id = ?"
                    + " AND applies_to = 'cleaning' AND active = true ORDER BY sort_order, item_key", tenantId);
        }

        private void event(
                String type, String actorRole, UUID actorId, Map<String, Object> payload, OffsetDateTime at) {
            events.add(new PendingEvent(type, actorRole, actorId, payload, at));
        }

        private void insert() {
            columns.put("event_seq", events.size());
            columns.put("updated_at", events.get(events.size() - 1).at());
            insertRow("tasks", columns);
            for (int i = 0; i < events.size(); i++) {
                PendingEvent event = events.get(i);
                String payload = event.payload() == null || event.payload().isEmpty()
                        ? null : toJson(event.payload());
                execute("""
                        INSERT INTO task_events
                            (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                        """, tenantId, taskId, i + 1, event.type(), event.actorRole(), event.actorId(),
                        payload, event.at());
            }
            if (repair) {
                insertRow("repair_tickets", repairTicket);
            }
            for (Object[] check : sopChecks) {
                execute("""
                        INSERT INTO task_sop_checks
                            (id, tenant_id, task_id, item_key, label, checked, skipped, checked_by, checked_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), tenantId, taskId, check[0], check[1], check[2], check[3],
                        check[4], check[5]);
            }
        }

        private void insertRow(String table, Map<String, Object> row) {
            String names = String.join(", ", row.keySet());
            String marks = String.join(", ", java.util.Collections.nCopies(row.size(), "?"));
            execute("INSERT INTO " + table + " (" + names + ") VALUES (" + marks + ")", row.values().toArray());
        }

        private String takeDisplayNo(
                String counterTable, String prefix, java.util.regex.Pattern format, String requested) {
            if (requested == null) {
                Object lastNo = queryScalar("INSERT INTO " + counterTable + " (tenant_id, last_no) VALUES (?, 1)"
                        + " ON CONFLICT (tenant_id) DO UPDATE SET last_no = " + counterTable + ".last_no + 1"
                        + " RETURNING last_no", tenantId)
                        .orElseThrow(() -> new IllegalStateException(counterTable + " 取號沒有回傳 last_no"));
                return prefix + String.format("%04d", ((Number) lastNo).intValue());
            }
            java.util.regex.Matcher matcher = format.matcher(requested);
            require(matcher.matches(), "單號「" + requested + "」的格式不對，這張單要的是 " + prefix + " 加至少 4 位數");
            execute("INSERT INTO " + counterTable + " (tenant_id, last_no) VALUES (?, ?)"
                    + " ON CONFLICT (tenant_id) DO UPDATE SET last_no = GREATEST(" + counterTable
                    + ".last_no, EXCLUDED.last_no)", tenantId, Integer.parseInt(matcher.group(1)));
            return requested;
        }

        private String titleFromGuestText() {
            return spec.guestText.length() <= 120 ? spec.guestText : spec.guestText.substring(0, 120) + "…";
        }

        private String taskNote() {
            if (spec.guestText == null) {
                if (spec.menuKey == null) {
                    return spec.note;
                }
                require(spec.note == null || repair, "對應品項與備註只能給一個（工務單的備註寫在工務單上，不受限）");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("menu_key", spec.menuKey);
                item.put("quantity", spec.menuQuantity);
                return toJson(item);
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("text", spec.guestText);
            putIfPresent(fields, "lang", spec.guestLang);
            putIfPresent(fields, "llm_translated", spec.translatedText);
            if (spec.menuKey != null) {
                fields.put("rule_id", "bdd-menu-" + spec.menuKey);
                fields.put("menu_key", spec.menuKey);
            }
            return toJson(fields);
        }

        private boolean staysInTriage() {
            if (situation == TaskSituation.TRIAGE || situation == TaskSituation.ANSWERED) {
                return true;
            }
            boolean cancellableInTriage = situation == TaskSituation.CANCELLED_BY_GUEST
                    || situation == TaskSituation.CANCELLED_BY_MANAGER;
            return cancellableInTriage && "frontdesk".equals(spec.dept);
        }

        private TaskSituationSpec.AcceptedVia acceptedVia() {
            TaskSituationSpec.AcceptedVia fixed = switch (situation) {
                case ACCEPTED_BY_PLAN -> TaskSituationSpec.AcceptedVia.PLAN_ASSIGNED;
                case ACCEPTED_BY_CLAIM -> TaskSituationSpec.AcceptedVia.SELF_CLAIMED;
                case ACCEPTED_BY_MANAGER -> TaskSituationSpec.AcceptedVia.MANAGER_ASSIGNED;
                case ACCEPTED_BY_REASSIGN -> TaskSituationSpec.AcceptedVia.REASSIGNED;
                default -> null;
            };
            if (fixed != null) {
                require(spec.acceptedVia == null || spec.acceptedVia == fixed,
                        "這個狀況的到手方式固定是 " + fixed + "，不能另外指定 " + spec.acceptedVia);
                return fixed;
            }
            if (spec.assignee == null) {
                return null;
            }
            return spec.acceptedVia != null ? spec.acceptedVia : TaskSituationSpec.AcceptedVia.SELF_CLAIMED;
        }

        private void validate() {
            require(spec.roomNo != null && !spec.roomNo.isBlank(), "房號必填");
            require(TASK_TYPE.containsValue(spec.type), "類型要是 " + TASK_TYPE.values() + " 之一，收到 " + spec.type);
            require(List.of("guest", "desk", "system", "staff").contains(spec.source),
                    "來源要是 guest、desk、system、staff 之一，收到 " + spec.source);
            require(spec.title != null || spec.guestText != null, "標題與住客原話至少要給一個");
            require(spec.note == null || spec.guestText == null, "備註與住客原話只能給一個");
            require(!"guest".equals(spec.source) || spec.reservationId != null,
                    "住客來源的單一定要連結訂房；空房也要先建一筆住期已經結束的訂房");
            boolean byStaff = "desk".equals(spec.source) || "staff".equals(spec.source);
            require(!byStaff || spec.createdBy != null, "櫃檯代開與同仁開的單要給開單的人");
            require(!repair || spec.createdBy != null || spec.assignee != null,
                    "工務單一定要有報修人；系統建的保養單沒有報修人時，至少要有負責人");
            require(spec.triageVia == null || List.of("button", "drag").contains(spec.triageVia),
                    "派單方式要是 button 或 drag，收到 " + spec.triageVia);
            validateType();
            validateDept();
            validatePeople();
            if (situation == TaskSituation.ANSWERED) {
                require(spec.answer != null && !spec.answer.isBlank() && spec.answer.strip().length() <= 120,
                        "已回答的單要給 1 到 120 字的回覆");
            }
            boolean hasSopProgress = spec.sopCheckedCount != null && spec.sopCheckedCount > 0;
            require(!hasSopProgress || (cleaning && spec.assignee != null),
                    "清潔 SOP 只有打掃單的負責人能勾，這張單不是打掃單或沒有負責人");
        }

        private void validateType() {
            switch (situation) {
                case DONE_AWAITING_INSPECTION, INSPECTION_PASSED, INSPECTION_WAIVED ->
                        require(cleaning, "只有打掃單會查房");
                case DONE_AWAITING_VERIFICATION, CLOSED_VERIFIED -> {
                    require(repair, "只有工務單會完修與驗收");
                    require(!"no_action_needed".equals(spec.resolutionType),
                            "判斷不需處理的工務單完成當下就結案，不會停在這個狀況");
                }
                case CLOSED_NO_ACTION_NEEDED -> {
                    require(repair, "只有工務單會結案");
                    require(spec.resolutionType == null || "no_action_needed".equals(spec.resolutionType),
                            "這個狀況的完修結果固定是 no_action_needed");
                }
                case CANCELLED_BY_REPORTER -> {
                    require(repair, "同仁撤回只發生在工務單");
                    require(spec.createdBy != null, "同仁撤回的單要給報修人，撤回的就是他");
                }
                case DONE -> require(!cleaning && !repair, "打掃單與工務單完成後還要查房或驗收，不會是已完成");
                case TRIAGE, ANSWERED -> require(!repair, "工務單不經過待派單");
                default -> { }
            }
        }

        private void validateDept() {
            if (staysInTriage()) {
                require(spec.dept == null || "frontdesk".equals(spec.dept),
                        "還在櫃檯手上的單固定歸 frontdesk，不能指定 " + spec.dept);
                require(spec.triagedBy == null, "還在櫃檯手上的單沒有被派出過，不能給派單的人");
                return;
            }
            require("housekeeping".equals(spec.dept) || "engineering".equals(spec.dept),
                    "已經離開櫃檯的單要給部門 housekeeping 或 engineering，收到 " + spec.dept);
        }

        private void validatePeople() {
            switch (situation) {
                case TRIAGE, POOL, ANSWERED, CANCELLED_BY_GUEST, CANCELLED_BY_REPORTER ->
                        require(spec.assignee == null, "這個狀況的單沒有負責人");
                case DEFERRED_BY_MANAGER, CANCELLED_BY_MANAGER, CANCELLED_PLAN_SUPERSEDED ->
                        require(spec.assignee == null || !staysInTriage(), "還在櫃檯手上的單沒有負責人");
                default -> require(spec.assignee != null, "這個狀況的單要給負責人");
            }
            switch (situation) {
                case DEFERRED_BY_MANAGER, CANCELLED_BY_MANAGER, INSPECTION_PASSED, CLOSED_VERIFIED ->
                        require(spec.actedBy != null, "這個狀況要給做這個動作的主管");
                case ANSWERED -> require(spec.actedBy != null, "已回答的單要給回答的人");
                default -> { }
            }
            TaskSituationSpec.AcceptedVia via = acceptedVia();
            if (via == TaskSituationSpec.AcceptedVia.PLAN_ASSIGNED) {
                require(spec.triagedBy == null, "班前排房的單建立時就有負責人，不會經過待派單");
            }
            if (via == TaskSituationSpec.AcceptedVia.MANAGER_ASSIGNED
                    || via == TaskSituationSpec.AcceptedVia.REASSIGNED) {
                require(spec.assignedBy != null, "主管指派與轉派要給指派的主管");
            }
            if (via == TaskSituationSpec.AcceptedVia.REASSIGNED) {
                require(spec.previousAssignee != null, "被轉派的單要給轉派之前的負責人");
            }
            validateReturnToPool();
        }

        private void validateReturnToPool() {
            if (!isReturnedToPool(situation, spec)) {
                require(spec.returnedBy == null, "只有待認領的單會是被退回來的");
                require(spec.poolSource == null || !RETURN_POOL_SOURCES.contains(spec.poolSource),
                        "退回的來源只能用在待認領的單");
                require(!"inspection_return".equals(spec.poolSource)
                                && !"verification_return".equals(spec.poolSource),
                        "查房退回與驗收不通過退回的單要先走完查房或驗收，這個入口不建");
                return;
            }
            String poolSource = returnPoolSource();
            require(RETURN_POOL_SOURCES.contains(poolSource),
                    "被退回的單來源要是 " + RETURN_POOL_SOURCES + " 之一，收到 " + poolSource);
            switch (poolSource) {
                case "manager_requeue" -> require(spec.returnedBy != null && spec.handoverNote != null
                                && !spec.handoverNote.isBlank(),
                        "主管退回要給退回的人與交代，後端不收沒有交代的退回");
                case "leave_return" -> require(spec.returnedBy != null, "請假退回要給登記請假的人");
                default -> require(spec.returnedBy == null, "班末退回是系統做的，不能給退回的人");
            }
        }

        private void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalArgumentException(
                        "「" + situation.term() + "」（房號 " + spec.roomNo + "）：" + message);
            }
        }
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String actorRole(CreatedStaff staff) {
        if ("owner".equals(staff.role())) {
            throw new IllegalArgumentException("「" + staff.name() + "」是老闆，任務事件的操作者不能是老闆");
        }
        return staff.role();
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        Connection transaction = connection();
        try {
            transaction.setAutoCommit(false);
            try {
                T result = work.get();
                transaction.commit();
                return result;
            } catch (RuntimeException e) {
                transaction.rollback();
                throw e;
            } finally {
                transaction.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("後端資料庫交易失敗", e);
        }
    }

    public UUID createReservation(
            String roomNo, String guestName, boolean vip, OffsetDateTime checkinAt, OffsetDateTime checkoutAt) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom(roomNo, floorFromRoomNo(roomNo));
        Object id = queryScalar("""
                INSERT INTO reservations_cache
                    (id, tenant_id, reservation_ref, room_id, guest_name, lang, attrs,
                     checkin_at, checkout_at, source, synced_at, superseded_at)
                VALUES (?, ?, ?, ?, ?, NULL, ?::jsonb, ?, ?, 'excel', ?, NULL)
                ON CONFLICT (tenant_id, reservation_ref) DO UPDATE SET
                    room_id = EXCLUDED.room_id, guest_name = EXCLUDED.guest_name, attrs = EXCLUDED.attrs,
                    checkin_at = EXCLUDED.checkin_at, checkout_at = EXCLUDED.checkout_at,
                    synced_at = EXCLUDED.synced_at, superseded_at = NULL
                RETURNING id
                """, UUID.randomUUID(), tenantId, "fixture:" + roomNo + ":" + checkinAt.toLocalDate(), roomId,
                guestName, vip ? "{\"vip\": true}" : "{}", checkinAt, checkoutAt, checkinAt)
                .orElseThrow(() -> new IllegalStateException("寫入訂房沒有回傳 id"));
        return (UUID) id;
    }

    public void supersedeReservations(String roomNo, OffsetDateTime from, OffsetDateTime to) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom(roomNo, floorFromRoomNo(roomNo));
        execute("""
                UPDATE reservations_cache SET superseded_at = ?
                 WHERE tenant_id = ? AND room_id = ? AND superseded_at IS NULL
                   AND tstzrange(checkin_at, checkout_at) && tstzrange(?, ?)
                """, backendNow(), tenantId, roomId, from, to);
    }

    public UUID guestSubmitRequest(String roomNo, String text, String lang) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        if (lang != null) {
            body.put("lang", lang);
        }
        HttpResponse<String> response = send(request("POST", "/guest-requests", null)
                .header(GUEST_SESSION_HEADER, guestSessionToken(roomNo))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8)));
        requireSuccess(response, "住客送出需求：POST /guest-requests（房號 " + roomNo + "）");
        return UUID.fromString(parseJson(response.body()).path("id").asText());
    }

    public UUID guestSubmitMenuItem(String roomNo, String itemKey, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itemId", itemKey);
        body.put("quantity", quantity);
        HttpResponse<String> response = send(request("POST", "/guest-requests", null)
                .header(GUEST_SESSION_HEADER, guestSessionToken(roomNo))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8)));
        requireSuccess(response, "住客勾選需求：POST /guest-requests（房號 " + roomNo + "）");
        return UUID.fromString(parseJson(response.body()).path("id").asText());
    }

    public void guestWithdrawRequest(String roomNo, UUID taskId) {
        HttpResponse<String> response = send(request("POST", "/guest-requests/" + taskId + "/cancel", null)
                .header(GUEST_SESSION_HEADER, guestSessionToken(roomNo))
                .POST(HttpRequest.BodyPublishers.noBody()));
        requireSuccess(response, "住客收回需求：POST /guest-requests/" + taskId + "/cancel（房號 " + roomNo + "）");
    }

    public JsonNode guestRequests(String roomNo) {
        HttpResponse<String> response = send(request("GET", "/guest-requests", null)
                .header(GUEST_SESSION_HEADER, guestSessionToken(roomNo))
                .GET());
        requireSuccess(response, "住客讀自己的需求：GET /guest-requests（房號 " + roomNo + "）");
        return parseJson(response.body());
    }

    public record RotatedQr(String oldToken, String newToken) {}

    public record GuestScan(String sessionToken, List<GuestMenuItem> menu) {}

    public record GuestMenuItem(String key, String label) {}

    public static final class GuestScanRejected extends IllegalStateException {

        private final int status;
        private final String code;

        GuestScanRejected(int status, String code, String body) {
            super("後端不肯用 QR 權杖換住客工作階段：POST /guest/scan -> HTTP " + status + "，body=" + body);
            this.status = status;
            this.code = code;
        }

        public int status() {
            return status;
        }

        public String code() {
            return code;
        }
    }

    public String roomQrToken(String roomNo) {
        String path = "/admin/rooms/" + roomNo + "/qr";
        HttpResponse<String> response = send(requestOutsideApi(path, jwt(defaultStaff(MANAGER_NAME))).GET());
        requireSuccess(response, "讀房間 QR 權杖：GET " + path);
        return parseJson(response.body()).path("token").asText();
    }

    public RotatedQr rotateRoomQr(String roomNo) {
        String oldToken = roomQrToken(roomNo);
        String path = "/admin/rooms/" + roomNo + "/qr/rotate";
        HttpResponse<String> response = send(requestOutsideApi(path, jwt(defaultStaff(MANAGER_NAME)))
                .POST(HttpRequest.BodyPublishers.noBody()));
        requireSuccess(response, "換房間 QR：POST " + path);
        return new RotatedQr(oldToken, parseJson(response.body()).path("token").asText());
    }

    public GuestScan guestScan(String qrToken) {
        HttpResponse<String> response = send(requestOutsideApi("/guest/scan", null)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(Map.of("token", qrToken)), StandardCharsets.UTF_8)));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GuestScanRejected(response.statusCode(), errorCode(response.body()), response.body());
        }
        JsonNode body = parseJson(response.body());
        List<GuestMenuItem> menu = new ArrayList<>();
        for (JsonNode item : body.path("menu")) {
            menu.add(new GuestMenuItem(item.path("key").asText(), item.path("label").asText()));
        }
        return new GuestScan(body.path("sessionToken").asText(), List.copyOf(menu));
    }

    private String errorCode(String body) {
        try {
            return objectMapper.readTree(body).path("code").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    public UUID guestSessionSubmitMenuItem(String sessionToken, String itemKey, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itemId", itemKey);
        body.put("quantity", quantity);
        HttpResponse<String> response = send(requestOutsideApi("/guest/requests", null)
                .header(GUEST_SESSION_HEADER, sessionToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8)));
        requireSuccess(response, "另一支手機送出需求：POST /guest/requests（" + itemKey + " ×" + quantity + "）");
        return UUID.fromString(parseJson(response.body()).path("id").asText());
    }

    public void guestRateRequest(String sessionToken, UUID taskId, int rating) {
        String path = "/guest/requests/" + taskId + "/rating";
        HttpResponse<String> response = send(requestOutsideApi(path, null)
                .header(GUEST_SESSION_HEADER, sessionToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(Map.of("rating", rating)), StandardCharsets.UTF_8)));
        requireSuccess(response, "住客評分：POST " + path);
    }

    public void revokeGuestSession(String sessionToken) {
        HttpResponse<String> response = send(request("POST", "/test/guest-sessions/revoke", null)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        toJson(Map.of("sessionToken", sessionToken)), StandardCharsets.UTF_8)));
        // 沒帶 test-support profile 時，這條路徑落在一般 API 的驗身分規則下、回 401，不會是 404。
        if (response.statusCode() == 404) {
            throw new IllegalStateException("找不到這個住客工作階段，撤銷不了：" + sessionToken);
        }
        requireTestSupport(response, 204, "撤銷住客工作階段");
    }

    /**
     * 清空後端掃碼失敗次數的限流器，讓每個情境從零開始算。住客每間房的送單名額是後端從 tasks 現數的，
     * 這裡清不到；要歸零就清業務資料，或用 seed 的建立時間與後端時鐘安排 10 分鐘內有幾張。
     */
    public void resetRateLimits() {
        HttpResponse<String> response = send(request("DELETE", "/test/rate-limits", null).DELETE());
        requireTestSupport(response, 204, "清空後端限流器");
    }

    public UUID seedGuestRequest(String roomNo, GuestSeed seed) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom(roomNo, floorFromRoomNo(roomNo));
        UUID reservationId = stayingReservationId(tenantId, roomId, backendNow())
                .orElseThrow(() -> new IllegalStateException(roomNo + " 房現在沒有人住，住客需求要掛在在住的訂房上"));
        if (seed.situation().dispatchedToEngineering()) {
            return seedEngineeringGuestRequest(roomNo, reservationId, seed);
        }
        String dept = seed.dept();
        TaskSituation situation = seed.situation().taskSituation();
        TaskSituationSpec spec = TaskSituationSpec.of(roomNo, seed.type(), "guest")
                .reservationId(reservationId)
                .title(seed.title())
                .dept(dept)
                .createdAt(seed.createdAt())
                .acceptedAt(seed.acceptedAt())
                .startedAt(seed.startedAt())
                .assignee(seed.assignee())
                .answer(seed.answer())
                .actedBy(guestSeedActor(situation));
        if ("frontdesk".equals(dept)) {
            spec.guestText(seed.title(), "zh-TW", null);
        }
        return createTaskInSituation(situation, spec);
    }

    private UUID seedEngineeringGuestRequest(String roomNo, UUID reservationId, GuestSeed seed) {
        if (!"repair".equals(seed.type())) {
            throw new IllegalArgumentException("「" + seed.situation().term() + "」是櫃檯派給工務的需求，後端派單時一律改成修繕，"
                    + "類型不能是 " + seed.type());
        }
        UUID taskId = createTaskInSituation(TaskSituation.TRIAGE,
                TaskSituationSpec.of(roomNo, "other", "guest")
                        .reservationId(reservationId)
                        .title(seed.title())
                        .guestText(seed.title(), "zh-TW", null)
                        .createdAt(seed.createdAt()));
        OffsetDateTime now = backendNow();
        try {
            setBackendClock(seed.createdAt());
            deskAssign(taskId, defaultStaff(DESK_CLERK_NAME), "engineering", "button");
            if (seed.acceptedAt() != null) {
                setBackendClock(seed.acceptedAt());
                postTaskAction(taskId, "claim", seed.assignee());
            }
            if (seed.startedAt() != null) {
                setBackendClock(seed.startedAt());
                postTaskAction(taskId, "start", seed.assignee());
            }
            if (seed.situation() == GuestSeed.Situation.CLOSED
                    || seed.situation() == GuestSeed.Situation.DONE_AWAITING_VERIFICATION) {
                setBackendClock(now);
                completeRepairTask(taskId, seed.assignee());
            }
            if (seed.situation() == GuestSeed.Situation.CLOSED) {
                verifyRepairTicket(taskId, defaultStaff(MANAGER_NAME), "passed", null);
            }
        } finally {
            setBackendClock(now);
        }
        return taskId;
    }

    private CreatedStaff guestSeedActor(TaskSituation situation) {
        return switch (situation) {
            case DEFERRED_BY_MANAGER, CANCELLED_BY_MANAGER, INSPECTION_PASSED, CLOSED_VERIFIED ->
                    defaultStaff(MANAGER_NAME);
            case ANSWERED -> defaultStaff(DESK_CLERK_NAME);
            default -> null;
        };
    }

    public void setTaskRating(UUID taskId, int rating) {
        long tenantId = ensureTenant();
        OffsetDateTime now = backendNow();
        inTransaction(() -> {
            queryScalar("""
                    UPDATE tasks SET rating = ?, updated_at = ?
                     WHERE tenant_id = ? AND id = ? AND state IN ('done', 'inspected') AND rating IS NULL
                    RETURNING id
                    """, rating, now, tenantId, taskId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "只有已完成或查房通過、還沒評過分的需求能評分：" + taskId));
            appendTaskEvent(taskId, "rated", "guest", null, Map.of("rating", rating), now);
            return null;
        });
    }

    public void setClassificationRule(String keyword, String dept, String type) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("rule_id", "bdd-" + keyword);
        rule.put("keywords", List.of(keyword));
        rule.put("dept", dept);
        rule.put("type", type);
        setTenantConfig(ensureTenant(), "classification_rules", List.of(rule));
    }

    private CreatedStaff defaultStaff(String name) {
        DefaultStaff staff = FRONTDESK_STAFF.stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("「" + name + "」不是櫃檯 Web 的預設帳號"));
        UUID id = (UUID) queryScalar("SELECT id FROM staff_accounts WHERE tenant_id = ? AND login_id = ?",
                ensureTenant(), staff.loginId())
                .orElseThrow(() -> new IllegalStateException(
                        "預設帳號 " + staff.loginId() + " 還沒建，先呼叫 ensureFrontdeskDefaults()"));
        return new CreatedStaff(id, staff.name(), staff.loginId(), FRONTDESK_PASSWORD, staff.role(), staff.dept());
    }

    private Optional<UUID> stayingReservationId(long tenantId, UUID roomId, OffsetDateTime at) {
        return queryScalar("""
                SELECT id FROM reservations_cache
                 WHERE tenant_id = ? AND room_id = ? AND superseded_at IS NULL
                   AND checkin_at <= ? AND checkout_at > ?
                """, tenantId, roomId, at, at)
                .map(UUID.class::cast);
    }

    private String guestSessionToken(String roomNo) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom(roomNo, floorFromRoomNo(roomNo));
        OffsetDateTime now = backendNow();
        Optional<UUID> stayingReservation = stayingReservationId(tenantId, roomId, now);
        UUID reservationId = stayingReservation.orElseGet(() -> {
            OffsetDateTime longAgo = now.minusDays(VACANT_ROOM_STAY_DAYS_AGO);
            return createReservation(roomNo, null, false, longAgo, longAgo.plusDays(1));
        });
        Optional<Object> existing = queryScalar("""
                SELECT session_token FROM guest_sessions
                 WHERE tenant_id = ? AND room_id = ? AND reservation_id = ?
                   AND revoked_at IS NULL AND expires_at > ?
                 ORDER BY created_at DESC LIMIT 1
                """, tenantId, roomId, reservationId, now);
        if (existing.isPresent()) {
            return String.valueOf(existing.get());
        }
        if (stayingReservation.isEmpty()) {
            return insertVacantRoomGuestSession(tenantId, roomId, reservationId, now);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roomId", roomId.toString());
        body.put("reservationId", reservationId.toString());
        HttpResponse<String> response = send(request("POST", "/guest-sessions/scan", jwt(guestSessionIssuer()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8)));
        requireSuccess(response, "建立住客連線：POST /guest-sessions/scan（房號 " + roomNo + "）");
        return parseJson(response.body()).path("sessionToken").asText();
    }

    private String insertVacantRoomGuestSession(long tenantId, UUID roomId, UUID reservationId, OffsetDateTime now) {
        byte[] randomBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(randomBytes);
        String token = tenantId + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        execute("""
                INSERT INTO guest_sessions (id, tenant_id, session_token, room_id, reservation_id, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, token, roomId, reservationId, now.plusDays(1), now);
        return token;
    }

    private CreatedStaff guestSessionIssuer() {
        return queryRows("SELECT id, login_id FROM staff_accounts WHERE tenant_id = ? AND name = ?",
                        ensureTenant(), GUEST_SESSION_ISSUER_NAME)
                .stream()
                .findFirst()
                .map(row -> new CreatedStaff((UUID) row.get("id"), GUEST_SESSION_ISSUER_NAME,
                        (String) row.get("login_id"), DEFAULT_PASSWORD, "frontdesk", "frontdesk"))
                .orElseGet(() -> insertStaff(GUEST_SESSION_ISSUER_NAME, "frontdesk", "frontdesk", "active"));
    }

    public JsonNode deskAssign(UUID taskId, CreatedStaff actor, String deptCode, String via) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dept", deptCode);
        if (via != null) {
            body.put("via", via);
        }
        return postDeskAction("/desk/tasks/" + taskId + "/assign", actor, body);
    }

    public JsonNode deskCreateRequests(CreatedStaff actor, String roomNo, List<Map<String, Object>> items,
            Map<String, Object> other, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roomNo", roomNo);
        body.put("items", items);
        body.put("other", other);
        HttpResponse<String> response = send(request("POST", "/desk/requests", jwt(actor))
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8)));
        requireSuccess(response, "櫃檯代開單：POST /desk/requests（" + actor.name() + "，" + roomNo + "）");
        return parseJson(response.body());
    }

    public JsonNode listDeskRooms(CreatedStaff actor) {
        HttpResponse<String> response = send(request("GET", "/desk/rooms", jwt(actor)).GET());
        requireSuccess(response, "代開單的房間清單：GET /desk/rooms（" + actor.name() + "）");
        return parseJson(response.body());
    }

    public void removeRoom(String roomNo) {
        Connection db = connection();
        try {
            db.setAutoCommit(false);
            try (PreparedStatement skipForeignKeys = db.prepareStatement("SET LOCAL session_replication_role = replica");
                    PreparedStatement delete = db.prepareStatement("DELETE FROM rooms WHERE tenant_id = ? AND room_no = ?")) {
                skipForeignKeys.execute();
                bind(delete, ensureTenant(), roomNo);
                delete.executeUpdate();
            }
            db.commit();
        } catch (SQLException e) {
            try {
                db.rollback();
            } catch (SQLException ignored) {
            }
            throw new IllegalStateException("從房間主檔移除 " + roomNo + " 失敗", e);
        } finally {
            try {
                db.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    public void changeRoleToHousekeeping(CreatedStaff staff) {
        execute("UPDATE staff_accounts SET role = 'housekeeping', dept = 'housekeeping', updated_at = ?"
                        + " WHERE tenant_id = ? AND id = ?",
                OffsetDateTime.now(), ensureTenant(), staff.id());
    }

    public void invalidateLogin(CreatedStaff staff) {
        setCredentials(staff.id(), staff.loginId(), staff.password());
    }

    public void failTaskTitled(String title) {
        String name = FAULT_TRIGGER_PREFIX + "task_title_" + Integer.toHexString(title.hashCode() & 0x7fffffff);
        String literal = "'" + title.replace("'", "''") + "'";
        execute("CREATE OR REPLACE FUNCTION " + name + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF NEW.title = " + literal + " THEN RAISE EXCEPTION 'bdd: 這張單在測試裡被設定成開不出來'; END IF; "
                + "RETURN NEW; END $$");
        execute("DROP TRIGGER IF EXISTS " + name + " ON tasks");
        execute("CREATE TRIGGER " + name + " BEFORE INSERT ON tasks FOR EACH ROW EXECUTE FUNCTION " + name + "()");
    }

    public void removeFaultTriggers() {
        List<Map<String, Object>> triggers = queryRows("""
                SELECT t.tgname AS name, c.relname AS table_name
                FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid
                WHERE t.tgname LIKE ?
                """, FAULT_TRIGGER_PREFIX + "%");
        for (Map<String, Object> trigger : triggers) {
            execute("DROP TRIGGER IF EXISTS " + trigger.get("name") + " ON " + trigger.get("table_name"));
            execute("DROP FUNCTION IF EXISTS " + trigger.get("name") + "()");
        }
    }

    public JsonNode deskUndoAssign(UUID taskId, CreatedStaff actor) {
        return postDeskAction("/desk/tasks/" + taskId + "/assign/undo", actor, null);
    }

    public JsonNode deskAnswer(UUID taskId, CreatedStaff actor, String answer) {
        return postDeskAction("/desk/tasks/" + taskId + "/answer", actor, Map.of("answer", answer));
    }

    private JsonNode postDeskAction(String path, CreatedStaff actor, Map<String, Object> body) {
        HttpRequest.Builder builder = request("POST", path, jwt(actor))
                .header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString());
        if (body == null) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json");
        }
        HttpResponse<String> response = send(builder);
        requireSuccess(response, "櫃檯動作：POST " + path + "（" + actor.name() + "）");
        return parseJson(response.body());
    }

    private static void requireSuccess(HttpResponse<String> response, String what) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("測試資料準備失敗，" + what + " -> HTTP "
                    + response.statusCode() + "，body=" + response.body());
        }
    }

    public String jwt(CreatedStaff staff) {
        return jwtByStaffId.computeIfAbsent(staff.id(), ignored -> login(staff.loginId(), staff.password()));
    }

    public String login(String loginId, String password) {
        String responseBody = postLogin(loginId, password);
        JsonNode token = parseJson(responseBody).path("token");
        if (token.isMissingNode() || token.isNull()) {
            throw new IllegalStateException("登入回應沒有 token 欄位：" + responseBody);
        }
        return token.asText();
    }

    public JsonNode loginResponse(CreatedStaff staff) {
        return parseJson(postLogin(staff.loginId(), staff.password()));
    }

    private String postLogin(String loginId, String password) {
        Map<String, Object> body = Map.of("loginId", loginId, "password", password);
        HttpResponse<String> response = send(request("POST", "/staff-accounts/login", null)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8))
                .header("Content-Type", "application/json"));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("登入失敗：POST /staff-accounts/login -> HTTP "
                    + response.statusCode() + "，body=" + response.body());
        }
        return response.body();
    }

    public int attemptLogin(String loginId, String password) {
        Map<String, Object> body = Map.of("loginId", loginId, "password", password);
        return send(request("POST", "/staff-accounts/login", null)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body), StandardCharsets.UTF_8))
                .header("Content-Type", "application/json"))
                .statusCode();
    }

    public JsonNode changeTemporaryPassword(String loginId, String temporaryPassword, String newPassword) {
        String token = login(loginId, temporaryPassword);
        HttpResponse<String> response = send(request("POST", "/staff-accounts/change-password", token)
                .POST(HttpRequest.BodyPublishers.ofString(
                        toJson(Map.of("newPassword", newPassword)), StandardCharsets.UTF_8))
                .header("Content-Type", "application/json"));
        requireSuccess(response, "改密碼：POST /staff-accounts/change-password（" + loginId + "）");
        return parseJson(response.body());
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

    public JsonNode patchDeskSettings(CreatedStaff manager, Map<String, Object> patch) {
        HttpResponse<String> response = send(request("PATCH", "/desk/settings", jwt(manager))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(toJson(patch), StandardCharsets.UTF_8)));
        requireSuccess(response, "改櫃檯設定 " + patch);
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
        return requestOutsideApi(BASE_PATH + path, jwt);
    }

    private HttpRequest.Builder requestOutsideApi(String path, String jwt) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
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
