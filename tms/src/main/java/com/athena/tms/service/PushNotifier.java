package com.athena.tms.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 「通知整個部門」類推播的共用組裝：新需求進待接池、住客撤回待接池需求。
 * 這兩種事件的收件人都是「該部門的在職成員」——收件人查詢共用
 * {@link PushOutboxWriter#activeDeptStaffIds}（其他未接提醒等排程事件也共用
 * 同一個查詢），之後改「誰收得到」的規則不會出現各事件各自為政。新需求
 * 另外要對每個收件人標示是否與該房同樓層（樓層以收件人本班排房 plan_rooms
 * 的負責樓層為準，口徑與待接池排序的「同樓層」一致）。
 *
 * <p>冪等鍵一律「事件:任務:收件人」，請求重試或掃描重放不會對同一個人重複插入。
 */
@Component
public class PushNotifier {

    private final JdbcTemplate jdbcTemplate;
    private final PushOutboxWriter pushOutboxWriter;
    private final ObjectMapper objectMapper;

    public PushNotifier(JdbcTemplate jdbcTemplate, PushOutboxWriter pushOutboxWriter, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.pushOutboxWriter = pushOutboxWriter;
        this.objectMapper = objectMapper;
    }

    /**
     * 新需求進待接池：推播該部門在職成員，標題「新需求 · {房號} {內容}」，
     * 與收件人同樓層時加註「（同樓層）」；去處為待接池並標亮這張單。
     */
    public void notifyPoolNewRequest(long tenantId, UUID taskId) {
        Map<String, Object> task = taskRow(tenantId, taskId);
        if (task == null) {
            return;
        }
        String dept = String.valueOf(task.get("dept"));
        String baseTitle = "新需求 · " + task.get("room_no") + " " + task.get("title");
        Integer roomFloor = task.get("floor") instanceof Number n ? n.intValue() : null;
        for (UUID recipient : pushOutboxWriter.activeDeptStaffIds(tenantId, dept)) {
            boolean sameFloor = roomFloor != null && staffFloors(tenantId, recipient).contains(roomFloor);
            ObjectNode payload = PushOutboxWriter.payload(
                    objectMapper, recipient, "pool_new_request", baseTitle + (sameFloor ? "（同樓層）" : ""));
            PushOutboxWriter.targetPool(payload, dept, taskId);
            pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload,
                    "pool_new_request:" + taskId + ":" + recipient);
        }
    }

    /** 住客撤回待接池中的需求：推播該部門在職成員，去處為待接池。 */
    public void notifyGuestWithdrew(long tenantId, UUID taskId) {
        Map<String, Object> task = taskRow(tenantId, taskId);
        if (task == null) {
            return;
        }
        String dept = String.valueOf(task.get("dept"));
        String title = task.get("room_no") + " " + task.get("title") + " 已由住客撤回";
        for (UUID recipient : pushOutboxWriter.activeDeptStaffIds(tenantId, dept)) {
            ObjectNode payload = PushOutboxWriter.payload(objectMapper, recipient, "guest_withdrew", title);
            PushOutboxWriter.targetPool(payload, dept);
            pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload,
                    "guest_withdrew:" + taskId + ":" + recipient);
        }
    }

    private Map<String, Object> taskRow(long tenantId, UUID taskId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT t.id, t.dept, t.title, r.room_no, r.floor
                  FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND t.id = ?
                """,
                tenantId, taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 收件人本班排房負責的樓層集合（plan_rooms 綁定房間的樓層）。 */
    private Set<Integer> staffFloors(long tenantId, UUID staffId) {
        return jdbcTemplate
                .queryForList(
                        """
                        SELECT DISTINCT pl.floor
                          FROM plan_rooms pr
                          JOIN rooms pl ON pl.tenant_id = pr.tenant_id AND pl.id = pr.room_id
                         WHERE pr.tenant_id = ? AND pr.staff_id = ?
                        """,
                        Integer.class, tenantId, staffId)
                .stream()
                .collect(Collectors.toSet());
    }
}
