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

    @Then(value="^\u67e5\u623f\u4e0d\u7559\u8a18\u9304\u3001\u4efb\u52d9\u72c0\u614b\u4e0d\u8b8a$")
    public void inspectionLeftNoTrace() {
        Assertions.assertEquals((int)0, (int)this.inspectionCount(this.ctx.taskId()), (String)"\u88ab\u64cb\u7684\u67e5\u623f\u4e0d\u5f97\u7559\u4e0b\u8a18\u9304");
        Assertions.assertEquals(this.ctx.get("preInspectionState"), (Object)this.db.taskRow(this.ctx.taskId()).get("state"), (String)"\u88ab\u64cb\u7684\u67e5\u623f\u4e0d\u5f97\u6539\u52d5\u4efb\u52d9");
    }

    @Then(value="^\u623f\u52d9\u4ee5\u81ea\u5df1\u7684\u8eab\u5206\u5c0d\u67e5\u623f\u7aef\u9ede\u9001\u51fa\u5224\u5b9a\u7684\u8acb\u6c42\u4e5f\u61c9\u8a72\u5931\u6557\uff0c\u56de\u61c9\u70ba 403$")
    public void housekeeperBlockedByRoleMatrix() {
        ScenarioContext.HttpResult blocked = this.api.postJson("/admin/inspections/" + String.valueOf(this.ctx.taskId()), this.api.staffToken(1L, this.ctx.staffLogin("\u5c0f\u7f8e")), Map.of("items", List.of()), null);
        Assertions.assertEquals((int)403, (int)blocked.status(), (String)("\u623f\u52d9\u9032\u4e0d\u4e86 /admin\uff08\u6b0a\u9650\u77e9\u9663\uff09: " + blocked.body()));
    }

    @Then(value="^\u56de\u61c9\u7684\u67e5\u623f\u7d50\u679c\u70ba \"passed\" \u4e14\u4efb\u52d9\u8f49\u70ba \"inspected\"$")
    public void allPassResponseAndState() {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u67e5\u623f\u8a72\u6210\u529f: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertEquals((Object)"passed", (Object)body.get("result").asText());
        Assertions.assertEquals((Object)"inspected", (Object)body.get("taskState").asText());
        Assertions.assertEquals((Object)false, (Object)body.get("returnedToPool").asBoolean());
        Assertions.assertEquals((Object)"inspected", (Object)this.db.taskRow(this.ctx.taskId()).get("state"), (String)"FR-031\uff1adone\u2192inspected");
        Assertions.assertEquals((Object)"inspection_passed", (Object)this.db.eventTypes(this.ctx.taskId()).getLast(), (String)"\u6700\u5f8c\u4e00\u7b46\u4e8b\u4ef6\u662f inspection_passed");
    }

    @Then(value="^\u56de\u61c9\u7684\u67e5\u623f\u7d50\u679c\u70ba \"([a-z]+)\" \u4e14\u4efb\u52d9\u72c0\u614b\u70ba \"([a-z_]+)\"$")
    public void inspectionResponseResultAndTaskState(String result, String taskState) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u67e5\u623f\u8a72\u6210\u529f: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertEquals((Object)result, (Object)body.get("result").asText(), (String)"\u67e5\u623f\u7d50\u679c");
        Assertions.assertEquals((Object)taskState, (Object)body.get("taskState").asText(), (String)"\u4efb\u52d9\u72c0\u614b\uff08\u56de\u61c9\uff09");
    }

    @Then(value="^outbox \u6392\u5165\u7684\u8a0a\u606f kind \u50c5\u6709 \"([a-z_]+)\"$")
    public void outboxContainsOnlyKind(String expectedKind) {
        List kinds = this.db.admin().queryForList("SELECT DISTINCT kind FROM outbox WHERE tenant_id = ?", String.class, new Object[]{1L});
        Assertions.assertEquals(List.of(expectedKind), (Object)kinds, (String)("outbox \u50c5\u5141\u8a31 kind=" + expectedKind + "\uff0c\u5be6\u969b: " + String.valueOf(kinds)));
    }

    @Then(value="^inspections \u7559\u4e0b\u4e00\u5217 passed\u3001items \u5b8c\u6574\u5b58\u4e0b\u6bcf\u500b\u67e5\u623f\u9805\u76ee\u7684\u5224\u5b9a$")
    public void inspectionRowRecordsFullItemSet() {
        List rows = this.db.admin().queryForList("SELECT result, inspector_id, items::text AS items FROM inspections WHERE task_id = ? ORDER BY created_at DESC", new Object[]{this.ctx.taskId()});
        Assertions.assertEquals((int)1, (int)rows.size(), (String)"FR-031\uff1a\u67e5\u623f\u7d50\u679c\u5b58 inspections \u8868\u3001\u4e00\u6b21\u4e00\u5217");
        Assertions.assertEquals((Object)"passed", ((Map)rows.getFirst()).get("result"));
        UUID inspector = (UUID)this.db.admin().queryForObject("SELECT id FROM staff_accounts WHERE tenant_id = 1 AND login_id = ?", UUID.class, new Object[]{this.ctx.staffLogin("\u738b\u4e3b\u7ba1")});
        Assertions.assertEquals((Object)inspector.toString(), (Object)((Map)rows.getFirst()).get("inspector_id").toString());
        JsonNode items = this.objectMapper.readTree((String)((Map)rows.getFirst()).get("items"));
        Assertions.assertEquals((int)((List)this.ctx.get("inspectionKeys")).size(), (int)items.size(), (String)"items jsonb \u5b58\u5224\u5b9a\u5168\u96c6");
    }

    @Then(value="^\u4e8b\u4ef6 \"inspection_failed\" \u5e36\u4e0d\u904e\u9805\u76ee\u300c([^\u300d]+)\u300d$")
    public void failedEventCarriesItemLabel(String label) {
        String payload = this.db.eventPayloadOfType(this.ctx.taskId(), "inspection_failed");
        Assertions.assertTrue((boolean)payload.contains(label), (String)("\u4e8b\u4ef6 inspection_failed \u8a72\u5e36\u4e0d\u904e\u9805\u76ee\u300c" + label + "\u300d: " + payload));
    }

    @Then(value="^\u6c92\u6709\u9023\u52d5\u9000\u6c60$")
    public void noReturnToPool() {
        Assertions.assertFalse((boolean)this.db.eventTypes(this.ctx.taskId()).contains("returned_to_pool"), (String)"\u5728\u73ed\u4e14\u672a\u8acb\u5047\uff1a\u4e0d\u5f97\u9023\u52d5\u9000\u6c60");
        Assertions.assertEquals((Object)false, (Object)this.objectMapper.readTree(this.ctx.lastResponse().body()).get("returnedToPool").asBoolean());
    }

    @Then(value="^\u7b2c\u4e00\u8f2a\u7684 \"started\" \u8207 \"completed\" \u4ecd\u53ef\u7531\u4e8b\u4ef6\u6d41\u5b8c\u6574\u56de\u6eaf$")
    public void firstRoundStillTraceable() {
        Assertions.assertTrue((boolean)this.db.eventTypes(this.ctx.taskId()).containsAll(List.of("started", "completed")), (String)"\u6295\u5f71\u6b78\u96f6\u4e0d\u5f71\u97ff\u4e8b\u4ef6\u6d41\u65e2\u6709\u5217");
    }

    @Then(value="^inspections \u5171\u5169\u5217\u3001\u6700\u65b0\u4e00\u5217\u70ba passed\u3001\u5148\u524d\u4e00\u5217\u70ba failed\uff0c\u4e14 redo_count \u4ecd\u70ba (\\d+)$")
    public void inspectionHistoryAppended(int redoCount) {
        List results = this.db.admin().queryForList("SELECT result FROM inspections WHERE task_id = ? ORDER BY created_at DESC", String.class, new Object[]{this.ctx.taskId()});
        Assertions.assertEquals(List.of("passed", "failed"), (Object)results, (String)"\u4e00\u6b21\u67e5\u623f\u4e00\u5217\u3001\u9000\u56de\u91cd\u67e5\u591a\u5217");
        Assertions.assertEquals((int)redoCount, (int)((Number)this.db.taskRow(this.ctx.taskId()).get("redo_count")).intValue(), (String)"\u91cd\u67e5\u901a\u904e\u4e0d\u518d +1");
    }

    @Then(value="^\u56de\u61c9\u7684 returnedToPool \u70ba true$")
    public void returnedToPoolResponse() {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u67e5\u623f\u8a72\u6210\u529f: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertEquals((Object)true, (Object)body.get("returnedToPool").asBoolean());
        Assertions.assertEquals((Object)"pool", (Object)body.get("taskState").asText());
    }

    @Then(value="^\u540c\u4e00\u7b46\u4ea4\u6613\u5beb\u5165\u5169\u7b46\u76f8\u9130\u4e8b\u4ef6\uff1a\"inspection_failed\" \u7dca\u8ddf \"returned_to_pool\"$")
    public void failAndReturnEventsAdjacent() {
        List<String> types = this.db.eventTypes(this.ctx.taskId());
        int failIndex = types.indexOf("inspection_failed");
        Assertions.assertTrue((failIndex >= 0 ? 1 : 0) != 0, (String)("\u4e8b\u4ef6\u6d41\u8a72\u6709 inspection_failed: " + String.valueOf(types)));
        Assertions.assertEquals((Object)"returned_to_pool", (Object)types.get(failIndex + 1), (String)("\u5169\u7b46\u4e8b\u4ef6\u540c\u4ea4\u6613\u76f8\u9130: " + String.valueOf(types)));
    }

    @Then(value="^outbox \u6392\u5165\u4e00\u7b46 push\u3001payload \u5e36\u512a\u5148\u540c\u6a13\u5c64\u63d0\u793a\uff0c\u542b floor (\\d+)\u3001\u623f (\\d+)\u3001dept ([a-z_]+)$")
    public void pushOutboxCarriesSameFloorHint(int floor, String roomNo, String dept) {
        List rows = this.db.admin().queryForList("SELECT payload::text AS payload FROM outbox WHERE tenant_id = ? AND kind = 'push' AND idempotency_key LIKE ?", new Object[]{1L, "task:" + String.valueOf(this.ctx.taskId()) + ":returned_to_pool:%"});
        Assertions.assertEquals((int)1, (int)rows.size(), (String)"\u9023\u52d5\u9000\u6c60\u8981\u6392\u4e00\u7b46 push outbox \u5217");
        JsonNode push = this.objectMapper.readTree((String)((Map)rows.getFirst()).get("payload"));
        Assertions.assertEquals((Object)"prefer_same_floor", (Object)push.get("payload").get("hint").asText(), (String)"\u512a\u5148\u63a8\u64ad\u540c\u6a13\u5c64\u7684\u63d0\u793a");
        Assertions.assertEquals((int)floor, (int)push.get("payload").get("floor").asInt());
        Assertions.assertEquals((Object)roomNo, (Object)push.get("payload").get("room_no").asText());
        Assertions.assertEquals((Object)dept, (Object)push.get("payload").get("dept").asText());
    }

    @Then(value="^\u4e8b\u4ef6\u6d41\u542b \"returned_to_pool\"$")
    public void eventsContainReturnedToPool() {
        Assertions.assertTrue((boolean)this.db.eventTypes(this.ctx.taskId()).contains("returned_to_pool"));
    }

    @Then(value="^token \u70ba base62 22 \u5b57\u5143\u3001\u96a8\u6a5f\u5f37\u5ea6\u81f3\u5c11 128 bit\uff0c\u4e14\u4e0d\u542b\u623f\u865f\u3001\u9996\u7c3d version \u70ba (\\d+)$")
    public void tokenShapeAndFirstVersion(int version) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u5217\u5370\u8a72\u6210\u529f: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        String token = body.get("token").asText();
        Assertions.assertTrue((boolean)token.matches("^[0-9A-Za-z]{22}$"), (String)("token \u5f62\u72c0\uff1abase62 22 \u5b57\u5143\uff0c\u96a8\u6a5f\u5f37\u5ea6\u81f3\u5c11 128 bit: " + token));
        Assertions.assertFalse((boolean)token.contains("301"), (String)"token \u4e0d\u542b\u623f\u865f\u8cc7\u8a0a");
        Assertions.assertEquals((int)version, (int)body.get("version").asInt(), (String)"\u9996\u7c3d version=1");
        Assertions.assertEquals((Object)"301", (Object)body.get("roomNo").asText());
        this.ctx.put("printedToken", token);
    }

    @Then(value="^\u518d\u5217\u5370\u56de\u540c\u4e00\u679a token$")
    public void reprintReturnsSameToken() {
        ScenarioContext.HttpResult second = this.api.get("/admin/rooms/301/qr", this.api.staffToken(1L, this.ctx.staffLogin("\u738b\u4e3b\u7ba1")));
        Assertions.assertEquals((int)200, (int)second.status(), (String)second.body());
        Assertions.assertEquals(this.ctx.get("printedToken"), (Object)this.objectMapper.readTree(second.body()).get("token").asText(), (String)"\u518d\u5217\u5370\u56de\u540c\u4e00\u679a\uff0cGET \u4e0d\u91cd\u7c3d");
    }

    @Then(value="^\u5217\u5370\u67e5\u7121\u623f\u865f \"(\\d+)\" \u7684\u8acb\u6c42\u61c9\u8a72\u5931\u6557\uff0c\u56de\u61c9\u70ba 404$")
    public void printUnknownRoomIs404(String roomNo) {
        ScenarioContext.HttpResult missing = this.api.get("/admin/rooms/" + roomNo + "/qr", this.api.staffToken(1L, this.ctx.staffLogin("\u738b\u4e3b\u7ba1")));
        Assertions.assertEquals((int)404, (int)missing.status(), (String)("\u67e5\u7121\u623f\u865f\uff08\u542b\u8de8\u79df\u6236\uff09\u4e00\u5f8b 404: " + missing.body()));
    }

    @Then(value="^\u5217\u5370\u5225\u5e97\u7684\u623f\u865f \"(\\d+)\" \u7684\u8acb\u6c42\u61c9\u8a72\u5931\u6557\uff0c\u56de\u61c9\u70ba 404$")
    public void printForeignRoomIs404(String roomNo) {
        this.printUnknownRoomIs404(roomNo);
    }

    @Then(value="^\u5beb\u5165\u88ab partial unique \u7d04\u675f\u4ee5 (\\d+) \u62d2\u7d55$")
    public void insertRejectedByPartialUnique(String sqlState) {
        Assertions.assertEquals((Object)sqlState, this.ctx.get("sqlState"), (String)"\u5fc5\u9808\u662f\u552f\u4e00\u7d04\u675f\u9055\u53cd\uff0823505\uff09");
    }

    @Then(value="^\u820a token \u6a19 \"revoked\"\u3001\u65b0 token version \\+1 \u751f\u6548$")
    public void oldRevokedNewVersionBumped() {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u8f2a\u66ff\u8a72\u6210\u529f: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        String newToken = body.get("token").asText();
        String oldToken = (String)this.ctx.get("oldQrToken");
        Assertions.assertNotEquals((Object)oldToken, (Object)newToken);
        Assertions.assertEquals((int)((Integer)this.ctx.get("oldQrVersion") + 1), (int)body.get("version").asInt(), (String)"\u8f2a\u66ff\u5f8c\u65b0 token \u7684 version \u8981 +1 \u751f\u6548");
        Map oldRow = this.db.admin().queryForMap("SELECT status, revoked_at FROM room_qr_tokens WHERE token = ?", new Object[]{oldToken});
        Assertions.assertEquals((Object)"revoked", oldRow.get("status"), (String)"\u820a token \u6a19 revoked\u2014\u2014\u6383\u78bc\u5074\u53ea\u8a8d active\uff0c\u6383\u5b83\u5373\u88ab\u62d2");
        Assertions.assertNotNull(oldRow.get("revoked_at"));
        String newStatus = (String)this.db.admin().queryForObject("SELECT status FROM room_qr_tokens WHERE token = ?", String.class, new Object[]{newToken});
        Assertions.assertEquals((Object)"active", (Object)newStatus);
    }

    @Then(value="^\u8a72\u623f\u6240\u6709\u672a\u64a4\u92b7 guest session \u90fd\u88ab\u5beb\u4e0a revoked_at\u3001\u56de\u61c9\u7684 revokedSessions \u70ba (\\d+)$")
    public void allGuestSessionsRevoked(int revokedSessions) {
        Assertions.assertEquals((int)revokedSessions, (int)this.objectMapper.readTree(this.ctx.lastResponse().body()).get("revokedSessions").asInt());
        Integer unrevoked = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM guest_sessions WHERE tenant_id = ? AND room_id = ? AND revoked_at IS NULL", Integer.class, new Object[]{1L, this.ctx.entityId("r-301")});
        Assertions.assertEquals((int)0, (int)unrevoked, (String)"\u8a72\u623f\u5168\u90e8\u672a\u64a4\u92b7 session \u90fd\u8981\u5beb\u4e0a revoked_at");
    }

    @Then(value="^\u8a72\u4f4f\u5ba2\u7684\u4e0b\u4e00\u500b\u8acb\u6c42\u61c9\u8a72\u5931\u6557\uff0c\u56de\u61c9\u70ba 401$")
    public void guestNextRequestIs401() {
        ScenarioContext.HttpResult context = this.api.get("/guest/context", (String)this.ctx.get("sessionToken"));
        Assertions.assertEquals((int)401, (int)context.status(), (String)"\u8f2a\u66ff\u9023\u5750\uff1a\u8f2a\u66ff\u524d\u6383\u904e\u7684\u4eba\u4e0b\u4e00\u500b\u8acb\u6c42 401");
    }

    private int inspectionCount(UUID taskId) {
        Integer count = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM inspections WHERE task_id = ?", Integer.class, new Object[]{taskId});
        return count == null ? -1 : count;
    }
}
