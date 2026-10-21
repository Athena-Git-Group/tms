package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.given.IntegrationGivenSteps;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.Awaits;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import io.cucumber.java.en.When;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 15-pms-integration feature 的動作步驟：拉取查詢、授權矩陣探測、
 * SSE 重連與即時事件觸發。
 */
public class IntegrationWhenSteps {

    private final ScenarioContext ctx;
    private final ApiClient api;
    private final IntegrationGivenSteps given;
    private final SseSupport sse;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    public IntegrationWhenSteps(ScenarioContext ctx, ApiClient api, IntegrationGivenSteps given,
                                SseSupport sse,
                                KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry) {
        this.ctx = ctx;
        this.api = api;
        this.given = given;
        this.sse = sse;
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
    }

    @When("^PMS 以 X-API-Key \"([^\"]*)\" 查詢整合事件$")
    public void queryEventsWithRawHeader(String rawKey) {
        // 空字串＝「缺失或空」邊界：整個 header 不帶，與帶空值在 filter 眼裡等價
        Map<String, String> headers = rawKey.isEmpty() ? Map.of() : Map.of("X-API-Key", rawKey);
        ctx.lastResponse(api.getWithHeaders("/integration/pms/events", headers));
    }

    @When("^PMS 以該改寫後的金鑰查詢整合事件$")
    public void queryEventsWithTamperedKey() {
        String tampered = ctx.get(IntegrationGivenSteps.TAMPERED_KEY);
        assertNotNull(tampered, "改寫金鑰尚未佈景——Given 漏了");
        ctx.lastResponse(api.getWithHeaders("/integration/pms/events",
                Map.of("X-API-Key", tampered)));
    }

    @When("^PMS 以別名 \"([^\"]+)\" 的金鑰查詢整合事件$")
    public void queryEvents(String alias) {
        ctx.lastResponse(api.getWithHeaders("/integration/pms/events",
                Map.of("X-API-Key", given.apiKey(alias))));
    }

    @When("^PMS 以別名 \"([^\"]+)\" 的金鑰查詢整合事件，參數 (after|limit) 為 \"([^\"]*)\"$")
    public void queryEventsWithParam(String alias, String param, String value) {
        ctx.lastResponse(api.getWithHeaders(
                "/integration/pms/events?" + param + "="
                        + URLEncoder.encode(value, StandardCharsets.UTF_8),
                Map.of("X-API-Key", given.apiKey(alias))));
    }

    @When("^PMS 以別名 \"([^\"]+)\" 的金鑰查詢整合事件，參數 after 為第 (\\d+) 筆事件的 event_id$")
    public void queryEventsAfterNthEvent(String alias, int ordinal) {
        List<String> eventIds = ctx.get(IntegrationGivenSteps.EVENT_IDS_KEY);
        assertNotNull(eventIds, "尚無已發生事件——Given 漏了");
        ctx.lastResponse(api.getWithHeaders(
                "/integration/pms/events?after=" + eventIds.get(ordinal - 1),
                Map.of("X-API-Key", given.apiKey(alias))));
    }

    @When("^PMS 以別名 \"([^\"]+)\" 的金鑰查詢員工任務列表$")
    public void queryStaffPoolWithApiKey(String alias) {
        ctx.lastResponse(api.getWithHeaders("/staff/pool",
                Map.of("X-API-Key", given.apiKey(alias))));
    }

    @When("^員工 \"([^\"]+)\" 以自己的憑證查詢整合事件$")
    public void staffQueriesIntegrationEvents(String alias) {
        Map<String, String> tokens = ctx.get(IntegrationGivenSteps.STAFF_TOKENS_KEY);
        assertNotNull(tokens, "員工 JWT 尚未佈景——Given 漏了");
        ctx.lastResponse(api.get("/integration/pms/events", tokens.get(alias)));
    }

    @When("^PMS 以別名 \"([^\"]+)\" 的金鑰帶 cursor 為房 \"(\\d+)\" 事件的 event_id 重新建立整合 SSE 連線$")
    public void reconnectWithCursor(String alias, String roomNo) throws Exception {
        Map<String, String> byRoom = ctx.get(IntegrationGivenSteps.EVENT_ID_BY_ROOM_KEY);
        assertNotNull(byRoom, "尚無已發生事件——Given 漏了");
        given.openIntegrationStream("/integration/pms/stream?cursor=" + byRoom.get(roomNo),
                given.apiKey(alias));
    }

    @When("^租戶 (\\d+) 的房 \"(\\d+)\" 發生查房通過$")
    public void inspectionPassesNow(long tenantId, String roomNo) throws Exception {
        emitRealtime(tenantId, roomNo, "inspection_passed");
    }

    @When("^租戶 (\\d+) 的房 \"(\\d+)\" 發生查房退回$")
    public void inspectionFailsNow(long tenantId, String roomNo) throws Exception {
        emitRealtime(tenantId, roomNo, "inspection_failed");
    }

    /**
     * 即時推送的觸發：先把事件列落庫（消費端要以 (task, seq) 查 event_id
     * 與房號組 payload，查無列會被它當 race 略過），再把 envelope 直送
     * Kafka。繞過 outbox relay 是為了時序可控——relay 的 2 秒輪詢會把
     * 「即時」場景拉長，而這裡要驗的是消費端分發，不是 relay。
     */
    private void emitRealtime(long tenantId, String roomNo, String eventType) throws Exception {
        waitForListenerAssignment();
        given.insertInspectionEvent(tenantId, roomNo, eventType, 0);
        sse.produceRaw(System.currentTimeMillis(), "task:" + ctx.taskId(),
                SseSupport.envelope(tenantId, ctx.taskId(), 1, eventType));
    }

    /** 事件送早於分區指派會靠 earliest 撈回，但等指派讓時序乾淨、失敗訊息不誤導。 */
    private void waitForListenerAssignment() {
        MessageListenerContainer container =
                kafkaListenerEndpointRegistry.getListenerContainer("sse-task-events");
        assertNotNull(container, "sse listener 容器應已註冊");
        Awaits.waitUntil(Duration.ofSeconds(30), "sse consumer 分到分區",
                () -> container.getAssignedPartitions() != null
                        && !container.getAssignedPartitions().isEmpty());
    }
}
