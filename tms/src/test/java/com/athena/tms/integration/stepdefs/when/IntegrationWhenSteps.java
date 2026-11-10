package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.given.IntegrationGivenSteps;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.Awaits;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import io.cucumber.java.en.When;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

public class IntegrationWhenSteps {
    private final ScenarioContext ctx;
    private final ApiClient api;
    private final IntegrationGivenSteps given;
    private final SseSupport sse;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    public IntegrationWhenSteps(ScenarioContext ctx, ApiClient api, IntegrationGivenSteps given, SseSupport sse, KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry) {
        this.ctx = ctx;
        this.api = api;
        this.given = given;
        this.sse = sse;
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
    }

    @When(value="^PMS 以 X-API-Key \"([^\"]*)\" 查詢整合事件$")
    public void queryEventsWithRawHeader(String rawKey) {
        Map<String, String> headers = rawKey.isEmpty() ? Map.of() : Map.of("X-API-Key", rawKey);
        this.ctx.lastResponse(this.api.getWithHeaders("/integration/pms/events", headers));
    }

    @When(value="^PMS 以該改寫後的金鑰查詢整合事件$")
    public void queryEventsWithTamperedKey() {
        String tampered = (String)this.ctx.get("integration:tamperedKey");
        Assertions.assertNotNull((Object)tampered, (String)"改寫金鑰尚未佈景——Given 漏了");
        this.ctx.lastResponse(this.api.getWithHeaders("/integration/pms/events", Map.of("X-API-Key", tampered)));
    }

    @When(value="^PMS 以別名 \"([^\"]+)\" 的金鑰查詢整合事件$")
    public void queryEvents(String alias) {
        this.ctx.lastResponse(this.api.getWithHeaders("/integration/pms/events", Map.of("X-API-Key", this.given.apiKey(alias))));
    }

    @When(value="^PMS 以別名 \"([^\"]+)\" 的金鑰查詢整合事件，參數 (after|limit) 為 \"([^\"]*)\"$")
    public void queryEventsWithParam(String alias, String param, String value) {
        this.ctx.lastResponse(this.api.getWithHeaders("/integration/pms/events?" + param + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8), Map.of("X-API-Key", this.given.apiKey(alias))));
    }

    @When(value="^PMS 以別名 \"([^\"]+)\" 的金鑰查詢整合事件，參數 after 為第 (\\d+) 筆事件的 event_id$")
    public void queryEventsAfterNthEvent(String alias, int ordinal) {
        List eventIds = (List)this.ctx.get("integration:eventIds");
        Assertions.assertNotNull((Object)eventIds, (String)"尚無已發生事件——Given 漏了");
        this.ctx.lastResponse(this.api.getWithHeaders("/integration/pms/events?after=" + (String)eventIds.get(ordinal - 1), Map.of("X-API-Key", this.given.apiKey(alias))));
    }

    @When(value="^PMS 以別名 \"([^\"]+)\" 的金鑰查詢員工任務列表$")
    public void queryStaffPoolWithApiKey(String alias) {
        this.ctx.lastResponse(this.api.getWithHeaders("/staff/pool", Map.of("X-API-Key", this.given.apiKey(alias))));
    }

    @When(value="^員工 \"([^\"]+)\" 以自己的憑證查詢整合事件$")
    public void staffQueriesIntegrationEvents(String alias) {
        Map tokens = (Map)this.ctx.get("integration:staffTokens");
        Assertions.assertNotNull((Object)tokens, (String)"員工 JWT 尚未佈景——Given 漏了");
        this.ctx.lastResponse(this.api.get("/integration/pms/events", (String)tokens.get(alias)));
    }

    @When(value="^PMS 以別名 \"([^\"]+)\" 的金鑰帶 cursor 為房 \"(\\d+)\" 事件的 event_id 重新建立整合 SSE 連線$")
    public void reconnectWithCursor(String alias, String roomNo) throws Exception {
        Map byRoom = (Map)this.ctx.get("integration:eventIdByRoom");
        Assertions.assertNotNull((Object)byRoom, (String)"尚無已發生事件——Given 漏了");
        this.given.openIntegrationStream("/integration/pms/stream?cursor=" + (String)byRoom.get(roomNo), this.given.apiKey(alias));
    }

    @When(value="^租戶 (\\d+) 的房 \"(\\d+)\" 發生查房通過$")
    public void inspectionPassesNow(long tenantId, String roomNo) throws Exception {
        this.emitRealtime(tenantId, roomNo, "inspection_passed");
    }

    @When(value="^租戶 (\\d+) 的房 \"(\\d+)\" 發生查房退回$")
    public void inspectionFailsNow(long tenantId, String roomNo) throws Exception {
        this.emitRealtime(tenantId, roomNo, "inspection_failed");
    }

    private void emitRealtime(long tenantId, String roomNo, String eventType) throws Exception {
        this.waitForListenerAssignment();
        this.given.insertInspectionEvent(tenantId, roomNo, eventType, 0);
        this.sse.produceRaw(System.currentTimeMillis(), "task:" + String.valueOf(this.ctx.taskId()), SseSupport.envelope(tenantId, this.ctx.taskId(), 1, eventType));
    }

    private void waitForListenerAssignment() {
        MessageListenerContainer container = this.kafkaListenerEndpointRegistry.getListenerContainer("sse-task-events");
        Assertions.assertNotNull((Object)container, (String)"sse listener 容器應已註冊");
        Awaits.waitUntil(Duration.ofSeconds(30L), "sse consumer 分到分區", () -> container.getAssignedPartitions() != null && !container.getAssignedPartitions().isEmpty());
    }
}
