package com.athena.tms.repair;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.service.TaskEngine;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
class RepairTicketOpenerTest {

    private static final long TENANT_ID = 1L;
    private static final UUID ROOM_502 = UUID.fromString("00000000-0000-7000-8000-000000000502");
    private static final UUID STAFF_FD1 = UUID.fromString("00000000-0000-7000-8000-00000000a004");
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
    TaskEngine taskEngine;

    @Autowired
    RepairTicketOpener repairTicketOpener;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void bindTenant() {
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void unbindTenant() {
        TenantContext.clear();
    }

    @Test
    void openingTheSameTaskTwiceKeepsOneTicketAndOneNumber() {
        RepairTicketDraft draft = new RepairTicketDraft("other", null, STAFF_FD1, null, null);
        UUID taskId = taskEngine.create(new TaskCreateCommand(
                TENANT_ID, "repair", "desk", ROOM_502, null, "engineering", "pool", null,
                "房間有怪聲", null, null, null, null, Actor.system(), false, draft)).getId();
        Integer counterAfterFirst = lastTicketNo();

        String again = repairTicketOpener.open(TENANT_ID, taskId, draft);

        assertThat(jdbcTemplate.queryForList(
                "SELECT ticket_no FROM repair_tickets WHERE tenant_id = ? AND task_id = ?", String.class, TENANT_ID, taskId))
                .containsExactly(again);
        assertThat(lastTicketNo()).isEqualTo(counterAfterFirst);
    }

    private Integer lastTicketNo() {
        return jdbcTemplate.queryForObject(
                "SELECT last_no FROM repair_ticket_counters WHERE tenant_id = ?", Integer.class, TENANT_ID);
    }
}
