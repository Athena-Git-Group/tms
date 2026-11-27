package com.athena.tms.relaywake;

import com.athena.tms.SharedContainers;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@CucumberContextConfiguration
@SpringBootTest(properties = "tms.outbox.relay.poll-interval=1h")
@ActiveProfiles("test")
public class RelayWakeSpringConfiguration {

    static final String TASK_EVENTS_TOPIC = "relaywake.tms.task-events";
    private static final String PUSH_TOPIC = "relaywake.tms.push";
    private static final String PUSH_DEAD_LETTER_TOPIC = "relaywake.tms.push.DLT";

    static {
        SharedContainers.createTopics(TASK_EVENTS_TOPIC, PUSH_TOPIC, PUSH_DEAD_LETTER_TOPIC);
    }

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> postgres = SharedContainers.postgres();
        String url = SharedContainers.database("relaywake");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", SharedContainers.kafka()::getBootstrapServers);
        registry.add("tms.kafka.topic.task-events", () -> TASK_EVENTS_TOPIC);
        registry.add("tms.kafka.topic.push", () -> PUSH_TOPIC);
        SharedContainers.wireStorage(registry, "relaywake-photos");
    }
}
