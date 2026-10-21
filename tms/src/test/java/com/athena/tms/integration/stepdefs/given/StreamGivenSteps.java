package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.Awaits;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import com.athena.tms.integration.stepdefs.utils.SseSupport.SseStreamClient;
import io.cucumber.java.en.Given;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_1;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 14-sse-stream feature 的佈景步驟（開流）。開出的連線登記在
 * ScenarioContext 的 {@value #CLIENTS_KEY}，由 hooks 的 @After("@sse") 收尾。
 */
public class StreamGivenSteps {

    public static final String CLIENTS_KEY = "sseClients";

    private final ScenarioContext ctx;
    private final ApiClient api;
    private final DbSupport db;
    private final SseSupport sse;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    public StreamGivenSteps(ScenarioContext ctx, ApiClient api, DbSupport db,
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
        SseStreamClient client = sse.open("/guest/stream", session);
        register(client);
        ctx.put("sse:guest:" + label, client);
        // 住客串流以訂單路由：記下這位住客綁的訂單，建單步驟靠它對上 A/B
        UUID reservationId = db.admin().queryForObject(
                "SELECT reservation_id FROM guest_sessions WHERE session_token = ?",
                UUID.class, session);
        ctx.put("reservation:" + label, reservationId);
    }

    @Given("^(?:房務|工務)「(小美|阿強)」開啟員工事件串流$")
    public void staffOpensStream(String name) throws Exception {
        waitForListenerAssignment();
        SseStreamClient client = sse.open("/staff/stream",
                api.staffToken(TENANT_1, ctx.staffLogin(name)));
        register(client);
        ctx.put("sse:staff:" + name, client);
    }

    /** 事件在 listener 分到分區前送出會被 earliest 撈回來，但等分區讓時序乾淨、失敗訊息不誤導。 */
    private void waitForListenerAssignment() {
        MessageListenerContainer container =
                kafkaListenerEndpointRegistry.getListenerContainer("sse-task-events");
        assertNotNull(container, "sse listener 容器應已註冊");
        Awaits.waitUntil(Duration.ofSeconds(30), "sse consumer 分到分區",
                () -> container.getAssignedPartitions() != null
                        && !container.getAssignedPartitions().isEmpty());
    }

    private void register(SseStreamClient client) {
        List<SseStreamClient> clients = ctx.get(CLIENTS_KEY);
        if (clients == null) {
            clients = new ArrayList<>();
            ctx.put(CLIENTS_KEY, clients);
        }
        clients.add(client);
    }
}
