package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.IntegrationSseSupport;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class IntegrationThenSteps {
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
    private final ScenarioContext ctx;
    private final DbSupport db;
    private final ObjectMapper objectMapper;

    public IntegrationThenSteps(ScenarioContext ctx, DbSupport db, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.db = db;
        this.objectMapper = objectMapper;
    }

    @Then(value="^\u56de\u61c9\u7684\u4e8b\u4ef6\u5217\u8868\u70ba\u7a7a$")
    public void eventListIsEmpty() {
        Assertions.assertEquals((int)0, (int)this.events().size(), (String)("\u4e8b\u4ef6\u5217\u8868\u8a72\u70ba\u7a7a: " + this.ctx.lastResponse().body()));
    }

    @Then(value="^\u56de\u61c9\u7684\u4e8b\u4ef6\u5217\u8868 (\u70ba\u7a7a|\u6070\u6709 1 \u7b46)$")
    public void eventListMatchesExpectation(String expectation) {
        int expected = "\u70ba\u7a7a".equals(expectation) ? 0 : 1;
        Assertions.assertEquals((int)expected, (int)this.events().size(), (String)("\u4e8b\u4ef6\u5217\u8868\u8a72" + expectation + ": " + this.ctx.lastResponse().body()));
    }

    @Then(value="^\u56de\u61c9\u5305\u542b 1 \u7b46\u4e8b\u4ef6\uff0c\u5176\u5167\u5bb9\u70ba:$")
    public void singleEventMatches(DataTable table) {
        JsonNode events = this.events();
        Assertions.assertEquals((int)1, (int)events.size(), (String)("\u8a72\u6070\u6709 1 \u7b46\u4e8b\u4ef6: " + this.ctx.lastResponse().body()));
        this.assertEventFields(events.get(0), table);
    }

    @Then(value="^\u8a72\u4e8b\u4ef6\u5305\u542b\u975e\u7a7a\u7684 event_id\u3001task_id\u3001seq \u8207 occurred_at$")
    public void eventCarriesIdentityFields() {
        JsonNode event = this.events().get(0);
        for (String field : List.of("event_id", "task_id", "seq", "occurred_at")) {
            JsonNode value = event.get(field);
            Assertions.assertNotNull((Object)value, (String)("\u4e8b\u4ef6\u8a72\u5e36 " + field + ": " + String.valueOf(event)));
            Assertions.assertFalse((boolean)value.asText().isBlank(), (String)(field + " \u8a72\u975e\u7a7a: " + String.valueOf(event)));
        }
    }

    @Then(value="^\u56de\u61c9\u7684 next_cursor \u7b49\u65bc\u8a72\u4e8b\u4ef6\u7684 event_id$")
    public void nextCursorEqualsSingleEventId() {
        Assertions.assertEquals((Object)this.events().get(0).get("event_id").asText(), (Object)this.nextCursor(), (String)"next_cursor \u8a72\u7b49\u65bc\u552f\u4e00\u4e8b\u4ef6\u7684 event_id");
    }

    @Then(value="^\u56de\u61c9\u4f9d\u5e8f\u5305\u542b room_no \u70ba (.+) \u7684 (\\d+) \u7b46\u4e8b\u4ef6$")
    public void eventsInRoomOrder(String quotedRoomNos, int count) {
        ArrayList<String> expected = new ArrayList<String>();
        Matcher matcher = QUOTED.matcher(quotedRoomNos);
        while (matcher.find()) {
            expected.add(matcher.group(1));
        }
        Assertions.assertEquals((int)count, (int)expected.size(), (String)"\u6b65\u9a5f\u53e5\u88e1\u7684\u623f\u865f\u6578\u8207\u7b46\u6578\u4e0d\u4e00\u81f4\uff08feature \u6a94\u7b46\u8aa4\uff09");
        JsonNode events = this.events();
        ArrayList actual = new ArrayList();
        events.forEach(event -> actual.add(event.get("room_no").asText()));
        Assertions.assertEquals(expected, actual, (String)("\u4e8b\u4ef6\u7684 room_no \u9806\u5e8f: " + this.ctx.lastResponse().body()));
    }

    @Then(value="^\u56de\u61c9\u7684 next_cursor \u7b49\u65bc\u7b2c (\\d+) \u7b46\u4e8b\u4ef6\u7684 event_id$")
    public void nextCursorEqualsNthEventId(int ordinal) {
        Assertions.assertEquals((Object)this.events().get(ordinal - 1).get("event_id").asText(), (Object)this.nextCursor(), (String)("next_cursor \u8a72\u7b49\u65bc\u7b2c " + ordinal + " \u7b46\u4e8b\u4ef6\u7684 event_id"));
    }

    @Then(value="^\u56de\u61c9\u7684 next_cursor \u7b49\u65bc\u6700\u5f8c\u4e00\u7b46\u7684 event_id$")
    public void nextCursorEqualsLastEventId() {
        JsonNode events = this.events();
        Assertions.assertEquals((Object)events.get(events.size() - 1).get("event_id").asText(), (Object)this.nextCursor(), (String)"next_cursor \u8a72\u7b49\u65bc\u6700\u5f8c\u4e00\u7b46\u4e8b\u4ef6\u7684 event_id");
    }

    @Then(value="^\u6574\u5408\u9023\u7dda\u6536\u5230\u4e8b\u4ef6\u540d\u70ba \"room_status\" \u7684 SSE \u4e8b\u4ef6\uff0c\u5176\u8cc7\u6599\u70ba:$")
    public void integrationStreamReceivesRoomStatus(DataTable table) throws InterruptedException {
        IntegrationSseSupport.IntegrationSseClient.Event event = this.integrationStream().awaitNamed("room_status", Duration.ofSeconds(10L));
        this.assertEventFields(event.data(), table);
        this.ctx.put("integration:lastSseEvent", event);
    }

    @Then(value="^\u8a72 SSE \u4e8b\u4ef6\u7684 id \u6b04\u4f4d\u7b49\u65bc\u5176\u8cc7\u6599\u4e2d\u7684 event_id$")
    public void sseEventIdMatchesPayloadEventId() {
        IntegrationSseSupport.IntegrationSseClient.Event event = (IntegrationSseSupport.IntegrationSseClient.Event)this.ctx.get("integration:lastSseEvent");
        Assertions.assertNotNull((Object)event, (String)"\u5c1a\u672a\u6536\u5230 room_status \u4e8b\u4ef6\u2014\u2014\u524d\u4e00\u500b\u65b7\u8a00\u6b65\u9a5f\u6f0f\u4e86");
        Assertions.assertEquals((Object)event.data().get("event_id").asText(), (Object)event.id(), (String)"SSE \u7684 id: \u884c\u8a72\u7b49\u65bc payload \u7684 event_id\uff08\u65b7\u7dda\u91cd\u9023\u62ff\u5b83\u7576 cursor\uff09");
    }

    @Then(value="^\u6574\u5408\u9023\u7dda\u6536\u5230 room_no \u70ba \"(\\d+)\" \u7684 \"room_status\" \u4e8b\u4ef6$")
    public void integrationStreamReceivesRoomStatusFor(String roomNo) throws InterruptedException {
        this.integrationStream().awaitRoomStatus(roomNo, Duration.ofSeconds(10L));
    }

    @Then(value="^\u54e1\u5de5\u9023\u7dda\u6536\u5230\u8a72\u4efb\u52d9\u7684\u8b8a\u5316\u4e8b\u4ef6$")
    public void staffStreamReceivesTaskChange() throws InterruptedException {
        SseSupport.SseStreamClient staff = (SseSupport.SseStreamClient)this.ctx.get("sse:staff:\u5c0f\u7f8e");
        Assertions.assertNotNull((Object)staff, (String)"\u54e1\u5de5\u4e32\u6d41\u5c1a\u672a\u958b\u555f\u2014\u2014Given \u6f0f\u4e86");
        staff.awaitTask(this.ctx.taskId(), "inspection_failed", Duration.ofSeconds(10L));
    }

    @Then(value="^\u6574\u5408\u9023\u7dda\u672a\u6536\u5230\u4efb\u4f55 \"room_status\" \u4e8b\u4ef6$")
    public void integrationStreamReceivedNoRoomStatus() throws InterruptedException {
        Thread.sleep(300L);
        List<IntegrationSseSupport.IntegrationSseClient.Event> received = this.integrationStream().roomStatusEvents();
        Assertions.assertTrue((boolean)received.isEmpty(), (String)("\u767d\u540d\u55ae\u5916\u4e8b\u4ef6\u4e0d\u5f97\u5916\u6d41\u5230\u6574\u5408\u9023\u7dda: " + String.valueOf(received)));
    }

    @Then(value="^outbox \u4e2d\u4e0d\u5b58\u5728 kind \u70ba \"([a-z_]+)\" \u7684\u8a0a\u606f$")
    public void outboxHasNoKind(String kind) {
        Integer count = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM outbox WHERE kind = ?", Integer.class, new Object[]{kind});
        Assertions.assertEquals((int)0, (Integer)count, (String)("outbox \u4e0d\u5f97\u518d\u51fa\u73fe kind=" + kind + " \u7684\u8a0a\u606f\uff08\u5df2\u9000\u5834\uff09"));
    }

    private void assertEventFields(JsonNode event, DataTable table) {
        Map<String, String> expected = table.asMaps().getFirst();
        expected.forEach((field, value) -> {
            JsonNode actual = event.get(field);
            Assertions.assertNotNull((Object)actual, (String)("\u4e8b\u4ef6\u8a72\u5e36 " + field + ": " + String.valueOf(event)));
            Assertions.assertEquals((Object)value, (Object)actual.asText(), (String)("\u4e8b\u4ef6\u7684 " + field + ": " + String.valueOf(event)));
        });
    }

    private IntegrationSseSupport.IntegrationSseClient integrationStream() {
        IntegrationSseSupport.IntegrationSseClient client = (IntegrationSseSupport.IntegrationSseClient)this.ctx.get("integration:sse");
        Assertions.assertNotNull((Object)client, (String)"\u6574\u5408 SSE \u9023\u7dda\u5c1a\u672a\u958b\u555f\u2014\u2014Given \u6f0f\u4e86");
        return client;
    }

    private JsonNode events() {
        JsonNode body = this.objectMapper.readTree(this.ctx.lastResponse().body());
        JsonNode events = body.get("events");
        Assertions.assertNotNull((Object)events, (String)("\u56de\u61c9\u8a72\u6709 events \u6b04: " + this.ctx.lastResponse().body()));
        return events;
    }

    private String nextCursor() {
        JsonNode body = this.objectMapper.readTree(this.ctx.lastResponse().body());
        JsonNode cursor = body.get("next_cursor");
        Assertions.assertNotNull((Object)cursor, (String)("\u56de\u61c9\u8a72\u6709 next_cursor \u6b04: " + this.ctx.lastResponse().body()));
        return cursor.asText();
    }
}
