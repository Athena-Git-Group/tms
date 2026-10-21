package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.IntegrationSseSupport;
import com.athena.tms.integration.stepdefs.utils.IntegrationSseSupport.IntegrationSseClient;
import io.cucumber.java.en.Given;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_1;

/**
 * 15-pms-integration feature 的佈景步驟：整合金鑰種子、已發生的查房事件、
 * 整合 SSE 連線。
 *
 * <p>為什麼佈景走 SQL 直插而不是走應用：
 * <ul>
 *   <li>金鑰開立沒有應用端點——簽發是營運拿 admin 身分做的事，應用角色
 *       對 integration_api_keys 只有 SELECT 與撤銷 UPDATE。測試照營運的
 *       做法直插，明文自算 SHA-256。</li>
 *   <li>「已發生查房通過事件」不走 TaskEngine，因為引擎寫的 created_at
 *       一律是當下時間，而讀取端有 3 秒安全遲滯：剛寫入的事件查不到，
 *       拉取場景會全部誤判成空列。直插才能把事件「做舊」到遲滯窗外，
 *       遲滯邊界場景也才能精準指定 2 秒前／5 秒前。回推一律以資料庫時鐘
 *       為基準（now() - interval）：遲滯比對用的是應用的系統時鐘，不是
 *       測試 context 釘死的 Clock bean，兩者與 DB 同機、相對時間不漂。</li>
 * </ul>
 */
public class IntegrationGivenSteps {

    /** ctx 鍵：別名 → 金鑰明文。 */
    public static final String KEYS_KEY = "integration:apiKeys";
    /** ctx 鍵：依發生順序記錄的 event_id（十進位字串）。 */
    public static final String EVENT_IDS_KEY = "integration:eventIds";
    /** ctx 鍵：room_no → 該房最新事件的 event_id。 */
    public static final String EVENT_ID_BY_ROOM_KEY = "integration:eventIdByRoom";
    /** ctx 鍵：改寫租戶段後的金鑰明文。 */
    public static final String TAMPERED_KEY = "integration:tamperedKey";
    /** ctx 鍵：員工別名 → JWT。 */
    public static final String STAFF_TOKENS_KEY = "integration:staffTokens";
    /** ctx 鍵：場景中開啟的整合 SSE 連線（hooks 收尾關閉）。 */
    public static final String SSE_CLIENTS_KEY = "integration:sseClients";
    /** ctx 鍵：最近一條整合 SSE 連線（斷言的預設對象）。 */
    public static final String CURRENT_SSE_KEY = "integration:sse";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ScenarioContext ctx;
    private final DbSupport db;
    private final ApiClient api;
    private final Environment environment;

    public IntegrationGivenSteps(ScenarioContext ctx, DbSupport db, ApiClient api,
                                 Environment environment) {
        this.ctx = ctx;
        this.db = db;
        this.api = api;
        this.environment = environment;
    }

    @Given("^租戶 (\\d+) 存在有效整合金鑰，別名 \"([^\"]+)\"$")
    public void activeApiKeyExists(long tenantId, String alias) {
        issueKey(tenantId, alias, tenantId, null);
    }

    @Given("^租戶 (\\d+) 存在整合金鑰，別名 \"([^\"]+)\"，revoked_at 為 \"([^\"]+)\"$")
    public void revokedApiKeyExists(long tenantId, String alias, String revokedAt) {
        issueKey(tenantId, alias, tenantId, OffsetDateTime.parse(revokedAt));
    }

    @Given("^租戶 (\\d+) 存在有效整合金鑰，別名 \"([^\"]+)\"，但其明文的租戶段被改寫為 (\\d+)$")
    public void tamperedApiKeyExists(long issuerTenantId, String alias, long claimedTenantId) {
        // 要打中「hash 命中列、但列上租戶與自述不符」的分支，列上的 key_hash
        // 必須等於改寫後明文的 hash——簽發時就以自述租戶段造明文、掛在簽發租戶名下
        String tampered = issueKey(issuerTenantId, alias, claimedTenantId, null);
        ctx.put(TAMPERED_KEY, tampered);
    }

    @Given("^租戶 1 存在已登入的 manager 員工，別名 \"([^\"]+)\"$")
    public void loggedInManagerExists(String alias) {
        Map<String, String> tokens = mapIn(STAFF_TOKENS_KEY);
        // V2 種子的王主管（mg1）就是 manager，走真登入換 JWT
        tokens.put(alias, api.staffToken(TENANT_1, "mg1"));
    }

    @Given("^租戶 (\\d+) 的房 \"(\\d+)\" 已發生查房通過事件$")
    public void inspectionPassedHappened(long tenantId, String roomNo) {
        insertInspectionEvent(tenantId, roomNo, "inspection_passed", 10);
    }

    @Given("^租戶 (\\d+) 的房 \"(\\d+)\" 已發生查房通過事件，發生於 (\\d+) 秒前$")
    public void inspectionPassedHappenedSecondsAgo(long tenantId, String roomNo, int secondsAgo) {
        insertInspectionEvent(tenantId, roomNo, "inspection_passed", secondsAgo);
    }

    @Given("^租戶 (\\d+) 的房 \"(\\d+)\" 已發生查房退回事件$")
    public void inspectionFailedHappened(long tenantId, String roomNo) {
        insertInspectionEvent(tenantId, roomNo, "inspection_failed", 10);
    }

    @Given("^租戶 (\\d+) 的房 \"(\\d+)\"、\"(\\d+)\"、\"(\\d+)\" 依序各發生一筆查房通過事件$")
    public void threeInspectionsPassedInOrder(long tenantId, String first, String second,
                                              String third) {
        // task_events.id 是 IDENTITY，依插入順序遞增——「依序」靠插入順序成立；
        // 一律做舊到遲滯窗外，查詢當下三筆都該可見
        insertInspectionEvent(tenantId, first, "inspection_passed", 10);
        insertInspectionEvent(tenantId, second, "inspection_passed", 10);
        insertInspectionEvent(tenantId, third, "inspection_passed", 10);
    }

    @Given("^租戶 1 的房 \"(\\d+)\" 已發生查房通過事件，且 PMS 已取得其 event_id$")
    public void inspectionPassedAndEventIdKnown(String roomNo) {
        // event_id 在插入時就記進 ctx，「已取得」不需要真的先打一次拉取端點
        insertInspectionEvent(TENANT_1, roomNo, "inspection_passed", 10);
    }

    @Given("^斷線期間租戶 1 的房 \"(\\d+)\" 又發生一筆查房通過事件$")
    public void inspectionPassedWhileDisconnected(String roomNo) {
        // 做舊 5 秒：回放有 3 秒安全遲滯，事件太新的話重連當下回放不含它，
        // 場景會誤判成漏推
        insertInspectionEvent(TENANT_1, roomNo, "inspection_passed", 5);
    }

    @Given("^PMS 以別名 \"([^\"]+)\" 的金鑰建立整合 SSE 連線$")
    public void pmsOpensIntegrationStream(String alias) throws Exception {
        openIntegrationStream("/integration/pms/stream", apiKey(alias));
    }

    /** 共用給 when 步驟（重連帶 cursor）開第二條連線。 */
    public IntegrationSseClient openIntegrationStream(String path, String apiKey) throws Exception {
        IntegrationSseClient client = IntegrationSseSupport.open(
                environment.getRequiredProperty("local.server.port", Integer.class), path, apiKey);
        List<IntegrationSseClient> clients = ctx.get(SSE_CLIENTS_KEY);
        if (clients == null) {
            clients = new ArrayList<>();
            ctx.put(SSE_CLIENTS_KEY, clients);
        }
        clients.add(client);
        ctx.put(CURRENT_SSE_KEY, client);
        return client;
    }

    public String apiKey(String alias) {
        Map<String, String> keys = mapIn(KEYS_KEY);
        String plaintext = keys.get(alias);
        if (plaintext == null) {
            throw new IllegalStateException("金鑰別名「" + alias + "」沒有登記——Given 漏了");
        }
        return plaintext;
    }

    /**
     * 造明文、落列、登記別名。claimedTenantId 是寫進明文租戶段的值，
     * issuerTenantId 是列上的 tenant_id——正常簽發兩者相同，租戶段改寫
     * 場景刻意不同。
     */
    private String issueKey(long issuerTenantId, String alias, long claimedTenantId,
                            OffsetDateTime revokedAt) {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String plaintext = "tmsik_" + claimedTenantId + "_"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        db.admin().update("""
                INSERT INTO integration_api_keys (id, tenant_id, name, key_prefix, key_hash,
                                                  created_at, revoked_at)
                VALUES (?, ?, ?, ?, ?, now(), ?)
                """,
                UUID.randomUUID(), issuerTenantId, alias, plaintext.substring(0, 16),
                sha256Hex(plaintext), revokedAt);
        mapIn(KEYS_KEY).put(alias, plaintext);
        return plaintext;
    }

    /**
     * 直插一張帶單筆查房事件的任務。type 用 amenity 避開「同房同日僅一張
     * 未完成打掃單」的約束；事件 seq 固定 1——整合讀取只認 (task, seq) 與
     * 事件列本身，前段事件鏈與這條路徑無關。
     */
    public void insertInspectionEvent(long tenantId, String roomNo, String eventType,
                                      int secondsAgo) {
        UUID roomId = db.admin().queryForObject(
                "SELECT id FROM rooms WHERE tenant_id = ? AND room_no = ?",
                UUID.class, tenantId, roomNo);
        UUID taskId = UUID.randomUUID();
        db.admin().update("""
                INSERT INTO tasks (id, tenant_id, type, source, room_id, dept, state, event_seq,
                                   title, created_at, updated_at)
                VALUES (?, ?, 'amenity', 'desk', ?, 'housekeeping', ?, 1, ?, now(), now())
                """,
                taskId, tenantId, roomId,
                "inspection_passed".equals(eventType) ? "inspected" : "accepted",
                "整合測試佈景（房 " + roomNo + "）");
        Long eventId = db.admin().queryForObject("""
                INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, payload,
                                         created_at)
                VALUES (?, ?, 1, ?, 'manager', NULL, now() - make_interval(secs => ?))
                RETURNING id
                """, Long.class, tenantId, taskId, eventType, (double) secondsAgo);
        ctx.taskId(taskId);
        listIn(EVENT_IDS_KEY).add(Long.toString(eventId));
        mapIn(EVENT_ID_BY_ROOM_KEY).put(roomNo, Long.toString(eventId));
    }

    private Map<String, String> mapIn(String key) {
        Map<String, String> map = ctx.get(key);
        if (map == null) {
            map = new HashMap<>();
            ctx.put(key, map);
        }
        return map;
    }

    private List<String> listIn(String key) {
        List<String> list = ctx.get(key);
        if (list == null) {
            list = new ArrayList<>();
            ctx.put(key, list);
        }
        return list;
    }

    private static String sha256Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
