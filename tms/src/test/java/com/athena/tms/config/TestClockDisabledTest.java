package com.athena.tms.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.controller.TestClockController;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 沒帶 {@code tms.test-clock.enabled} 的後端（正式環境就是這樣）：時鐘是系統時鐘、
 * 可撥動的時鐘與撥動端點都不存在，打撥動路徑一律 404。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class TestClockDisabledTest {

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

    @Autowired
    ApplicationContext context;

    @Test
    void 時鐘是系統時鐘_撥動端點不存在() throws Exception {
        assertThat(clock).isNotInstanceOf(AdjustableClock.class);
        assertThat(context.getBeansOfType(AdjustableClock.class)).isEmpty();
        assertThat(context.getBeansOfType(TestClockController.class)).isEmpty();

        HttpClient http = HttpClient.newHttpClient();
        URI uri = URI.create("http://127.0.0.1:" + port + "/api/v1/test/clock");
        HttpResponse<String> post = http.send(HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"isoTime\":\"2026-11-11T18:00:00+08:00\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(post.statusCode()).isEqualTo(404);
        HttpResponse<String> delete = http.send(HttpRequest.newBuilder(uri).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(delete.statusCode()).isEqualTo(404);
    }
}
