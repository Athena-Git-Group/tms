package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.IntegrationSseSupport;
import io.cucumber.java.en.Given;
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
import org.springframework.core.env.Environment;

public class IntegrationGivenSteps {
    public static final String KEYS_KEY = "integration:apiKeys";
    public static final String EVENT_IDS_KEY = "integration:eventIds";
    public static final String EVENT_ID_BY_ROOM_KEY = "integration:eventIdByRoom";
    public static final String TAMPERED_KEY = "integration:tamperedKey";
    public static final String STAFF_TOKENS_KEY = "integration:staffTokens";
    public static final String SSE_CLIENTS_KEY = "integration:sseClients";
    public static final String CURRENT_SSE_KEY = "integration:sse";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ScenarioContext ctx;
    private final DbSupport db;
    private final ApiClient api;
    private final Environment environment;

    public IntegrationGivenSteps(ScenarioContext ctx, DbSupport db, ApiClient api, Environment environment) {
        this.ctx = ctx;
        this.db = db;
        this.api = api;
        this.environment = environment;
    }

    @Given(value="^租戶 (\\d+) 存在有效整合金鑰，別名 \"([^\"]+)\"$")
    public void activeApiKeyExists(long tenantId, String alias) {
        this.issueKey(tenantId, alias, tenantId, null);
    }

    @Given(value="^租戶 (\\d+) 存在整合金鑰，別名 \"([^\"]+)\"，revoked_at 為 \"([^\"]+)\"$")
    public void revokedApiKeyExists(long tenantId, String alias, String revokedAt) {
        this.issueKey(tenantId, alias, tenantId, OffsetDateTime.parse(revokedAt));
    }

    @Given(value="^租戶 (\\d+) 存在有效整合金鑰，別名 \"([^\"]+)\"，但其明文的租戶段被改寫為 (\\d+)$")
    public void tamperedApiKeyExists(long issuerTenantId, String alias, long claimedTenantId) {
        String tampered = this.issueKey(issuerTenantId, alias, claimedTenantId, null);
        this.ctx.put(TAMPERED_KEY, tampered);
    }

    @Given(value="^租戶 1 存在已登入的 manager 員工，別名 \"([^\"]+)\"$")
    public void loggedInManagerExists(String alias) {
        Map<String, String> tokens = this.mapIn(STAFF_TOKENS_KEY);
        tokens.put(alias, this.api.staffToken(1L, "mg1"));
    }

    @Given(value="^租戶 (\\d+) 的房 \"(\\d+)\" 已發生查房通過事件$")
    public void inspectionPassedHappened(long tenantId, String roomNo) {
        this.insertInspectionEvent(tenantId, roomNo, "inspection_passed", 10);
    }

    @Given(value="^租戶 (\\d+) 的房 \"(\\d+)\" 已發生查房通過事件，發生於 (\\d+) 秒前$")
    public void inspectionPassedHappenedSecondsAgo(long tenantId, String roomNo, int secondsAgo) {
        this.insertInspectionEvent(tenantId, roomNo, "inspection_passed", secondsAgo);
    }

    @Given(value="^租戶 (\\d+) 的房 \"(\\d+)\" 已發生查房退回事件$")
    public void inspectionFailedHappened(long tenantId, String roomNo) {
        this.insertInspectionEvent(tenantId, roomNo, "inspection_failed", 10);
    }

    @Given(value="^租戶 (\\d+) 的房 \"(\\d+)\"、\"(\\d+)\"、\"(\\d+)\" 依序各發生一筆查房通過事件$")
    public void threeInspectionsPassedInOrder(long tenantId, String first, String second, String third) {
        this.insertInspectionEvent(tenantId, first, "inspection_passed", 10);
        this.insertInspectionEvent(tenantId, second, "inspection_passed", 10);
        this.insertInspectionEvent(tenantId, third, "inspection_passed", 10);
    }

    @Given(value="^租戶 1 的房 \"(\\d+)\" 已發生查房通過事件，且 PMS 已取得其 event_id$")
    public void inspectionPassedAndEventIdKnown(String roomNo) {
        this.insertInspectionEvent(1L, roomNo, "inspection_passed", 10);
    }

    @Given(value="^斷線期間租戶 1 的房 \"(\\d+)\" 又發生一筆查房通過事件$")
    public void inspectionPassedWhileDisconnected(String roomNo) {
        this.insertInspectionEvent(1L, roomNo, "inspection_passed", 5);
    }

    @Given(value="^PMS 以別名 \"([^\"]+)\" 的金鑰建立整合 SSE 連線$")
    public void pmsOpensIntegrationStream(String alias) throws Exception {
        this.openIntegrationStream("/integration/pms/stream", this.apiKey(alias));
    }

    public IntegrationSseSupport.IntegrationSseClient openIntegrationStream(String path, String apiKey) throws Exception {
        IntegrationSseSupport.IntegrationSseClient client = IntegrationSseSupport.open((Integer)this.environment.getRequiredProperty("local.server.port", Integer.class), path, apiKey);
        ArrayList<IntegrationSseSupport.IntegrationSseClient> clients = (ArrayList<IntegrationSseSupport.IntegrationSseClient>)this.ctx.get(SSE_CLIENTS_KEY);
        if (clients == null) {
            clients = new ArrayList<IntegrationSseSupport.IntegrationSseClient>();
            this.ctx.put(SSE_CLIENTS_KEY, clients);
        }
        clients.add(client);
        this.ctx.put(CURRENT_SSE_KEY, client);
        return client;
    }

    public String apiKey(String alias) {
        Map<String, String> keys = this.mapIn(KEYS_KEY);
        String plaintext = keys.get(alias);
        if (plaintext == null) {
            throw new IllegalStateException("金鑰別名「" + alias + "」沒有登記——Given 漏了");
        }
        return plaintext;
    }

    private String issueKey(long issuerTenantId, String alias, long claimedTenantId, OffsetDateTime revokedAt) {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String plaintext = "tmsik_" + claimedTenantId + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        this.db.admin().update("INSERT INTO integration_api_keys (id, tenant_id, name, key_prefix, key_hash,\n                                  created_at, revoked_at)\nVALUES (?, ?, ?, ?, ?, now(), ?)\n", new Object[]{UUID.randomUUID(), issuerTenantId, alias, plaintext.substring(0, 16), IntegrationGivenSteps.sha256Hex(plaintext), revokedAt});
        this.mapIn(KEYS_KEY).put(alias, plaintext);
        return plaintext;
    }

    public void insertInspectionEvent(long tenantId, String roomNo, String eventType, int secondsAgo) {
        UUID roomId = (UUID)this.db.admin().queryForObject("SELECT id FROM rooms WHERE tenant_id = ? AND room_no = ?", UUID.class, new Object[]{tenantId, roomNo});
        UUID taskId = UUID.randomUUID();
        this.db.admin().update("INSERT INTO tasks (id, tenant_id, type, source, room_id, dept, state, event_seq,\n                   title, created_at, updated_at)\nVALUES (?, ?, 'amenity', 'desk', ?, 'housekeeping', ?, 1, ?, now(), now())\n", new Object[]{taskId, tenantId, roomId, "inspection_passed".equals(eventType) ? "inspected" : "accepted", "整合測試佈景（房 " + roomNo + "）"});
        Long eventId = (Long)this.db.admin().queryForObject("INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, payload,\n                         created_at)\nVALUES (?, ?, 1, ?, 'manager', NULL, now() - make_interval(secs => ?))\nRETURNING id\n", Long.class, new Object[]{tenantId, taskId, eventType, (double)secondsAgo});
        this.ctx.taskId(taskId);
        this.listIn(EVENT_IDS_KEY).add(Long.toString(eventId));
        this.mapIn(EVENT_ID_BY_ROOM_KEY).put(roomNo, Long.toString(eventId));
    }

    private Map<String, String> mapIn(String key) {
        HashMap map = (HashMap)this.ctx.get(key);
        if (map == null) {
            map = new HashMap();
            this.ctx.put(key, map);
        }
        return map;
    }

    private List<String> listIn(String key) {
        ArrayList list = (ArrayList)this.ctx.get(key);
        if (list == null) {
            list = new ArrayList();
            this.ctx.put(key, list);
        }
        return list;
    }

    private static String sha256Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
