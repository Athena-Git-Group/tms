package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.entity.Task;
import java.sql.Timestamp;
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
class TaskEngineRowWriteTest {

    private static final long TENANT_ID = 1L;
    private static final UUID ROOM_301 = UUID.fromString("00000000-0000-7000-8000-000000000301");
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
    void createdAtComesFromInjectedClock() {
        Task task = taskEngine.create(command("pool", false));

        Map<String, Object> row = taskRow(task.getId());
        assertThat(((Timestamp) row.get("created_at")).toInstant()).isEqualTo(FIXED_NOW);
        assertThat(((Timestamp) row.get("updated_at")).toInstant()).isEqualTo(FIXED_NOW);
    }

    @Test
    void urgentFlagIsWrittenByCreate() {
        Task urgent = taskEngine.create(command("pool", true));
        Task plain = taskEngine.create(command("pool", false));

        assertThat(taskRow(urgent.getId()).get("urgent")).isEqualTo(true);
        assertThat(taskRow(plain.getId()).get("urgent")).isEqualTo(false);
    }

    @Test
    void cancelPersistsReasonOnTaskRow() {
        Task task = taskEngine.create(command("pool", false));

        taskEngine.cancel(TENANT_ID, task.getId(), "manager_void", Actor.staff("manager", UUID.randomUUID()));

        Map<String, Object> row = taskRow(task.getId());
        assertThat(row.get("state")).isEqualTo("cancelled");
        assertThat(row.get("cancel_reason")).isEqualTo("manager_void");
    }

    @Test
    void guestWithdrawalPersistsReasonOnTaskRow() {
        Task task = taskEngine.create(command("pool", false));

        taskEngine.cancelByGuest(TENANT_ID, task.getId());

        assertThat(taskRow(task.getId()).get("cancel_reason")).isEqualTo("guest_withdrew");
    }

    private static TaskCreateCommand command(String initialState, boolean urgent) {
        return new TaskCreateCommand(
                TENANT_ID, "repair", "desk", ROOM_301, null, "engineering", initialState, null,
                "水龍頭漏水", null, null, null, null, Actor.system(), urgent);
    }

    private Map<String, Object> taskRow(UUID taskId) {
        return jdbcTemplate.queryForMap(
                "SELECT state, urgent, cancel_reason, created_at, updated_at FROM tasks WHERE tenant_id = ? AND id = ?",
                TENANT_ID, taskId);
    }
}
