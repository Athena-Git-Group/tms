package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.PostgresSpringTest;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.GuestRequestCreate;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class GuestRequestRoutingTest extends PostgresSpringTest {

    private static final long TENANT_ID = 1L;
    private static final UUID ROOM_305 = UUID.fromString("00000000-0000-7000-8000-000000000305");
    private static final UUID RESERVATION_B001 = UUID.fromString("00000000-0000-7000-8000-00000000b001");
    private static final Instant BEFORE_RATE_LIMIT_CASES = Instant.parse("2026-10-19T00:00:00Z");

    @Autowired
    GuestRequestService guestRequestService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void bindGuest() {
        TenantContext.set(TENANT_ID);
        CurrentActor.set(CurrentActor.guest(TENANT_ID, UUID.randomUUID(), ROOM_305, RESERVATION_B001));
        // 同房同訂單的住客需求會佔 RoomRequestRateLimiterTest 的名額，放在它所有時段之前。
        clock.setInstant(BEFORE_RATE_LIMIT_CASES);
    }

    @AfterEach
    void unbind() {
        clock.reset();
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
