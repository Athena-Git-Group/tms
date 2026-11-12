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

/**
 * harness 對「真後端」的資料通道：fixture 建立（員工與帳號、任務與工務單）與
 * 權威狀態讀回（REST 詳情＋資料庫整列）。
 *
 * <p><b>為什麼是 JDBC＋REST 混合，而不是純 REST：</b>後端沒有開任何「測試資料準備」
 * REST 端點（正式 API 刻意不含 admin 造數入口），tms 自己的 BDD 是在同一個 JVM 裡用
 * {@code tms/src/test/java/com/athena/tms/bdd/support/} 的 fixture 類別直接落地
 * （JdbcClient＋TaskEngine）。harness 跑在另一個 JVM，拿不到那些 bean，所以：
 * <ul>
 *   <li><b>fixture 建立</b>走 JDBC，SQL 逐句對照 tms 的 {@code TenantFixtures}／
 *       {@code RoomFixtures}／{@code StaffFixtures}／{@code RepairGivenSteps}，
 *       欄位與 enum 值不自創；</li>
 *   <li><b>狀態轉移</b>（認領／開工／中斷／完成）改走正式 REST 端點
 *       （{@code POST /api/v1/tasks/{id}/claim} 等），事件歷程（task_events）由
 *       產品碼自己長出來，跟正式流程同形狀；</li>
 *   <li><b>讀回</b>提供兩條：REST（{@code GET /tasks/{id}} 等員工視角詳情）與
 *       JDBC 整列（REST 沒曝露的權威欄位，例如 {@code staff_accounts.failed_login_count}）。</li>
 * </ul>
 *
 * <p>與 tms fixture 的三個已知偏差（刻意為之，不是漏做）：
 * <ol>
 *   <li>JWT 不是直接簽發（harness 沒有簽章私鑰），改用正式登入端點
 *       {@code POST /api/v1/staff-accounts/login} 以 fixture 帳密換取；</li>
 *   <li>直接落地的 created 事件不寫 outbox（tms 經 TaskEngine 會送出 Kafka 事件）。
 *       App UI 測試不消費那條事件流，不影響任何斷言；</li>
 *   <li>id 用 UUID v4（tms 用 UuidV7；schema 對 uuid 版本沒有約束，差別只在
 *       索引的排序局部性，對測試無感）。</li>
 * </ol>
 *
 * <p>連線設定全用系統屬性（比照 {@code appium.server.url} 的風格）：
 * {@code backend.base.url}（預設 {@code http://127.0.0.1:8080}）、
 * {@code backend.db.url}（預設 {@code jdbc:postgresql://127.0.0.1:5433/tms}，
 * 對齊 docker-compose.yml 對外開的 5433）、{@code backend.db.user}／
 * {@code backend.db.password}（預設 tms／tms）。後端與資料庫由 docker-compose＋
 * tms 既有方式先起好（就緒檢查在 {@code app/bdd/lib/env.sh}），本類別不負責啟動；
 * 建構子也不發起任何連線，dry-run／純編譯驗證不需要後端在跑。
 */
public class BackendChannel {

    /** API 的共同前綴，對齊 tms BddApiClient 的 BASE_PATH。 */
    private static final String BASE_PATH = "/api/v1";

    /** 冪等鍵 header 名稱，定義見 specs/truth/contracts/components/parameters.yaml 的 IdempotencyKey。 */
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /** 固定明文密碼，僅供測試環境；對齊 tms StaffFixtures.DEFAULT_PASSWORD。 */
    public static final String DEFAULT_PASSWORD = "demo1234";

    /**
     * fixture 照片的內容：一張合法的 1×1 JPEG。後端只驗「有沒有上傳成功」，
     * 不看影像內容，所以不需要真的照片檔。
     */
    private static final byte[] PLACEHOLDER_JPEG = java.util.Base64.getDecoder().decode(
            "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRof"
            + "Hh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAAB"
            + "AAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==");

    /** tms TenantFixtures 的共用測試租戶名，兩邊 BDD 用同一個，fixture 語意才同構。 */
    private static final String TEST_TENANT_NAME = "測試飯店";

    /**
     * 任務狀態中文 → 權威 {@code tasks.state}。逐條照抄
     * {@code tms/src/test/java/com/athena/tms/bdd/support/Terms.java} 的 TASK_STATE
     * （權威來源：specs/truth/features/backend/dsl.md「狀態用語對照」表前半）。
     * tms 那份在另一個 Maven module，harness 掛不進去，只能複製；改權威值時兩份要一起改。
     */
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

    /**
     * 部門中文 → 權威 {@code staff_accounts.dept}。逐條照抄 tms Terms 的 DEPT
     * （權威來源：specs/truth/features/backend/dsl.md「部門用語」表 dept 列）；
     * 複製的理由同 {@link #TASK_STATE}，改權威值時兩份要一起改。
     */
    private static final Map<String, String> DEPT = Map.ofEntries(
            Map.entry("房務", "housekeeping"),
            Map.entry("工務", "engineering"),
            Map.entry("櫃檯", "frontdesk"));

    /** 角色中文 → 權威 {@code role}（照抄 tms Terms 的 ROLE，僅收錄 dsl.md 點名的兩種）。 */
    private static final Map<String, String> ROLE = Map.ofEntries(
            Map.entry("主管", "manager"),
            Map.entry("值班經理", "duty_manager"));

    /**
     * 任務類別中文 → 權威 {@code tasks.type}（＝契約 PoolCategory 的值域）。
     * 來源：specs/truth/features/backend/dsl.md 的任務類別對照。
     */
    private static final Map<String, String> TASK_TYPE = Map.ofEntries(
            Map.entry("打掃", "cleaning"),
            Map.entry("備品", "amenity"),
            Map.entry("修繕", "repair"),
            Map.entry("其他", "other"));

    /**
     * 待認領來源中文 → 權威 {@code tasks.pool_source}（＝契約 PoolSource 的值域）。
     * 「自動化規則」落 {@code system}，對照 backend dsl.md「房務待認領有以下任務：」
     * 的「自動化規則＝system」。
     */
    private static final Map<String, String> POOL_SOURCE = Map.ofEntries(
            Map.entry("住客掃碼", "guest_scan"),
            Map.entry("櫃檯轉單", "desk_forward"),
            Map.entry("主管退回", "manager_requeue"),
            Map.entry("請假退回", "leave_return"),
            Map.entry("班末退池", "shift_end_return"),
            Map.entry("查房退回", "inspection_return"),
            Map.entry("房務報修", "staff_report"),
            Map.entry("自動化規則", "system"));

    /**
     * 員工狀態的中文用語對權威值。劇本的「飯店有以下員工」表用中文寫狀態，而待開通與停用的
     * 員工要能不出現在挑人名單、不收推播，就得落成後端認得的狀態值；對照表照抄 tms Terms，
     * 兩邊 BDD 才會對同一個狀態值下斷言。
     */
    private static final Map<String, String> STAFF_STATE = Map.ofEntries(
            Map.entry("在職", "active"),
            Map.entry("待開通", "invited"),
            Map.entry("停用", "inactive"));

    /**
     * 工務單狀態的中文用語對權威值，給用單號指稱工務單的斷言用。工務單在後端也是一筆任務，
     * 但它的狀態用語跟房務任務不同（已結案、已撤回是工務單的說法），所以不能沿用
     * {@link #TASK_STATE}。已完修（done）是「等主管驗收」，驗收通過或以不需處理完修才是
     * 已結案（closed）；待開始與已接單同為 accepted。
     */
    private static final Map<String, String> REPAIR_TICKET_STATE = Map.ofEntries(
            Map.entry("待認領", "pool"),
            Map.entry("已接單", "accepted"),
            Map.entry("待開始", "accepted"),
            Map.entry("維修中", "doing"),
            Map.entry("已完修", "done"),
            Map.entry("已結案", "closed"),
            Map.entry("已撤回", "cancelled"));

    /**
     * 完修處理結果的中文用語對權威值。「已有以下工務單」表的「處理結果」欄要送進正式的完修端點
     * （{@code CompleteTaskRequest.resolutionType}），端點只收這四個值，中文對不上就早爆，
     * 不讓一張寫錯的單默默落成「已修好」。
     */
    private static final Map<String, String> RESOLUTION_TYPE = Map.ofEntries(
            Map.entry("已修好", "fixed"),
            Map.entry("暫時處理", "temporary"),
            Map.entry("無法修復", "cannot_fix"),
            Map.entry("不需處理", "no_action_needed"));

    /**
     * 房況的中文用語對權威值。主管查房、驗收之後要確認房況有沒有跟著走，斷言拿劇本的中文去比
     * {@code rooms.hk_state}；對照取自 backend dsl「房況為」那一列，App 這邊不另訂一套。
     */
    private static final Map<String, String> HK_STATE = Map.ofEntries(
            Map.entry("還沒打掃好", "dirty"),
            Map.entry("已打掃", "cleaned"),
            Map.entry("已查房", "inspected"),
            Map.entry("停用", "out_of_order"));

    /** 未接提醒階段中文 → 權威 {@code tasks.reminder_stage}（V13 的 CHECK 值域）。 */
    private static final Map<String, String> REMINDER_STAGE = Map.ofEntries(
            Map.entry("已提醒同仁", "staff_reminded"),
            Map.entry("已通知主管", "manager_notified"));

    /**
     * 公共區域佔位房的房號。後端用一間 {@code room_type='public_area'} 的佔位房代表
     * 「沒有房號」的單（{@code tasks.room_id} 是 NOT NULL），房號字串與
     * {@code AutomationRuleService.PUBLIC_AREA_ROOM_NO} 同值。
     */
    public static final String PUBLIC_AREA_ROOM_NO = "公共區域";

    /**
     * 「負責樓層」那一筆佔位排房計畫的班次欄位值（{@link #assignPlanRoom}）。刻意不是
     * 一個可以解析的 {@code HH:mm}、也不是租戶 shift_times 設定裡的任何一個鍵，班末
     * 掃描因此解析不出班次結束時刻、整個跳過這位員工——理由寫在 assignPlanRoom。
     */
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

    /** fixture 建出的員工帳號；password 固定 {@link #DEFAULT_PASSWORD}，JWT 用 {@link #jwt} 換。 */
    public record CreatedStaff(UUID id, String name, String loginId, String password, String role, String dept) {}

    /** 確保「測試飯店」租戶存在，回傳 tenants.id（對照 TenantFixtures.testTenantId）。 */
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

    /**
     * 每個情境開場清掉上一個情境的業務資料，避免跨情境的唯一鍵碰撞與待認領池殘留。
     * 清法照抄 tms ResetHooks：TRUNCATE public 下所有 base table，
     * 保留 flyway_schema_history 與 tenants（租戶 id 有快取重用）。
     */
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
        // staff_accounts 已被清空，快取的 JWT 全指向不存在的員工，留著只會害下一個情境 401。
        jwtByStaffId.clear();
    }

    /**
     * 後端少了撥時鐘端點時的提示。沒帶旗標的新版後端回 404；還沒更新到含撥時鐘端點的舊版後端，
     * 這條路徑會被角色規則擋成 401 或 403。三種都代表要重新啟動後端，而不是測試本身有錯。
     */
    private static final String TEST_CLOCK_RESTART_HINT =
            "後端沒有開放撥時鐘端點（/api/v1/test/clock）。請用最新程式碼、帶旗標重啟後端："
                    + "TMS_DB_URL=jdbc:postgresql://localhost:5433/tms TMS_TEST_CLOCK_ENABLED=true "
                    + "./tms/mvnw -f tms/pom.xml spring-boot:run";

    /**
     * 把跑著的後端的「現在」固定在指定時刻，直到下一次撥動或 {@link #resetBackendClock()}。
     * 後端要帶 {@code tms.test-clock.enabled=true} 啟動才有這個端點；沒有就直接失敗並提示
     * 重啟指令，不能只撥手機那一半，否則後端照真實時間判定，失敗看起來會像產品錯誤。
     */
    public void setBackendClock(OffsetDateTime moment) {
        HttpResponse<String> response = send(request("POST", "/test/clock", null)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        toJson(Map.of("isoTime", moment.toString())), StandardCharsets.UTF_8)));
        requireTestClock(response, "撥後端時鐘到 " + moment);
    }

    /**
     * 把後端時鐘撥到今天、落在飯店所有班別時段之外的第一個整分鐘，回傳撥到的時刻，
     * 讓呼叫端把裝置時間也撥到同一刻。
     *
     * <p>「某位工務人員今天的班已經結束」靠的是換人判定：現在不在飯店任何一個班別時段裡，
     * 就視為原處理人接不了。班別時段讀租戶的 {@code shift_times} 設定，形狀是
     * {@code {班別: {start, end}}}；涵蓋規則照後端：起訖相同視為全天，起點晚於終點視為跨午夜。
     * 現在已經在所有班別之外時直接撥到現在。
     *
     * <p>租戶沒有設定班別，或班別首尾相接涵蓋全天時，後端會把任何時刻都當成在班，
     * 這裡直接噴錯，提醒情境先給出班別時段，免得撥了時鐘卻判不出下班。
     */
    public OffsetDateTime setBackendClockOutsideAllShifts() {
        long tenantId = ensureTenant();
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

    /** 讓後端回到系統時鐘；每個情境開場呼叫，免得上一個情境撥過的時間留下來。 */
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

    /** 確保房間存在，回傳 rooms.id（SQL 與預設值對照 RoomFixtures.ensureRoom）。 */
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

    /**
     * 確保公共區域佔位房存在，回傳 rooms.id。欄位（floor 0、room_type public_area、
     * hk_state cleaned）照抄 {@code AutomationRuleService.ensurePublicAreaRoom}：
     * 沒有房號的單在後端一律掛在這間房上，fixture 建的那間必須和產品碼建的同形狀，
     * 否則同一個房號會長出兩種房間資料。
     */
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

    /**
     * 幫這位員工掛一筆最小可用的 {@code plan_rooms} 列，讓「這位員工負責哪一層樓」查得到。
     * SQL 與佔位房號／預留值逐句對照 {@code tms StaffFixtures.assignPlanRoom}。
     *
     * <p>為什麼非有不可：{@code GET /pool} 的 {@code sameFloor} 是用查詢者的
     * {@code plan_rooms} 所屬房間樓層去比對（TaskExecutionService.deptPool 的
     * EXISTS 子查詢），員工身上沒有任何 plan_rooms 列時，所有單的同樓層標示都會是
     * false——「負責樓層」這一欄不落地，同樓層的場景就沒有真相可驗。
     *
     * <p>{@code shift} 存的是不可解析成時刻的佔位字串（{@link #PLAN_SHIFT_PLACEHOLDER}），
     * 不是某個具體的 {@code HH:mm}。班末掃描（tms ShiftEndScanJob）就是拿
     * {@code cleaning_plans.shift} 當這位員工的班次結束時刻：填一個真的時刻，等於替
     * 每一位有「負責樓層」的員工宣告了一個班末，牆上的時鐘一過那個點，掃描就會把她
     * 名下待開始與本人延後的單整批退回待接池（{@code pool_source=shift_end_return}）
     * 並推播通知她。劇本從沒要求過班末退池，卻會在跑測試的時間落在那個時刻之後時
     * 隨機吃到它——首頁的任務卡不見、後端狀態讀回變成 pool、延後面板送不出去。
     * 解析不出時刻的值會讓掃描整個跳過這位員工（寧可漏掃也不猜），所以佔位字串是
     * 唯一不會讓測試資料替劇本多宣告一件事的寫法；tms StaffFixtures 不指定班末時刻
     * 時走的也是同一個佔位值。真的要驗班末行為的劇本，應該明寫班次而不是靠這一支。
     */
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

    /** 「{@code "X" 已停用}」：沒有對應的 admin API，直接落地（對照 tms StaffFixtures.deactivate）。 */
    public void deactivateStaff(UUID staffId) {
        execute("UPDATE staff_accounts SET active = false, status = 'inactive', updated_at = ?"
                        + " WHERE tenant_id = ? AND id = ?",
                OffsetDateTime.now(), ensureTenant(), staffId);
    }

    /**
     * 「房務待認領有以下任務：」一列：建一張房務部門、狀態 {@code pool} 的任務，並補上
     * 待認領池單卡要用的欄位。欄位對照 backend dsl.md 同名句型的「怎麼做」欄
     * （{@code pool_source}、{@code priority_notify}、{@code return_reason}、
     * {@code requeue_note}、{@code rule_hit}）加上未接提醒階段（{@code reminder_stage}）。
     *
     * @param roomNo 房號；null 代表沒有房號的單，掛到公共區域佔位房
     * @param floor  樓層；null 時由房號第一位數字推導（與 RoomFixtures 同口徑）
     */
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

    /** 由房號推導樓層：取房號第一位數字（對照 RoomFixtures.floorFromRoomNo）。 */
    public static int floorFromRoomNo(String roomNo) {
        if (roomNo == null || roomNo.isEmpty() || !Character.isDigit(roomNo.charAt(0))) {
            return 0;
        }
        return Character.getNumericValue(roomNo.charAt(0));
    }

    /**
     * 現場部門員工（房務／工務／櫃檯）：role 與 dept 同值（INSERT 欄位對照
     * StaffFixtures.insertStaff）。「負責樓層／班次」的 plan_rooms 列這裡不建——
     * 只有「查我的班次」類情境需要，要用時由 stepdef 另外補。
     */
    public CreatedStaff createFieldStaff(String name, String deptCode, String stateCode) {
        return insertStaff(name, deptCode, deptCode, stateCode);
    }

    /** 主管／值班經理：dept 固定 NULL（對照 StaffFixtures.createManagerLikeStaff）。 */
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

    /** 改這位員工的登入帳密（對照 StaffFixtures.setCredentials：沒有 admin API，直接落地）。 */
    public void setCredentials(UUID staffId, String loginId, String rawPassword) {
        execute("UPDATE staff_accounts SET login_id = ?, password_hash = ?, updated_at = ? WHERE tenant_id = ? AND id = ?",
                loginId, passwordEncoder.encode(rawPassword), OffsetDateTime.now(), ensureTenant(), staffId);
        jwtByStaffId.remove(staffId);
    }

    /**
     * Given「已連續輸入錯誤密碼 N 次」：直接落地 {@code staff_accounts.failed_login_count}
     * （T240 建的欄位）。真的在畫面上連打 N 次錯密碼又慢又把「準備狀態」跟「被測行為」
     * 攪在一起，所以照 dsl 的「透過後端測試資料」直接寫欄位。
     *
     * <p>達到鎖定門檻（5 次）時要連 {@code locked_until} 一起落地：登入端點（T287）
     * 是在「第 5 次失敗嘗試的當下」寫入鎖定到期時間，之後的登入只看
     * {@code locked_until}、不回頭看次數——只寫次數不寫鎖定，等於造出一個產品
     * 流程產生不出來的狀態（連錯 5 次卻沒被鎖），下一次密碼正確會直接登入成功，
     * 「顯示鎖定剩餘時間」的場景就永遠到不了。門檻與時長照權威規格
     * （contracts staffAccountsLogin：5 次鎖 15 分鐘）。
     */
    public void setFailedLoginCount(UUID staffId, int count) {
        OffsetDateTime lockedUntil = count >= 5 ? OffsetDateTime.now().plusMinutes(15) : null;
        execute("UPDATE staff_accounts SET failed_login_count = ?, locked_until = ?, updated_at = ?"
                        + " WHERE tenant_id = ? AND id = ?",
                count, lockedUntil, OffsetDateTime.now(), ensureTenant(), staffId);
    }

    /**
     * Given「帳號是臨時密碼」：標記 {@code staff_accounts.must_change_password}（T240 建的
     * 欄位），下次登入時後端（T287）會在 LoginResponse 帶 {@code mustChangePassword=true}，
     * App 據此進強制改密畫面。沒有 admin API，直接落地（同 {@link #setCredentials} 的理由）。
     */
    public void markMustChangePassword(UUID staffId) {
        execute("UPDATE staff_accounts SET must_change_password = true, updated_at = ? WHERE tenant_id = ? AND id = ?",
                OffsetDateTime.now(), ensureTenant(), staffId);
    }

    /**
     * Given 表的「重做次數」：直接落地 {@code tasks.redo_count}。重做是查房退回那條路累積的
     * 計數，沒有「把次數設成 N」的端點；今日進度的推薦順序（重做多的先）要有這個真相才排得出來。
     */
    public void setRedoCount(UUID taskId, int redoCount) {
        execute("UPDATE tasks SET redo_count = ? WHERE tenant_id = ? AND id = ?",
                redoCount, ensureTenant(), taskId);
    }

    /**
     * Given 表的「已做分鐘」套在一筆剛從 App 開工的任務上：把這一段的起算時刻往前推。
     *
     * <p>「已經做了 35 分鐘」沒有辦法用等的——測試不可能真的等 35 分鐘，所以已做時間
     * 一律是直接落地的。{@code tasks.segment_started_at} 就是後端計算已做時間的起點，
     * 把它往前推 N 分鐘，後端算出來的已做時間就是 N 分鐘。
     */
    public void backdateWorkStart(UUID taskId, int workedMinutes) {
        execute("UPDATE tasks SET worked_sec = 0, segment_started_at = ? WHERE tenant_id = ? AND id = ?",
                OffsetDateTime.now().minusMinutes(workedMinutes), ensureTenant(), taskId);
    }

    /**
     * 把一張單在後端的時間軸整批往前推 N 分鐘：事件時刻、開工時刻與最後更新時刻一起推。
     * 推完這張單看起來就像 N 分鐘前發生那些事，兩邊的時鐘都不必動。
     *
     * <p>「已經中斷 N 分鐘」沒有辦法用等的，也不能靠撥時鐘。撥裝置時鐘只動得到 App
     * 那一邊，後端恢復開工時寫的起算時刻用的是後端自己的系統時鐘，兩邊差出來的那一段
     * 會被算進已做時間；而注入值是固定的一個時刻，畫面上的計時從此不再前進，「計時正在
     * 走」就驗不出來了。
     *
     * <p>事件整批推同一個量，彼此的先後次序不變。{@code worked_sec} 與
     * {@code segment_started_at} 刻意不動：中斷期間不累計工時，往前推時間不該改到
     * 已經累計好的那個數字。
     */
    public void backdateTaskTimeline(UUID taskId, int minutes) {
        long tenantId = ensureTenant();
        execute("UPDATE task_events SET created_at = created_at - make_interval(mins => ?)"
                + " WHERE tenant_id = ? AND task_id = ?", minutes, tenantId, taskId);
        execute("UPDATE tasks SET started_at = started_at - make_interval(mins => ?),"
                + " updated_at = updated_at - make_interval(mins => ?)"
                + " WHERE tenant_id = ? AND id = ?", minutes, minutes, tenantId, taskId);
    }

    /**
     * 凍結一張交班單，裡面只有一則指定對象的項目（欄位對照 tms {@code HandoverFixtures}：
     * {@code snapshot} 是 {@code [{target, content, authorName}, …]}，{@code generated_by}
     * 固定 {@code system}）。交班單沒有建立端點——它是班末掃描凍結出來的快照，所以直接落地。
     *
     * <p>{@code dept} 要和任務同部門：任務詳情的交辦是以「同部門交班單中對象＝這間房的項目」
     * 併進來的（{@code HandoverService.handoversOf}），部門對不上就一則也併不進去。
     * {@code shift} 只是標籤，沒有任何斷言比對它，用時刻的上下午粗分即可（同 tms 的作法）。
     */
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

    /**
     * 以查房者身分判定某一項不合格（走正式的 {@code POST /inspections}）。後端要求判定要
     * 涵蓋租戶查房項目全集，所以這裡先把全集讀出來，指定那一項落 false、其餘 true。
     *
     * <p>情境點名的項目（例如「換床單鋪床」）不一定在租戶的查房項目裡——每個情境開場會
     * TRUNCATE 全部業務表（{@link #resetBusinessData}），{@code sop_items} 也在其中，所以
     * 這裡現場補一筆再判定。權威來源仍然是 {@code sop_items}，不是繞過它直接竄改
     * {@code inspections} 的結果。
     */
    public void failInspection(UUID taskId, CreatedStaff inspector, String failedItemLabel) {
        long tenantId = ensureTenant();
        String failKey = ensureInspectionItem(tenantId, failedItemLabel);
        List<Map<String, Object>> items = activeInspectionItemKeys(tenantId).stream()
                .map(key -> Map.<String, Object>of("key", key, "pass", !failKey.equals(key)))
                .toList();
        postInspection(taskId, inspector, items);
    }

    /** 租戶現行啟用的查房項目鍵，依 sort_order——就是 App 查房畫面列出的那一串。 */
    private List<String> activeInspectionItemKeys(long tenantId) {
        return queryRows(
                "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection'"
                        + " AND active = true ORDER BY sort_order", tenantId)
                .stream()
                .map(row -> String.valueOf(row.get("item_key")))
                .toList();
    }

    /**
     * 以查房者身分走正式的 {@code POST /inspections} 送出逐項判定（契約
     * {@code CreateInspectionRequest{taskId, items[{key, pass}]}}），每次帶一把新的
     * Idempotency-Key。非 2xx 直接噴錯：fixture 送不出去是準備狀態壞了，不是受測行為。
     */
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

    /**
     * 落地一筆房間往事（{@code room_memory}）。這張表是從完修紀錄整理出來、方便查詢的
     * 目前狀態副本，隨時可以照完修紀錄重算。產品上只有工務單完修回寫會長出一筆
     * （{@code kind=repair}），沒有任何建立端點，所以這裡直接落地。情境要的是
     * 「這間房過去修過什麼」這個既成事實，不是重演一次完修流程。
     *
     * <p>{@code summary} 的落地慣例是「{標題}：{原文}」合併成一欄（查一下的出處卡再由
     * 後端從第一個「：」切回標題與原文兩段），所以呼叫端給的標題與原文在這裡合起來寫。
     */
    public void insertRoomMemory(
            String roomNo, String kind, String title, String excerpt, OffsetDateTime occurredAt) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom(roomNo, floorFromRoomNo(roomNo));
        execute("""
                INSERT INTO room_memory (id, tenant_id, room_id, kind, summary, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, roomId, kind, title + "：" + excerpt, occurredAt);
    }

    /**
     * 以員工本人身分把某個 SOP 項標成跳過，走正式端點
     * {@code POST /api/v1/tasks/{taskId}/sop-check}（body {@code {"sopItemId": …, "result": "skipped"}}）。
     *
     * <p>情境點名的項目不一定在租戶的打掃 SOP 項裡——每個情境開場會 TRUNCATE 全部業務表
     * （{@link #resetBusinessData}），{@code sop_items} 也在其中，所以這裡現場補一筆再跳過；
     * 權威來源仍然是 {@code sop_items}，查一下的推薦問題也是從那裡還原項目全名的
     * （作法與 {@link #failInspection} 的查房項目同一套）。
     */
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

    /**
     * 確保這個租戶的打掃 SOP 項裡有這個名稱的項目，回傳它的 {@code sop_items.id}。
     *
     * <p>每個場景開場會 TRUNCATE 全部業務表（{@link #resetBusinessData}），
     * {@code sop_items} 也在其中，所以場景點名的 SOP 項要在這裡現場補一筆。
     * 權威來源仍然是 {@code sop_items}：App 的 SOP 清單與後端的勾記都認它。
     */
    public UUID ensureCleaningSopItemId(String label) {
        long tenantId = ensureTenant();
        ensureCleaningSopItem(tenantId, label);
        return (UUID) queryScalar(
                "SELECT id FROM sop_items WHERE tenant_id = ? AND applies_to = 'cleaning' AND label = ?",
                tenantId, label)
                .orElseThrow(() -> new IllegalStateException("補不出打掃 SOP 項：" + label));
    }

    /**
     * 寫一筆租戶設定。值以 JSON 落地，讀的那一邊（產品碼）一律當文字比對。
     *
     * <p>設定沒有員工視角的端點可走，後台設定端點要先造一位主管帳號，那會讓
     * 「這個租戶有誰」這件事跟著場景變動，所以這裡直接落地設定列。
     */
    public void setTenantConfig(long tenantId, String key, Object value) {
        execute("""
                INSERT INTO tenant_config (tenant_id, key, value, updated_at)
                VALUES (?, ?, ?::jsonb, ?)
                ON CONFLICT (tenant_id, key) DO UPDATE SET value = EXCLUDED.value,
                                                           updated_at = EXCLUDED.updated_at
                """, tenantId, key, toJson(value), OffsetDateTime.now());
    }

    /**
     * 免查房是租戶層的開關，產品碼在開工那一步讀它、把結果寫進這張單的
     * {@code tasks.inspection_waived}，所以要免查房的單必須在開關打開的那一刻開工。
     * 直接改這張單的欄位沒有用：開工會依開關覆寫它。
     *
     * <p>開關用完就收回：同一個場景裡後面建的其他單不該跟著免查。
     */
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

    /** 刪掉一筆租戶設定，讓這個鍵回到「沒有設定」的預設行為。 */
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

    /**
     * 班末換人的落地結果：把查房退回重做的單改掛給接手人，並把退池來源記成班末退池
     * （{@code tasks.pool_source = shift_end_return}）——退回資訊裡那句「原清房者 X 已下班，
     * 改由您接手」就是後端從這個欄位推導出來的（{@code HandoverService.returnInfoOf}）。
     *
     * <p>為什麼直接落地而不走端點：產品上這是班末掃描 job 的結果，main 還沒有這個 job，
     * 也沒有任何「把單改派給誰」的員工端或主管端端點（後端自己的 BDD 同一句 Given 走
     * {@code JobTrigger}，固定丟 UnsupportedOperationException）。退回重做時後端自動退池
     * 的那條路（{@code TaskEngine.returnToPool}）也沒有寫 {@code pool_source}，所以就算
     * 讓原清房者真的不在班上，換人原因一樣生不出來。見 tasks.md 末節的上游缺口。
     */
    public void reassignAfterShiftEnd(UUID taskId, CreatedStaff successor) {
        execute("UPDATE tasks SET state = 'accepted', assignee_id = ?, pool_source = 'shift_end_return',"
                        + " updated_at = ? WHERE tenant_id = ? AND id = ?",
                successor.id(), OffsetDateTime.now(), ensureTenant(), taskId);
    }

    /**
     * 建一張任務並帶到指定狀態。起點列（tasks＋created 事件）直接落地（欄位對照
     * TaskEngine.create 寫出的形狀），之後的狀態轉移走正式 REST 端點（claim／start／
     * interrupt／complete），事件歷程由產品碼產生。支援的狀態：
     * pool｜accepted｜doing｜interrupted｜done（pool 以外都需要 assignee）。
     * 其他狀態（inspected 等）目前沒有員工視角 REST 可走，要用時再擴充，先丟明確錯誤。
     *
     * @param workedMinutes 「已做分鐘」；doing＝目前段落回推 segment_started_at、
     *                      interrupted＝整段落在 worked_sec（落法對照 TaskFixtures.applyWorkedSeconds）
     * @param holdReason    停住原因，直接 UPDATE tasks.hold_reason（狀態轉移不經手這個欄位，
     *                      與 tms TaskFixtures 的落法一致）
     */
    public UUID createTask(
            String roomNo, String title, String typeCode, String deptCode, Integer estMin,
            String stateCode, CreatedStaff assignee, Integer workedMinutes, String holdReason) {
        return createTask(roomNo, title, typeCode, deptCode, estMin, stateCode, assignee,
                workedMinutes, holdReason, false);
    }

    /**
     * 同上，另外吃「免查房」旗標。「查房通過」與「完成 · 免查房」在狀態對照表上都是
     * {@code inspected}，差別只在 {@code tasks.inspection}（passed／waived），而今日進度的
     * 「查房通過」那一格只數 {@code inspection = 'passed'}，所以兩者必須落成不同的真相。
     * 免查房照正式流程在開工前鎖定 {@code inspection_waived}，由產品碼自己在完成時落成
     * inspected／waived（對照 tms {@code TaskFixtures} 的作法）。
     */
    public UUID createTask(
            String roomNo, String title, String typeCode, String deptCode, Integer estMin,
            String stateCode, CreatedStaff assignee, Integer workedMinutes, String holdReason,
            boolean inspectionWaived) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom(roomNo, floorFromRoomNo(roomNo));
        // 打掃任務的 work_date 是 NOT NULL 的業務約束（一房一天），其他種類不帶。
        LocalDate workDate = "cleaning".equals(typeCode) ? LocalDate.now() : null;

        UUID taskId = insertTaskAtCreatableState(
                tenantId, roomId, title, typeCode, deptCode, estMin, workDate, "pool", "desk");

        switch (stateCode) {
            case "pool" -> { /* 起點即終點 */ }
            case "accepted" -> claimAs(tenantId, taskId, requireAssignee(stateCode, assignee));
            case "doing" -> {
                claimAs(tenantId, taskId, requireAssignee(stateCode, assignee));
                withInspectionWaivedDuringStart(tenantId, inspectionWaived,
                        () -> postTaskAction(taskId, "start", assignee));
            }
            case "interrupted" -> {
                claimAs(tenantId, taskId, requireAssignee(stateCode, assignee));
                postTaskAction(taskId, "start", assignee);
                // 中斷端點（contracts tasksInterrupt）要求 reason 1–60 字，不帶 body 會
                // 被 422 擋下。場景有給停住原因就用它（之後的 hold_reason UPDATE 再覆
                // 寫同值，形同正式流程）；沒給就落一個說明用途的佔位原因。
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
                // 「已延後」：本人延後（POST /tasks/{id}/defer）只從 accepted 出發，原因 1–60 字。
                claimAs(tenantId, taskId, requireAssignee(stateCode, assignee));
                postTaskAction(taskId, "defer", assignee,
                        Map.of("reason", holdReason != null ? holdReason : "測試資料準備：延後"));
            }
            case "cancelled" -> {
                // 「已取消」＝主管作廢（POST /tasks/{id}/cancel），原因 enum 只收
                // manager_void／plan_superseded。情境只說這張單被取消、沒指定誰取消，
                // 先認領成本人名下再作廢，讓這筆真相跟其他幾筆一樣掛在同一位員工底下。
                claimAs(tenantId, taskId, requireAssignee(stateCode, assignee));
                postTaskAction(taskId, "cancel", assignee, Map.of("reason", "manager_void"));
            }
            case "inspected" -> {
                // 「查房通過」：先照正式流程走到 done，最後一步（查房通過）直接落地
                // 狀態欄位。不走正式查房 REST（POST /inspections）是刻意的：那條路
                // 會由產品碼自動補寫一筆「{房號} 查房通過」的 notifications（見 tms
                // InspectionService.notifyAssignee），跟通知中心 Given 鋪的同標題通知
                // 撞在一起，把「剛好一筆」的權威讀回弄成兩筆。
                //
                // 「完成 · 免查房」走另一條：開工前鎖定免查，產品碼在完成那一步自己
                // 落成 inspected／waived，不需要也不可以補寫 inspection = 'passed'。
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
                // 終局狀態的已做分鐘是完成當下的累計值，畫面上讀的就是它。這個 UPDATE
                // 排在狀態轉移之後，刻意覆寫完成那一步自己算出來的 worked_sec：fixture
                // 的開工與完成只隔幾毫秒，算出來一律是 0 分，場景要的分鐘數只能直接落。
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

    /**
     * 建一張工務單：任務列（type=repair、dept=engineering、source=staff）＋
     * repair_tickets 延伸列（INSERT 欄位對照 tms RepairGivenSteps.existingRepairTickets）。
     * 任務部分沿用 {@link #createTask}，支援 pool｜accepted｜doing｜done；已結案、已撤回與
     * 驗收不通過的歷程走 {@link #createRepairTicket(RepairTicketSpec)}。
     */
    public UUID createRepairTicket(
            String ticketNo, String roomNo, String categoryCode, String stateCode,
            CreatedStaff reporter, CreatedStaff assignee, String description, boolean urgent) {
        long tenantId = ensureTenant();
        UUID roomId = ensureRoom(roomNo, floorFromRoomNo(roomNo));

        // 已完修的單先帶到維修中，延到工務單列落地之後才完修：完修會走
        // 後端的修繕結案路徑，它查不到 repair_tickets 列就回 404。
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

    /**
     * 以主管身分把急件降級為一般件（contracts {@code downgradeRepairTicketUrgency}）：
     * {@code repair_tickets.is_urgent} 轉 false、{@code urgent_downgrade_reason} 落原因，
     * {@code tasks.urgent} 同步。走正式端點而不是直接 UPDATE，降級事件才會跟著長出來。
     */
    public void downgradeRepairTicketUrgency(UUID ticketTaskId, CreatedStaff manager, String note) {
        postRepairTicketAction(ticketTaskId, "downgrade-urgency", manager, Map.of("note", note));
    }

    /**
     * 不經 App，以該主管身分走正式驗收端點送出驗收結果（contracts {@code verifyRepairTicket}，
     * body 是 {@code VerifyRepairTicketRequest}）。[result] 是 {@code passed} 或 {@code failed}；
     * [reason] 是 null 時整個欄位不送。給「另一位主管已先驗收」「剛送出驗收不通過」這類句子
     * 在後端鋪好先到的那一筆。
     */
    public void verifyRepairTicket(UUID ticketTaskId, CreatedStaff manager, String result, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", result);
        if (reason != null) {
            body.put("reason", reason);
        }
        postRepairTicketAction(ticketTaskId, "verify", manager, body);
    }

    /**
     * 與 {@link #verifyRepairTicket} 送同一個請求，但不把非 2xx 當成 fixture 失敗，
     * 直接回 HTTP 狀態碼。給「這個身分呼叫驗收應被拒」這類斷言用。
     */
    public int verifyRepairTicketStatus(UUID ticketTaskId, CreatedStaff actor, String result, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", result);
        if (reason != null) {
            body.put("reason", reason);
        }
        return sendRepairTicketAction(ticketTaskId, "verify", actor, body).statusCode();
    }

    /** 以某人身分對工務單執行一個 REST 動作（verify／downgrade-urgency／…），帶 JSON body。 */
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

    /**
     * 寫一筆「今天入住」的訂房快取列（{@code reservations_cache}，欄位對照 tms
     * {@code ReservationFixtures}）。急件判定只看這張表：後端
     * {@code RepairService.judgeUrgent} 查同房今天的未作廢訂單，命中就判急件、
     * 理由組成 {@code (VIP · )HH:mm 入住}。
     *
     * @param vip {@code attrs.vip}；判定理由的「VIP · 」前綴由它決定
     */
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

    /**
     * 改一張任務的來源。{@link #createRepairTicket} 一律落成房務報修，櫃檯
     * 交辦的單要用這個改成 desk——櫃檯交辦是櫃檯端的動作，App 這側沒有可以
     * 走的正式流程。
     */
    public void setTaskSource(UUID taskId, String source) {
        execute("UPDATE tasks SET source = ? WHERE tenant_id = ? AND id = ?",
                source, ensureTenant(), taskId);
    }

    /**
     * 把一張已完修的工務單落成「主管驗收通過、已結案」：{@code tasks.state} done→closed、
     * {@code verified_by}＝驗收人、{@code verified_at}＝現在，並補一筆 {@code repair_verified}
     * 事件。fixture 不走正式驗收端點：那條路會推播報修人並寫通知，與情境自己鋪的推播、
     * 通知撞在一起；這裡只落 backend dsl「已有以下工務單」那一列要的狀態與事件。
     */
    public void closeRepairTicketAsVerified(UUID ticketTaskId, CreatedStaff verifier) {
        long tenantId = ensureTenant();
        OffsetDateTime now = OffsetDateTime.now();
        execute("UPDATE tasks SET state = 'closed', updated_at = ? WHERE tenant_id = ? AND id = ? AND state = 'done'",
                now, tenantId, ticketTaskId);
        execute("UPDATE repair_tickets SET verified_by = ?, verified_at = ? WHERE tenant_id = ? AND task_id = ?",
                verifier.id(), now, tenantId, ticketTaskId);
        appendTaskEvent(ticketTaskId, "repair_verified", "manager", verifier.id(), null, now);
    }

    /**
     * 在任務歷程最後補一筆事件：{@code event_seq} 加一，新事件取這個序號
     * （與 TaskEngine 帶狀態條件的 UPDATE 同句取號的形狀一致）。[payload] 為 null 時存 SQL NULL。
     */
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

    // ---- 主管工作台（主管首頁三類待辦、查房）用的 fixture 入口。先只放方法殼，語意另行補上。 ----

    /**
     * 把租戶啟用的查房項目整批換成這些顯示名稱，依傳入順序寫 sort_order（ios 主管工作台
     * dsl「飯店目前設定的查房項目如下」）。
     *
     * <p>查房項目沒有員工端或主管端的設定端點（它是 /admin/settings 的門面），所以直接落地
     * {@code sop_items}。先把這個租戶現有的查房項目全部停用、再逐一啟用或補建表格列出的：
     * 停用不刪（歷史查房快照要能對回來源，同 V1 schema 的註解），既有同名項目保留原
     * {@code item_key}——App 的列 id（{@code inspect.item.{key}}）與後端判定都認鍵不認名。
     */
    public void replaceInspectionItems(List<String> labels) {
        long tenantId = ensureTenant();
        disableAllInspectionItems();
        int sortOrder = 0;
        for (String label : labels) {
            upsertInspectionItem(tenantId, label, sortOrder++);
        }
    }

    /** 把租戶的查房項目全部停用（「飯店目前沒有任何啟用的查房項目」）。 */
    public void disableAllInspectionItems() {
        execute("UPDATE sop_items SET active = false WHERE tenant_id = ? AND applies_to = 'inspection'",
                ensureTenant());
    }

    /**
     * 在現行查房項目集合最後追加一項啟用的項目（「飯店在送出前新增了查房項目」）：
     * sort_order 取現行啟用集合的最大值加一，所以它一定排在最後。
     */
    public void appendInspectionItem(String label) {
        long tenantId = ensureTenant();
        int nextSortOrder = queryScalar(
                "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM sop_items"
                        + " WHERE tenant_id = ? AND applies_to = 'inspection' AND active = true", tenantId)
                .map(value -> ((Number) value).intValue())
                .orElse(0);
        upsertInspectionItem(tenantId, label, nextSortOrder);
    }

    /** 同名查房項目已存在就啟用並改排序，否則補建一筆（鍵的長法同 {@link #ensureInspectionItem}）。 */
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

    /**
     * 不經 App，以指定主管的身分對一張待查房的單送出查房，items 為現行全集且全部通過
     * （「剛被另一位主管查完」）。走正式的 {@code POST /inspections}，帶新的 Idempotency-Key。
     */
    public void passInspectionAs(UUID taskId, CreatedStaff inspector) {
        List<Map<String, Object>> items = activeInspectionItemKeys(ensureTenant()).stream()
                .map(key -> Map.<String, Object>of("key", key, "pass", true))
                .toList();
        postInspection(taskId, inspector, items);
    }

    /**
     * 把別人名下、已完成的打掃單寫成指定的完成時間（跨日待查房的 fixture）。
     * 只改 {@code tasks.done_at}（與 work_date），不動狀態與負責人。
     */
    public void setTaskDoneAt(UUID taskId, OffsetDateTime doneAt) {
        long tenantId = ensureTenant();
        // completed 事件的時刻要跟著走：backend dsl「完成時間」寫的是 done_at 與 completed
        // 事件時間兩處，待查房清單的「開始等主管」就是從這裡讀的。
        execute("UPDATE tasks SET done_at = ?, work_date = ? WHERE tenant_id = ? AND id = ?",
                doneAt, doneAt.toLocalDate(), tenantId, taskId);
        execute("UPDATE task_events SET created_at = ? WHERE tenant_id = ? AND task_id = ? AND type = 'completed'",
                doneAt, tenantId, taskId);
    }

    /**
     * 「已有以下工務單：」的一列，欄位同後端共用 DSL 的同名句型：處理結果（fixed／temporary／
     * cannot_fix／no_action_needed，null 時已完修與已結案的單視為 fixed）、處理說明、
     * 完修照片張數（null 時不需處理是 0、其他是 1）、完修時間、驗收人、來源（reported／scheduled）、
     * 最近一次驗收不通過的原因、判定人與時間，房號可為「公共區域」。
     * 時間欄為 null 時沿用 dsl 的預設；驗收人只用於已結案、且處理結果不是不需處理的單。
     */
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

    /**
     * 依 {@link RepairTicketSpec} 建一張工務單，回任務 id。
     *
     * <p>先沿用 {@link #createRepairTicket} 落到 pool｜accepted｜doing，已完修與已結案的單
     * 停在維修中，等工務單列落地後以處理人身分走正式完修（帶處理結果）：不需處理由產品碼在
     * 完修那一步直接結案，其他結果完修後再落成驗收通過（{@link #closeRepairTicketAsVerified}）。
     * 已撤回的單從待認領以報修人撤回的形狀落地。有不通過原因時最後補上最近一次驗收不通過的
     * 三欄與 {@code repair_verification_failed} 事件。
     */
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

        // 報修人是 NOT NULL 欄（repair_tickets.reporter_id）：定期保養的單劇本可以不寫
        // 報修人，資料層卻沒有「沒有人報」的寫法，先以處理人頂上；兩者都沒有就早爆。
        CreatedStaff reporter = spec.reporter() != null ? spec.reporter() : spec.assignee();
        if (reporter == null) {
            throw new IllegalArgumentException("工務單「" + spec.ticketNo()
                    + "」沒有報修人也沒有處理人：repair_tickets.reporter_id 是 NOT NULL，定期保養的單至少要有處理人");
        }
        String roomNo = spec.roomNo();
        if (spec.publicArea()) {
            // 先把佔位房建成產品碼要的形狀，後面 ensureRoom 同房號就會直接沿用它。
            ensurePublicAreaRoom();
            roomNo = PUBLIC_AREA_ROOM_NO;
        }

        String baseState = finished ? "doing" : "cancelled".equals(state) ? "pool" : state;
        UUID taskId = createRepairTicket(spec.ticketNo(), roomNo, spec.categoryCode(), baseState,
                reporter, spec.assignee(), spec.description(), spec.urgent());

        if ("scheduled".equals(spec.origin())) {
            // 定期保養＝系統開的單（backend dsl：定期保養＝system／scheduled）。
            // repair_tickets 沒有 origin 欄，只落得了 tasks.source。
            setTaskSource(taskId, "system");
        }
        if (finished) {
            int photos = spec.completionPhotoCount() != null
                    ? spec.completionPhotoCount() : (noActionNeeded ? 0 : 1);
            completeRepairTask(taskId, requireAssignee(state, spec.assignee()),
                    resolution, spec.resolutionNote(), photos);
            if ("cannot_fix".equals(resolution)) {
                // 無法修復：房況轉停用（backend dsl 同名句型的「處理結果」欄）。產品碼在完修
                // 那一步會做，這裡再寫一次只是把 fixture 的真相釘死，不依賴產品碼有沒有做。
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

    /**
     * 待認領的工務單落成「報修人撤回」：state=cancelled、cancel_reason=staff_withdrew，補一筆
     * cancelled 事件（payload reason=staff_withdrew，與 TaskEngine 撤回同形狀）。
     */
    private void markWithdrawnByReporter(UUID taskId, CreatedStaff reporter) {
        long tenantId = ensureTenant();
        OffsetDateTime now = OffsetDateTime.now();
        execute("UPDATE tasks SET state = 'cancelled', cancel_reason = 'staff_withdrew', updated_at = ?"
                        + " WHERE tenant_id = ? AND id = ? AND state = 'pool'",
                now, tenantId, taskId);
        String actorRole = reporter.dept() != null ? reporter.dept() : reporter.role();
        appendTaskEvent(taskId, "cancelled", actorRole, reporter.id(), Map.of("reason", "staff_withdrew"), now);
    }

    /**
     * 補上「這張單曾被主管驗收不通過」：三個 {@code last_verify_failed_*} 欄同時寫入，
     * 並補一筆 {@code repair_verification_failed} 事件（payload 帶 reason、destination：
     * 待認領是 pool，其他是 assignee；有處理人時另帶 assigneeId）。待認領的單另把
     * {@code pool_source} 寫成 verification_return、清空負責人，代表原處理人接不了、回到工務待認領。
     */
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

    /**
     * 同 {@link #completeRepairTask(UUID, CreatedStaff)}，另外帶處理結果四選一
     * （契約 {@code CompleteTaskRequest.resolutionType}：fixed／temporary／cannot_fix／no_action_needed，null 視為 fixed）、
     * 處理說明與完修照片張數；照片逐張走正式的建立＋上傳。
     */
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

    /**
     * 幫一張已落地的工務單補 N 張報修照片（{@code repair_photos}，purpose=report、已上傳）。
     * 報修照片在產品上是開單前上傳、開單時綁定的；fixture 的單不是從 App 開的，沒有那一步，
     * 而詳情頁的照片區與「照片張數」斷言要有東西可數，所以直接落地已綁定的列。
     */
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

    /** 直接落地某房的房況（{@code rooms.hk_state}：dirty／cleaned／inspected／out_of_order）。 */
    public void setRoomHkState(String roomNo, String hkState) {
        execute("UPDATE rooms SET hk_state = ?, updated_at = ? WHERE tenant_id = ? AND room_no = ?",
                hkState, OffsetDateTime.now(), ensureTenant(), roomNo);
    }

    /** 權威讀回：某房目前的 {@code rooms.hk_state}；沒有這間房直接噴錯。 */
    public String roomHkState(String roomNo) {
        return String.valueOf(queryScalar(
                "SELECT hk_state FROM rooms WHERE tenant_id = ? AND room_no = ?", ensureTenant(), roomNo)
                .orElseThrow(() -> new IllegalStateException("後端沒有房號「" + roomNo + "」的房間")));
    }

    /**
     * 權威讀回：{@code outbox} 裡發給這位員工的推播（kind push），回每一筆的標題，新到舊。
     * 後端寫推播有兩種形狀：{@code OutboxWriter.enqueuePush} 包成
     * {@code {tenant_id, task_id, payload: PushMessage}} 的信封，收件人與標題在內層；
     * {@code PushOutboxWriter.enqueue}（查房結果等）直接把 PushMessage 當 payload，收件人與
     * 標題在最外層。兩種都要認，只讀一種會把另一種的推播當成沒送。
     */
    public List<String> pushTitlesSentTo(UUID staffId) {
        return queryRows("""
                SELECT COALESCE(payload->'payload'->>'title', payload->>'title') AS title FROM outbox
                 WHERE tenant_id = ? AND kind = 'push'
                   AND COALESCE(payload->'payload'->>'recipientStaffId', payload->>'recipientStaffId') = ?
                 ORDER BY id DESC
                """, ensureTenant(), staffId.toString())
                .stream().map(row -> String.valueOf(row.get("title"))).toList();
    }

    /** 權威讀回：以該主管身分呼叫 {@code getManagerQueues}（{@code GET /me/manager-queues}）。 */
    public JsonNode managerQueues(CreatedStaff manager) {
        return getJson("/me/manager-queues", manager);
    }

    /**
     * 幫這位員工掛一筆 {@code plan_rooms} 列，並把班次欄寫成真的班次結束時刻（{@code HH:mm}）。
     * 與 {@link #assignPlanRoom(UUID, int)} 的差別只在 {@code cleaning_plans.shift}：那一支刻意
     * 寫佔位值讓班末掃描跳過；劇本明寫「班次結束」時才用這一支，班末行為就由這個值驅動。
     */
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

    /**
     * 建員工並明寫角色、部門與狀態（ios dsl「飯店有以下員工」的「角色」欄：部門寫房務或工務而
     * 角色寫主管，代表被設了部門的主管帳號——{@code role}=manager、{@code dept} 仍是該部門）。
     * 其他兩支（{@link #createFieldStaff}／{@link #createManagerLikeStaff}）是這一支的固定組合。
     */
    public CreatedStaff createStaff(String name, String roleCode, String deptCode, String stateCode) {
        return insertStaff(name, roleCode, deptCode, stateCode);
    }

    /**
     * 以負責人身分走正式完修流程完修一張工務單。給「已完修」的工務單
     * fixture 用：修繕任務的 complete 一定要附完修照片，所以這裡連照片一起
     * 備齊，事件歷程與正式流程同形狀。
     */
    public void completeRepairTask(UUID taskId, CreatedStaff assignee) {
        UUID photoId = createCompletionPhoto(assignee);
        postTaskAction(taskId, "complete", assignee,
                Map.of("photoIds", List.of(photoId.toString())));
    }

    /**
     * 備一張可以被 complete 引用的完修照片。後端只認 status=uploaded 的照片，
     * 所以建立位置之後一定要接著上傳內容，少了第二段 complete 會被 422 擋下。
     */
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

    /**
     * 租戶內遞增取下一個工務單號（取號 SQL 對照 tms RepairGivenSteps.allocateNextTicketNo，
     * 與產品 createRepairTicket 共用同一個 repair_ticket_counters 計數器），給「手上有 N 張
     * 維修中的工務單」這類合成單用，不會與場景點名的單號撞號。
     */
    public int allocateNextRepairTicketNo() {
        Object lastNo = queryScalar("""
                INSERT INTO repair_ticket_counters (tenant_id, last_no) VALUES (?, 1)
                ON CONFLICT (tenant_id) DO UPDATE SET last_no = repair_ticket_counters.last_no + 1
                RETURNING last_no
                """, ensureTenant())
                .orElseThrow(() -> new IllegalStateException("repair_ticket_counters 取號沒有回傳 last_no"));
        return ((Number) lastNo).intValue();
    }

    /**
     * 建一筆通知中心的通知紀錄（INSERT 欄位對照 tms 後端 PushGivenSteps 對
     * notifications 表的既有建法；該表沒有 REST 造數入口，走 JDBC 直接落地）。
     *
     * @param event  這筆通知從哪一個推播事件落地（contracts PushPayload.event 的值域；
     *               NOT NULL 欄位，資料層另有 CHECK 把關）。Given 句型沒有這個參數，
     *               由呼叫端依標題的文案格式反推（推導規則見 steps/push 的 PushGivenSteps）
     * @param target 去處（contracts PushTarget 形狀，key→value；NOT NULL 欄位）。
     *               通知的 Given 句型沒有去處參數，由呼叫端依標題對應的任務推導
     *               （推導邏輯見 steps/push 的 PushGivenSteps），對不上任務時用
     *               最小合法形狀 {@code kind=home}
     * @param read   是否已讀；已讀時 {@code read_at} 落在與 {@code created_at}
     *               相同的時刻，因為句型沒有規定已讀動作發生在何時，另外編一個
     *               時間是在測試資料裡製造沒有規格依據的事實
     */
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

    /**
     * 直接落地一列「可作為起點」的任務（pool）＋第 1 筆 created 事件，
     * 欄位與事件形狀對照 TaskEngine.create（event_seq=1、task_events.seq=1、actor=system、
     * payload 帶建立快照）。不寫 outbox，理由見類別 Javadoc 的偏差 2。
     */
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

    /**
     * 讓 assignee 認領這張單。員工走正式認領端點；主管沒有部門，認領端點只放行同部門的人
     * （{@code TaskEngine.claim} 比對 {@code tasks.dept}），而產品上也沒有「把單派給主管」的
     * 端點，所以主管名下的單照 {@code TaskEngine.claim} 寫出的形狀直接落地：
     * state=accepted、assignee_id、event_seq+1，並補一筆 claimed 事件。之後的開工、完成
     * 仍走正式端點（那兩支只比對 assignee_id）。
     */
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

    /**
     * 用 fixture 帳密走正式登入端點換 JWT，同一位員工快取重用。tms 的 StaffFixtures
     * 是直接簽發 JWT，harness 沒有簽章私鑰，理由見類別 Javadoc 的偏差 1。
     */
    public String jwt(CreatedStaff staff) {
        return jwtByStaffId.computeIfAbsent(staff.id(), ignored -> login(staff.loginId(), staff.password()));
    }

    /** POST /api/v1/staff-accounts/login，回傳 LoginResponse.token（欄位名依契約 員工帳號與登入.yaml）。 */
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

    /** 以某員工身分對任務執行一個動作（claim/start/interrupt/complete/…），不帶 body。 */
    public void postTaskAction(UUID taskId, String action, CreatedStaff actor) {
        postTaskAction(taskId, action, actor, null);
    }

    /** 同上，但帶 JSON body（例如 interrupt 的 {@code {"reason": …}}）。 */
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

    /**
     * 以某員工身分重送一次任務動作，預期後端以 4xx 拒絕，回傳解析後的錯誤 body
     * （contracts 的 Error：{@code {code, message, …}}）。預期之外的狀態碼
     * （2xx 成功、5xx 掛掉）直接噴錯。
     *
     * <p>給「App 的動作被後端拒絕，畫面要原封不動轉述後端 message」的 Then 用：
     * App 當下收到的那份 4xx 回應事後讀不到，所以以同一位員工對同一筆任務重送
     * 同一個動作，取回同一句拒絕訊息當比對基準。被拒的動作在後端沒有副作用，
     * 重送不會改變狀態——但那是這裡的前提不是證明，呼叫端仍應在重送後自己查核
     * 權威狀態沒變。
     */
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

    /** 權威讀回（REST）：GET /api/v1 下任意路徑，回傳解析後的 JSON。斷言入口，非 200 直接噴錯。 */
    public JsonNode getJson(String path, CreatedStaff viewer) {
        HttpResponse<String> response = send(request("GET", path, jwt(viewer)).GET());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("權威讀回失敗：GET " + path + " -> HTTP "
                    + response.statusCode() + "，body=" + response.body());
        }
        return parseJson(response.body());
    }

    /** 權威讀回（JDBC）：tasks 整列，給 REST 沒曝露的欄位（state／hold_reason／worked_sec…）斷言用。 */
    public Map<String, Object> taskRow(UUID taskId) {
        return singleRow("SELECT * FROM tasks WHERE tenant_id = ? AND id = ?",
                "tasks id=" + taskId, ensureTenant(), taskId);
    }

    /** 權威讀回（JDBC）：repair_tickets 連所屬任務的狀態欄位一起回。 */
    public Map<String, Object> repairTicketRow(String ticketNo) {
        return singleRow("""
                SELECT rt.*, t.state AS task_state, t.urgent AS task_urgent
                FROM repair_tickets rt JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                WHERE rt.tenant_id = ? AND rt.ticket_no = ?
                """, "repair_tickets ticket_no=" + ticketNo, ensureTenant(), ticketNo);
    }

    /** 權威讀回（JDBC）：staff_accounts 整列（failed_login_count／locked_until／must_change_password…）。 */
    public Map<String, Object> staffAccountRow(UUID staffId) {
        return singleRow("SELECT * FROM staff_accounts WHERE tenant_id = ? AND id = ?",
                "staff_accounts id=" + staffId, ensureTenant(), staffId);
    }

    /** 泛用唯讀查詢：上面三個整列入口涵蓋不到的權威斷言用（只給 SELECT）。 */
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
            // 鎖 HTTP/1.1：比照 AppiumWorld 對後門連線的既有設定，整個 harness 用同一種
            // 連線行為，出問題時只有一種行為要排查。
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
