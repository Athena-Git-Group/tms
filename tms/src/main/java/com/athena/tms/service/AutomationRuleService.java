package com.athena.tms.service;

import com.athena.tms.common.UuidV7;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.entity.Task;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationRuleService {

    public static final String PUBLIC_AREA_ROOM_NO = "公共區域";

    public static boolean isPublicAreaRoomNo(Object roomNo) {
        return PUBLIC_AREA_ROOM_NO.equals(roomNo == null ? null : String.valueOf(roomNo));
    }

    private record FactoryRule(String attribute, String name) {
    }

    private record RuleTask(
            UUID roomId, String name, String category, Integer dueAfterMinutes, String idemKey, UUID assigneeId) {
    }

    private static final List<FactoryRule> FACTORY_RULES = List.of(
            new FactoryRule("VIP", "VIP 迎賓準備"),
            new FactoryRule("親子", "嬰兒床佈置"),
            new FactoryRule("寵物", "寵物墊準備"),
            new FactoryRule("紀念日", "紀念日佈置"));

    private final JdbcTemplate jdbcTemplate;
    private final TaskEngine taskEngine;
    private final PushNotifier pushNotifier;
    private final Clock clock;

    public AutomationRuleService(
            JdbcTemplate jdbcTemplate, TaskEngine taskEngine, PushNotifier pushNotifier, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskEngine = taskEngine;
        this.pushNotifier = pushNotifier;
        this.clock = clock;
    }

    @Transactional
    public void applyTodayRoomState(long tenantId, String roomNo, List<String> attributes, LocalDate checkinDate) {
        seedFactoryRulesIfFactoryNew(tenantId);
        UUID roomId = requireRoomId(tenantId, roomNo);
        LocalDate today = today();
        UUID assigneeId = findTodayCleaningAssignee(tenantId, roomId, today);

        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                """
                SELECT name, condition, category, due_after_minutes
                  FROM automation_rules
                 WHERE tenant_id = ? AND enabled AND trigger_source = 'reservation'
                """,
                tenantId);
        for (Map<String, Object> rule : rules) {
            if (!attributes.contains(String.valueOf(rule.get("condition")))) {
                continue;
            }
            String name = String.valueOf(rule.get("name"));
            createRuleTaskOnce(
                    tenantId,
                    new RuleTask(
                            roomId,
                            name,
                            String.valueOf(rule.get("category")),
                            (Integer) rule.get("due_after_minutes"),
                            roomNo + "|" + name + "|" + today,
                            assigneeId),
                    today);
        }
    }

    @Transactional
    public void scanSchedules(long tenantId) {
        seedFactoryRulesIfFactoryNew(tenantId);
        LocalTime now = LocalTime.ofInstant(clock.instant(), clock.getZone());
        LocalDate today = today();

        List<Map<String, Object>> rules = jdbcTemplate.queryForList(
                """
                SELECT name, condition, category, room_no, due_after_minutes
                  FROM automation_rules
                 WHERE tenant_id = ? AND enabled AND trigger_source = 'schedule'
                """,
                tenantId);
        for (Map<String, Object> rule : rules) {
            LocalTime at;
            try {
                at = LocalTime.parse(String.valueOf(rule.get("condition")));
            } catch (DateTimeParseException e) {
                continue;
            }
            if (now.isBefore(at)) {
                continue;
            }
            String ruleRoomNo = (String) rule.get("room_no");
            UUID roomId = ruleRoomNo == null
                    ? ensurePublicAreaRoom(tenantId)
                    : findRoomId(tenantId, ruleRoomNo);
            if (roomId == null) {
                continue;
            }
            String name = String.valueOf(rule.get("name"));
            createRuleTaskOnce(
                    tenantId,
                    new RuleTask(
                            roomId,
                            name,
                            String.valueOf(rule.get("category")),
                            (Integer) rule.get("due_after_minutes"),
                            name + "|" + today,
                            null),
                    today);
        }
    }

    @Transactional
    public void deleteRule(long tenantId, String ruleName) {
        seedFactoryRulesIfFactoryNew(tenantId);
        jdbcTemplate.update(
                "DELETE FROM automation_rules WHERE tenant_id = ? AND name = ?", tenantId, ruleName);
    }

    @Transactional
    public void disableRule(long tenantId, String ruleName) {
        seedFactoryRulesIfFactoryNew(tenantId);
        jdbcTemplate.update(
                """
                UPDATE automation_rules SET enabled = false, updated_at = ?
                 WHERE tenant_id = ? AND name = ?
                """,
                OffsetDateTime.now(clock), tenantId, ruleName);
    }

    private void createRuleTaskOnce(long tenantId, RuleTask ruleTask, LocalDate today) {
        boolean exists = !jdbcTemplate
                .queryForList(
                        "SELECT 1 FROM tasks WHERE tenant_id = ? AND rule_idem_key = ?",
                        tenantId, ruleTask.idemKey())
                .isEmpty();
        if (exists) {
            return;
        }

        String initialState = ruleTask.assigneeId() != null ? "accepted" : "pool";
        Task task = taskEngine.create(new TaskCreateCommand(
                tenantId,
                ruleTask.category(),
                "system",
                ruleTask.roomId(),
                null,
                "housekeeping",
                initialState,
                ruleTask.assigneeId(),
                ruleTask.name(),
                null,
                null,
                null,
                "cleaning".equals(ruleTask.category()) ? today : null,
                Actor.system()));

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime dueAt = ruleTask.dueAfterMinutes() == null ? null : now.plusMinutes(ruleTask.dueAfterMinutes());
        jdbcTemplate.update(
                """
                UPDATE tasks
                   SET rule_hit = ?, rule_idem_key = ?, due_at = ?,
                       pool_source = CASE WHEN state = 'pool' THEN 'system' ELSE pool_source END,
                       updated_at = ?
                 WHERE tenant_id = ? AND id = ?
                """,
                ruleTask.name(), ruleTask.idemKey(), dueAt, now, tenantId, task.getId());

        if ("pool".equals(initialState)) {
            this.pushNotifier.notifyPoolNewRequest(tenantId, task.getId());
        }
    }

    private void seedFactoryRulesIfFactoryNew(long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM automation_rules WHERE tenant_id = ?", Long.class, tenantId);
        if (count != null && count > 0) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (FactoryRule rule : FACTORY_RULES) {
            jdbcTemplate.update(
                    """
                    INSERT INTO automation_rules
                        (id, tenant_id, name, trigger_source, condition, category,
                         room_no, due_after_minutes, enabled, created_at, updated_at)
                    VALUES (?, ?, ?, 'reservation', ?, 'amenity', NULL, NULL, true, ?, ?)
                    ON CONFLICT (tenant_id, name) DO NOTHING
                    """,
                    UuidV7.generate(), tenantId, rule.name(), rule.attribute(), now, now);
        }
    }

    private UUID findTodayCleaningAssignee(long tenantId, UUID roomId, LocalDate today) {
        List<UUID> assignees = jdbcTemplate.query(
                """
                SELECT assignee_id FROM tasks
                 WHERE tenant_id = ? AND room_id = ? AND type = 'cleaning' AND work_date = ?
                   AND assignee_id IS NOT NULL AND state <> 'cancelled'
                 ORDER BY created_at
                 LIMIT 1
                """,
                (rs, i) -> rs.getObject(1, UUID.class),
                tenantId, roomId, today);
        return assignees.isEmpty() ? null : assignees.getFirst();
    }

    private UUID requireRoomId(long tenantId, String roomNo) {
        UUID roomId = findRoomId(tenantId, roomNo);
        if (roomId == null) {
            throw new IllegalStateException("房號「" + roomNo + "」不存在，無法套用今日房態");
        }
        return roomId;
    }

    private UUID findRoomId(long tenantId, String roomNo) {
        List<UUID> ids = jdbcTemplate.query(
                "SELECT id FROM rooms WHERE tenant_id = ? AND room_no = ?",
                (rs, i) -> rs.getObject(1, UUID.class),
                tenantId, roomNo);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private UUID ensurePublicAreaRoom(long tenantId) {
        UUID existing = findRoomId(tenantId, PUBLIC_AREA_ROOM_NO);
        if (existing != null) {
            return existing;
        }
        UUID id = UuidV7.generate();
        jdbcTemplate.update(
                """
                INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state, occupancy, updated_at)
                VALUES (?, ?, ?, 0, 'public_area', 'cleaned', 'vacant', ?)
                ON CONFLICT (tenant_id, room_no) DO NOTHING
                """,
                id, tenantId, PUBLIC_AREA_ROOM_NO, OffsetDateTime.now(clock));
        UUID after = findRoomId(tenantId, PUBLIC_AREA_ROOM_NO);
        return after != null ? after : id;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), clock.getZone());
    }
}
