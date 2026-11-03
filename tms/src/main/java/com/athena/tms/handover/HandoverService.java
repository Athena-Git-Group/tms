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

/**
 * 房間交辦（FR-037、FR-038）：櫃檯備註的建立、交辦已讀的記錄，與任務詳情的
 * 交辦合併查詢。交辦不是另一張表，而是把三個已存在的權威來源在讀取時合併——
 * 交班單凍結快照（handovers.snapshot 中該房的項目）、櫃檯備註（desk_notes）、
 * 接手進度（tasks.requeue_note，作者與時間取自 returned_to_pool 事件）。
 *
 * <p>已讀模型是 handover_reads 的「員工 × 任務」一列：第一次已讀寫入 read_at，
 * 之後同一個人再展開就撞主鍵、ON CONFLICT DO NOTHING 不重記（dbml 的設計註記）。
 */
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

    /**
     * 櫃檯為房間留備註（contracts createDeskNote）。房間不存在要回 404：
     * 備註是掛在房間上的，收下一筆掛在不存在房間的備註，之後任何任務都撈不到它。
     */
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

    /**
     * 員工展開任務的交辦（contracts markHandoverRead）。先確認任務存在（404），
     * 再記已讀；回應永遠是這個人對這張任務的第一筆已讀時間——重送或重複展開
     * 都回放同一筆，不新增。
     */
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

    /**
     * 記一筆交辦已讀並回傳第一次已讀的時間。主鍵（tenant × 任務 × 員工）就是
     * 冪等鍵：已有資料列時 ON CONFLICT DO NOTHING 一列也不動，之後讀回的
     * read_at 仍是第一次的。開工（tasksStart）也走這裡自動記已讀。
     */
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

    /** 這位員工有沒有讀過這張任務的交辦（TaskDetail.handoverRead）。 */
    public boolean isRead(long tenantId, UUID taskId, UUID staffId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM handover_reads WHERE tenant_id = ? AND task_id = ? AND staff_id = ?",
                Integer.class, tenantId, taskId, staffId);
        return count != null && count > 0;
    }

    /**
     * 任務詳情的合併交辦清單（TaskDetail.handovers，形狀對應 contracts 的
     * HandoverEntry：kind、author、at、content）。三個來源依序併入：
     *
     * <ul>
     * <li>交班單（shift_handover）：同部門交班單凍結快照中「對象＝這間房」的項目；
     *     樓層層級的項目（如 3F）不併入——那是整層的話，不該擠進單一房的任務。
     *     作者取快照裡定格的撰寫人名，時間取交班單凍結時間。</li>
     * <li>櫃檯備註（desk_note）：同房間的 desk_notes，作者由 author_id 還原成顯示名。</li>
     * <li>接手進度（requeue_progress）：tasks.requeue_note 有交代時併入一則，
     *     作者與時間取最近一次 returned_to_pool 事件的操作者——交代是那次退池
     *     留下的，不是現任負責人寫的。</li>
     * </ul>
     */
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

    /**
     * 任務詳情的查房退回資訊（TaskDetail.returnInfo，形狀對應 contracts 的
     * ReturnInfo）。讀取時合併三個權威來源，不另存一張表：
     *
     * <ul>
     * <li>不合格項（failedItems）：最近一次查房紀錄（inspections）中 pass=false 的
     *     項目，還原成 sop_items 的顯示名。最近一次不是 failed（或根本沒查過房）
     *     就回 null——任務不在退回重做中，沒有退回資訊可言。</li>
     * <li>重做次數（redoCount）：tasks.redo_count。</li>
     * <li>換人原因（reassignReason）：任務被班末掃描退池（pool_source=shift_end_return）
     *     時記 original_cleaner_off_shift，原清房者取最近一次 completed 事件的操作者。
     *     換人原因併在這裡成一句，不另列為主管交代（manager_note）。</li>
     * </ul>
     */
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

    /**
     * 任務最近一次指定類型事件的操作者顯示名（author）與發生時間（created_at）。
     * 查無該類型事件時回 null。交辦與退回資訊都要回答「這件事是誰做的」，
     * 共用同一條「取最近一筆事件並還原操作者名」的查詢。
     */
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
