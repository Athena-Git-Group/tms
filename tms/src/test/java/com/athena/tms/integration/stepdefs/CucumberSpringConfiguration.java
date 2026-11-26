package com.athena.tms.integration.stepdefs;

import com.athena.tms.SharedContainers;
import com.athena.tms.auth.AuthTestEndpointsConfiguration;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.GuestFixtures;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import com.athena.tms.integration.stepdefs.utils.TaskFixtures;
import com.athena.tms.service.TaskEngine;
import io.cucumber.spring.CucumberContextConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(AuthTestEndpointsConfiguration.class)
public class CucumberSpringConfiguration {

    public static final String TASK_EVENTS_TOPIC = "integration.tms.task-events";
    public static final String PUSH_TOPIC = "integration.tms.push";
    private static final String DATABASE = "integration";

    static {
        SharedContainers.createTopics(TASK_EVENTS_TOPIC, PUSH_TOPIC);
    }

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> postgres = SharedContainers.postgres();
        String url = SharedContainers.database(DATABASE);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "tms_app");
        registry.add("spring.datasource.password", () -> "tms_app");
        registry.add("spring.kafka.bootstrap-servers", SharedContainers.kafka()::getBootstrapServers);
        registry.add("tms.kafka.topic.task-events", () -> TASK_EVENTS_TOPIC);
        registry.add("tms.kafka.topic.push", () -> PUSH_TOPIC);
        SharedContainers.wireStorage(registry, "integration-photos");
        registry.add("tms.stream.heartbeat-interval", () -> "1s");
        registry.add("tms.stream.degraded-threshold", () -> "5s");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    public static String kafkaBootstrapServers() {
        return SharedContainers.kafka().getBootstrapServers();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HarnessConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        ScenarioContext scenarioContext() {
            return new ScenarioContext();
        }

        @Bean
        ApiClient apiClient(Environment environment, ObjectMapper objectMapper) {
            return new ApiClient(environment, objectMapper);
        }

        @Bean
        DbSupport dbSupport(JdbcTemplate appJdbc) {
            PostgreSQLContainer<?> postgres = SharedContainers.postgres();
            return new DbSupport(appJdbc, SharedContainers.database(DATABASE), postgres.getUsername(), postgres.getPassword());
        }

        @Bean
        TaskFixtures taskFixtures(TaskEngine engine) {
            return new TaskFixtures(engine);
        }

        @Bean
        GuestFixtures guestFixtures(ApiClient apiClient) {
            return new GuestFixtures(apiClient);
        }

        @Bean
        SseSupport sseSupport(Environment environment) {
            return new SseSupport(environment);
        }
    }
}
