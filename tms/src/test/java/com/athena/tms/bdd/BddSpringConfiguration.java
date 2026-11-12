package com.athena.tms.bdd;

import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.HandoverFixtures;
import com.athena.tms.bdd.support.InspectionFixtures;
import com.athena.tms.bdd.support.JobTrigger;
import com.athena.tms.bdd.support.KafkaGroupAwait;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.VerifyFixtures;
import com.athena.tms.bdd.support.RecordingPushSender;
import com.athena.tms.bdd.support.RepairCountGuard;
import com.athena.tms.push.PushSender;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.OutboxAwait;
import com.athena.tms.bdd.support.PmsProbe;
import com.athena.tms.bdd.support.ReservationFixtures;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.StaffFixtures;
import com.athena.tms.bdd.support.TaskFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.service.JwtService;
import com.athena.tms.service.TaskEngine;
import org.springframework.security.crypto.password.PasswordEncoder;
import io.cucumber.spring.CucumberContextConfiguration;
import io.cucumber.spring.ScenarioScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
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
public class BddSpringConfiguration {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

    static {
        POSTGRES.start();
        KAFKA.start();
        createTopics();
    }

    @DynamicPropertySource
    static void wireFlyway(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BddHarnessConfig {
        @Bean
        @ScenarioScope
        ScenarioWorld scenarioWorld() {
            return new ScenarioWorld();
        }

        @Bean
        @ScenarioScope
        BddApiClient bddApiClient(Environment environment, ObjectMapper objectMapper, ScenarioWorld scenarioWorld) {
            return new BddApiClient(environment, objectMapper, scenarioWorld);
        }

        @Bean
        @ScenarioScope
        LastOperationGuard lastOperationGuard(DbProbe dbProbe) {
            return new LastOperationGuard(dbProbe);
        }

        @Bean
        @ScenarioScope
        RepairCountGuard repairCountGuard(DbProbe dbProbe, TenantFixtures tenantFixtures) {
            return new RepairCountGuard(dbProbe, tenantFixtures);
        }

        @Bean
        DbProbe dbProbe(JdbcClient jdbcClient) {
            return new DbProbe(jdbcClient);
        }

        @Bean
        OutboxAwait outboxAwait(DbProbe dbProbe, ObjectMapper objectMapper) {
            return new OutboxAwait(dbProbe, objectMapper);
        }

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }

        @Bean
        @Primary
        RecordingPushSender recordingPushSender() {
            return new RecordingPushSender();
        }

        @Bean(destroyMethod = "close")
        KafkaGroupAwait kafkaGroupAwait() {
            return new KafkaGroupAwait(KAFKA.getBootstrapServers());
        }

        @Bean
        JobTrigger jobTrigger(ApplicationContext applicationContext) {
            return new JobTrigger(applicationContext);
        }

        @Bean
        TenantFixtures tenantFixtures(JdbcClient jdbcClient) {
            return new TenantFixtures(jdbcClient);
        }

        @Bean
        RoomFixtures roomFixtures(JdbcClient jdbcClient, TenantFixtures tenantFixtures) {
            return new RoomFixtures(jdbcClient, tenantFixtures);
        }

        @Bean
        StaffFixtures staffFixtures(
                JdbcClient jdbcClient,
                TenantFixtures tenantFixtures,
                RoomFixtures roomFixtures,
                PasswordEncoder passwordEncoder,
                JwtService jwtService) {
            return new StaffFixtures(jdbcClient, tenantFixtures, roomFixtures, passwordEncoder, jwtService);
        }

        @Bean
        TaskFixtures taskFixtures(
                TaskEngine taskEngine,
                JdbcClient jdbcClient,
                TenantFixtures tenantFixtures,
                RoomFixtures roomFixtures,
                MutableClock mutableClock) {
            return new TaskFixtures(taskEngine, jdbcClient, tenantFixtures, roomFixtures, mutableClock);
        }

        @Bean
        ReservationFixtures reservationFixtures(
                JdbcClient jdbcClient,
                TenantFixtures tenantFixtures,
                RoomFixtures roomFixtures,
                MutableClock mutableClock,
                ObjectMapper objectMapper) {
            return new ReservationFixtures(jdbcClient, tenantFixtures, roomFixtures, mutableClock, objectMapper);
        }

        @Bean
        HandoverFixtures handoverFixtures(
                JdbcClient jdbcClient, TenantFixtures tenantFixtures, ObjectMapper objectMapper) {
            return new HandoverFixtures(jdbcClient, tenantFixtures, objectMapper);
        }

        @Bean
        @ScenarioScope
        InspectionFixtures inspectionFixtures(BddApiClient bddApiClient) {
            return new InspectionFixtures(bddApiClient);
        }

        @Bean
        @ScenarioScope
        VerifyFixtures verifyFixtures(BddApiClient bddApiClient) {
            return new VerifyFixtures(bddApiClient);
        }

        @Bean
        @ScenarioScope
        PmsProbe pmsProbe(
                OutboxAwait outboxAwait,
                DbProbe dbProbe,
                TenantFixtures tenantFixtures,
                RoomFixtures roomFixtures,
                com.athena.tms.service.AutomationRuleService automationRuleService,
                ScenarioWorld scenarioWorld,
                MutableClock mutableClock) {
            return new PmsProbe(
                    outboxAwait, dbProbe, tenantFixtures, roomFixtures,
                    automationRuleService, scenarioWorld, mutableClock);
        }
    }

    private static void createTopics() {
        try (AdminClient admin = AdminClient.create(
                Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic("tms.task-events", 1, (short) 1),
                    new NewTopic("tms.push", 1, (short) 1),
                    new NewTopic("tms.push.DLT", 1, (short) 1))).all().get();
        } catch (Exception e) {
            throw new IllegalStateException("建立 Kafka topic 失敗", e);
        }
    }
}
