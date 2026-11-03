/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.cucumber.java.en.When
 *  org.junit.jupiter.api.Assertions
 *  org.springframework.kafka.config.KafkaListenerEndpointRegistry
 *  org.springframework.kafka.listener.MessageListenerContainer
 */
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

    @When(value="^PMS \u4ee5 X-API-Key \"([^\"]*)\" \u67e5\u8a62\u6574\u5408\u4e8b\u4ef6$")
    public void queryEventsWithRawHeader(String rawKey) {
        Map<String, String> headers = rawKey.isEmpty() ? Map.of() : Map.of("X-API-Key", rawKey);
        this.ctx.lastResponse(this.api.getWithHeaders("/integration/pms/events", headers));
    }

    @When(value="^PMS \u4ee5\u8a72\u6539\u5beb\u5f8c\u7684\u91d1\u9470\u67e5\u8a62\u6574\u5408\u4e8b\u4ef6$")
    public void queryEventsWithTamperedKey() {
        String tampered = (String)this.ctx.get("integration:tamperedKey");
        Assertions.assertNotNull((Object)tampered, (String)"\u6539\u5beb\u91d1\u9470\u5c1a\u672a\u4f48\u666f\u2014\u2014Given \u6f0f\u4e86");
        this.ctx.lastResponse(this.api.getWithHeaders("/integration/pms/events", Map.of("X-API-Key", tampered)));
    }

    @When(value="^PMS \u4ee5\u5225\u540d \"([^\"]+)\" \u7684\u91d1\u9470\u67e5\u8a62\u6574\u5408\u4e8b\u4ef6$")
    public void queryEvents(String alias) {
        this.ctx.lastResponse(this.api.getWithHeaders("/integration/pms/events", Map.of("X-API-Key", this.given.apiKey(alias))));
    }

    @When(value="^PMS \u4ee5\u5225\u540d \"([^\"]+)\" \u7684\u91d1\u9470\u67e5\u8a62\u6574\u5408\u4e8b\u4ef6\uff0c\u53c3\u6578 (after|limit) \u70ba \"([^\"]*)\"$")
    public void queryEventsWithParam(String alias, String param, String value) {
        this.ctx.lastResponse(this.api.getWithHeaders("/integration/pms/events?" + param + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8), Map.of("X-API-Key", this.given.apiKey(alias))));
    }

    @When(value="^PMS \u4ee5\u5225\u540d \"([^\"]+)\" \u7684\u91d1\u9470\u67e5\u8a62\u6574\u5408\u4e8b\u4ef6\uff0c\u53c3\u6578 after \u70ba\u7b2c (\\d+) \u7b46\u4e8b\u4ef6\u7684 event_id$")
    public void queryEventsAfterNthEvent(String alias, int ordinal) {
        List eventIds = (List)this.ctx.get("integration:eventIds");
        Assertions.assertNotNull((Object)eventIds, (String)"\u5c1a\u7121\u5df2\u767c\u751f\u4e8b\u4ef6\u2014\u2014Given \u6f0f\u4e86");
        this.ctx.lastResponse(this.api.getWithHeaders("/integration/pms/events?after=" + (String)eventIds.get(ordinal - 1), Map.of("X-API-Key", this.given.apiKey(alias))));
    }

    @When(value="^PMS \u4ee5\u5225\u540d \"([^\"]+)\" \u7684\u91d1\u9470\u67e5\u8a62\u54e1\u5de5\u4efb\u52d9\u5217\u8868$")
    public void queryStaffPoolWithApiKey(String alias) {
        this.ctx.lastResponse(this.api.getWithHeaders("/staff/pool", Map.of("X-API-Key", this.given.apiKey(alias))));
    }

    @When(value="^\u54e1\u5de5 \"([^\"]+)\" \u4ee5\u81ea\u5df1\u7684\u6191\u8b49\u67e5\u8a62\u6574\u5408\u4e8b\u4ef6$")
    public void staffQueriesIntegrationEvents(String alias) {
        Map tokens = (Map)this.ctx.get("integration:staffTokens");
        Assertions.assertNotNull((Object)tokens, (String)"\u54e1\u5de5 JWT \u5c1a\u672a\u4f48\u666f\u2014\u2014Given \u6f0f\u4e86");
        this.ctx.lastResponse(this.api.get("/integration/pms/events", (String)tokens.get(alias)));
    }

    @When(value="^PMS \u4ee5\u5225\u540d \"([^\"]+)\" \u7684\u91d1\u9470\u5e36 cursor \u70ba\u623f \"(\\d+)\" \u4e8b\u4ef6\u7684 event_id \u91cd\u65b0\u5efa\u7acb\u6574\u5408 SSE \u9023\u7dda$")
    public void reconnectWithCursor(String alias, String roomNo) throws Exception {
        Map byRoom = (Map)this.ctx.get("integration:eventIdByRoom");
        Assertions.assertNotNull((Object)byRoom, (String)"\u5c1a\u7121\u5df2\u767c\u751f\u4e8b\u4ef6\u2014\u2014Given \u6f0f\u4e86");
        this.given.openIntegrationStream("/integration/pms/stream?cursor=" + (String)byRoom.get(roomNo), this.given.apiKey(alias));
    }

    @When(value="^\u79df\u6236 (\\d+) \u7684\u623f \"(\\d+)\" \u767c\u751f\u67e5\u623f\u901a\u904e$")
    public void inspectionPassesNow(long tenantId, String roomNo) throws Exception {
        this.emitRealtime(tenantId, roomNo, "inspection_passed");
    }

    @When(value="^\u79df\u6236 (\\d+) \u7684\u623f \"(\\d+)\" \u767c\u751f\u67e5\u623f\u9000\u56de$")
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
        Assertions.assertNotNull((Object)container, (String)"sse listener \u5bb9\u5668\u61c9\u5df2\u8a3b\u518a");
        Awaits.waitUntil(Duration.ofSeconds(30L), "sse consumer \u5206\u5230\u5206\u5340", () -> container.getAssignedPartitions() != null && !container.getAssignedPartitions().isEmpty());
    }
}
