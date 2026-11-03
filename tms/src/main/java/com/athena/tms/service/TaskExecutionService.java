package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.CompleteTaskRequest;
import com.athena.tms.dto.HoldReasonRequest;
import com.athena.tms.dto.RequeueRequest;
import com.athena.tms.dto.SopCheckItemRequest;
import com.athena.tms.repair.RepairService;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 員工 App 的任務執行入口：本人延後、開工、恢復、查看任務詳情與部門待接池。
 * 狀態轉移一律委派 {@link TaskEngine}（前提條件寫在 UPDATE 的 WHERE 裡，
 * 條件不符一列也不改）；這裡只做請求驗證、身分解析與回應組裝。
 */
@Service
public class TaskExecutionService {

    private final TaskEngine taskEngine;
    private final JdbcTemplate jdbcTemplate;
    private final PushOutboxWriter pushOutboxWriter;
    private final TaskRowLookup taskRowLookup;
    private final RepairService repairService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TaskExecutionService(
            TaskEngine taskEngine,
            JdbcTemplate jdbcTemplate,
            PushOutboxWriter pushOutboxWriter,
            TaskRowLookup taskRowLookup,
            RepairService repairService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.taskEngine = taskEngine;
        this.jdbcTemplate = jdbcTemplate;
        this.pushOutboxWriter = pushOutboxWriter;
        this.taskRowLookup = taskRowLookup;
        this.repairService = repairService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** 查看任務詳情，形狀對應 contracts 的 TaskDetail。 */
    public Map<String, Object> detail(UUID taskId) {
        long tenantId = TenantContext.require();
        return toDetail(taskRowLookup.require(tenantId, taskId));
    }

    /**
     * 負責人延後自己名下的任務。原因去掉頭尾空白後必須是 1 到 60 字，
     * 否則回 422 reason_invalid，不進任何狀態轉移。
     */
    public Map<String, Object> deferBySelf(UUID taskId, HoldReasonRequest request) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        String reason = requireValidReason(request);
        return transition(tenantId, taskId, actor,
                (actorRef) -> taskEngine.deferBySelf(tenantId, taskId, reason, actorRef));
    }

    /** 負責人中斷自己進行中的任務，原因規則同 {@link #deferBySelf}（contracts tasksInterrupt）。 */
    public Map<String, Object> interrupt(UUID taskId, HoldReasonRequest request) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        String reason = requireValidReason(request);
        return transition(tenantId, taskId, actor,
                (actorRef) -> taskEngine.interrupt(tenantId, taskId, reason, actorRef));
    }

    /** 延後與中斷共用的原因驗證：去掉頭尾空白後必須是 1 到 60 字，否則 422 reason_invalid。 */
    private static String requireValidReason(HoldReasonRequest request) {
        String reason = request == null || request.reason() == null ? "" : request.reason().strip();
        if (reason.isEmpty() || reason.length() > 60) {
            throw new UnprocessableEntityException("reason_invalid", "原因去掉頭尾空白後必須是 1–60 字");
        }
        return reason;
    }

    /** 負責人開工，接受待開始與本人延後的任務。同一位員工同時只能有一間進行中。 */
    public Map<String, Object> start(UUID taskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        rejectIfAnotherTaskDoing(tenantId, taskId, actor);
        return transition(tenantId, taskId, actor,
                (actorRef) -> taskEngine.start(tenantId, taskId, actorRef));
    }

    /**
     * 開工前先確認這位員工沒有另一筆進行中的任務（contracts tasksStart 的 409
     * doing_task_exists，message 直接給 App 顯示，context 帶那一筆的 taskId 與
     * roomNo）。真正的防線是資料庫的 uq_tasks_one_doing_per_staff 唯一索引，
     * 這裡先查一次是為了把它翻成業務提示，而不是讓唯一索引炸成 500。
     */
    private void rejectIfAnotherTaskDoing(long tenantId, UUID taskId, CurrentActor actor) {
        List<Map<String, Object>> doing = jdbcTemplate.queryForList(
                """
                SELECT t.id, r.room_no
                  FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND t.assignee_id = ? AND t.state = 'doing' AND t.id <> ?
                 LIMIT 1
                """,
                tenantId, actor.staffId(), taskId);
        if (!doing.isEmpty()) {
            Map<String, Object> doingTask = doing.get(0);
            String roomNo = String.valueOf(doingTask.get("room_no"));
            throw new StateConflictException(
                    "doing_task_exists",
                    roomNo + " 還在進行中",
                    Map.of("taskId", String.valueOf(doingTask.get("id")), "roomNo", roomNo));
        }
    }

    /**
     * 負責人勾選或跳過一個 SOP 項（contracts tasksSopCheck）。以 SOP 項的 id 定位，
     * 翻成 item_key 與 label 快照後交給任務引擎記事件並更新勾記列。
     */
    public Map<String, Object> sopCheck(UUID taskId, SopCheckItemRequest request) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        if (request == null || request.sopItemId() == null
                || !("checked".equals(request.result()) || "skipped".equals(request.result()))) {
            throw new UnprocessableEntityException("sopItemId 必填，result 必須是 checked 或 skipped");
        }
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT item_key, label FROM sop_items WHERE tenant_id = ? AND id = ?",
                tenantId, request.sopItemId());
        if (items.isEmpty()) {
            throw new NotFoundException("查無 SOP 項: " + request.sopItemId());
        }
        String itemKey = String.valueOf(items.get(0).get("item_key"));
        String label = String.valueOf(items.get(0).get("label"));
        boolean checked = "checked".equals(request.result());
        return transition(tenantId, taskId, actor,
                (actorRef) -> taskEngine.sopCheck(tenantId, taskId, itemKey, label, checked, !checked, actorRef));
    }

    /**
     * 負責人完成自己進行中的任務（contracts tasksComplete）。修繕任務（工務單）在
     * 狀態轉移成功後，由 {@link RepairService#resolveOnComplete} 做完修收尾——照片
     * 綁定、處理說明、推播報修人與原任務的停住原因連動。整個方法包在同一個交易裡，
     * 不會出現「單已結案但報修人沒收到通知」的中間態。
     */
    @Transactional
    public Map<String, Object> complete(UUID taskId, CompleteTaskRequest request) {
        long tenantId = TenantContext.require();
        Map<String, Object> task = taskRowLookup.require(tenantId, taskId);
        requireCompletionPhotosForRepair(task, request);
        Map<String, Object> detail = transition(tenantId, taskId, CurrentActor.require(),
                (actorRef) -> taskEngine.complete(tenantId, taskId, actorRef));
        if ("repair".equals(String.valueOf(task.get("type")))) {
            // 修繕任務能走到這裡，表示上面的完修照片驗證已確認 request 與照片存在。
            repairService.resolveOnComplete(taskId, request.photoIds(), request.resolutionNote());
        }
        return detail;
    }

    /**
     * 完成前的完修照片驗證：修繕任務（工務單）必須附至少 1 張完修照片才能完成，
     * 缺照片時回 422 completion_photo_required，不進任何狀態轉移。放行的話，
     * 工務單會在沒有完修證據的情況下結案，報修人與主管就無從確認真的修好了。
     * 打掃與一般任務不需要照片，直接放行。
     */
    private static void requireCompletionPhotosForRepair(Map<String, Object> task, CompleteTaskRequest request) {
        if (!"repair".equals(String.valueOf(task.get("type")))) {
            return;
        }
        if (request == null || request.photoIds() == null || request.photoIds().isEmpty()) {
            throw new UnprocessableEntityException("completion_photo_required", "需要完修照片");
        }
    }

    /**
     * 主管附交代把已中斷的任務退回待接池（contracts tasksRequeue）。退池與事件由
     * {@link TaskEngine#requeueInterrupted} 以條件式更新把關；之後推播同樓層的房務員，請他們到
     * 待接池接手——推播和退池包在同一個交易裡，退池成立但沒人收到通知的狀態不會出現。
     */
    @Transactional
    public Map<String, Object> requeue(UUID taskId, RequeueRequest request) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        String note = request == null || request.note() == null ? "" : request.note().strip();
        if (note.isEmpty()) {
            throw new UnprocessableEntityException("note_required", "附交代退池必須附上接手進度的交代");
        }
        Map<String, Object> before = taskRowLookup.require(tenantId, taskId);
        int seq = taskEngine.requeueInterrupted(tenantId, taskId, note, staffActor(actor));
        notifySameFloorHousekeepers(tenantId, taskId, seq, before);
        return toDetail(taskRowLookup.require(tenantId, taskId));
    }

    /**
     * 附交代退池後推播該樓層的在職房務員（樓層以 plan_rooms 的負責樓層為準），
     * 去處標亮這張單；收件人以「任務＋事件序號＋收件人」組冪等鍵，同一次退池
     * 不會對同一個人推兩次。
     */
    private void notifySameFloorHousekeepers(long tenantId, UUID taskId, int seq, Map<String, Object> taskRow) {
        List<UUID> recipients = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT sa.id
                  FROM plan_rooms pr
                  JOIN rooms pl ON pl.tenant_id = pr.tenant_id AND pl.id = pr.room_id
                  JOIN staff_accounts sa ON sa.tenant_id = pr.tenant_id AND sa.id = pr.staff_id
                  JOIN rooms tr ON tr.tenant_id = ? AND tr.id = ?
                 WHERE pr.tenant_id = ? AND pl.floor = tr.floor
                   AND sa.active = true AND sa.dept = 'housekeeping'
                """,
                UUID.class, tenantId, taskRow.get("room_id"), tenantId);
        String roomNo = String.valueOf(taskRow.get("room_no"));
        for (UUID recipient : recipients) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("recipientStaffId", recipient.toString());
            payload.put("event", "returned_to_pool");
            payload.put("title", roomNo + " 已附交代退回待接池");
            ObjectNode target = payload.putObject("target");
            target.put("kind", "pool");
            target.put("dept", "housekeeping");
            target.put("highlightTaskId", taskId.toString());
            pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload,
                    "requeue:" + taskId + ":seq:" + seq + ":" + recipient);
        }
    }

    /** 負責人恢復已中斷的任務。 */
    public Map<String, Object> resume(UUID taskId) {
        long tenantId = TenantContext.require();
        return transition(tenantId, taskId, CurrentActor.require(),
                (actorRef) -> taskEngine.resume(tenantId, taskId, actorRef));
    }

    /**
     * 依登入員工的部門回傳待接池，形狀對應 contracts 的 DeptPool。帶 category 時
     * items 只回傳該類別，但 counts 仍統計全部類別（contracts listDeptPool），
     * 所以先撈整個部門池、再在組裝時過濾，不在 SQL 先砍掉其他類別。
     *
     * <p>排序依 feature「待接池排序與篩選」：已逾期（過 due_at）或已升級（esc_level）
     * 的單排最前、彼此間依房號；其餘依急件、優先通知、與查詢者同樓層、房號遞增。
     * 「同樓層」以查詢者本班排房（plan_rooms）負責房間的樓層判斷，和附交代退池的
     * 推播收件人用同一個樓層口徑（見 {@link #notifySameFloorHousekeepers}）。
     */
    public Map<String, Object> deptPool(String categoryFilter) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        if (actor.dept() == null) {
            throw new UnprocessableEntityException("此帳號未設部門，沒有對應的部門待接池");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        // 先在內層算好 overdue_or_escalated 與 same_floor 兩個旗標，外層 ORDER BY
        // 直接引用。SQL 不允許在 ORDER BY 的運算式裡引用同一層 SELECT 的別名，
        // 不包一層就得在排序鍵重抄整段逾期判斷。
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT * FROM (
                    SELECT t.id, t.title, t.type, t.urgent, t.priority_notify, t.pool_source,
                           t.return_reason, t.requeue_note, t.created_at, r.room_no, r.floor,
                           rt.ticket_no,
                           ((t.due_at IS NOT NULL AND t.due_at < ?) OR COALESCE(t.esc_level, 0) > 0)
                               AS overdue_or_escalated,
                           EXISTS (SELECT 1
                                     FROM plan_rooms pr
                                     JOIN rooms pl ON pl.tenant_id = pr.tenant_id AND pl.id = pr.room_id
                                    WHERE pr.tenant_id = t.tenant_id AND pr.staff_id = ?
                                      AND pl.floor = r.floor) AS same_floor
                      FROM tasks t
                      JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                      LEFT JOIN repair_tickets rt ON rt.tenant_id = t.tenant_id AND rt.task_id = t.id
                     WHERE t.tenant_id = ? AND t.dept = ? AND t.state = 'pool'
                ) pool
                ORDER BY pool.overdue_or_escalated DESC,
                         CASE WHEN pool.overdue_or_escalated THEN pool.room_no END,
                         pool.urgent DESC, pool.priority_notify DESC, pool.same_floor DESC,
                         pool.room_no, pool.created_at
                """,
                now, actor.staffId(), tenantId, actor.dept());

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String category = String.valueOf(row.get("type"));
            byCategory.merge(category, 1, Integer::sum);
            if (categoryFilter != null && !categoryFilter.equals(category)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", String.valueOf(row.get("id")));
            item.put("roomNo", row.get("room_no"));
            item.put("floor", row.get("floor"));
            item.put("title", row.get("title"));
            item.put("category", category);
            item.put("source", row.get("pool_source") != null ? row.get("pool_source") : "desk_forward");
            item.put("urgent", row.get("urgent"));
            item.put("ticketNo", row.get("ticket_no"));
            item.put("priorityNotify", row.get("priority_notify"));
            item.put("sameFloor", row.get("same_floor"));
            item.put("returnReason", row.get("return_reason"));
            item.put("handoverNote", row.get("requeue_note"));
            item.put("createdAt", row.get("created_at"));
            items.add(item);
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("all", rows.size());
        counts.put("cleaning", byCategory.getOrDefault("cleaning", 0));
        counts.put("amenity", byCategory.getOrDefault("amenity", 0));
        counts.put("repair", byCategory.getOrDefault("repair", 0));
        counts.put("other", byCategory.getOrDefault("other", 0));
        return Map.of("items", items, "counts", counts);
    }

    /**
     * 狀態轉移的共用流程：先確認任務存在（查無任務要回 404，不能被後續狀態不符的
     * 409 蓋掉），再委派 {@link TaskEngine} 執行轉移，最後重讀資料列組出最新的
     * TaskDetail 回給呼叫端。
     */
    private Map<String, Object> transition(
            long tenantId, UUID taskId, CurrentActor actor, Consumer<Actor> engineCall) {
        taskRowLookup.require(tenantId, taskId);
        engineCall.accept(staffActor(actor));
        return toDetail(taskRowLookup.require(tenantId, taskId));
    }

    private Map<String, Object> toDetail(Map<String, Object> row) {
        int minutes = elapsedMinutes(row);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", String.valueOf(row.get("id")));
        detail.put("tenantId", row.get("tenant_id"));
        detail.put("type", row.get("type"));
        detail.put("source", row.get("source"));
        detail.put("roomId", String.valueOf(row.get("room_id")));
        detail.put("dept", row.get("dept"));
        detail.put("state", row.get("state"));
        detail.put("eventSeq", row.get("event_seq"));
        detail.put("assigneeId", row.get("assignee_id") == null ? null : String.valueOf(row.get("assignee_id")));
        detail.put("title", row.get("title"));
        detail.put("redoCount", row.get("redo_count"));
        detail.put("inspection", row.get("inspection"));
        detail.put("roomNo", row.get("room_no"));
        detail.put("holdReason", row.get("hold_reason"));
        detail.put("deferredBySelf", row.get("deferred_by_self"));
        detail.put("elapsedMinutes", minutes);
        detail.put("accumulatedMinutes", minutes);
        detail.put("checkedSopItems", checkedSopItemIds(row));
        return detail;
    }

    /**
     * TaskDetail 的 checkedSopItems：已勾選 SOP 項對應的 sop_items.id。勾記列
     * （task_sop_checks，從事件紀錄整理出來、方便查詢的目前勾選狀態）存的是
     * item_key 快照，不是 sop_items 的 id，所以回應時 join 回去還原成 id 給 App 用。
     */
    private List<UUID> checkedSopItemIds(Map<String, Object> row) {
        return jdbcTemplate.queryForList(
                """
                SELECT s.id
                  FROM task_sop_checks c
                  JOIN sop_items s ON s.tenant_id = c.tenant_id AND s.item_key = c.item_key
                 WHERE c.tenant_id = ? AND c.task_id = ? AND c.checked
                """,
                UUID.class, row.get("tenant_id"), row.get("id"));
    }

    /**
     * 已做分鐘＝已結束段落的 worked_sec，加上還在進行中的這段（segment_started_at
     * 到現在）。「現在」用注入的 {@link Clock}，和逾時判斷共用同一個時間來源。
     */
    private int elapsedMinutes(Map<String, Object> row) {
        long seconds = row.get("worked_sec") instanceof Number n ? n.longValue() : 0;
        Object segmentStarted = row.get("segment_started_at");
        if (segmentStarted instanceof java.sql.Timestamp ts) {
            seconds += Duration.between(ts.toInstant(), clock.instant()).getSeconds();
        } else if (segmentStarted instanceof OffsetDateTime odt) {
            seconds += Duration.between(odt.toInstant(), clock.instant()).getSeconds();
        }
        return (int) (seconds / 60);
    }

    private static Actor staffActor(CurrentActor actor) {
        return Actor.staff(actor.role(), actor.staffId());
    }
}
