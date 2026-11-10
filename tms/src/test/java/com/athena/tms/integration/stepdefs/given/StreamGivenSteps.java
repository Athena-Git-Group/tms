package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.Awaits;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import io.cucumber.java.en.Given;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

public class StreamGivenSteps {
    public static final String CLIENTS_KEY = "sseClients";
    private final ScenarioContext ctx;
    private final ApiClient api;
    private final DbSupport db;
    private final SseSupport sse;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    public StreamGivenSteps(ScenarioContext ctx, ApiClient api, DbSupport db, SseSupport sse, KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry) {
        this.ctx = ctx;
        this.api = api;
        this.db = db;
        this.sse = sse;
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
    }

    @Given(value="^住客 (A|B) 以 session \"([^\"]+)\" 開啟住客事件串流$")
    public void guestOpensStream(String label, String sessionSymbol) throws Exception {
        this.waitForListenerAssignment();
        String session = this.ctx.guestSession(sessionSymbol);
        SseSupport.SseStreamClient client = this.sse.open("/guest/stream", session);
        this.register(client);
        this.ctx.put("sse:guest:" + label, client);
        UUID reservationId = (UUID)this.db.admin().queryForObject("SELECT reservation_id FROM guest_sessions WHERE session_token = ?", UUID.class, new Object[]{session});
        this.ctx.put("reservation:" + label, reservationId);
    }

    @Given(value="^(?:房務|工務)「(小美|阿強)」開啟員工事件串流$")
    public void staffOpensStream(String name) throws Exception {
        this.waitForListenerAssignment();
        SseSupport.SseStreamClient client = this.sse.open("/staff/stream", this.api.staffToken(1L, this.ctx.staffLogin(name)));
        this.register(client);
        this.ctx.put("sse:staff:" + name, client);
    }

    private void waitForListenerAssignment() {
        MessageListenerContainer container = this.kafkaListenerEndpointRegistry.getListenerContainer("sse-task-events");
        Assertions.assertNotNull((Object)container, (String)"sse listener 容器應已註冊");
        Awaits.waitUntil(Duration.ofSeconds(30L), "sse consumer 分到分區", () -> container.getAssignedPartitions() != null && !container.getAssignedPartitions().isEmpty());
    }

    private void register(SseSupport.SseStreamClient client) {
        ArrayList<SseSupport.SseStreamClient> clients = (ArrayList<SseSupport.SseStreamClient>)this.ctx.get(CLIENTS_KEY);
        if (clients == null) {
            clients = new ArrayList<SseSupport.SseStreamClient>();
            this.ctx.put(CLIENTS_KEY, clients);
        }
        clients.add(client);
    }
}
