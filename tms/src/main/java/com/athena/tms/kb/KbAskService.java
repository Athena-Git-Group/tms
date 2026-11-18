package com.athena.tms.kb;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.service.PushOutboxWriter;
import com.athena.tms.service.TaskRowLookup;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class KbAskService {

    private final JdbcTemplate jdbcTemplate;
    private final TaskRowLookup taskRowLookup;
    private final PushOutboxWriter pushOutboxWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public KbAskService(
            JdbcTemplate jdbcTemplate,
            TaskRowLookup taskRowLookup,
            PushOutboxWriter pushOutboxWriter,
            ObjectMapper objectMapper,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRowLookup = taskRowLookup;
        this.pushOutboxWriter = pushOutboxWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public KbAnswer ask(UUID taskId, String question) {
        long tenantId = TenantContext.require();
        UUID askerId = CurrentActor.require().staffId();
        Map<String, Object> task = taskRowLookup.require(tenantId, taskId);
        String roomNo = String.valueOf(task.get("room_no"));
        UUID roomId = UUID.fromString(String.valueOf(task.get("room_id")));

        Map<String, Object> memory = findLatestMemory(tenantId, roomId, memoryKindFor(question));
        Map<String, Object> sop = memory == null ? findSop(tenantId, sopKeywordFor(question)) : null;

        UUID answerId = UuidV7.generate();
        KbAnswer answer;
        String citedKind;
        ArrayNode citedRefs = objectMapper.createArrayNode();
        if (memory == null && sop == null) {
            citedKind = "none";
            answer = new KbAnswer(
                    answerId,
                    false,
                    null,
                    "查不到 " + roomNo + " 的相關記錄：可能是沒有記錄，或資料還在更新。系統不會用猜的回答。",
                    List.of());
        } else if (sop != null) {
            citedKind = "sop";
            String label = String.valueOf(sop.get("label"));
            String title = titleOf(label);
            String excerpt = excerptOf(label);
            String updatedOn = toLocalDate(sop.get("created_at")).toString();
            KbSource source = new KbSource("sop", title, excerpt, updatedOn);

            ObjectNode ref = citedRefs.addObject();
            ref.put("kind", "sop");
            ref.put("id", String.valueOf(sop.get("id")));
            ref.put("title", title);
            ref.put("updated_on", updatedOn);

            answer = new KbAnswer(answerId, true, "依 SOP「" + title + "」：" + excerpt, null, List.of(source));
        } else {
            citedKind = "room_memory";
            String summary = String.valueOf(memory.get("summary"));
            String title = titleOf(summary);
            String excerpt = excerptOf(summary);
            LocalDate occurredOn = toLocalDate(memory.get("occurred_at"));
            String kind = String.valueOf(memory.get("kind"));
            KbSource source = new KbSource("room_memory", title, excerpt, occurredOn.toString());

            ObjectNode ref = citedRefs.addObject();
            ref.put("kind", "room_memory");
            ref.put("id", String.valueOf(memory.get("id")));
            ref.put("title", title);
            ref.put("updated_on", occurredOn.toString());

            answer = new KbAnswer(answerId, true, composeAnswer(kind, title, excerpt, occurredOn), null, List.of(source));
        }

        jdbcTemplate.update(
                """
                INSERT INTO kb_audit_logs (id, tenant_id, asker_id, task_id, question, cited_kind, cited_refs, answer, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """,
                answerId, tenantId, askerId, taskId, question, citedKind,
                citedRefs.toString(), answer.answer(), OffsetDateTime.now(clock));
        return answer;
    }

    @Transactional
    public void submitFeedback(UUID answerId, boolean helpful) {
        long tenantId = TenantContext.require();
        int updated = jdbcTemplate.update(
                "UPDATE kb_audit_logs SET feedback = ? WHERE tenant_id = ? AND id = ?",
                helpful ? "helpful" : "unhelpful", tenantId, answerId);
        if (updated == 0) {
            throw new NotFoundException("查無此回答: " + answerId);
        }
        if (!helpful) {
            notifySopManagers(tenantId, answerId);
        }
    }

    private void notifySopManagers(long tenantId, UUID answerId) {
        Map<String, Object> audit = findAuditLog(tenantId, answerId);
        if (audit == null || !"sop".equals(String.valueOf(audit.get("cited_kind")))) {
            return;
        }
        var citedRefs = objectMapper.readTree(String.valueOf(audit.get("cited_refs")));
        if (!citedRefs.isArray() || citedRefs.isEmpty()) {
            return;
        }
        String sopTitle = citedRefs.get(0).path("title").asText();
        for (UUID manager : pushOutboxWriter.activeManagerIds(tenantId)) {
            ObjectNode payload = PushOutboxWriter.payload(
                    objectMapper, manager, "kb_unhelpful", "回答被評為沒幫助，請檢查 SOP「" + sopTitle + "」");
            payload.put("body", String.valueOf(audit.get("question")));
            payload.putObject("target").put("kind", "home");
            pushOutboxWriter.enqueue(
                    tenantId, "kb_audit:" + answerId, payload, "kb_unhelpful:" + answerId + ":" + manager);
        }
    }

    @Transactional
    public KbEscalationView escalate(UUID answerId) {
        long tenantId = TenantContext.require();
        UUID askedBy = CurrentActor.require().staffId();
        Map<String, Object> audit = findAuditLog(tenantId, answerId);
        if (audit == null) {
            throw new NotFoundException("查無此回答: " + answerId);
        }
        int marked = jdbcTemplate.update(
                "UPDATE kb_audit_logs SET escalated = true WHERE tenant_id = ? AND id = ? AND escalated = false",
                tenantId, answerId);
        if (marked == 0) {
            throw new StateConflictException("這則回答已轉問主管");
        }
        UUID escalationId = UuidV7.generate();
        String question = String.valueOf(audit.get("question"));
        jdbcTemplate.update(
                """
                INSERT INTO kb_escalations (id, tenant_id, audit_log_id, question, asked_by, status, created_at)
                VALUES (?, ?, ?, ?, ?, 'pending', ?)
                """,
                escalationId, tenantId, answerId, question, askedBy, OffsetDateTime.now(clock));
        return new KbEscalationView(
                escalationId, answerId, question, uuidOf(audit.get("task_id")), askedBy, "pending", null, null);
    }

    public List<KbEscalationView> listEscalations(String status) {
        long tenantId = TenantContext.require();
        StringBuilder sql = new StringBuilder(
                """
                SELECT e.*, a.task_id
                  FROM kb_escalations e
                  JOIN kb_audit_logs a ON a.tenant_id = e.tenant_id AND a.id = e.audit_log_id
                 WHERE e.tenant_id = ?
                """);
        List<Object> params = new ArrayList<>(List.of(tenantId));
        if (status != null && !status.isBlank()) {
            sql.append(" AND e.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY e.created_at");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray()).stream()
                .map(KbAskService::toEscalationView)
                .toList();
    }

    @Transactional
    public KbEscalationView reply(UUID escalationId, String reply) {
        long tenantId = TenantContext.require();
        UUID repliedBy = CurrentActor.require().staffId();
        List<Map<String, Object>> updated = jdbcTemplate.queryForList(
                """
                UPDATE kb_escalations
                   SET status = 'answered', reply = ?, replied_by = ?, replied_at = ?
                 WHERE tenant_id = ? AND id = ? AND status = 'pending'
                RETURNING *
                """,
                reply, repliedBy, OffsetDateTime.now(clock), tenantId, escalationId);
        if (updated.isEmpty()) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM kb_escalations WHERE tenant_id = ? AND id = ?",
                    Integer.class, tenantId, escalationId);
            if (exists == null || exists == 0) {
                throw new NotFoundException("查無此提問: " + escalationId);
            }
            throw new StateConflictException("這則提問已有回覆");
        }
        Map<String, Object> row = updated.get(0);
        Map<String, Object> audit = findAuditLog(tenantId, uuidOf(row.get("audit_log_id")));
        UUID taskId = audit == null ? null : uuidOf(audit.get("task_id"));
        notifyAsker(tenantId, escalationId, uuidOf(row.get("asked_by")), taskId, reply);
        return toEscalationView(row, taskId);
    }

    private void notifyAsker(long tenantId, UUID escalationId, UUID askedBy, UUID taskId, String reply) {
        ObjectNode payload = PushOutboxWriter.payload(objectMapper, askedBy, "kb_reply", "主管回覆了您的提問");
        payload.put("body", reply);
        ObjectNode target = payload.putObject("target");
        if (taskId != null) {
            target.put("kind", "task");
            target.put("taskId", taskId.toString());
        } else {
            target.put("kind", "home");
        }
        pushOutboxWriter.enqueue(
                tenantId, "kb_escalation:" + escalationId, payload, "kb_reply:" + escalationId);
    }

    private static String memoryKindFor(String question) {
        if (question.contains("修")) {
            return "repair";
        }
        if (question.contains("要求")) {
            return "preference";
        }
        return null;
    }

    private static String sopKeywordFor(String question) {
        if (question.contains("備品")) {
            return "備品";
        }
        return null;
    }

    private Map<String, Object> findSop(long tenantId, String keyword) {
        if (keyword == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT * FROM sop_items
                 WHERE tenant_id = ? AND active = true AND label LIKE ?
                 ORDER BY sort_order, created_at DESC
                 LIMIT 1
                """,
                tenantId, "%" + keyword + "%");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findLatestMemory(long tenantId, UUID roomId, String kind) {
        if (kind == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT * FROM room_memory
                 WHERE tenant_id = ? AND room_id = ? AND kind = ?
                 ORDER BY occurred_at DESC
                 LIMIT 1
                """,
                tenantId, roomId, kind);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findAuditLog(long tenantId, UUID answerId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM kb_audit_logs WHERE tenant_id = ? AND id = ?", tenantId, answerId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String composeAnswer(String kind, String title, String excerpt, LocalDate occurredOn) {
        String date = occurredOn.getMonthValue() + "/" + String.format("%02d", occurredOn.getDayOfMonth());
        if ("repair".equals(kind)) {
            String[] halves = excerpt.split(" → ", 2);
            if (halves.length == 2) {
                String problem = halves[0].trim();
                String action = halves[1].split("[，。]", 2)[0].trim();
                if (action.startsWith("更換")) {
                    String subject = title.contains("· ") ? title.substring(title.indexOf("· ") + 2).trim() : "";
                    return "有。" + date + " 工務換過" + subject + action.substring("更換".length())
                            + "，原因是住客反映" + problem + "，之後沒有再報修。";
                }
            }
        } else if ("preference".equals(kind)) {
            int items = excerpt.split("；").length;
            return "有" + chineseNumber(items) + "項：" + excerpt;
        }
        return "有。" + date + " " + excerpt;
    }

    private static String chineseNumber(int n) {
        String[] words = {"零", "一", "兩", "三", "四", "五", "六", "七", "八", "九", "十"};
        return (n >= 0 && n < words.length) ? words[n] : String.valueOf(n);
    }

    private static String titleOf(String summary) {
        int idx = summary.indexOf('：');
        return idx < 0 ? "" : summary.substring(0, idx);
    }

    private static String excerptOf(String summary) {
        int idx = summary.indexOf('：');
        return idx < 0 ? summary : summary.substring(idx + 1);
    }

    private LocalDate toLocalDate(Object occurredAt) {
        if (occurredAt instanceof OffsetDateTime odt) {
            return odt.atZoneSameInstant(clock.getZone()).toLocalDate();
        }
        if (occurredAt instanceof Timestamp ts) {
            return ts.toInstant().atZone(clock.getZone()).toLocalDate();
        }
        throw new IllegalStateException("room_memory.occurred_at 讀出未知型別: " + occurredAt);
    }

    private static KbEscalationView toEscalationView(Map<String, Object> row) {
        return toEscalationView(row, row.get("task_id") == null ? null : uuidOf(row.get("task_id")));
    }

    private static KbEscalationView toEscalationView(Map<String, Object> row, UUID taskId) {
        return new KbEscalationView(
                uuidOf(row.get("id")),
                uuidOf(row.get("audit_log_id")),
                String.valueOf(row.get("question")),
                taskId,
                uuidOf(row.get("asked_by")),
                String.valueOf(row.get("status")),
                row.get("reply") == null ? null : String.valueOf(row.get("reply")),
                row.get("replied_by") == null ? null : uuidOf(row.get("replied_by")));
    }

    private static UUID uuidOf(Object dbValue) {
        return UUID.fromString(String.valueOf(dbValue));
    }
}
