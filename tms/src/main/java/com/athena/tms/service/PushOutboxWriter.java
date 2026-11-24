package com.athena.tms.service;

import com.athena.tms.common.UuidV7;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class PushOutboxWriter {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final OutboxWriter outboxWriter;

    public PushOutboxWriter(JdbcTemplate jdbcTemplate, Clock clock, OutboxWriter outboxWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.outboxWriter = outboxWriter;
    }

    public void enqueue(long tenantId, String aggregateKey, ObjectNode payload, String idempotencyKey) {
        boolean inserted = outboxWriter.enqueueIfAbsent(
                tenantId, "push", aggregateKey, payload.toString(), idempotencyKey,
                OffsetDateTime.now(clock));
        if (inserted) {
            writeNotification(tenantId, payload);
        }
    }

    private void writeNotification(long tenantId, ObjectNode payload) {
        UUID recipientStaffId = UUID.fromString(payload.get("recipientStaffId").asText());
        String event = payload.get("event").asText();
        String title = payload.get("title").asText();
        String body = payload.hasNonNull("body") ? payload.get("body").asText() : null;
        String targetJson = payload.get("target").toString();
        jdbcTemplate.update(
                """
                INSERT INTO notifications
                    (id, tenant_id, recipient_staff_id, event, title, body, target, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                UuidV7.generate(), tenantId, recipientStaffId, event, title, body, targetJson,
                OffsetDateTime.now(clock));
    }

    public static ObjectNode payload(ObjectMapper objectMapper, UUID recipientStaffId, String event, String title) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("recipientStaffId", recipientStaffId.toString());
        payload.put("event", event);
        payload.put("title", title);
        return payload;
    }

    public static void targetOwnedTask(ObjectNode payload, String taskType, UUID taskId) {
        ObjectNode target = payload.putObject("target");
        target.put("kind", "repair".equals(taskType) ? "ticket" : "task");
        target.put("taskId", taskId.toString());
    }

    public static void targetPool(ObjectNode payload, String dept) {
        ObjectNode target = payload.putObject("target");
        target.put("kind", "pool");
        target.put("dept", dept);
    }

    public static void targetPool(ObjectNode payload, String dept, UUID highlightTaskId) {
        ObjectNode target = payload.putObject("target");
        target.put("kind", "pool");
        target.put("dept", dept);
        target.put("highlightTaskId", highlightTaskId.toString());
    }

    public static void targetHome(ObjectNode payload) {
        payload.putObject("target").put("kind", "home");
    }

    public List<UUID> activeManagerIds(long tenantId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM staff_accounts WHERE tenant_id = ? AND role = 'manager' AND active = true",
                UUID.class, tenantId);
    }

    public List<UUID> activeDeptStaffIds(long tenantId, String dept) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND active = true",
                UUID.class, tenantId, dept);
    }
}
