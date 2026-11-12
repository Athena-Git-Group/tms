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
