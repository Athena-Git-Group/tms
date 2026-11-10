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

    @Then(value="^回應的事件列表為空$")
    public void eventListIsEmpty() {
        Assertions.assertEquals((int)0, (int)this.events().size(), (String)("事件列表該為空: " + this.ctx.lastResponse().body()));
    }

    @Then(value="^回應的事件列表 (為空|恰有 1 筆)$")
    public void eventListMatchesExpectation(String expectation) {
        int expected = "為空".equals(expectation) ? 0 : 1;
        Assertions.assertEquals((int)expected, (int)this.events().size(), (String)("事件列表該" + expectation + ": " + this.ctx.lastResponse().body()));
    }

    @Then(value="^回應包含 1 筆事件，其內容為:$")
    public void singleEventMatches(DataTable table) {
        JsonNode events = this.events();
        Assertions.assertEquals((int)1, (int)events.size(), (String)("該恰有 1 筆事件: " + this.ctx.lastResponse().body()));
        this.assertEventFields(events.get(0), table);
    }

    @Then(value="^該事件包含非空的 event_id、task_id、seq 與 occurred_at$")
    public void eventCarriesIdentityFields() {
        JsonNode event = this.events().get(0);
        for (String field : List.of("event_id", "task_id", "seq", "occurred_at")) {
            JsonNode value = event.get(field);
            Assertions.assertNotNull((Object)value, (String)("事件該帶 " + field + ": " + String.valueOf(event)));
            Assertions.assertFalse((boolean)value.asText().isBlank(), (String)(field + " 該非空: " + String.valueOf(event)));
        }
    }

    @Then(value="^回應的 next_cursor 等於該事件的 event_id$")
    public void nextCursorEqualsSingleEventId() {
        Assertions.assertEquals((Object)this.events().get(0).get("event_id").asText(), (Object)this.nextCursor(), (String)"next_cursor 該等於唯一事件的 event_id");
    }

    @Then(value="^回應依序包含 room_no 為 (.+) 的 (\\d+) 筆事件$")
    public void eventsInRoomOrder(String quotedRoomNos, int count) {
        ArrayList<String> expected = new ArrayList<String>();
        Matcher matcher = QUOTED.matcher(quotedRoomNos);
        while (matcher.find()) {
            expected.add(matcher.group(1));
        }
        Assertions.assertEquals((int)count, (int)expected.size(), (String)"步驟句裡的房號數與筆數不一致（feature 檔筆誤）");
        JsonNode events = this.events();
        ArrayList actual = new ArrayList();
        events.forEach(event -> actual.add(event.get("room_no").asText()));
        Assertions.assertEquals(expected, actual, (String)("事件的 room_no 順序: " + this.ctx.lastResponse().body()));
    }

    @Then(value="^回應的 next_cursor 等於第 (\\d+) 筆事件的 event_id$")
    public void nextCursorEqualsNthEventId(int ordinal) {
        Assertions.assertEquals((Object)this.events().get(ordinal - 1).get("event_id").asText(), (Object)this.nextCursor(), (String)("next_cursor 該等於第 " + ordinal + " 筆事件的 event_id"));
    }

    @Then(value="^回應的 next_cursor 等於最後一筆的 event_id$")
    public void nextCursorEqualsLastEventId() {
        JsonNode events = this.events();
        Assertions.assertEquals((Object)events.get(events.size() - 1).get("event_id").asText(), (Object)this.nextCursor(), (String)"next_cursor 該等於最後一筆事件的 event_id");
    }

    @Then(value="^整合連線收到事件名為 \"room_status\" 的 SSE 事件，其資料為:$")
    public void integrationStreamReceivesRoomStatus(DataTable table) throws InterruptedException {
        IntegrationSseSupport.IntegrationSseClient.Event event = this.integrationStream().awaitNamed("room_status", Duration.ofSeconds(10L));
        this.assertEventFields(event.data(), table);
        this.ctx.put("integration:lastSseEvent", event);
    }

    @Then(value="^該 SSE 事件的 id 欄位等於其資料中的 event_id$")
    public void sseEventIdMatchesPayloadEventId() {
        IntegrationSseSupport.IntegrationSseClient.Event event = (IntegrationSseSupport.IntegrationSseClient.Event)this.ctx.get("integration:lastSseEvent");
        Assertions.assertNotNull((Object)event, (String)"尚未收到 room_status 事件——前一個斷言步驟漏了");
        Assertions.assertEquals((Object)event.data().get("event_id").asText(), (Object)event.id(), (String)"SSE 的 id: 行該等於 payload 的 event_id（斷線重連拿它當 cursor）");
    }

    @Then(value="^整合連線收到 room_no 為 \"(\\d+)\" 的 \"room_status\" 事件$")
    public void integrationStreamReceivesRoomStatusFor(String roomNo) throws InterruptedException {
        this.integrationStream().awaitRoomStatus(roomNo, Duration.ofSeconds(10L));
    }

    @Then(value="^員工連線收到該任務的變化事件$")
    public void staffStreamReceivesTaskChange() throws InterruptedException {
        SseSupport.SseStreamClient staff = (SseSupport.SseStreamClient)this.ctx.get("sse:staff:小美");
        Assertions.assertNotNull((Object)staff, (String)"員工串流尚未開啟——Given 漏了");
        staff.awaitTask(this.ctx.taskId(), "inspection_failed", Duration.ofSeconds(10L));
    }

    @Then(value="^整合連線未收到任何 \"room_status\" 事件$")
    public void integrationStreamReceivedNoRoomStatus() throws InterruptedException {
        Thread.sleep(300L);
        List<IntegrationSseSupport.IntegrationSseClient.Event> received = this.integrationStream().roomStatusEvents();
        Assertions.assertTrue((boolean)received.isEmpty(), (String)("白名單外事件不得外流到整合連線: " + String.valueOf(received)));
    }

    @Then(value="^outbox 中不存在 kind 為 \"([a-z_]+)\" 的訊息$")
    public void outboxHasNoKind(String kind) {
        Integer count = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM outbox WHERE kind = ?", Integer.class, new Object[]{kind});
        Assertions.assertEquals((int)0, (Integer)count, (String)("outbox 不得再出現 kind=" + kind + " 的訊息（已退場）"));
    }

    private void assertEventFields(JsonNode event, DataTable table) {
        Map<String, String> expected = table.asMaps().getFirst();
        expected.forEach((field, value) -> {
            JsonNode actual = event.get(field);
            Assertions.assertNotNull((Object)actual, (String)("事件該帶 " + field + ": " + String.valueOf(event)));
            Assertions.assertEquals((Object)value, (Object)actual.asText(), (String)("事件的 " + field + ": " + String.valueOf(event)));
        });
    }

    private IntegrationSseSupport.IntegrationSseClient integrationStream() {
        IntegrationSseSupport.IntegrationSseClient client = (IntegrationSseSupport.IntegrationSseClient)this.ctx.get("integration:sse");
        Assertions.assertNotNull((Object)client, (String)"整合 SSE 連線尚未開啟——Given 漏了");
        return client;
    }

    private JsonNode events() {
        JsonNode body = this.objectMapper.readTree(this.ctx.lastResponse().body());
        JsonNode events = body.get("events");
        Assertions.assertNotNull((Object)events, (String)("回應該有 events 欄: " + this.ctx.lastResponse().body()));
        return events;
    }

    private String nextCursor() {
        JsonNode body = this.objectMapper.readTree(this.ctx.lastResponse().body());
        JsonNode cursor = body.get("next_cursor");
        Assertions.assertNotNull((Object)cursor, (String)("回應該有 next_cursor 欄: " + this.ctx.lastResponse().body()));
        return cursor.asText();
    }
}
