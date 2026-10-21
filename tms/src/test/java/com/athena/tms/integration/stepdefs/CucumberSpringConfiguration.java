package com.athena.tms.integration.stepdefs;

import com.athena.tms.auth.AuthTestEndpointsConfiguration;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.GuestFixtures;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import com.athena.tms.integration.stepdefs.utils.TaskFixtures;
import com.athena.tms.service.TaskEngine;
import io.cucumber.spring.CucumberContextConfiguration;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
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
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Cucumber 的 Spring context 擁有者（glue 內唯一的 @CucumberContextConfiguration）。
 *
 * <p>容器用 static 手動啟動而不是 @Testcontainers/@Container：Cucumber engine
 * 不跑 JUnit Jupiter 的 extension，@Container 的生命週期掛不上；static 區塊
 * 在類別載入（= context 建立前）執行一次，整個 Suite 共用，收尾交給 Ryuk。
 *
 * <p>接線同 AuthSecurityIT 的雙身分款：Flyway 走容器管理身分（建 extension／
 * 角色／RLS policy 需要），datasource 走 V1 建的最小權限角色 tms_app——RLS
 * 生效，測試斷言拿到的就是正式部署的隔離行為。
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// 角色矩陣逐格測試與「session 驗過即綁上下文」斷言需要的探針端點。
@Import(AuthTestEndpointsConfiguration.class)
public class CucumberSpringConfiguration {

    @SuppressWarnings("resource")
    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @SuppressWarnings("resource")
    public static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

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
        // SSE 場景（14-sse-stream）要快：心跳縮到 1s（正式預設 15s）；degraded
        // 門檻 5s，靠「造舊時間戳」觸發而不是真等。心跳只廣播給開著的 SSE 連線，
        // 對其他 feature 無副作用
        registry.add("tms.stream.heartbeat-interval", () -> "1s");
        registry.add("tms.stream.degraded-threshold", () -> "5s");
        // SSE 消費者先起或後起都不能漏事件：從頭讀，去重與過濾由被測程式自己扛
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    /** topic 顯式建，不賭 broker auto-create 預設（同 TaskEngineOutboxIT 的做法）。 */
    private static void createTopics() {
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic("tms.task-events", 1, (short) 1),
                    new NewTopic("tms.push", 1, (short) 1))).all().get();
        } catch (Exception e) {
            throw new IllegalStateException("建立 Kafka topic 失敗", e);
        }
    }

    /**
     * harness 專用 bean 一律在這裡用 @Bean 註冊、不加 @Component——
     * integration.stepdefs 在 TmsApplication 的 component scan 範圍內，
     * 加了 stereotype 會被掃進所有其他 IT 的 context。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class HarnessConfig {

        /**
         * 固定時鐘：feature 檔與 V2 種子的住期／session 到期日都是寫死的日曆
         * 日期（2026-10-18 ～ 10-20），用真實時鐘的話整套測試會在 2026-10-20
         * 中午後全面過期。釘在 2026-10-19T12:00Z——res-305／b001 住期內、
         * sess-expired 與 b002/b003 已過期——讓時間相關斷言不隨跑測試的
         * 日期漂移。
         */
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
            return new DbSupport(appJdbc,
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
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
