package com.athena.tms.repair;

import com.athena.tms.service.PushOutboxWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class RepairTicketOpener {

    private static final DateTimeFormatter CHECKIN_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final PushOutboxWriter pushOutboxWriter;
    private final ObjectMapper objectMapper;

    public RepairTicketOpener(
            JdbcTemplate jdbcTemplate, Clock clock, PushOutboxWriter pushOutboxWriter, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.pushOutboxWriter = pushOutboxWriter;
        this.objectMapper = objectMapper;
    }

    public String open(long tenantId, UUID taskId, RepairTicketDraft draft) {
        List<String> existing = jdbcTemplate.queryForList(
                "SELECT ticket_no FROM repair_tickets WHERE tenant_id = ? AND task_id = ?",
                String.class, tenantId, taskId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        Map<String, Object> task = jdbcTemplate.queryForMap(
                """
                SELECT t.room_id, r.room_no
                  FROM tasks t JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND t.id = ?
                """,
                tenantId, taskId);
        UUID roomId = (UUID) task.get("room_id");
        // 追蹤單接續原單的處理，不是新的報修，所以不做急件判定。
        String urgentRule = draft.originTicketTaskId() == null
                ? (String) judgeUrgent(tenantId, roomId).get("rule")
                : null;
        boolean urgent = urgentRule != null;
        String ticketNo = nextTicketNo(tenantId);
        jdbcTemplate.update(
                """
                INSERT INTO repair_tickets
                    (task_id, tenant_id, ticket_no, room_id, category, description, reporter_id,
                     source_task_id, origin_ticket_task_id, created_at, is_urgent, urgent_rule)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                taskId, tenantId, ticketNo, roomId, draft.category(), draft.description(), draft.reporterId(),
                draft.sourceTaskId(), draft.originTicketTaskId(), OffsetDateTime.now(clock), urgent, urgentRule);
        if (urgent) {
            jdbcTemplate.update("UPDATE tasks SET urgent = true WHERE tenant_id = ? AND id = ?", tenantId, taskId);
            notifyUrgent(tenantId, taskId, ticketNo, String.valueOf(task.get("room_no")), draft.category());
        }
        return ticketNo;
    }

    public void discard(long tenantId, UUID taskId) {
        jdbcTemplate.update("DELETE FROM repair_tickets WHERE tenant_id = ? AND task_id = ?", tenantId, taskId);
    }

    public Map<String, Object> judgeUrgent(long tenantId, UUID roomId) {
        Map<String, Object> judgement = new LinkedHashMap<>();
        judgement.put("suggested", false);
        judgement.put("rule", null);

        LocalDate today = OffsetDateTime.now(clock).toLocalDate();
        List<Map<String, Object>> reservations = jdbcTemplate.queryForList(
                """
                SELECT checkin_at, (attrs ->> 'vip') = 'true' AS vip
                  FROM reservations_cache
                 WHERE tenant_id = ? AND room_id = ? AND superseded_at IS NULL
                   AND (checkin_at AT TIME ZONE ?)::date = ?
                 ORDER BY checkin_at
                 LIMIT 1
                """,
                tenantId, roomId, clock.getZone().getId(), today);
        if (!reservations.isEmpty()) {
            Map<String, Object> reservation = reservations.get(0);
            boolean vip = Boolean.TRUE.equals(reservation.get("vip"));
            String checkinTime = toOffsetDateTime(reservation.get("checkin_at"))
                    .atZoneSameInstant(clock.getZone())
                    .format(CHECKIN_TIME);
            judgement.put("suggested", true);
            judgement.put("rule", (vip ? "VIP · " : "") + checkinTime + " 入住");
        }
        return judgement;
    }

    private String nextTicketNo(long tenantId) {
        Integer lastNo = jdbcTemplate.queryForObject(
                """
                INSERT INTO repair_ticket_counters (tenant_id, last_no) VALUES (?, 1)
                ON CONFLICT (tenant_id) DO UPDATE SET last_no = repair_ticket_counters.last_no + 1
                RETURNING last_no
                """,
                Integer.class, tenantId);
        return String.format("W-%04d", lastNo);
    }

    // 防重複鍵以工務單號為單位：同一張任務復原後再派開出的新單是另一張單，要再推一次。
    private void notifyUrgent(long tenantId, UUID taskId, String ticketNo, String roomNo, String category) {
        String categoryLabel = RepairService.CATEGORY_LABELS.getOrDefault(category, category);
        List<UUID> recipients = jdbcTemplate.queryForList(
                """
                SELECT id FROM staff_accounts
                 WHERE tenant_id = ? AND active = true AND (dept = 'engineering' OR role = 'manager')
                """,
                UUID.class, tenantId);
        for (UUID recipient : recipients) {
            ObjectNode payload = PushOutboxWriter.payload(
                    objectMapper, recipient, "repair_urgent",
                    roomNo + " 急件報修（" + categoryLabel + " " + ticketNo + "）");
            PushOutboxWriter.targetPool(payload, "engineering", taskId);
            pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload,
                    "repair_urgent:" + ticketNo + ":" + recipient);
        }
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        throw new IllegalStateException("預期時間型別，實際 " + (value == null ? "null" : value.getClass()));
    }
}
