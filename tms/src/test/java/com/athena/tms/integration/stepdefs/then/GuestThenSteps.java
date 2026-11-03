/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ch.qos.logback.core.read.ListAppender
 *  io.cucumber.java.en.Then
 *  org.junit.jupiter.api.Assertions
 *  tools.jackson.databind.JsonNode
 *  tools.jackson.databind.ObjectMapper
 */
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

    @Then(value="^\u7cfb\u7d71\u672a\u7c3d\u767c\u4efb\u4f55 guest session \u4e5f\u672a\u5efa\u7acb\u4efb\u4f55\u4efb\u52d9$")
    public void nothingSignedNothingCreated() {
        Assertions.assertEquals((long)((Long)this.ctx.get("sessionsBefore")), (long)this.db.countAll("guest_sessions"), (String)"\u4e0d\u7c3d session\uff08E-03\uff09");
        Assertions.assertEquals((long)((Long)this.ctx.get("tasksBefore")), (long)this.db.countAll("tasks"), (String)"\u4e0d\u5efa\u55ae\uff08E-03\uff09");
    }

    @Then(value="^\u4ee5\u4e0d\u5b58\u5728\u7684 token \u6383\u78bc\u5f97\u5230\u540c\u4e00\u500b\u932f\u8aa4\u78bc$")
    public void unknownTokenGetsSameCode() {
        String previousCode = this.objectMapper.readTree(this.ctx.lastResponse().body()).get("code").asText();
        ScenarioContext.HttpResult unknown = this.guest.scan("NoSuchTokenAnywhere123", null);
        Assertions.assertEquals((int)409, (int)unknown.status());
        Assertions.assertEquals((Object)previousCode, (Object)this.objectMapper.readTree(unknown.body()).get("code").asText(), (String)"\u4e0d\u5b58\u5728\u7684 token \u4e0d\u5f97\u8207\u4f5c\u5ee2\u53ef\u5340\u5206");
    }

    @Then(value="^\u56de\u61c9\u542b\u8cc7\u8a0a\u9801\u8207\u9700\u6c42\u9078\u55ae$")
    public void responseCarriesInfoPagesAndMenu() {
        JsonNode body = this.objectMapper.readTree(this.ctx.lastResponse().body());
        Assertions.assertTrue((boolean)GuestThenSteps.hasNodeWithField(body.get("infoPages"), "title", "Wi-Fi \u4f7f\u7528\u8aaa\u660e"), (String)("\u8cc7\u8a0a\u9801\u4f86\u81ea tenant_config guest_info_pages: " + String.valueOf(body.get("infoPages"))));
        Assertions.assertTrue((boolean)GuestThenSteps.hasNodeWithField(body.get("menu"), "label", "\u52a0\u6bdb\u5dfe"), (String)("\u9078\u55ae\u4f86\u81ea tenant_config request_menu: " + String.valueOf(body.get("menu"))));
    }

    @Then(value="^\u7c3d\u767c\u7684 session token \u4ee5\u79df\u6236 2 \u70ba\u524d\u7db4$")
    public void sessionTokenHasTenant2Prefix() {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u8de8\u79df\u6236\u6383\u78bc\u8a72\u6210\u529f: " + last.body()));
        String sessionToken = this.objectMapper.readTree(last.body()).get("sessionToken").asText();
        Assertions.assertTrue((boolean)sessionToken.startsWith("2."), (String)("\u79df\u6236 2 \u7684\u78bc\u5fc5\u9808\u5728 RLS \u4e0b\u4e5f\u627e\u5f97\u56de\u4f86\u4e14\u524d\u7db4\uff1d2: " + sessionToken));
    }

    @Then(value="^\u56de\u61c9\u5167\u5bb9\u4f86\u81ea\u79df\u6236\u8a2d\u5b9a guest_info_pages$")
    public void homeContentFromTenantConfig() {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u56de\u61c9 body: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertTrue((boolean)GuestThenSteps.hasNodeWithField(body.get("infoPages"), "title", "\u65e9\u9910\u6642\u9593\u8207\u5730\u9ede"), (String)("\u5167\u5bb9\u4f86\u81ea guest_info_pages: " + String.valueOf(body.get("infoPages"))));
    }

    @Then(value="^\u4efb\u52d9\u7e3d\u6578\u4e0d\u8b8a$")
    public void taskCountUnchanged() {
        Assertions.assertEquals((long)((Long)this.ctx.get("tasksBeforeHome")), (long)this.db.countAll("tasks"), (String)"\u8cc7\u8a0a\u9801\u700f\u89bd\u4e0d\u7522\u751f\u4efb\u52d9\uff08FR-002\uff09");
    }

    @Then(value="^\u5efa\u7acb\u4efb\u52d9 state \u70ba \"([a-z_]+)\" \u4e14 dept \u70ba \"([a-z_]+)\"$")
    public void createdTaskStateAndDept(String state, String dept) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)201, (int)last.status(), (String)("\u5efa\u55ae\u8a72\u6210\u529f: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertEquals((Object)state, (Object)body.get("state").asText(), (String)"\u547d\u4e2d 0 \u6216 >1 \u90e8\u9580\u2192\u4e0d\u731c\u3001\u505c triage");
        Assertions.assertEquals((Object)dept, (Object)body.get("dept").asText());
    }

    @Then(value="^\u5efa\u7acb\u4efb\u52d9 type \u70ba ([a-z_]+)\u3001dept \u70ba ([a-z_]+)\u3001state \u70ba ([a-z_]+)$")
    public void createdTaskTypeDeptState(String type, String dept, String state) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)201, (int)last.status(), (String)("\u5efa\u55ae\u8a72\u6210\u529f: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertEquals((Object)type, (Object)body.get("type").asText());
        Assertions.assertEquals((Object)dept, (Object)body.get("dept").asText());
        Assertions.assertEquals((Object)state, (Object)body.get("state").asText(), (String)"\u9078\u55ae\u9805\u9810\u5c0d\u61c9\u90e8\u9580\uff0c\u76f4\u5165 pool \u4e0d\u7d93 triage");
    }

    @Then(value="^\u4e8b\u4ef6 \"created\" \u7684\u5feb\u7167\u4e4b\u7a2e\u985e\u70ba ([a-z_]+)\u3001\u4f86\u6e90\u70ba \"guest\"\u3001\u623f\u865f\u70ba \"(\\d+)\"\u3001\u8a02\u55ae\u70ba \"([^\"]+)\"\u3001\u6a19\u984c\u70ba \"([^\"]*)\"$")
    public void createdSnapshotLiteralValues(String type, String roomNo, String reservationRef, String title) {
        ScenarioContext.HttpResult response = this.ctx.lastResponse();
        UUID taskId = UUID.fromString(this.objectMapper.readTree(response.body()).get("id").asText());
        JsonNode snapshot = this.objectMapper.readTree(this.db.eventPayloadOfType(taskId, "created"));
        Assertions.assertEquals((Object)type, (Object)snapshot.get("type").asText(), (String)"\u7a2e\u985e");
        Assertions.assertEquals((Object)"guest", (Object)snapshot.get("source").asText(), (String)"\u4f86\u6e90");
        String roomInDb = (String)this.db.admin().queryForObject("SELECT room_no FROM rooms WHERE id = ?", String.class, new Object[]{UUID.fromString(snapshot.get("room_id").asText())});
        Assertions.assertEquals((Object)roomNo, (Object)roomInDb, (String)"\u623f\u865f");
        String refInDb = (String)this.db.admin().queryForObject("SELECT reservation_ref FROM reservations_cache WHERE id = ?", String.class, new Object[]{UUID.fromString(snapshot.get("reservation_id").asText())});
        Assertions.assertEquals((Object)reservationRef, (Object)refInDb, (String)"\u8a02\u55ae");
        Assertions.assertEquals((Object)title, (Object)snapshot.get("title").asText(), (String)"\u6a19\u984c");
    }

    @Then(value="^\u4e8b\u4ef6 payload \u7684 note \u8a18\u9304\u547d\u4e2d\u7684 rule_id \"([^\"]+)\" \u8207\u539f\u6587\u3001\u8a9e\u8a00$")
    public void ruleTraceInNote(String ruleId) {
        JsonNode snapshot = this.objectMapper.readTree(this.db.eventPayloadOfType(this.ctx.taskId(), "created"));
        JsonNode note = this.objectMapper.readTree(snapshot.get("note").asText());
        Assertions.assertEquals((Object)ruleId, (Object)note.get("rule_id").asText(), (String)"\u547d\u4e2d\u7559\u75d5\u53ef\u89e3\u91cb\uff08A10\uff09");
        Assertions.assertEquals(this.ctx.get("freeText"), (Object)note.get("text").asText());
        Assertions.assertEquals((Object)"zh-TW", (Object)note.get("lang").asText());
    }

    @Then(value="^\u7b2c 3 \u7b46\u9700\u6c42\u4ecd\u70ba\u6210\u529f\u5efa\u7acb$")
    public void thirdRequestStillCreated() {
        ScenarioContext.HttpResult third = (ScenarioContext.HttpResult)this.ctx.get("thirdResponse");
        Assertions.assertEquals((int)201, (int)third.status(), (String)("\u7b2c 3 \u7b46\u8a72\u5728\u9650\u6d41\u7dda\u5167: " + third.body()));
        JsonNode body = this.objectMapper.readTree(third.body());
        Assertions.assertNotNull((Object)body.get("id"));
        Assertions.assertEquals((Object)"pool", (Object)this.db.taskRow(UUID.fromString(body.get("id").asText())).get("state"));
    }

    @Then(value="^\u6e05\u55ae\u542b\u81ea\u5df1\u7684\u300c([^\u300d]+)\u300d\u4f46\u4e0d\u542b\u524d\u4e00\u7b46\u8a02\u55ae\u7684\u4efb\u4f55\u9700\u6c42$")
    public void listFiltersByReservation(String myTitle) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u67e5\u8a62\u8a72\u6210\u529f: " + last.body()));
        JsonNode list = this.objectMapper.readTree(last.body());
        boolean mineVisible = false;
        for (JsonNode task : list) {
            mineVisible |= myTitle.equals(task.get("title").asText());
        }
        Assertions.assertTrue((boolean)mineVisible, (String)("\u81ea\u5df1\u7684\u55ae\u770b\u5f97\u5230: " + last.body()));
        List foreignTaskIds = this.db.admin().queryForList("SELECT t.id::text FROM tasks t WHERE t.reservation_id IS DISTINCT FROM (SELECT reservation_id FROM tasks WHERE title = ? LIMIT 1)", String.class, new Object[]{myTitle});
        for (JsonNode task : list) {
            Assertions.assertFalse((boolean)foreignTaskIds.contains(task.get("id").asText()), (String)("\u5225\u8a02\u55ae\u7684\u9700\u6c42\u4e0d\u5f97\u51fa\u73fe: " + String.valueOf(task)));
        }
    }

    @Then(value="^\u4e8b\u4ef6 \"cancelled\" \u7684\u539f\u56e0\u70ba \"([a-z_]+)\"$")
    public void cancelledEventReason(String reason) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u6536\u56de\u8a72\u6210\u529f: " + last.body()));
        JsonNode payload = this.objectMapper.readTree(this.db.eventPayloadOfType(this.ctx.taskId(), "cancelled"));
        Assertions.assertEquals((Object)reason, (Object)payload.get("reason").asText(), (String)"\u6536\u56de\u539f\u56e0\u662f\u56fa\u5b9a\u7684\u5217\u8209\u503c");
    }

    @Then(value="^\u56de\u61c9\u7684\u4efb\u52d9 rating \u70ba (\\d+) \u4e14\u4e8b\u4ef6 \"rated\" \u5e36\u5206\u6578$")
    public void ratingAcceptedWithEvent(int rating) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u8a55\u5206\u8a72\u6210\u529f: " + last.body()));
        Assertions.assertEquals((int)rating, (int)this.objectMapper.readTree(last.body()).get("rating").asInt());
        JsonNode payload = this.objectMapper.readTree(this.db.eventPayloadOfType(this.ctx.taskId(), "rated"));
        Assertions.assertEquals((int)rating, (int)payload.get("rating").asInt(), (String)"rated \u4e8b\u4ef6\u5e36\u5206\u6578");
    }

    @Then(value="^\u7522\u751f\u542b\u8a72 IP \u7684\u6383\u78bc\u9650\u6d41\u544a\u8b66\uff0c\u4ee5 WARN \u65e5\u8a8c\u8a18\u9304$")
    public void alertLoggedWithIp() {
        ListAppender<ILoggingEvent> alerts = this.ctx.get("alertAppender");
        Assertions.assertNotNull((Object)alerts, (String)"Given \u6b65\u9a5f\u8a72\u5148\u639b\u4e0a\u65e5\u8a8c\u76e3\u807d");
        String attackerIp = (String)this.ctx.get("attackerIp");
        Assertions.assertTrue((boolean)alerts.list.stream().anyMatch(event -> "WARN".equals(event.getLevel().toString()) && event.getFormattedMessage().contains("[alert][scan-rate-limit]") && event.getFormattedMessage().contains(attackerIp)), (String)"gherkin\uff1a\u8acb\u6c42\u88ab\u64cb\u300c\u4e26\u7522\u751f\u544a\u8b66\u300d\u2014\u2014\u544a\u8b66\u901a\u9053\uff1dWARN \u65e5\u8a8c");
    }

    @Then(value="^\u5225\u7684 IP \u4ee5 token \"([^\"]+)\" \u6383\u78bc\u4ecd\u6210\u529f$")
    public void otherIpNotAffected(String token) {
        ScenarioContext.HttpResult other = this.guest.scan(token, "198.51.100.7");
        Assertions.assertEquals((int)200, (int)other.status(), (String)("\u9650\u6d41\u6309\u55ae\u4e00 IP \u8a08\u6578\uff0c\u5225\u7684 IP \u4e0d\u53d7\u9023\u5750: " + other.body()));
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
