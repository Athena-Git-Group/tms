package com.athena.tms.bdd;

import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.HandoverFixtures;
import com.athena.tms.bdd.support.JobTrigger;
import com.athena.tms.bdd.support.KafkaGroupAwait;
import com.athena.tms.bdd.support.RecordingPushSender;
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

/**
 * truth suite（glue com.athena.tms.bdd）的 Spring 環境：
 * 整個應用程式以 RANDOM_PORT 啟動，PostgreSQL 16 與 Kafka 用 Testcontainers，
 * Flyway 照常對容器執行。只開環境，不含任何 stepdef。
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class BddSpringConfiguration {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // Boot 4.1 沒有對 org.testcontainers.kafka.KafkaContainer 的 ConnectionDetails factory，改走 DynamicPropertySource
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

        /**
         * 查「權威狀態」只讀資料庫、不帶任何 scenario 專屬狀態，所以用單例即可，
         * 不用像 ScenarioWorld 那樣每個 scenario 各開一份。
         */
        @Bean
        DbProbe dbProbe(JdbcClient jdbcClient) {
            return new DbProbe(jdbcClient);
        }

        /** 同 dbProbe：輪詢邏輯本身無狀態，調整逾時走 {@code withTimeout}/{@code withPollInterval} 回傳的新實例即可。 */
        @Bean
        OutboxAwait outboxAwait(DbProbe dbProbe, ObjectMapper objectMapper) {
            return new OutboxAwait(dbProbe, objectMapper);
        }

        /**
         * 可調測試時鐘，單例、蓋過 {@code ClockConfig} 的 {@code Clock.systemDefaultZone()}。
         * 預設固定在 {@link MutableClock#DEFAULT_TEST_DATE} 中午（{@link MutableClock#DEFAULT_ZONE}）；
         * 情境要操控時間就直接注入 {@code MutableClock}（而不是 {@code Clock}）呼叫
         * {@code setInstant}/{@code setTodayAt}/{@code advanceMinutes}。
         */
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }

        /**
         * 推播服務替身，蓋過 {@code PushSenderConfig} 依設定選出來的實作（測試不設 FCM 憑證，
         * 那邊會是只寫日誌的版本）。單例：消費者執行緒與情境執行緒共用同一份紀錄。
         */
        @Bean
        @Primary
        RecordingPushSender recordingPushSender() {
            return new RecordingPushSender();
        }

        @Bean(destroyMethod = "close")
        KafkaGroupAwait kafkaGroupAwait() {
            return new KafkaGroupAwait(KAFKA.getBootstrapServers());
        }

        /** 依名稱同步觸發排程 job（不用等 {@code @Scheduled} 的真實間隔），見 {@link JobTrigger}。 */
        @Bean
        JobTrigger jobTrigger(ApplicationContext applicationContext) {
            return new JobTrigger(applicationContext);
        }

        /**
         * 以下是測試資料入口（見 com.athena.tms.bdd.support 各類別 Javadoc）。單例即可：
         * 每個方法本身無狀態，建出來的資料才是情境各自的狀態。
         */
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

        /**
         * 需要 {@link ScenarioWorld} 登記建出的任務 id，而 world 是 scenario 一份的
         * 代理 bean，所以 PmsProbe 也跟著掛 {@code @ScenarioScope}，避免 singleton
         * 抓住第一個 scenario 的 world 不放。
         */
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
