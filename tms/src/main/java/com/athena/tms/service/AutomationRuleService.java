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

/**
 * 自動化規則引擎：訂房屬性或每日排程命中時自動開準備工單，並保證同一規則同一天
 * 對同一對象最多建一張單。
 *
 * <p>建單走三步：先查冪等鍵（訂房屬性規則＝房號｜規則名｜當地日期，排程規則＝
 * 規則名｜當地日期）已存在就跳過；再經 {@link TaskEngine#create} 建單，讓 task_events
 * 留下與人工開單相同的歷程；最後補上規則欄位（rule_hit、due_at、rule_idem_key）。
 * 查與建之間若有並發重複，{@code uq_tasks_rule_idem_key} 唯一索引會擋下第二筆，
 * 不會留下兩張同鍵的工單。
 *
 * <p>指派規則：該房今天已有打掃負責人（今天有一筆已指派的打掃任務）時，準備工單
 * 直接以 accepted 派給他，不經待接池；否則進房務待接池（state=pool、pool_source=system）。
 * 不這樣做的話，有專人負責的房間的迎賓準備會被不熟該房的人搶走。
 *
 * <p>出廠內建四條訂房屬性規則（VIP、親子、寵物、紀念日）。租戶完全沒有任何規則列時
 * 視為出廠狀態，評估或管理規則前先補齊這四條；已經有任何規則（含主管自訂或刪改過）
 * 就不再補，避免把主管刪掉的內建規則偷偷復活。
 *
 * <p>刪除或停用規則只讓它之後不再觸發，已由該規則建立的任務一律不回收：
 * 單子可能已經有人接手甚至做到一半，回收會把現場正在進行的工作無聲抽走。
 */
@Service
public class AutomationRuleService {

    /** 排程規則沒有房號時使用的佔位房號，樓層固定 0，不屬於任何客房。 */
    public static final String PUBLIC_AREA_ROOM_NO = "公共區域";

    private record FactoryRule(String attribute, String name) {
    }

    /** 一次規則建單需要的欄位，訂房屬性與每日排程兩條觸發路徑共用同一組。 */
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

    /**
     * 套用一筆今日房態：拿該房訂房屬性比對啟用中的訂房屬性規則，每命中一條建一張
     * 準備工單。PMS 批次拉取與 CSV 匯入共用這個入口，因此兩條路徑天然共用同一套
     * 比對與冪等鍵，重複匯入不會多開單。
     */
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

    /**
     * 掃描每日排程規則：時點已到而今天還沒建單的規則，各建一張進待接池的工單。
     * 判準是「時點已到」而不是「時點剛好等於現在」，所以系統停機錯過時點後，
     * 恢復時的下一輪掃描會自動補建；冪等鍵只含規則名與日期，補建也只會補出
     * 當日那一張。
     */
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
                // 時點解析不出來就跳過這條，寧可漏建也不要在猜錯的時間亂建單。
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
            // 進池的準備工單沒有指定的負責人，部門成員不會主動盯著池看，
            // 比照住客新需求推播部門在職成員，才有人知道要接。
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

    /**
     * 查該房今天的打掃負責人：今天有一筆已指派、未取消的打掃任務時，那位員工
     * 就是負責人；同房多筆時取最早建立的那筆，讓重複評估得到穩定的同一個人。
     */
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

    /** 公共區域佔位房不存在就建一間，排程規則的工單需要一個掛單的位置。 */
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
