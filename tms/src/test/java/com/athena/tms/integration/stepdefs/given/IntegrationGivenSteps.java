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

    @Given(value="^\u79df\u6236 (\\d+) \u5b58\u5728\u6709\u6548\u6574\u5408\u91d1\u9470\uff0c\u5225\u540d \"([^\"]+)\"$")
    public void activeApiKeyExists(long tenantId, String alias) {
        this.issueKey(tenantId, alias, tenantId, null);
    }

    @Given(value="^\u79df\u6236 (\\d+) \u5b58\u5728\u6574\u5408\u91d1\u9470\uff0c\u5225\u540d \"([^\"]+)\"\uff0crevoked_at \u70ba \"([^\"]+)\"$")
    public void revokedApiKeyExists(long tenantId, String alias, String revokedAt) {
        this.issueKey(tenantId, alias, tenantId, OffsetDateTime.parse(revokedAt));
    }

    @Given(value="^\u79df\u6236 (\\d+) \u5b58\u5728\u6709\u6548\u6574\u5408\u91d1\u9470\uff0c\u5225\u540d \"([^\"]+)\"\uff0c\u4f46\u5176\u660e\u6587\u7684\u79df\u6236\u6bb5\u88ab\u6539\u5beb\u70ba (\\d+)$")
    public void tamperedApiKeyExists(long issuerTenantId, String alias, long claimedTenantId) {
        String tampered = this.issueKey(issuerTenantId, alias, claimedTenantId, null);
        this.ctx.put(TAMPERED_KEY, tampered);
    }

    @Given(value="^\u79df\u6236 1 \u5b58\u5728\u5df2\u767b\u5165\u7684 manager \u54e1\u5de5\uff0c\u5225\u540d \"([^\"]+)\"$")
    public void loggedInManagerExists(String alias) {
        Map<String, String> tokens = this.mapIn(STAFF_TOKENS_KEY);
        tokens.put(alias, this.api.staffToken(1L, "mg1"));
    }

    @Given(value="^\u79df\u6236 (\\d+) \u7684\u623f \"(\\d+)\" \u5df2\u767c\u751f\u67e5\u623f\u901a\u904e\u4e8b\u4ef6$")
    public void inspectionPassedHappened(long tenantId, String roomNo) {
        this.insertInspectionEvent(tenantId, roomNo, "inspection_passed", 10);
    }

    @Given(value="^\u79df\u6236 (\\d+) \u7684\u623f \"(\\d+)\" \u5df2\u767c\u751f\u67e5\u623f\u901a\u904e\u4e8b\u4ef6\uff0c\u767c\u751f\u65bc (\\d+) \u79d2\u524d$")
    public void inspectionPassedHappenedSecondsAgo(long tenantId, String roomNo, int secondsAgo) {
        this.insertInspectionEvent(tenantId, roomNo, "inspection_passed", secondsAgo);
    }

    @Given(value="^\u79df\u6236 (\\d+) \u7684\u623f \"(\\d+)\" \u5df2\u767c\u751f\u67e5\u623f\u9000\u56de\u4e8b\u4ef6$")
    public void inspectionFailedHappened(long tenantId, String roomNo) {
        this.insertInspectionEvent(tenantId, roomNo, "inspection_failed", 10);
    }

    @Given(value="^\u79df\u6236 (\\d+) \u7684\u623f \"(\\d+)\"\u3001\"(\\d+)\"\u3001\"(\\d+)\" \u4f9d\u5e8f\u5404\u767c\u751f\u4e00\u7b46\u67e5\u623f\u901a\u904e\u4e8b\u4ef6$")
    public void threeInspectionsPassedInOrder(long tenantId, String first, String second, String third) {
        this.insertInspectionEvent(tenantId, first, "inspection_passed", 10);
        this.insertInspectionEvent(tenantId, second, "inspection_passed", 10);
        this.insertInspectionEvent(tenantId, third, "inspection_passed", 10);
    }

    @Given(value="^\u79df\u6236 1 \u7684\u623f \"(\\d+)\" \u5df2\u767c\u751f\u67e5\u623f\u901a\u904e\u4e8b\u4ef6\uff0c\u4e14 PMS \u5df2\u53d6\u5f97\u5176 event_id$")
    public void inspectionPassedAndEventIdKnown(String roomNo) {
        this.insertInspectionEvent(1L, roomNo, "inspection_passed", 10);
    }

    @Given(value="^\u65b7\u7dda\u671f\u9593\u79df\u6236 1 \u7684\u623f \"(\\d+)\" \u53c8\u767c\u751f\u4e00\u7b46\u67e5\u623f\u901a\u904e\u4e8b\u4ef6$")
    public void inspectionPassedWhileDisconnected(String roomNo) {
        this.insertInspectionEvent(1L, roomNo, "inspection_passed", 5);
    }

    @Given(value="^PMS \u4ee5\u5225\u540d \"([^\"]+)\" \u7684\u91d1\u9470\u5efa\u7acb\u6574\u5408 SSE \u9023\u7dda$")
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
            throw new IllegalStateException("\u91d1\u9470\u5225\u540d\u300c" + alias + "\u300d\u6c92\u6709\u767b\u8a18\u2014\u2014Given \u6f0f\u4e86");
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
        this.db.admin().update("INSERT INTO tasks (id, tenant_id, type, source, room_id, dept, state, event_seq,\n                   title, created_at, updated_at)\nVALUES (?, ?, 'amenity', 'desk', ?, 'housekeeping', ?, 1, ?, now(), now())\n", new Object[]{taskId, tenantId, roomId, "inspection_passed".equals(eventType) ? "inspected" : "accepted", "\u6574\u5408\u6e2c\u8a66\u4f48\u666f\uff08\u623f " + roomNo + "\uff09"});
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
            throw new IllegalStateException("SHA-256 \u4e0d\u53ef\u7528", e);
        }
    }
}
