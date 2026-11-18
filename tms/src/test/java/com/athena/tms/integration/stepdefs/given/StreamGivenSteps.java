package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.Awaits;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import io.cucumber.java.en.Given;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    public StreamGivenSteps(
            ScenarioContext ctx,
            ApiClient api,
            DbSupport db,
            SseSupport sse,
            KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry) {
        this.ctx = ctx;
        this.api = api;
        this.db = db;
        this.sse = sse;
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
    }

    @Given("^住客 (A|B) 以 session \"([^\"]+)\" 開啟住客事件串流$")
    public void guestOpensStream(String label, String sessionSymbol) throws Exception {
        waitForListenerAssignment();
        String session = ctx.guestSession(sessionSymbol);
        SseSupport.SseStreamClient client = sse.open("/guest/stream", session);
        register(client);
        ctx.put("sse:guest:" + label, client);
        UUID reservationId = db.admin().queryForObject(
                "SELECT reservation_id FROM guest_sessions WHERE session_token = ?", UUID.class, session);
        ctx.put("reservation:" + label, reservationId);
    }

    @Given("^(?:房務|工務)「(小美|阿強)」開啟員工事件串流$")
    public void staffOpensStream(String name) throws Exception {
        waitForListenerAssignment();
        SseSupport.SseStreamClient client = sse.open("/staff/stream", api.staffToken(1L, ctx.staffLogin(name)));
        register(client);
        ctx.put("sse:staff:" + name, client);
    }

    private void waitForListenerAssignment() {
        MessageListenerContainer container = kafkaListenerEndpointRegistry.getListenerContainer("sse-task-events");
        Assertions.assertNotNull(container, "sse listener 容器應已註冊");
        Awaits.waitUntil(Duration.ofSeconds(30), "sse consumer 分到分區",
                () -> container.getAssignedPartitions() != null && !container.getAssignedPartitions().isEmpty());
    }

    private void register(SseSupport.SseStreamClient client) {
        List<SseSupport.SseStreamClient> clients = ctx.get(CLIENTS_KEY);
        if (clients == null) {
            clients = new ArrayList<>();
            ctx.put(CLIENTS_KEY, clients);
        }
        clients.add(client);
    }
}
