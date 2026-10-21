package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.given.IntegrationGivenSteps;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.IntegrationSseSupport.IntegrationSseClient;
import com.athena.tms.integration.stepdefs.utils.SseSupport.SseStreamClient;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 15-pms-integration feature 的斷言步驟：拉取回應形狀、SSE 收訊、outbox 退場。 */
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

    @Then("^回應的事件列表為空$")
    public void eventListIsEmpty() {
        assertEquals(0, events().size(), "事件列表該為空: " + ctx.lastResponse().body());
    }

    @Then("^回應的事件列表 (為空|恰有 1 筆)$")
    public void eventListMatchesExpectation(String expectation) {
        int expected = "為空".equals(expectation) ? 0 : 1;
        assertEquals(expected, events().size(),
                "事件列表該" + expectation + ": " + ctx.lastResponse().body());
    }

    @Then("^回應包含 1 筆事件，其內容為:$")
    public void singleEventMatches(DataTable table) {
        JsonNode events = events();
        assertEquals(1, events.size(), "該恰有 1 筆事件: " + ctx.lastResponse().body());
        assertEventFields(events.get(0), table);
    }

    @Then("^該事件包含非空的 event_id、task_id、seq 與 occurred_at$")
    public void eventCarriesIdentityFields() {
        JsonNode event = events().get(0);
        for (String field : List.of("event_id", "task_id", "seq", "occurred_at")) {
            JsonNode value = event.get(field);
            assertNotNull(value, "事件該帶 " + field + ": " + event);
            assertFalse(value.asText().isBlank(), field + " 該非空: " + event);
        }
    }

    @Then("^回應的 next_cursor 等於該事件的 event_id$")
    public void nextCursorEqualsSingleEventId() {
        assertEquals(events().get(0).get("event_id").asText(), nextCursor(),
                "next_cursor 該等於唯一事件的 event_id");
    }

    @Then("^回應依序包含 room_no 為 (.+) 的 (\\d+) 筆事件$")
    public void eventsInRoomOrder(String quotedRoomNos, int count) {
        List<String> expected = new ArrayList<>();
        Matcher matcher = QUOTED.matcher(quotedRoomNos);
        while (matcher.find()) {
            expected.add(matcher.group(1));
        }
        assertEquals(count, expected.size(), "步驟句裡的房號數與筆數不一致（feature 檔筆誤）");
        JsonNode events = events();
        List<String> actual = new ArrayList<>();
        events.forEach(event -> actual.add(event.get("room_no").asText()));
        assertEquals(expected, actual, "事件的 room_no 順序: " + ctx.lastResponse().body());
    }

    @Then("^回應的 next_cursor 等於第 (\\d+) 筆事件的 event_id$")
    public void nextCursorEqualsNthEventId(int ordinal) {
        assertEquals(events().get(ordinal - 1).get("event_id").asText(), nextCursor(),
                "next_cursor 該等於第 " + ordinal + " 筆事件的 event_id");
    }

    @Then("^回應的 next_cursor 等於最後一筆的 event_id$")
    public void nextCursorEqualsLastEventId() {
        JsonNode events = events();
        assertEquals(events.get(events.size() - 1).get("event_id").asText(), nextCursor(),
                "next_cursor 該等於最後一筆事件的 event_id");
    }

    @Then("^整合連線收到事件名為 \"room_status\" 的 SSE 事件，其資料為:$")
    public void integrationStreamReceivesRoomStatus(DataTable table) throws InterruptedException {
        IntegrationSseClient.Event event =
                integrationStream().awaitNamed("room_status", Duration.ofSeconds(10));
        assertEventFields(event.data(), table);
        ctx.put("integration:lastSseEvent", event);
    }

    @Then("^該 SSE 事件的 id 欄位等於其資料中的 event_id$")
    public void sseEventIdMatchesPayloadEventId() {
        IntegrationSseClient.Event event = ctx.get("integration:lastSseEvent");
        assertNotNull(event, "尚未收到 room_status 事件——前一個斷言步驟漏了");
        assertEquals(event.data().get("event_id").asText(), event.id(),
                "SSE 的 id: 行該等於 payload 的 event_id（斷線重連拿它當 cursor）");
    }

    @Then("^整合連線收到 room_no 為 \"(\\d+)\" 的 \"room_status\" 事件$")
    public void integrationStreamReceivesRoomStatusFor(String roomNo) throws InterruptedException {
        integrationStream().awaitRoomStatus(roomNo, Duration.ofSeconds(10));
    }

    @Then("^員工連線收到該任務的變化事件$")
    public void staffStreamReceivesTaskChange() throws InterruptedException {
        SseStreamClient staff = ctx.get("sse:staff:小美");
        assertNotNull(staff, "員工串流尚未開啟——Given 漏了");
        staff.awaitTask(ctx.taskId(), "inspection_failed", Duration.ofSeconds(10));
    }

    @Then("^整合連線未收到任何 \"room_status\" 事件$")
    public void integrationStreamReceivedNoRoomStatus() throws InterruptedException {
        // 消費端是先廣播員工、後推整合：員工收到時，整合側若會被誤推也已寫出。
        // 再留一小段窗只是保險，防 client 讀取執行緒還沒把已寫出的事件收進歷史
        Thread.sleep(300);
        List<IntegrationSseClient.Event> received = integrationStream().roomStatusEvents();
        assertTrue(received.isEmpty(), "白名單外事件不得外流到整合連線: " + received);
    }

    @Then("^outbox 中不存在 kind 為 \"([a-z_]+)\" 的訊息$")
    public void outboxHasNoKind(String kind) {
        Integer count = db.admin().queryForObject(
                "SELECT count(*) FROM outbox WHERE kind = ?", Integer.class, kind);
        assertEquals(0, count, "outbox 不得再出現 kind=" + kind + " 的訊息（已退場）");
    }

    /** 表格逐欄比對事件 payload（拉取與 SSE 的 wire 契約同形，共用這個比對）。 */
    private void assertEventFields(JsonNode event, DataTable table) {
        Map<String, String> expected = table.asMaps().getFirst();
        expected.forEach((field, value) -> {
            JsonNode actual = event.get(field);
            assertNotNull(actual, "事件該帶 " + field + ": " + event);
            assertEquals(value, actual.asText(), "事件的 " + field + ": " + event);
        });
    }

    private IntegrationSseClient integrationStream() {
        IntegrationSseClient client = ctx.get(IntegrationGivenSteps.CURRENT_SSE_KEY);
        assertNotNull(client, "整合 SSE 連線尚未開啟——Given 漏了");
        return client;
    }

    private JsonNode events() {
        JsonNode body = objectMapper.readTree(ctx.lastResponse().body());
        JsonNode events = body.get("events");
        assertNotNull(events, "回應該有 events 欄: " + ctx.lastResponse().body());
        return events;
    }

    private String nextCursor() {
        JsonNode body = objectMapper.readTree(ctx.lastResponse().body());
        JsonNode cursor = body.get("next_cursor");
        assertNotNull(cursor, "回應該有 next_cursor 欄: " + ctx.lastResponse().body());
        return cursor.asText();
    }
}
