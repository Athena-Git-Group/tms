package com.athena.tms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles(value={"test"})
@Testcontainers
class TmsApplicationTests {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    TmsApplicationTests() {
    }

    @DynamicPropertySource
    static void wireContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", () -> ((PostgreSQLContainer)POSTGRES).getJdbcUrl());
        registry.add("spring.flyway.user", () -> ((PostgreSQLContainer)POSTGRES).getUsername());
        registry.add("spring.flyway.password", () -> ((PostgreSQLContainer)POSTGRES).getPassword());
        registry.add("spring.datasource.url", () -> ((PostgreSQLContainer)POSTGRES).getJdbcUrl());
        registry.add("spring.datasource.username", () -> "tms_app");
        registry.add("spring.datasource.password", () -> "tms_app");
    }

    @Test
    void contextLoads() {
    }
}
