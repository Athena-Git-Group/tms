package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.PostgresSpringTest;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.entity.Task;
import com.athena.tms.repair.RepairTicketDraft;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TaskEngineRowWriteTest extends PostgresSpringTest {

    private static final long TENANT_ID = 1L;
    private static final UUID ROOM_502 = UUID.fromString("00000000-0000-7000-8000-000000000502");
    private static final UUID STAFF_FD1 = UUID.fromString("00000000-0000-7000-8000-00000000a004");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-19T04:00:00Z");

    @Autowired
    TaskEngine taskEngine;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void bindTenant() {
        TenantContext.set(TENANT_ID);
        clock.setInstant(FIXED_NOW);
    }

    @AfterEach
    void unbindTenant() {
        TenantContext.clear();
        clock.reset();
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

    private static TaskCreateCommand command(String initialState, boolean urgent) {
        return new TaskCreateCommand(
                TENANT_ID, "repair", "desk", ROOM_502, null, "engineering", initialState, null,
                "水龍頭漏水", null, null, null, null, Actor.system(), urgent,
                new RepairTicketDraft("other", null, STAFF_FD1, null, null));
    }

    private Map<String, Object> taskRow(UUID taskId) {
        return jdbcTemplate.queryForMap(
                "SELECT urgent, created_at, updated_at FROM tasks WHERE tenant_id = ? AND id = ?",
                TENANT_ID, taskId);
    }
}
