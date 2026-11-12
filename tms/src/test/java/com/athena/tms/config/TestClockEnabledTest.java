package com.athena.tms.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 開了 {@code tms.test-clock.enabled=true} 的後端，要能從 HTTP 撥動全系統共用的那一個
 * {@link Clock}：撥了之後注入給各服務的時鐘就停在該時刻，回復之後又跟著系統時間走。
 * 不帶權杖也要打得到，因為測試工具在建立任何員工之前就要先把時間撥好。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "tms.test-clock.enabled=true")
@ActiveProfiles("test")
@Testcontainers
class TestClockEnabledTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void wireContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "tms_app");
        registry.add("spring.datasource.password", () -> "tms_app");
    }

    @LocalServerPort
    int port;

    @Autowired
    Clock clock;

    @AfterEach
    void backToSystemClock() throws Exception {
        call("DELETE", null);
    }

    @Test
    void 撥動後注入的時鐘固定在該時刻_回復後跟著系統時間走() throws Exception {
        assertThat(clock).isInstanceOf(AdjustableClock.class);

        HttpResponse<String> set = call("POST", "{\"isoTime\":\"2026-11-11T18:00:00+08:00\"}");
        assertThat(set.statusCode()).isEqualTo(200);
        assertThat(set.body()).contains("\"fixed\":true");
        Instant expected = Instant.parse("2026-11-11T10:00:00Z");
        assertThat(clock.instant()).isEqualTo(expected);
        Thread.sleep(20);
        assertThat(clock.instant()).as("固定語意：時間不會自己往前走").isEqualTo(expected);

        HttpResponse<String> reset = call("DELETE", null);
        assertThat(reset.statusCode()).isEqualTo(200);
        assertThat(reset.body()).contains("\"fixed\":false");
        assertThat(Duration.between(clock.instant(), Instant.now()).abs()).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void 時刻格式不對回422() throws Exception {
        HttpResponse<String> response = call("POST", "{\"isoTime\":\"18:00\"}");
        assertThat(response.statusCode()).isEqualTo(422);
    }

    private HttpResponse<String> call(String method, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/test/clock"));
        if (json == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
