package com.athena.tms.handover;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.SqlTimestamps;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.service.TaskRowLookup;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HandoverService {

    private final JdbcTemplate jdbcTemplate;
    private final TaskRowLookup taskRowLookup;
    private final Clock clock;

    public HandoverService(JdbcTemplate jdbcTemplate, TaskRowLookup taskRowLookup, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRowLookup = taskRowLookup;
        this.clock = clock;
    }

    public Map<String, Object> createDeskNote(UUID roomId, String content) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        Integer roomCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM rooms WHERE tenant_id = ? AND id = ?", Integer.class, tenantId, roomId);
        if (roomCount == null || roomCount == 0) {
            throw new NotFoundException("查無房間: " + roomId);
        }
        UUID id = UuidV7.generate();
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbcTemplate.update(
                """
                INSERT INTO desk_notes (id, tenant_id, room_id, content, author_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id, tenantId, roomId, content, actor.staffId(), now);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id.toString());
        body.put("roomId", roomId.toString());
        body.put("content", content);
        body.put("authorId", String.valueOf(actor.staffId()));
        body.put("createdAt", now.toString());
        return body;
    }

    public Map<String, Object> markRead(UUID taskId) {
        long tenantId = TenantContext.require();
        UUID staffId = CurrentActor.require().staffId();
        taskRowLookup.require(tenantId, taskId);
        OffsetDateTime readAt = recordRead(tenantId, taskId, staffId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", taskId.toString());
        body.put("staffId", staffId.toString());
        body.put("readAt", readAt.toString());
        return body;
    }

    public OffsetDateTime recordRead(long tenantId, UUID taskId, UUID staffId) {
        jdbcTemplate.update(
                """
                INSERT INTO handover_reads (tenant_id, task_id, staff_id, read_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tenant_id, task_id, staff_id) DO NOTHING
                """,
                tenantId, taskId, staffId, OffsetDateTime.now(clock));
        return jdbcTemplate.queryForObject(
                "SELECT read_at FROM handover_reads WHERE tenant_id = ? AND task_id = ? AND staff_id = ?",
                OffsetDateTime.class, tenantId, taskId, staffId);
    }

    public boolean isRead(long tenantId, UUID taskId, UUID staffId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM handover_reads WHERE tenant_id = ? AND task_id = ? AND staff_id = ?",
                Integer.class, tenantId, taskId, staffId);
        return count != null && count > 0;
    }

    public List<Map<String, Object>> handoversOf(Map<String, Object> taskRow) {
        long tenantId = ((Number) taskRow.get("tenant_id")).longValue();
        UUID taskId = UUID.fromString(String.valueOf(taskRow.get("id")));
        String roomNo = String.valueOf(taskRow.get("room_no"));
        String dept = String.valueOf(taskRow.get("dept"));
        Object roomId = taskRow.get("room_id");

        List<Map<String, Object>> entries = new ArrayList<>();

        for (Map<String, Object> row : jdbcTemplate.queryForList(
                """
                SELECT h.created_at, item ->> 'authorName' AS author, item ->> 'content' AS content
                  FROM handovers h
                 CROSS JOIN LATERAL jsonb_array_elements(h.snapshot) AS item
                 WHERE h.tenant_id = ? AND h.dept = ? AND item ->> 'target' = ?
                 ORDER BY h.created_at
                """,
                tenantId, dept, roomNo)) {
            entries.add(entry("shift_handover", row.get("author"), row.get("created_at"), row.get("content")));
        }

        for (Map<String, Object> row : jdbcTemplate.queryForList(
                """
                SELECT d.created_at, s.name AS author, d.content
                  FROM desk_notes d
                  JOIN staff_accounts s ON s.tenant_id = d.tenant_id AND s.id = d.author_id
                 WHERE d.tenant_id = ? AND d.room_id = ?
                 ORDER BY d.created_at
                """,
                tenantId, roomId)) {
            entries.add(entry("desk_note", row.get("author"), row.get("created_at"), row.get("content")));
        }

        Object requeueNote = taskRow.get("requeue_note");
        if (requeueNote != null) {
            Map<String, Object> event = latestEventActor(tenantId, taskId, "returned_to_pool");
            if (event != null) {
                entries.add(entry("requeue_progress", event.get("author"), event.get("created_at"), requeueNote));
            }
        }
        return entries;
    }

    public Map<String, Object> returnInfoOf(Map<String, Object> taskRow) {
        long tenantId = ((Number) taskRow.get("tenant_id")).longValue();
        UUID taskId = UUID.fromString(String.valueOf(taskRow.get("id")));

        List<UUID> failedInspections = jdbcTemplate.queryForList(
                """
                SELECT id
                  FROM inspections
                 WHERE tenant_id = ? AND task_id = ? AND result = 'failed'
                   AND created_at = (SELECT max(created_at) FROM inspections
                                      WHERE tenant_id = ? AND task_id = ?)
                """,
                UUID.class, tenantId, taskId, tenantId, taskId);
        if (failedInspections.isEmpty()) {
            return null;
        }

        List<String> failedItems = jdbcTemplate.queryForList(
                """
                SELECT s.label
                  FROM inspections i
                 CROSS JOIN LATERAL jsonb_array_elements(i.items) AS item
                  JOIN sop_items s ON s.tenant_id = i.tenant_id
                                  AND s.applies_to = 'inspection'
                                  AND s.item_key = item ->> 'key'
                 WHERE i.tenant_id = ? AND i.id = ? AND (item ->> 'pass')::boolean = false
                 ORDER BY s.sort_order
                """,
                String.class, tenantId, failedInspections.get(0));

        int redoCount = taskRow.get("redo_count") instanceof Number n ? n.intValue() : 0;

        StringBuilder summary = new StringBuilder("查房退回：")
                .append(String.join("、", failedItems))
                .append("不合格 · 第 ")
                .append(redoCount)
                .append(" 次重做");

        String reassignReason = null;
        if ("shift_end_return".equals(taskRow.get("pool_source"))) {
            Map<String, Object> completed = latestEventActor(tenantId, taskId, "completed");
            if (completed != null) {
                reassignReason = "original_cleaner_off_shift";
                summary.append(" · 原清房者").append(completed.get("author")).append("已下班，改由您接手");
            }
        }

        Map<String, Object> returnInfo = new LinkedHashMap<>();
        returnInfo.put("failedItems", failedItems);
        returnInfo.put("redoCount", redoCount);
        returnInfo.put("reassignReason", reassignReason);
        returnInfo.put("summary", summary.toString());
        return returnInfo;
    }

    private Map<String, Object> latestEventActor(long tenantId, UUID taskId, String eventType) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT e.created_at, s.name AS author
                  FROM task_events e
                  JOIN staff_accounts s ON s.tenant_id = e.tenant_id AND s.id = e.actor_id
                 WHERE e.tenant_id = ? AND e.task_id = ? AND e.type = ?
                 ORDER BY e.seq DESC
                 LIMIT 1
                """,
                tenantId, taskId, eventType);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static Map<String, Object> entry(String kind, Object author, Object at, Object content) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("kind", kind);
        entry.put("author", author == null ? null : String.valueOf(author));
        entry.put("at", SqlTimestamps.toOffsetDateTime(at).toString());
        entry.put("content", content == null ? null : String.valueOf(content));
        return entry;
    }
}
