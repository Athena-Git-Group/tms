package com.athena.tms.integration.stepdefs.then;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.GuestFixtures;
import io.cucumber.java.en.Then;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class GuestThenSteps {
    private final ScenarioContext ctx;
    private final DbSupport db;
    private final GuestFixtures guest;
    private final ObjectMapper objectMapper;

    public GuestThenSteps(ScenarioContext ctx, DbSupport db, GuestFixtures guest, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.db = db;
        this.guest = guest;
        this.objectMapper = objectMapper;
    }

    @Then(value="^系統未簽發任何 guest session 也未建立任何任務$")
    public void nothingSignedNothingCreated() {
        Assertions.assertEquals((long)((Long)this.ctx.get("sessionsBefore")), (long)this.db.countAll("guest_sessions"), (String)"不簽 session（E-03）");
        Assertions.assertEquals((long)((Long)this.ctx.get("tasksBefore")), (long)this.db.countAll("tasks"), (String)"不建單（E-03）");
    }

    @Then(value="^以不存在的 token 掃碼得到同一個錯誤碼$")
    public void unknownTokenGetsSameCode() {
        String previousCode = this.objectMapper.readTree(this.ctx.lastResponse().body()).get("code").asText();
        ScenarioContext.HttpResult unknown = this.guest.scan("NoSuchTokenAnywhere123", null);
        Assertions.assertEquals((int)409, (int)unknown.status());
        Assertions.assertEquals((Object)previousCode, (Object)this.objectMapper.readTree(unknown.body()).get("code").asText(), (String)"不存在的 token 不得與作廢可區分");
    }

    @Then(value="^回應含資訊頁與需求選單$")
    public void responseCarriesInfoPagesAndMenu() {
        JsonNode body = this.objectMapper.readTree(this.ctx.lastResponse().body());
        Assertions.assertTrue((boolean)GuestThenSteps.hasNodeWithField(body.get("infoPages"), "title", "Wi-Fi 使用說明"), (String)("資訊頁來自 tenant_config guest_info_pages: " + String.valueOf(body.get("infoPages"))));
        Assertions.assertTrue((boolean)GuestThenSteps.hasNodeWithField(body.get("menu"), "label", "加毛巾"), (String)("選單來自 tenant_config request_menu: " + String.valueOf(body.get("menu"))));
    }

    @Then(value="^簽發的 session token 以租戶 2 為前綴$")
    public void sessionTokenHasTenant2Prefix() {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("跨租戶掃碼該成功: " + last.body()));
        String sessionToken = this.objectMapper.readTree(last.body()).get("sessionToken").asText();
        Assertions.assertTrue((boolean)sessionToken.startsWith("2."), (String)("租戶 2 的碼必須在 RLS 下也找得回來且前綴＝2: " + sessionToken));
    }

    @Then(value="^回應內容來自租戶設定 guest_info_pages$")
    public void homeContentFromTenantConfig() {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("回應 body: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertTrue((boolean)GuestThenSteps.hasNodeWithField(body.get("infoPages"), "title", "早餐時間與地點"), (String)("內容來自 guest_info_pages: " + String.valueOf(body.get("infoPages"))));
    }

    @Then(value="^任務總數不變$")
    public void taskCountUnchanged() {
        Assertions.assertEquals((long)((Long)this.ctx.get("tasksBeforeHome")), (long)this.db.countAll("tasks"), (String)"資訊頁瀏覽不產生任務（FR-002）");
    }

    @Then(value="^建立任務 state 為 \"([a-z_]+)\" 且 dept 為 \"([a-z_]+)\"$")
    public void createdTaskStateAndDept(String state, String dept) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)201, (int)last.status(), (String)("建單該成功: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertEquals((Object)state, (Object)body.get("state").asText(), (String)"命中 0 或 >1 部門→不猜、停 triage");
        Assertions.assertEquals((Object)dept, (Object)body.get("dept").asText());
    }

    @Then(value="^建立任務 type 為 ([a-z_]+)、dept 為 ([a-z_]+)、state 為 ([a-z_]+)$")
    public void createdTaskTypeDeptState(String type, String dept, String state) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)201, (int)last.status(), (String)("建單該成功: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertEquals((Object)type, (Object)body.get("type").asText());
        Assertions.assertEquals((Object)dept, (Object)body.get("dept").asText());
        Assertions.assertEquals((Object)state, (Object)body.get("state").asText(), (String)"選單項預對應部門，直入 pool 不經 triage");
    }

    @Then(value="^事件 \"created\" 的快照之種類為 ([a-z_]+)、來源為 \"guest\"、房號為 \"(\\d+)\"、訂單為 \"([^\"]+)\"、標題為 \"([^\"]*)\"$")
    public void createdSnapshotLiteralValues(String type, String roomNo, String reservationRef, String title) {
        ScenarioContext.HttpResult response = this.ctx.lastResponse();
        UUID taskId = UUID.fromString(this.objectMapper.readTree(response.body()).get("id").asText());
        JsonNode snapshot = this.objectMapper.readTree(this.db.eventPayloadOfType(taskId, "created"));
        Assertions.assertEquals((Object)type, (Object)snapshot.get("type").asText(), (String)"種類");
        Assertions.assertEquals((Object)"guest", (Object)snapshot.get("source").asText(), (String)"來源");
        String roomInDb = (String)this.db.admin().queryForObject("SELECT room_no FROM rooms WHERE id = ?", String.class, new Object[]{UUID.fromString(snapshot.get("room_id").asText())});
        Assertions.assertEquals((Object)roomNo, (Object)roomInDb, (String)"房號");
        String refInDb = (String)this.db.admin().queryForObject("SELECT reservation_ref FROM reservations_cache WHERE id = ?", String.class, new Object[]{UUID.fromString(snapshot.get("reservation_id").asText())});
        Assertions.assertEquals((Object)reservationRef, (Object)refInDb, (String)"訂單");
        Assertions.assertEquals((Object)title, (Object)snapshot.get("title").asText(), (String)"標題");
    }

    @Then(value="^事件 payload 的 note 記錄命中的 rule_id \"([^\"]+)\" 與原文、語言$")
    public void ruleTraceInNote(String ruleId) {
        JsonNode snapshot = this.objectMapper.readTree(this.db.eventPayloadOfType(this.ctx.taskId(), "created"));
        JsonNode note = this.objectMapper.readTree(snapshot.get("note").asText());
        Assertions.assertEquals((Object)ruleId, (Object)note.get("rule_id").asText(), (String)"命中留痕可解釋（A10）");
        Assertions.assertEquals(this.ctx.get("freeText"), (Object)note.get("text").asText());
        Assertions.assertEquals((Object)"zh-TW", (Object)note.get("lang").asText());
    }

    @Then(value="^第 3 筆需求仍為成功建立$")
    public void thirdRequestStillCreated() {
        ScenarioContext.HttpResult third = (ScenarioContext.HttpResult)this.ctx.get("thirdResponse");
        Assertions.assertEquals((int)201, (int)third.status(), (String)("第 3 筆該在限流線內: " + third.body()));
        JsonNode body = this.objectMapper.readTree(third.body());
        Assertions.assertNotNull((Object)body.get("id"));
        Assertions.assertEquals((Object)"pool", (Object)this.db.taskRow(UUID.fromString(body.get("id").asText())).get("state"));
    }

    @Then(value="^清單含自己的「([^」]+)」但不含前一筆訂單的任何需求$")
    public void listFiltersByReservation(String myTitle) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("查詢該成功: " + last.body()));
        JsonNode list = this.objectMapper.readTree(last.body());
        boolean mineVisible = false;
        for (JsonNode task : list) {
            mineVisible |= myTitle.equals(task.get("title").asText());
        }
        Assertions.assertTrue((boolean)mineVisible, (String)("自己的單看得到: " + last.body()));
        List foreignTaskIds = this.db.admin().queryForList("SELECT t.id::text FROM tasks t WHERE t.reservation_id IS DISTINCT FROM (SELECT reservation_id FROM tasks WHERE title = ? LIMIT 1)", String.class, new Object[]{myTitle});
        for (JsonNode task : list) {
            Assertions.assertFalse((boolean)foreignTaskIds.contains(task.get("id").asText()), (String)("別訂單的需求不得出現: " + String.valueOf(task)));
        }
    }

    @Then(value="^事件 \"cancelled\" 的原因為 \"([a-z_]+)\"$")
    public void cancelledEventReason(String reason) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("收回該成功: " + last.body()));
        JsonNode payload = this.objectMapper.readTree(this.db.eventPayloadOfType(this.ctx.taskId(), "cancelled"));
        Assertions.assertEquals((Object)reason, (Object)payload.get("reason").asText(), (String)"收回原因是固定的列舉值");
    }

    @Then(value="^回應的任務 rating 為 (\\d+) 且事件 \"rated\" 帶分數$")
    public void ratingAcceptedWithEvent(int rating) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("評分該成功: " + last.body()));
        Assertions.assertEquals((int)rating, (int)this.objectMapper.readTree(last.body()).get("rating").asInt());
        JsonNode payload = this.objectMapper.readTree(this.db.eventPayloadOfType(this.ctx.taskId(), "rated"));
        Assertions.assertEquals((int)rating, (int)payload.get("rating").asInt(), (String)"rated 事件帶分數");
    }

    @Then(value="^產生含該 IP 的掃碼限流告警，以 WARN 日誌記錄$")
    public void alertLoggedWithIp() {
        ListAppender<ILoggingEvent> alerts = this.ctx.get("alertAppender");
        Assertions.assertNotNull((Object)alerts, (String)"Given 步驟該先掛上日誌監聽");
        String attackerIp = (String)this.ctx.get("attackerIp");
        Assertions.assertTrue((boolean)alerts.list.stream().anyMatch(event -> "WARN".equals(event.getLevel().toString()) && event.getFormattedMessage().contains("[alert][scan-rate-limit]") && event.getFormattedMessage().contains(attackerIp)), (String)"gherkin：請求被擋「並產生告警」——告警通道＝WARN 日誌");
    }

    @Then(value="^別的 IP 以 token \"([^\"]+)\" 掃碼仍成功$")
    public void otherIpNotAffected(String token) {
        ScenarioContext.HttpResult other = this.guest.scan(token, "198.51.100.7");
        Assertions.assertEquals((int)200, (int)other.status(), (String)("限流按單一 IP 計數，別的 IP 不受連坐: " + other.body()));
    }

    private static boolean hasNodeWithField(JsonNode array, String field, String expected) {
        if (array == null) {
            return false;
        }
        for (JsonNode node : array) {
            if (!node.has(field) || !expected.equals(node.get(field).asText())) continue;
            return true;
        }
        return false;
    }
}
