package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.common.codes.InspectionResult;
import com.athena.tms.common.codes.TaskEventType;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import io.cucumber.java.en.Then;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InspectionQrThenSteps {

    private final ScenarioContext ctx;
    private final DbSupport db;
    private final ApiClient api;
    private final ObjectMapper objectMapper;

    public InspectionQrThenSteps(ScenarioContext ctx, DbSupport db, ApiClient api,
                                 ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.db = db;
        this.api = api;
        this.objectMapper = objectMapper;
    }

    @Then("^查房不留記錄、任務狀態不變$")
    public void inspectionLeftNoTrace() {
        assertEquals(0, inspectionCount(ctx.taskId()), "被擋的查房不得留下記錄");
        assertEquals(ctx.<String>get("preInspectionState"), db.taskRow(ctx.taskId()).get("state"),
                "被擋的查房不得改動任務");
    }

    @Then("^房務以自己的身分對查房端點送出判定的請求也應該失敗，回應為 403$")
    public void housekeeperBlockedByRoleMatrix() {
        // 房務角色連 /admin 前綴都進不來：角色×動作矩陣在路由層就擋下
        HttpResult blocked = api.postJson("/admin/inspections/" + ctx.taskId(),
                api.staffToken(TENANT_1, ctx.staffLogin("小美")), Map.of("items", List.of()), null);
        assertEquals(403, blocked.status(), "房務進不了 /admin（權限矩陣）: " + blocked.body());
    }

    @Then("^回應的查房結果為 \"passed\" 且任務轉為 \"inspected\"$")
    public void allPassResponseAndState() {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "查房該成功: " + last.body());
        JsonNode body = objectMapper.readTree(last.body());
        assertEquals(InspectionResult.PASSED, body.get("result").asText());
        assertEquals(TaskState.INSPECTED, body.get("taskState").asText());
        assertEquals(false, body.get("returnedToPool").asBoolean());
        assertEquals(TaskState.INSPECTED, db.taskRow(ctx.taskId()).get("state"),
                "FR-031：done→inspected");
        assertEquals(TaskEventType.INSPECTION_PASSED,
                db.eventTypes(ctx.taskId()).getLast(), "最後一筆事件是 inspection_passed");
    }

    @Then("^回應的查房結果為 \"([a-z]+)\" 且任務狀態為 \"([a-z_]+)\"$")
    public void inspectionResponseResultAndTaskState(String result, String taskState) {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "查房該成功: " + last.body());
        JsonNode body = objectMapper.readTree(last.body());
        assertEquals(result, body.get("result").asText(), "查房結果");
        assertEquals(taskState, body.get("taskState").asText(), "任務狀態（回應）");
    }

    @Then("^outbox 排入的訊息 kind 僅有 \"([a-z_]+)\"$")
    public void outboxContainsOnlyKind(String expectedKind) {
        // 退場驗證：整個租戶的 outbox 不得再出現其他 kind（例如已移除的房況回寫）
        List<String> kinds = db.admin().queryForList(
                "SELECT DISTINCT kind FROM outbox WHERE tenant_id = ?", String.class, TENANT_1);
        assertEquals(List.of(expectedKind), kinds,
                "outbox 僅允許 kind=" + expectedKind + "，實際: " + kinds);
    }

    @Then("^inspections 留下一列 passed、items 完整存下每個查房項目的判定$")
    public void inspectionRowRecordsFullItemSet() {
        List<Map<String, Object>> rows = db.admin().queryForList(
                "SELECT result, inspector_id, items::text AS items FROM inspections"
                        + " WHERE task_id = ? ORDER BY created_at DESC", ctx.taskId());
        assertEquals(1, rows.size(), "FR-031：查房結果存 inspections 表、一次一列");
        assertEquals(InspectionResult.PASSED, rows.getFirst().get("result"));
        UUID inspector = db.admin().queryForObject(
                "SELECT id FROM staff_accounts WHERE tenant_id = 1 AND login_id = ?",
                UUID.class, ctx.staffLogin("王主管"));
        assertEquals(inspector.toString(), rows.getFirst().get("inspector_id").toString());
        JsonNode items = objectMapper.readTree((String) rows.getFirst().get("items"));
        assertEquals(ctx.<List<String>>get("inspectionKeys").size(), items.size(),
                "items jsonb 存判定全集");
    }

    @Then("^事件 \"inspection_failed\" 帶不過項目「([^」]+)」$")
    public void failedEventCarriesItemLabel(String label) {
        String payload = db.eventPayloadOfType(ctx.taskId(), TaskEventType.INSPECTION_FAILED);
        assertTrue(payload.contains(label), "事件 inspection_failed 該帶不過項目「" + label + "」: " + payload);
    }

    @Then("^沒有連動退池$")
    public void noReturnToPool() {
        assertFalse(db.eventTypes(ctx.taskId()).contains(TaskEventType.RETURNED_TO_POOL),
                "在班且未請假：不得連動退池");
        assertEquals(false,
                objectMapper.readTree(ctx.lastResponse().body()).get("returnedToPool").asBoolean());
    }

    @Then("^第一輪的 \"started\" 與 \"completed\" 仍可由事件流完整回溯$")
    public void firstRoundStillTraceable() {
        assertTrue(db.eventTypes(ctx.taskId()).containsAll(
                        List.of(TaskEventType.STARTED, TaskEventType.COMPLETED)),
                "投影歸零不影響事件流既有列");
    }

    @Then("^inspections 共兩列、最新一列為 passed、先前一列為 failed，且 redo_count 仍為 (\\d+)$")
    public void inspectionHistoryAppended(int redoCount) {
        List<String> results = db.admin().queryForList(
                "SELECT result FROM inspections WHERE task_id = ? ORDER BY created_at DESC",
                String.class, ctx.taskId());
        assertEquals(List.of(InspectionResult.PASSED, InspectionResult.FAILED), results,
                "一次查房一列、退回重查多列");
        assertEquals(redoCount, ((Number) db.taskRow(ctx.taskId()).get("redo_count")).intValue(),
                "重查通過不再 +1");
    }

    @Then("^回應的 returnedToPool 為 true$")
    public void returnedToPoolResponse() {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "查房該成功: " + last.body());
        JsonNode body = objectMapper.readTree(last.body());
        assertEquals(true, body.get("returnedToPool").asBoolean());
        assertEquals(TaskState.POOL, body.get("taskState").asText());
    }

    @Then("^同一筆交易寫入兩筆相鄰事件：\"inspection_failed\" 緊跟 \"returned_to_pool\"$")
    public void failAndReturnEventsAdjacent() {
        List<String> types = db.eventTypes(ctx.taskId());
        int failIndex = types.indexOf(TaskEventType.INSPECTION_FAILED);
        assertTrue(failIndex >= 0, "事件流該有 inspection_failed: " + types);
        assertEquals(TaskEventType.RETURNED_TO_POOL, types.get(failIndex + 1),
                "兩筆事件同交易相鄰: " + types);
    }

    @Then("^outbox 排入一筆 push、payload 帶優先同樓層提示，含 floor (\\d+)、房 (\\d+)、dept ([a-z_]+)$")
    public void pushOutboxCarriesSameFloorHint(int floor, String roomNo, String dept) {
        List<Map<String, Object>> rows = db.admin().queryForList(
                "SELECT payload::text AS payload FROM outbox WHERE tenant_id = ?"
                        + " AND kind = 'push' AND idempotency_key LIKE ?",
                TENANT_1, "task:" + ctx.taskId() + ":returned_to_pool:%");
        assertEquals(1, rows.size(), "連動退池要排一筆 push outbox 列");
        JsonNode push = objectMapper.readTree((String) rows.getFirst().get("payload"));
        assertEquals("prefer_same_floor", push.get("payload").get("hint").asText(),
                "優先推播同樓層的提示");
        assertEquals(floor, push.get("payload").get("floor").asInt());
        assertEquals(roomNo, push.get("payload").get("room_no").asText());
        assertEquals(dept, push.get("payload").get("dept").asText());
    }

    @Then("^事件流含 \"returned_to_pool\"$")
    public void eventsContainReturnedToPool() {
        assertTrue(db.eventTypes(ctx.taskId()).contains(TaskEventType.RETURNED_TO_POOL));
    }

    // ---------------------------------------------------------------- QR

    @Then("^token 為 base62 22 字元、隨機強度至少 128 bit，且不含房號、首簽 version 為 (\\d+)$")
    public void tokenShapeAndFirstVersion(int version) {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "列印該成功: " + last.body());
        JsonNode body = objectMapper.readTree(last.body());
        String token = body.get("token").asText();
        assertTrue(token.matches("^[0-9A-Za-z]{22}$"),
                "token 形狀：base62 22 字元，隨機強度至少 128 bit: " + token);
        assertFalse(token.contains("301"), "token 不含房號資訊");
        assertEquals(version, body.get("version").asInt(), "首簽 version=1");
        assertEquals("301", body.get("roomNo").asText());
        ctx.put("printedToken", token);
    }

    @Then("^再列印回同一枚 token$")
    public void reprintReturnsSameToken() {
        HttpResult second = api.get("/admin/rooms/301/qr",
                api.staffToken(TENANT_1, ctx.staffLogin("王主管")));
        assertEquals(200, second.status(), second.body());
        assertEquals(ctx.<String>get("printedToken"),
                objectMapper.readTree(second.body()).get("token").asText(),
                "再列印回同一枚，GET 不重簽");
    }

    @Then("^列印查無房號 \"(\\d+)\" 的請求應該失敗，回應為 404$")
    public void printUnknownRoomIs404(String roomNo) {
        HttpResult missing = api.get("/admin/rooms/" + roomNo + "/qr",
                api.staffToken(TENANT_1, ctx.staffLogin("王主管")));
        assertEquals(404, missing.status(), "查無房號（含跨租戶）一律 404: " + missing.body());
    }

    @Then("^列印別店的房號 \"(\\d+)\" 的請求應該失敗，回應為 404$")
    public void printForeignRoomIs404(String roomNo) {
        printUnknownRoomIs404(roomNo);
    }

    @Then("^寫入被 partial unique 約束以 (\\d+) 拒絕$")
    public void insertRejectedByPartialUnique(String sqlState) {
        assertEquals(sqlState, ctx.get("sqlState"), "必須是唯一約束違反（23505）");
    }

    @Then("^舊 token 標 \"revoked\"、新 token version \\+1 生效$")
    public void oldRevokedNewVersionBumped() {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "輪替該成功: " + last.body());
        JsonNode body = objectMapper.readTree(last.body());
        String newToken = body.get("token").asText();
        String oldToken = ctx.get("oldQrToken");
        assertNotEquals(oldToken, newToken);
        assertEquals(ctx.<Integer>get("oldQrVersion") + 1, body.get("version").asInt(),
                "輪替後新 token 的 version 要 +1 生效");
        Map<String, Object> oldRow = db.admin().queryForMap(
                "SELECT status, revoked_at FROM room_qr_tokens WHERE token = ?", oldToken);
        assertEquals("revoked", oldRow.get("status"),
                "舊 token 標 revoked——掃碼側只認 active，掃它即被拒");
        assertNotNull(oldRow.get("revoked_at"));
        String newStatus = db.admin().queryForObject(
                "SELECT status FROM room_qr_tokens WHERE token = ?", String.class, newToken);
        assertEquals("active", newStatus);
    }

    @Then("^該房所有未撤銷 guest session 都被寫上 revoked_at、回應的 revokedSessions 為 (\\d+)$")
    public void allGuestSessionsRevoked(int revokedSessions) {
        assertEquals(revokedSessions,
                objectMapper.readTree(ctx.lastResponse().body()).get("revokedSessions").asInt());
        Integer unrevoked = db.admin().queryForObject(
                "SELECT count(*) FROM guest_sessions WHERE tenant_id = ? AND room_id = ?"
                        + " AND revoked_at IS NULL", Integer.class, TENANT_1, ctx.entityId("r-301"));
        assertEquals(0, unrevoked.intValue(), "該房全部未撤銷 session 都要寫上 revoked_at");
    }

    @Then("^該住客的下一個請求應該失敗，回應為 401$")
    public void guestNextRequestIs401() {
        HttpResult context = api.get("/guest/context", ctx.get("sessionToken"));
        assertEquals(401, context.status(), "輪替連坐：輪替前掃過的人下一個請求 401");
    }

    private int inspectionCount(UUID taskId) {
        Integer count = db.admin().queryForObject(
                "SELECT count(*) FROM inspections WHERE task_id = ?", Integer.class, taskId);
        return count == null ? -1 : count;
    }
}
