package com.athena.tms.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.PostgresSpringTest;
import com.athena.tms.controller.TestClockController;
import com.athena.tms.controller.TestSupportController;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

class TestClockDisabledTest extends PostgresSpringTest {

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;

    @Test
    void 時鐘是系統時鐘_撥動端點不存在() throws Exception {
        assertThat(context.getBean("clock", Clock.class)).isNotInstanceOf(AdjustableClock.class);
        assertThat(context.getBeansOfType(AdjustableClock.class)).isEmpty();
        assertThat(context.getBeansOfType(TestClockController.class)).isEmpty();
        assertThat(context.getBeansOfType(TestSupportController.class)).isEmpty();

        HttpClient http = HttpClient.newHttpClient();
        URI uri = URI.create("http://127.0.0.1:" + port + "/api/v1/test/clock");
        HttpResponse<String> post = http.send(HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"isoTime\":\"2026-11-11T18:00:00+08:00\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(post.statusCode()).isEqualTo(401);
        HttpResponse<String> delete = http.send(HttpRequest.newBuilder(uri).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(delete.statusCode()).isEqualTo(401);
    }
}
