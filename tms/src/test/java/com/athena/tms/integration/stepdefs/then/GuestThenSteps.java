package com.athena.tms.integration.stepdefs.then;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.athena.tms.common.codes.TaskEventType;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
import com.athena.tms.integration.stepdefs.hooks.CucumberHooks;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.GuestFixtures;
import io.cucumber.java.en.Then;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GuestThenSteps {

    private final ScenarioContext ctx;
    private final DbSupport db;
    private final GuestFixtures guest;
    private final ObjectMapper objectMapper;

    public GuestThenSteps(ScenarioContext ctx, DbSupport db, GuestFixtures guest,
                          ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.db = db;
        this.guest = guest;
        this.objectMapper = objectMapper;
    }

    @Then("^系統未簽發任何 guest session 也未建立任何任務$")
    public void nothingSignedNothingCreated() {
        assertEquals(ctx.<Long>get("sessionsBefore").longValue(), db.countAll("guest_sessions"),
                "不簽 session（E-03）");
        assertEquals(ctx.<Long>get("tasksBefore").longValue(), db.countAll("tasks"), "不建單（E-03）");
    }

    @Then("^以不存在的 token 掃碼得到同一個錯誤碼$")
    public void unknownTokenGetsSameCode() {
        String previousCode = objectMapper.readTree(ctx.lastResponse().body()).get("code").asText();
        HttpResult unknown = guest.scan("NoSuchTokenAnywhere123", null);
        assertEquals(409, unknown.status());
        assertEquals(previousCode, objectMapper.readTree(unknown.body()).get("code").asText(),
                "不存在的 token 不得與作廢可區分");
    }

    @Then("^回應含資訊頁與需求選單$")
    public void responseCarriesInfoPagesAndMenu() {
        JsonNode body = objectMapper.readTree(ctx.lastResponse().body());
        assertTrue(hasNodeWithField(body.get("infoPages"), "title", "Wi-Fi 使用說明"),
                "資訊頁來自 tenant_config guest_info_pages: " + body.get("infoPages"));
        assertTrue(hasNodeWithField(body.get("menu"), "label", "加毛巾"),
                "選單來自 tenant_config request_menu: " + body.get("menu"));
    }

    @Then("^簽發的 session token 以租戶 2 為前綴$")
    public void sessionTokenHasTenant2Prefix() {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "跨租戶掃碼該成功: " + last.body());
        String sessionToken = objectMapper.readTree(last.body()).get("sessionToken").asText();
        assertTrue(sessionToken.startsWith("2."),
                "租戶 2 的碼必須在 RLS 下也找得回來且前綴＝2: " + sessionToken);
    }

    @Then("^回應內容來自租戶設定 guest_info_pages$")
    public void homeContentFromTenantConfig() {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "回應 body: " + last.body());
        JsonNode body = objectMapper.readTree(last.body());
        assertTrue(hasNodeWithField(body.get("infoPages"), "title", "早餐時間與地點"),
                "內容來自 guest_info_pages: " + body.get("infoPages"));
    }

    @Then("^任務總數不變$")
    public void taskCountUnchanged() {
        assertEquals(ctx.<Long>get("tasksBeforeHome").longValue(), db.countAll("tasks"),
                "資訊頁瀏覽不產生任務（FR-002）");
    }

    @Then("^建立任務 state 為 \"([a-z_]+)\" 且 dept 為 \"([a-z_]+)\"$")
    public void createdTaskStateAndDept(String state, String dept) {
        HttpResult last = ctx.lastResponse();
        assertEquals(201, last.status(), "建單該成功: " + last.body());
        JsonNode body = objectMapper.readTree(last.body());
        assertEquals(state, body.get("state").asText(), "命中 0 或 >1 部門→不猜、停 triage");
        assertEquals(dept, body.get("dept").asText());
    }

    @Then("^建立任務 type 為 ([a-z_]+)、dept 為 ([a-z_]+)、state 為 ([a-z_]+)$")
    public void createdTaskTypeDeptState(String type, String dept, String state) {
        HttpResult last = ctx.lastResponse();
        assertEquals(201, last.status(), "建單該成功: " + last.body());
        JsonNode body = objectMapper.readTree(last.body());
        assertEquals(type, body.get("type").asText());
        assertEquals(dept, body.get("dept").asText());
        assertEquals(state, body.get("state").asText(), "選單項預對應部門，直入 pool 不經 triage");
    }

    @Then("^事件 \"created\" 的快照之種類為 ([a-z_]+)、來源為 \"guest\"、房號為 \"(\\d+)\"、訂單為 \"([^\"]+)\"、標題為 \"([^\"]*)\"$")
    public void createdSnapshotLiteralValues(String type, String roomNo, String reservationRef, String title) {
        HttpResult response = ctx.lastResponse();
        UUID taskId = UUID.fromString(objectMapper.readTree(response.body()).get("id").asText());
        JsonNode snapshot = objectMapper.readTree(db.eventPayloadOfType(taskId, TaskEventType.CREATED));
        assertEquals(type, snapshot.get("type").asText(), "種類");
        assertEquals("guest", snapshot.get("source").asText(), "來源");
        String roomInDb = db.admin().queryForObject(
                "SELECT room_no FROM rooms WHERE id = ?", String.class,
                UUID.fromString(snapshot.get("room_id").asText()));
        assertEquals(roomNo, roomInDb, "房號");
        String refInDb = db.admin().queryForObject(
                "SELECT reservation_ref FROM reservations_cache WHERE id = ?", String.class,
                UUID.fromString(snapshot.get("reservation_id").asText()));
        assertEquals(reservationRef, refInDb, "訂單");
        assertEquals(title, snapshot.get("title").asText(), "標題");
    }

    @Then("^事件 payload 的 note 記錄命中的 rule_id \"([^\"]+)\" 與原文、語言$")
    public void ruleTraceInNote(String ruleId) {
        JsonNode snapshot = objectMapper.readTree(
                db.eventPayloadOfType(ctx.taskId(), TaskEventType.CREATED));
        // rule_id 留痕走快照的 note 欄（引擎快照鍵集固定）：事後要答得出「為什麼派工務」
        JsonNode note = objectMapper.readTree(snapshot.get("note").asText());
        assertEquals(ruleId, note.get("rule_id").asText(), "命中留痕可解釋（A10）");
        assertEquals(ctx.<String>get("freeText"), note.get("text").asText());
        assertEquals("zh-TW", note.get("lang").asText());
    }

    @Then("^第 3 筆需求仍為成功建立$")
    public void thirdRequestStillCreated() {
        HttpResult third = ctx.get("thirdResponse");
        assertEquals(201, third.status(), "第 3 筆該在限流線內: " + third.body());
        JsonNode body = objectMapper.readTree(third.body());
        assertNotNull(body.get("id"));
        // taskRow 查無會直接丟例外＝第 3 筆真的落了庫
        assertEquals(TaskState.POOL,
                db.taskRow(UUID.fromString(body.get("id").asText())).get("state"));
    }

    @Then("^清單含自己的「([^」]+)」但不含前一筆訂單的任何需求$")
    public void listFiltersByReservation(String myTitle) {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "查詢該成功: " + last.body());
        JsonNode list = objectMapper.readTree(last.body());
        boolean mineVisible = false;
        for (JsonNode task : list) {
            mineVisible |= myTitle.equals(task.get("title").asText());
        }
        assertTrue(mineVisible, "自己的單看得到: " + last.body());
        // 進度清單按訂單過濾、不按房號撈：別的訂單（含前一位住客）底下的任務
        // 一筆都不得出現
        List<String> foreignTaskIds = db.admin().queryForList(
                "SELECT t.id::text FROM tasks t WHERE t.reservation_id IS DISTINCT FROM"
                        + " (SELECT reservation_id FROM tasks WHERE title = ? LIMIT 1)",
                String.class, myTitle);
        for (JsonNode task : list) {
            assertFalse(foreignTaskIds.contains(task.get("id").asText()),
                    "別訂單的需求不得出現: " + task);
        }
    }

    @Then("^事件 \"cancelled\" 的原因為 \"([a-z_]+)\"$")
    public void cancelledEventReason(String reason) {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "收回該成功: " + last.body());
        JsonNode payload = objectMapper.readTree(
                db.eventPayloadOfType(ctx.taskId(), TaskEventType.CANCELLED));
        assertEquals(reason, payload.get("reason").asText(), "收回原因是固定的列舉值");
    }

    @Then("^回應的任務 rating 為 (\\d+) 且事件 \"rated\" 帶分數$")
    public void ratingAcceptedWithEvent(int rating) {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "評分該成功: " + last.body());
        assertEquals(rating, objectMapper.readTree(last.body()).get("rating").asInt());
        JsonNode payload = objectMapper.readTree(
                db.eventPayloadOfType(ctx.taskId(), TaskEventType.RATED));
        assertEquals(rating, payload.get("rating").asInt(), "rated 事件帶分數");
    }

    @Then("^產生含該 IP 的掃碼限流告警，以 WARN 日誌記錄$")
    public void alertLoggedWithIp() {
        ListAppender<ILoggingEvent> alerts = ctx.get(CucumberHooks.ALERT_APPENDER_KEY);
        assertNotNull(alerts, "Given 步驟該先掛上日誌監聽");
        String attackerIp = ctx.get("attackerIp");
        assertTrue(alerts.list.stream().anyMatch(event ->
                        "WARN".equals(event.getLevel().toString())
                                && event.getFormattedMessage().contains("[alert][scan-rate-limit]")
                                && event.getFormattedMessage().contains(attackerIp)),
                "gherkin：請求被擋「並產生告警」——告警通道＝WARN 日誌");
    }

    @Then("^別的 IP 以 token \"([^\"]+)\" 掃碼仍成功$")
    public void otherIpNotAffected(String token) {
        HttpResult other = guest.scan(token, "198.51.100.7");
        assertEquals(200, other.status(), "限流按單一 IP 計數，別的 IP 不受連坐: " + other.body());
    }

    private static boolean hasNodeWithField(JsonNode array, String field, String expected) {
        if (array == null) {
            return false;
        }
        for (JsonNode node : array) {
            if (node.has(field) && expected.equals(node.get(field).asText())) {
                return true;
            }
        }
        return false;
    }
}
