package com.athena.tms.bdd;

import com.athena.tms.SharedContainers;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.DeskContext;
import com.athena.tms.bdd.support.DeskCreateFaults;
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
import com.athena.tms.bdd.support.ScriptedRepairTicketNumbers;
import com.athena.tms.bdd.support.OutboxAwait;
import com.athena.tms.bdd.support.PmsProbe;
import com.athena.tms.bdd.support.ReservationFixtures;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.StaffFixtures;
import com.athena.tms.bdd.support.StaffStreamClient;
import com.athena.tms.bdd.support.StreamFaults;
import com.athena.tms.config.KafkaTopicsProperties;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import com.athena.tms.bdd.support.TaskFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.service.JwtService;
import com.athena.tms.service.TaskEngine;
import io.cucumber.spring.CucumberContextConfiguration;
import io.cucumber.spring.ScenarioScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class BddSpringConfiguration {

    static {
        SharedContainers.createTopics("tms.task-events", "tms.push", "tms.push.DLT");
    }

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> postgres = SharedContainers.postgres();
        String url = SharedContainers.database("truth");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", SharedContainers.kafka()::getBootstrapServers);
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
        ScriptedRepairTicketNumbers scriptedRepairTicketNumbers(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
            return new ScriptedRepairTicketNumbers(jdbcTemplate);
        }

        @Bean
        @Primary
        RecordingPushSender recordingPushSender() {
            return new RecordingPushSender();
        }

        @Bean(destroyMethod = "close")
        KafkaGroupAwait kafkaGroupAwait() {
            return new KafkaGroupAwait(SharedContainers.kafka().getBootstrapServers());
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
                JwtService jwtService) {
            return new StaffFixtures(jdbcClient, tenantFixtures, roomFixtures, jwtService);
        }

        @Bean
        TaskFixtures taskFixtures(
                TaskEngine taskEngine,
                JdbcClient jdbcClient,
                TenantFixtures tenantFixtures,
                RoomFixtures roomFixtures,
                MutableClock mutableClock,
                ScriptedRepairTicketNumbers scriptedRepairTicketNumbers) {
            return new TaskFixtures(
                    taskEngine, jdbcClient, tenantFixtures, roomFixtures, mutableClock, scriptedRepairTicketNumbers);
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
        DeskContext deskContext() {
            return new DeskContext();
        }

        @Bean
        @ScenarioScope
        DeskApi deskApi(
                BddApiClient bddApiClient,
                ScenarioWorld scenarioWorld,
                DeskContext deskContext,
                DbProbe dbProbe,
                TenantFixtures tenantFixtures,
                RoomFixtures roomFixtures,
                ReservationFixtures reservationFixtures,
                MutableClock mutableClock,
                StaffFixtures staffFixtures) {
            return new DeskApi(
                    bddApiClient, scenarioWorld, deskContext, dbProbe,
                    tenantFixtures, roomFixtures, reservationFixtures, mutableClock, staffFixtures);
        }

        @Bean(destroyMethod = "removeAll")
        @ScenarioScope
        DeskCreateFaults deskCreateFaults(JdbcClient jdbcClient) {
            return new DeskCreateFaults(jdbcClient);
        }

        @Bean(destroyMethod = "close")
        @ScenarioScope
        StaffStreamClient staffStreamClient(
                Environment environment,
                ObjectMapper objectMapper,
                ScenarioWorld scenarioWorld,
                JobTrigger jobTrigger) {
            return new StaffStreamClient(environment, objectMapper, scenarioWorld, jobTrigger);
        }

        @Bean(destroyMethod = "restore")
        @ScenarioScope
        StreamFaults streamFaults(
                KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry,
                KafkaTemplate<String, String> kafkaTemplate,
                KafkaTopicsProperties kafkaTopicsProperties,
                KafkaGroupAwait kafkaGroupAwait,
                TenantFixtures tenantFixtures) {
            return new StreamFaults(
                    kafkaListenerEndpointRegistry, kafkaTemplate, kafkaTopicsProperties,
                    kafkaGroupAwait, tenantFixtures);
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
}
