package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.GuestRequestCreate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class GuestRequestRoutingTest {

    private static final long TENANT_ID = 1L;
    private static final UUID ROOM_305 = UUID.fromString("00000000-0000-7000-8000-000000000305");
    private static final UUID RESERVATION_B001 = UUID.fromString("00000000-0000-7000-8000-00000000b001");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-19T04:00:00Z");

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

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneId.of("Asia/Taipei"));
        }
    }

    @Autowired
    GuestRequestService guestRequestService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void bindGuest() {
        TenantContext.set(TENANT_ID);
        CurrentActor.set(CurrentActor.guest(TENANT_ID, UUID.randomUUID(), ROOM_305, RESERVATION_B001));
    }

    @AfterEach
    void unbind() {
        CurrentActor.clear();
        TenantContext.clear();
    }

    @Test
    void textMatchingAHousekeepingRepairRuleWaitsForTheDeskWithATaskNumber() {
        jdbcTemplate.update(
                "UPDATE tenant_config SET value = CAST(? AS jsonb) WHERE tenant_id = ? AND key = 'classification_rules'",
                "[{\"rule_id\": \"r-lamp\", \"keywords\": [\"燈\"], \"dept\": \"housekeeping\", \"type\": \"repair\"}]",
                TENANT_ID);

        UUID taskId = guestRequestService.create(new GuestRequestCreate(null, null, "床頭燈不亮", "zh-TW")).id();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT state, dept, type, task_no FROM tasks WHERE tenant_id = ? AND id = ?", TENANT_ID, taskId);
        assertThat(row.get("state")).isEqualTo("triage");
        assertThat(row.get("dept")).isEqualTo("frontdesk");
        assertThat(row.get("type")).isEqualTo("other");
        assertThat(String.valueOf(row.get("task_no"))).matches("T-\\d{4,}");
    }
}
