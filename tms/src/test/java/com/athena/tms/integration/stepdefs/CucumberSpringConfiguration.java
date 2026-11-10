package com.athena.tms.integration.stepdefs;

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
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
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
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(AuthTestEndpointsConfiguration.class)
public class CucumberSpringConfiguration {

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    public static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

    static {
        POSTGRES.start();
        KAFKA.start();
        createTopics();
    }

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "tms_app");
        registry.add("spring.datasource.password", () -> "tms_app");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("tms.stream.heartbeat-interval", () -> "1s");
        registry.add("tms.stream.degraded-threshold", () -> "5s");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    private static void createTopics() {
        try (AdminClient admin = AdminClient.create(
                Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic("tms.task-events", 1, (short) 1),
                    new NewTopic("tms.push", 1, (short) 1))).all().get();
        } catch (Exception e) {
            throw new IllegalStateException("建立 Kafka topic 失敗", e);
        }
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
            return new DbSupport(appJdbc, POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
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
