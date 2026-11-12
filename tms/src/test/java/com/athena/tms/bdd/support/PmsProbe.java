package com.athena.tms.bdd.support;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.service.AutomationRuleService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PmsProbe {

    private final OutboxAwait outboxAwait;
    private final DbProbe dbProbe;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final AutomationRuleService automationRuleService;
    private final ScenarioWorld world;
    private final MutableClock clock;

    public PmsProbe(
            OutboxAwait outboxAwait,
            DbProbe dbProbe,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            AutomationRuleService automationRuleService,
            ScenarioWorld world,
            MutableClock clock) {
        this.outboxAwait = outboxAwait;
        this.dbProbe = dbProbe;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.automationRuleService = automationRuleService;
        this.world = world;
        this.clock = clock;
    }

    public void pushTodayRoomState(String roomNo, List<String> attrs, LocalDate checkinDate) {
        long tenantId = tenantFixtures.testTenantId();
        automationRuleService.applyTodayRoomState(tenantId, roomNo, attrs, checkinDate);

        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        Optional<Map<String, Object>> created = dbProbe.queryOne(
                """
                SELECT t.id FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND r.room_no = ? AND t.rule_hit IS NOT NULL
                   AND t.created_at >= ? AND t.created_at < ?
                 ORDER BY t.created_at DESC
                 LIMIT 1
                """,
                tenantId, roomNo,
                today.atStartOfDay(clock.getZone()).toOffsetDateTime(),
                today.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime());
        created.ifPresent(row -> world.rememberTask(roomNo, String.valueOf(row.get("id"))));
    }

    public Map<String, Object> lastWritebackFor(String roomNo) {
        return dbProbe
                .queryOne(
                        """
                        SELECT payload ->> 'roomNo' AS room_no, payload ->> 'status' AS status
                          FROM outbox
                         WHERE tenant_id = ? AND kind = 'pms_status' AND payload ->> 'roomNo' = ?
                         ORDER BY id DESC
                         LIMIT 1
                        """,
                        tenantFixtures.testTenantId(), roomNo)
                .orElse(Map.of());
    }
}
