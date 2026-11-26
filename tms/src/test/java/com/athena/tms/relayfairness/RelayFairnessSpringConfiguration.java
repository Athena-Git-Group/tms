package com.athena.tms.relayfairness;

import com.athena.tms.SharedContainers;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 只開 1 個 relay 工作執行緒：多開幾個執行緒會掩蓋輪流制沒做對的問題，1 個還過得了才證明是輪流在保護其他租戶。
 * 定時補漏拉到 1 小時，訊息只能靠提交喚醒送出。Kafka 單筆上限調小，才做得出一筆永遠送不出去的訊息。
 */
@CucumberContextConfiguration
@SpringBootTest(properties = {
        "tms.outbox.relay.poll-interval=1h",
        "tms.outbox.relay.workers=1",
        "tms.outbox.relay.batch-size=50",
        "tms.outbox.relay.turn-budget=100ms",
        "tms.outbox.relay.lock-timeout=500ms",
        "spring.kafka.producer.properties.max.request.size=65536"})
@ActiveProfiles("test")
public class RelayFairnessSpringConfiguration {

    static final String TASK_EVENTS_TOPIC = "relayfairness.tms.task-events";
    private static final String PUSH_TOPIC = "relayfairness.tms.push";

    static {
        SharedContainers.createTopics(TASK_EVENTS_TOPIC, PUSH_TOPIC, PUSH_TOPIC + ".DLT");
    }

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> postgres = SharedContainers.postgres();
        String url = SharedContainers.database("relayfairness");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", SharedContainers.kafka()::getBootstrapServers);
        registry.add("tms.kafka.topic.task-events", () -> TASK_EVENTS_TOPIC);
        registry.add("tms.kafka.topic.push", () -> PUSH_TOPIC);
    }
}
