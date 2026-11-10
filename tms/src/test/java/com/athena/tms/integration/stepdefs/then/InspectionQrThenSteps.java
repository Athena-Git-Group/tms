package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import io.cucumber.java.en.Then;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class InspectionQrThenSteps {
    private final ScenarioContext ctx;
    private final DbSupport db;
    private final ApiClient api;
    private final ObjectMapper objectMapper;

    public InspectionQrThenSteps(ScenarioContext ctx, DbSupport db, ApiClient api, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.db = db;
        this.api = api;
        this.objectMapper = objectMapper;
    }

    @Then(value="^查房不留記錄、任務狀態不變$")
    public void inspectionLeftNoTrace() {
        Assertions.assertEquals((int)0, (int)this.inspectionCount(this.ctx.taskId()), (String)"被擋的查房不得留下記錄");
        Assertions.assertEquals(this.ctx.get("preInspectionState"), (Object)this.db.taskRow(this.ctx.taskId()).get("state"), (String)"被擋的查房不得改動任務");
    }

    @Then(value="^房務以自己的身分對查房端點送出判定的請求也應該失敗，回應為 403$")
    public void housekeeperBlockedByRoleMatrix() {
        ScenarioContext.HttpResult blocked = this.api.postJson("/admin/inspections/" + String.valueOf(this.ctx.taskId()), this.api.staffToken(1L, this.ctx.staffLogin("小美")), Map.of("items", List.of()), null);
        Assertions.assertEquals((int)403, (int)blocked.status(), (String)("房務進不了 /admin（權限矩陣）: " + blocked.body()));
    }

    @Then(value="^回應的查房結果為 \"passed\" 且任務轉為 \"inspected\"$")
    public void allPassResponseAndState() {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("查房該成功: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertEquals((Object)"passed", (Object)body.get("result").asText());
        Assertions.assertEquals((Object)"inspected", (Object)body.get("taskState").asText());
        Assertions.assertEquals((Object)false, (Object)body.get("returnedToPool").asBoolean());
        Assertions.assertEquals((Object)"inspected", (Object)this.db.taskRow(this.ctx.taskId()).get("state"), (String)"FR-031：done→inspected");
        Assertions.assertEquals((Object)"inspection_passed", (Object)this.db.eventTypes(this.ctx.taskId()).getLast(), (String)"最後一筆事件是 inspection_passed");
    }

    @Then(value="^回應的查房結果為 \"([a-z]+)\" 且任務狀態為 \"([a-z_]+)\"$")
    public void inspectionResponseResultAndTaskState(String result, String taskState) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("查房該成功: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertEquals((Object)result, (Object)body.get("result").asText(), (String)"查房結果");
        Assertions.assertEquals((Object)taskState, (Object)body.get("taskState").asText(), (String)"任務狀態（回應）");
    }

    @Then(value="^outbox 排入的訊息 kind 僅有 \"([a-z_]+)\"$")
    public void outboxContainsOnlyKind(String expectedKind) {
        List kinds = this.db.admin().queryForList("SELECT DISTINCT kind FROM outbox WHERE tenant_id = ?", String.class, new Object[]{1L});
        Assertions.assertEquals(List.of(expectedKind), (Object)kinds, (String)("outbox 僅允許 kind=" + expectedKind + "，實際: " + String.valueOf(kinds)));
    }

    @Then(value="^inspections 留下一列 passed、items 完整存下每個查房項目的判定$")
    public void inspectionRowRecordsFullItemSet() {
        List rows = this.db.admin().queryForList("SELECT result, inspector_id, items::text AS items FROM inspections WHERE task_id = ? ORDER BY created_at DESC", new Object[]{this.ctx.taskId()});
        Assertions.assertEquals((int)1, (int)rows.size(), (String)"FR-031：查房結果存 inspections 表、一次一列");
        Assertions.assertEquals((Object)"passed", ((Map)rows.getFirst()).get("result"));
        UUID inspector = (UUID)this.db.admin().queryForObject("SELECT id FROM staff_accounts WHERE tenant_id = 1 AND login_id = ?", UUID.class, new Object[]{this.ctx.staffLogin("王主管")});
        Assertions.assertEquals((Object)inspector.toString(), (Object)((Map)rows.getFirst()).get("inspector_id").toString());
        JsonNode items = this.objectMapper.readTree((String)((Map)rows.getFirst()).get("items"));
        Assertions.assertEquals((int)((List)this.ctx.get("inspectionKeys")).size(), (int)items.size(), (String)"items jsonb 存判定全集");
    }

    @Then(value="^事件 \"inspection_failed\" 帶不過項目「([^」]+)」$")
    public void failedEventCarriesItemLabel(String label) {
        String payload = this.db.eventPayloadOfType(this.ctx.taskId(), "inspection_failed");
        Assertions.assertTrue((boolean)payload.contains(label), (String)("事件 inspection_failed 該帶不過項目「" + label + "」: " + payload));
    }

    @Then(value="^沒有連動退池$")
    public void noReturnToPool() {
        Assertions.assertFalse((boolean)this.db.eventTypes(this.ctx.taskId()).contains("returned_to_pool"), (String)"在班且未請假：不得連動退池");
        Assertions.assertEquals((Object)false, (Object)this.objectMapper.readTree(this.ctx.lastResponse().body()).get("returnedToPool").asBoolean());
    }

    @Then(value="^第一輪的 \"started\" 與 \"completed\" 仍可由事件流完整回溯$")
    public void firstRoundStillTraceable() {
        Assertions.assertTrue((boolean)this.db.eventTypes(this.ctx.taskId()).containsAll(List.of("started", "completed")), (String)"投影歸零不影響事件流既有列");
    }

    @Then(value="^inspections 共兩列、最新一列為 passed、先前一列為 failed，且 redo_count 仍為 (\\d+)$")
    public void inspectionHistoryAppended(int redoCount) {
        List results = this.db.admin().queryForList("SELECT result FROM inspections WHERE task_id = ? ORDER BY created_at DESC", String.class, new Object[]{this.ctx.taskId()});
        Assertions.assertEquals(List.of("passed", "failed"), (Object)results, (String)"一次查房一列、退回重查多列");
        Assertions.assertEquals((int)redoCount, (int)((Number)this.db.taskRow(this.ctx.taskId()).get("redo_count")).intValue(), (String)"重查通過不再 +1");
    }

    @Then(value="^回應的 returnedToPool 為 true$")
    public void returnedToPoolResponse() {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("查房該成功: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertEquals((Object)true, (Object)body.get("returnedToPool").asBoolean());
        Assertions.assertEquals((Object)"pool", (Object)body.get("taskState").asText());
    }

    @Then(value="^同一筆交易寫入兩筆相鄰事件：\"inspection_failed\" 緊跟 \"returned_to_pool\"$")
    public void failAndReturnEventsAdjacent() {
        List<String> types = this.db.eventTypes(this.ctx.taskId());
        int failIndex = types.indexOf("inspection_failed");
        Assertions.assertTrue((failIndex >= 0 ? 1 : 0) != 0, (String)("事件流該有 inspection_failed: " + String.valueOf(types)));
        Assertions.assertEquals((Object)"returned_to_pool", (Object)types.get(failIndex + 1), (String)("兩筆事件同交易相鄰: " + String.valueOf(types)));
    }

    @Then(value="^outbox 排入一筆 push、payload 帶優先同樓層提示，含 floor (\\d+)、房 (\\d+)、dept ([a-z_]+)$")
    public void pushOutboxCarriesSameFloorHint(int floor, String roomNo, String dept) {
        List rows = this.db.admin().queryForList("SELECT payload::text AS payload FROM outbox WHERE tenant_id = ? AND kind = 'push' AND idempotency_key LIKE ?", new Object[]{1L, "task:" + String.valueOf(this.ctx.taskId()) + ":returned_to_pool:%"});
        Assertions.assertEquals((int)1, (int)rows.size(), (String)"連動退池要排一筆 push outbox 列");
        JsonNode push = this.objectMapper.readTree((String)((Map)rows.getFirst()).get("payload"));
        Assertions.assertEquals((Object)"prefer_same_floor", (Object)push.get("payload").get("hint").asText(), (String)"優先推播同樓層的提示");
        Assertions.assertEquals((int)floor, (int)push.get("payload").get("floor").asInt());
        Assertions.assertEquals((Object)roomNo, (Object)push.get("payload").get("room_no").asText());
        Assertions.assertEquals((Object)dept, (Object)push.get("payload").get("dept").asText());
    }

    @Then(value="^事件流含 \"returned_to_pool\"$")
    public void eventsContainReturnedToPool() {
        Assertions.assertTrue((boolean)this.db.eventTypes(this.ctx.taskId()).contains("returned_to_pool"));
    }

    @Then(value="^token 為 base62 22 字元、隨機強度至少 128 bit，且不含房號、首簽 version 為 (\\d+)$")
    public void tokenShapeAndFirstVersion(int version) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("列印該成功: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        String token = body.get("token").asText();
        Assertions.assertTrue((boolean)token.matches("^[0-9A-Za-z]{22}$"), (String)("token 形狀：base62 22 字元，隨機強度至少 128 bit: " + token));
        Assertions.assertFalse((boolean)token.contains("301"), (String)"token 不含房號資訊");
        Assertions.assertEquals((int)version, (int)body.get("version").asInt(), (String)"首簽 version=1");
        Assertions.assertEquals((Object)"301", (Object)body.get("roomNo").asText());
        this.ctx.put("printedToken", token);
    }

    @Then(value="^再列印回同一枚 token$")
    public void reprintReturnsSameToken() {
        ScenarioContext.HttpResult second = this.api.get("/admin/rooms/301/qr", this.api.staffToken(1L, this.ctx.staffLogin("王主管")));
        Assertions.assertEquals((int)200, (int)second.status(), (String)second.body());
        Assertions.assertEquals(this.ctx.get("printedToken"), (Object)this.objectMapper.readTree(second.body()).get("token").asText(), (String)"再列印回同一枚，GET 不重簽");
    }

    @Then(value="^列印查無房號 \"(\\d+)\" 的請求應該失敗，回應為 404$")
    public void printUnknownRoomIs404(String roomNo) {
        ScenarioContext.HttpResult missing = this.api.get("/admin/rooms/" + roomNo + "/qr", this.api.staffToken(1L, this.ctx.staffLogin("王主管")));
        Assertions.assertEquals((int)404, (int)missing.status(), (String)("查無房號（含跨租戶）一律 404: " + missing.body()));
    }

    @Then(value="^列印別店的房號 \"(\\d+)\" 的請求應該失敗，回應為 404$")
    public void printForeignRoomIs404(String roomNo) {
        this.printUnknownRoomIs404(roomNo);
    }

    @Then(value="^寫入被 partial unique 約束以 (\\d+) 拒絕$")
    public void insertRejectedByPartialUnique(String sqlState) {
        Assertions.assertEquals((Object)sqlState, this.ctx.get("sqlState"), (String)"必須是唯一約束違反（23505）");
    }

    @Then(value="^舊 token 標 \"revoked\"、新 token version \\+1 生效$")
    public void oldRevokedNewVersionBumped() {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("輪替該成功: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        String newToken = body.get("token").asText();
        String oldToken = (String)this.ctx.get("oldQrToken");
        Assertions.assertNotEquals((Object)oldToken, (Object)newToken);
        Assertions.assertEquals((int)((Integer)this.ctx.get("oldQrVersion") + 1), (int)body.get("version").asInt(), (String)"輪替後新 token 的 version 要 +1 生效");
        Map oldRow = this.db.admin().queryForMap("SELECT status, revoked_at FROM room_qr_tokens WHERE token = ?", new Object[]{oldToken});
        Assertions.assertEquals((Object)"revoked", oldRow.get("status"), (String)"舊 token 標 revoked——掃碼側只認 active，掃它即被拒");
        Assertions.assertNotNull(oldRow.get("revoked_at"));
        String newStatus = (String)this.db.admin().queryForObject("SELECT status FROM room_qr_tokens WHERE token = ?", String.class, new Object[]{newToken});
        Assertions.assertEquals((Object)"active", (Object)newStatus);
    }

    @Then(value="^該房所有未撤銷 guest session 都被寫上 revoked_at、回應的 revokedSessions 為 (\\d+)$")
    public void allGuestSessionsRevoked(int revokedSessions) {
        Assertions.assertEquals((int)revokedSessions, (int)this.objectMapper.readTree(this.ctx.lastResponse().body()).get("revokedSessions").asInt());
        Integer unrevoked = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM guest_sessions WHERE tenant_id = ? AND room_id = ? AND revoked_at IS NULL", Integer.class, new Object[]{1L, this.ctx.entityId("r-301")});
        Assertions.assertEquals((int)0, (int)unrevoked, (String)"該房全部未撤銷 session 都要寫上 revoked_at");
    }

    @Then(value="^該住客的下一個請求應該失敗，回應為 401$")
    public void guestNextRequestIs401() {
        ScenarioContext.HttpResult context = this.api.get("/guest/context", (String)this.ctx.get("sessionToken"));
        Assertions.assertEquals((int)401, (int)context.status(), (String)"輪替連坐：輪替前掃過的人下一個請求 401");
    }

    private int inspectionCount(UUID taskId) {
        Integer count = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM inspections WHERE task_id = ?", Integer.class, new Object[]{taskId});
        return count == null ? -1 : count;
    }
}
