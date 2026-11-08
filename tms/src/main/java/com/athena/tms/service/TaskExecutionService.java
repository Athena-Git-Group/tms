package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.SqlTimestamps;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.ForbiddenException;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.CompleteTaskRequest;
import com.athena.tms.dto.HoldReasonRequest;
import com.athena.tms.dto.RequeueRequest;
import com.athena.tms.dto.SopCheckItemRequest;
import com.athena.tms.handover.HandoverService;
import com.athena.tms.repair.RepairService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * 三個有自己待接池的部門。角色名稱與部門名稱共用同一組值，所以同一個集合既用來
     * 判斷呼叫者是不是現場員工，也用來擋掉不存在的部門參數。主管與老闆不在其中，
     * 他們不屬於單一部門，要查待接池得自己指定看哪一個。
     */
    private static final Set<String> FIELD_DEPTS = Set.of("housekeeping", "engineering", "frontdesk");

    private final TaskEngine taskEngine;
    private final JdbcTemplate jdbcTemplate;
    private final PushOutboxWriter pushOutboxWriter;
    private final TaskRowLookup taskRowLookup;
    private final RepairService repairService;
    private final HandoverService handoverService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TaskExecutionService(
            TaskEngine taskEngine,
            JdbcTemplate jdbcTemplate,
            PushOutboxWriter pushOutboxWriter,
            TaskRowLookup taskRowLookup,
            RepairService repairService,
            HandoverService handoverService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.taskEngine = taskEngine;
        this.jdbcTemplate = jdbcTemplate;
        this.pushOutboxWriter = pushOutboxWriter;
        this.taskRowLookup = taskRowLookup;
        this.repairService = repairService;
        this.handoverService = handoverService;
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

    /**
     * 員工認領部門待認領池中的任務（contracts tasksClaim）。只有同部門、還在池中
     * 的任務才能被認領；條件不符時 {@link TaskEngine#claim} 的條件式 UPDATE 一列
     * 也不改，轉成 409 由共用的狀態衝突路徑回應（已被他人認領也是這個狀態碼）。
     */
    public Map<String, Object> claim(UUID taskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        return transition(tenantId, taskId, actor,
                (actorRef) -> taskEngine.claim(tenantId, taskId, actor.dept(), actorRef));
    }

    /**
     * 負責人開工，接受待開始與本人延後的任務。同一位員工同時只能有一間進行中。
     * 開工時順帶記交辦已讀（contracts tasksStart、FR-038）——開工前 App 會把
     * 交辦攤在確認頁上，走到開工就代表看過了；重複開工或先展開過都不重記
     * （handover_reads 主鍵衝突不動作）。兩個寫入包在同一個交易，不會出現
     * 「已開工但已讀沒記到」的中間態。
     */
    @Transactional
    public Map<String, Object> start(UUID taskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        rejectIfAnotherTaskDoing(tenantId, taskId, actor);
        Map<String, Object> detail = transition(tenantId, taskId, actor,
                (actorRef) -> taskEngine.start(tenantId, taskId, actorRef));
        handoverService.recordRead(tenantId, taskId, actor.staffId());
        detail.put("handoverRead", true);
        return detail;
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
            repairService.resolveOnComplete(
                    taskId, request.photoIds(), request.resolutionNote(), request.resolutionType());
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
     * {@link TaskEngine#requeueToPool} 以條件式更新把關；之後推播同樓層的房務員，請他們到
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
        int seq = taskEngine.requeueToPool(tenantId, taskId, note, staffActor(actor));
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
            ObjectNode payload = PushOutboxWriter.payload(
                    objectMapper, recipient, "returned_to_pool", roomNo + " 已附交代退回待接池");
            PushOutboxWriter.targetPool(payload, "housekeeping", taskId);
            pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload,
                    "requeue:" + taskId + ":seq:" + seq + ":" + recipient);
        }
    }

    /**
     * 主管作廢任務（contracts tasksCancel）。原因只收 manager_void 與 plan_superseded：
     * guest_withdrew 是住客撤回入口專用的原因，混進這裡會讓「誰收掉這張單」的追溯失真。
     * 任務有負責人時推播他，點開直達該任務頁，他才知道手上的單被收掉、不用白跑一趟。
     */
    @Transactional
    public Map<String, Object> cancel(UUID taskId, String reason, String note) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        if (!"manager_void".equals(reason) && !"plan_superseded".equals(reason)) {
            throw new UnprocessableEntityException("作廢必附原因 manager_void 或 plan_superseded，收到: " + reason);
        }
        Map<String, Object> before = taskRowLookup.require(tenantId, taskId);
        int seq = taskEngine.cancel(tenantId, taskId, reason, staffActor(actor));
        Object assignee = before.get("assignee_id");
        if (assignee != null) {
            UUID recipient = UUID.fromString(String.valueOf(assignee));
            ObjectNode payload = PushOutboxWriter.payload(
                    objectMapper, recipient, "task_cancelled", before.get("room_no") + " 已被主管作廢");
            PushOutboxWriter.targetTask(payload, taskId);
            pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload,
                    "task_cancelled:" + taskId + ":seq:" + seq + ":" + recipient);
        }
        return toDetail(taskRowLookup.require(tenantId, taskId));
    }

    /**
     * 主管改派（contracts tasksReassign）：還沒開工的單換負責人。兩邊的推播去處
     * 刻意不同——新負責人點開要直達這張單（接下來是他要做的事），原負責人點開
     * 只回首頁（這張單已經不屬於他，直達任務頁反而會讓他誤操作別人的單）。
     */
    @Transactional
    public Map<String, Object> reassign(UUID taskId, UUID newAssigneeId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        if (newAssigneeId == null) {
            throw new UnprocessableEntityException("改派必須指定新負責人（assigneeId）");
        }
        Map<String, Object> before = taskRowLookup.require(tenantId, taskId);
        int seq = taskEngine.reassign(tenantId, taskId, newAssigneeId, staffActor(actor));

        String newAssigneeName = jdbcTemplate.queryForObject(
                "SELECT name FROM staff_accounts WHERE tenant_id = ? AND id = ?",
                String.class, tenantId, newAssigneeId);
        Object previousAssignee = before.get("assignee_id");
        if (previousAssignee != null && !newAssigneeId.toString().equalsIgnoreCase(String.valueOf(previousAssignee))) {
            UUID recipient = UUID.fromString(String.valueOf(previousAssignee));
            ObjectNode payload = PushOutboxWriter.payload(
                    objectMapper, recipient, "reassigned_away", before.get("room_no") + " 已改派給" + newAssigneeName);
            PushOutboxWriter.targetHome(payload);
            pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload,
                    "reassigned_away:" + taskId + ":seq:" + seq + ":" + recipient);
        }
        ObjectNode payload = PushOutboxWriter.payload(objectMapper, newAssigneeId, "reassigned_to_me", "主管改派一單給您");
        PushOutboxWriter.targetTask(payload, taskId);
        pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload,
                "reassigned_to_me:" + taskId + ":seq:" + seq + ":" + newAssigneeId);

        return toDetail(taskRowLookup.require(tenantId, taskId));
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
     * <p>排序依 feature「待認領排序與篩選」：未接超時（reminder_stage 不是 NULL，
     * 已進入提醒同仁或通知主管階段）的單排最前、彼此間依房號；其餘依急件、優先通知、
     * 與查詢者同樓層、房號遞增。「同樓層」以查詢者本班排房（plan_rooms）負責房間的
     * 樓層判斷，和附交代退池的推播收件人用同一個樓層口徑
     * （見 {@link #notifySameFloorHousekeepers}）。
     *
     * <p>工務池另支援 urgent／source 兩個篩選維度（contracts listDeptPool），只篩選
     * items，counts 仍統計全部（與既有 category 篩選同一套做法）。修繕單
     * （category=repair）的 PoolItem.source 刻意不看 {@code tasks.pool_source}——那欄
     * 是房務各種「被動退池」情境（住客掃碼、主管附交代、請假、班末、查房退回）專用的
     * 標籤，工務單走的是完全不同的開單去向判定，改由 {@code tasks.source} 換算：
     * staff（房務報修或工務自開）→staff_report、desk（櫃檯交辦）→desk_forward。
     *
     * <p>要看哪一池由 deptFilter 決定（contracts listDeptPool 的 dept 參數）：現場員工
     * （房務、工務、櫃檯）省略就是自己部門，帶了自己部門以外的值回 403
     * forbidden_dept_pool；主管與老闆不屬於單一部門，省略時無從判斷要看哪一池，回 422
     * dept_required。回應另外帶 activeStaffCount＝該部門在職員工數，讓畫面分辨
     * 「單都被接走了」（大於 0 但 items 空）與「這個部門沒有人可以搶單」（等於 0）。
     */
    public Map<String, Object> deptPool(
            String deptFilter, String categoryFilter, Boolean urgentFilter, String sourceFilter) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        String targetDept = resolvePoolDept(actor, deptFilter);
        // 先在內層算好 unclaimed_overdue 與 same_floor 兩個旗標，外層 ORDER BY
        // 直接引用。SQL 不允許在 ORDER BY 的運算式裡引用同一層 SELECT 的別名，
        // 不包一層就得在排序鍵重抄整段未接超時判斷。
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT * FROM (
                    SELECT t.id, t.title, t.type, t.urgent, t.priority_notify, t.pool_source, t.source,
                           t.return_reason, t.requeue_note, t.created_at, t.reminder_stage,
                           r.room_no, r.floor,
                           rt.ticket_no,
                           (t.reminder_stage IS NOT NULL) AS unclaimed_overdue,
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
                ORDER BY pool.unclaimed_overdue DESC,
                         CASE WHEN pool.unclaimed_overdue THEN pool.room_no END,
                         pool.urgent DESC, pool.priority_notify DESC, pool.same_floor DESC,
                         pool.room_no, pool.created_at
                """,
                actor.staffId(), tenantId, targetDept);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String category = String.valueOf(row.get("type"));
            byCategory.merge(category, 1, Integer::sum);
            if (categoryFilter != null && !categoryFilter.equals(category)) {
                continue;
            }
            boolean urgent = Boolean.TRUE.equals(row.get("urgent"));
            if (urgentFilter != null && urgentFilter != urgent) {
                continue;
            }
            String poolItemSource = "repair".equals(category)
                    ? ("desk".equals(row.get("source")) ? "desk_forward" : "staff_report")
                    : (row.get("pool_source") != null ? String.valueOf(row.get("pool_source")) : "desk_forward");
            if (sourceFilter != null && !sourceFilter.equals(poolItemSource)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", String.valueOf(row.get("id")));
            item.put("roomNo", row.get("room_no"));
            item.put("floor", row.get("floor"));
            item.put("title", row.get("title"));
            item.put("category", category);
            item.put("source", poolItemSource);
            item.put("urgent", row.get("urgent"));
            item.put("ticketNo", row.get("ticket_no"));
            item.put("priorityNotify", row.get("priority_notify"));
            item.put("sameFloor", row.get("same_floor"));
            // 未接提醒階段：原值回傳，還沒進提醒階段就是 null。這裡只是把
            // tasks.reminder_stage 露給 App 標示單卡，排序仍走上面的
            // unclaimed_overdue 旗標，兩者不互相影響。
            item.put("reminderStage", row.get("reminder_stage"));
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

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("counts", counts);
        result.put("activeStaffCount", activeStaffCount(tenantId, targetDept));
        return result;
    }

    /**
     * 回答「這次查詢要看哪一個部門的待接池」。房務、工務、櫃檯員工只能看自己帳號上的
     * 部門，所以省略 dept 時用帳號上的部門，指定別的部門時以 403 拒絕。主管與老闆的
     * 帳號沒有部門，要看哪一池只能由呼叫端指定，省略時無從推斷，以 422 要求補上。
     *
     * <p>現場角色而帳號沒設部門是員工資料本身不完整，既推斷不出部門也不是權限問題，
     * 以 422 請管理者去補帳號設定。
     */
    private String resolvePoolDept(CurrentActor actor, String deptFilter) {
        boolean fieldStaff = FIELD_DEPTS.contains(actor.role());
        if (deptFilter != null) {
            if (!FIELD_DEPTS.contains(deptFilter)) {
                throw new UnprocessableEntityException("dept 只能是 housekeeping、engineering 或 frontdesk");
            }
            if (fieldStaff && !deptFilter.equals(actor.dept())) {
                throw new ForbiddenException("forbidden_dept_pool", "只能查看自己部門的待認領");
            }
            return deptFilter;
        }
        if (!fieldStaff) {
            throw new UnprocessableEntityException("dept_required", "主管與老闆不屬於單一部門，查待認領要指定 dept");
        }
        if (actor.dept() == null) {
            throw new UnprocessableEntityException("此帳號未設部門，沒有對應的部門待接池");
        }
        return actor.dept();
    }

    /**
     * 數出這個部門現在有幾個人能把池裡的單接走。畫面要靠這個數字分辨兩種都看不到單的
     * 情況：有人在職而池是空的，代表單都被接走了；一個人都沒有，代表沒有人能搶單，
     * 該提示管理者補人。待開通與停用的帳號都還不能接單，只有 status 是 active 的算數。
     */
    private int activeStaffCount(long tenantId, String dept) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND status = 'active'",
                Integer.class, tenantId, dept);
        return count == null ? 0 : count;
    }

    /**
     * 查看呼叫者的今日進度與下一間，形狀對應 contracts 的 MyToday。統計的口徑：
     * 打掃總數＝本人今日（work_date＝今天）未取消的打掃任務數，打掃完成數＝其中
     * done 或 inspected 的數量；查房通過數＝本人 inspection 為 passed 的任務數；
     * 今日完成單數＝本人今天完成（done_at 落在今天）且
     * 狀態 done 或 inspected 的任務數；臨時需求完成數＝其中非打掃類 done 的數量；
     * 預估剩餘工時＝Σ（accepted、doing、deferred、interrupted）max(0, 預估分鐘 −
     * 已做分鐘），進行中的段落以現在時刻折算。下一間只列 accepted，排序為 redo
     * 次數多的先、非打掃類先於打掃類、最後依房號遞增，讓員工先清掉臨時需求再回
     * 打掃動線。連續零退回天數的計算定義尚未拍板，先固定回 0。
     *
     * <p>首頁任務列（todayTasks）收錄本人今日未取消的全部任務（accepted、doing、
     * deferred、interrupted、done、inspected 六個狀態），已做分鐘沿用
     * {@link #elapsedMinutes} 的口徑。它和 nextTasks 是兩個不同用途的陣列：
     * nextTasks 只是其中 accepted 的推薦子集，排序規則也不同，兩邊各自組。
     *
     * <p>工務部門（dept=engineering）的呼叫者另外帶 engineeringSummary：三個計數欄位
     * 加上單列 tickets（手上與今天完修的工務單，組裝見 {@link #engineeringTickets}）；
     * 其他部門這個物件為 null。
     */
    public Map<String, Object> myToday() {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
        OffsetDateTime dayStart = today.atStartOfDay(clock.getZone()).toOffsetDateTime();
        OffsetDateTime dayEnd = today.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT t.id, t.type, t.state, t.title, t.est_min, t.worked_sec, t.segment_started_at,
                       t.work_date, t.done_at, t.redo_count, t.inspection, t.inspection_waived,
                       t.hold_reason, t.urgent, t.source, r.room_no,
                       rt.ticket_no,
                       COALESCE(rt.is_urgent, t.urgent) AS ticket_is_urgent,
                       COALESCE(rt.created_at, t.created_at) AS ticket_created_at
                  FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                  LEFT JOIN repair_tickets rt ON rt.tenant_id = t.tenant_id AND rt.task_id = t.id
                 WHERE t.tenant_id = ? AND t.assignee_id = ?
                """,
                tenantId, actor.staffId());

        int cleaningTotal = 0;
        int cleaningDone = 0;
        int inspectedCount = 0;
        int completedCount = 0;
        int adHocCompleted = 0;
        long remainingMinutes = 0;
        // 工務視角今日進度（engineeringSummary）：手上張數（accepted／doing 的工務單）、
        // 今天已完修（今天 done 的工務單）、急件數（手上單中 isUrgent 的數量）。與上面
        // 幾個既有統計共用同一次 rows 查詢，不為了這三個數字另開一次查表。
        int engineeringOpenCount = 0;
        int engineeringCompletedToday = 0;
        int engineeringUrgentCount = 0;
        // 工務首頁單列（engineeringSummary.tickets）：與上面三個計數欄位用同一組判定——
        // 手上單（accepted／doing）加上今天 done 的工務單——所以陣列筆數與 openCount／
        // completedToday 不會各算各的。排序鍵用到開單時間，不是回應欄位，比照
        // todayTasks 的做法排資料列、排完再組回應。
        List<Map<String, Object>> engineeringTicketRows = new ArrayList<>();
        List<Map<String, Object>> nextTasks = new ArrayList<>();
        // 首頁任務列（todayTasks）：先把符合收錄口徑的資料列挑出來，排序完再組回應。
        // 排序鍵用到 done_at，但它不是 TodayTask 的欄位，所以排資料列而不是排組好的 Map。
        List<Map<String, Object>> todayTaskRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String type = String.valueOf(row.get("type"));
            String state = String.valueOf(row.get("state"));
            boolean doneLike = "done".equals(state) || "inspected".equals(state);
            if ("cleaning".equals(type) && today.equals(toLocalDate(row.get("work_date")))
                    && !"cancelled".equals(state)) {
                cleaningTotal++;
                if (doneLike) {
                    cleaningDone++;
                }
            }
            if ("passed".equals(row.get("inspection"))) {
                inspectedCount++;
            }
            OffsetDateTime doneAt = SqlTimestamps.toOffsetDateTime(row.get("done_at"));
            boolean doneToday = doneAt != null && !doneAt.isBefore(dayStart) && doneAt.isBefore(dayEnd);
            if (doneLike && doneToday) {
                completedCount++;
                if (!"cleaning".equals(type) && "done".equals(state)) {
                    adHocCompleted++;
                }
            }
            if ("accepted".equals(state) || "doing".equals(state)
                    || "deferred".equals(state) || "interrupted".equals(state)) {
                remainingMinutes += remainingMinutesOf(row, now);
            }
            if (today.equals(toLocalDate(row.get("work_date"))) && TODAY_TASK_STATES.contains(state)) {
                todayTaskRows.add(row);
            }
            if ("accepted".equals(state)) {
                Map<String, Object> next = new LinkedHashMap<>();
                next.put("taskId", String.valueOf(row.get("id")));
                next.put("roomNo", row.get("room_no"));
                next.put("title", row.get("title"));
                next.put("type", type);
                next.put("redoCount", row.get("redo_count"));
                nextTasks.add(next);
            }
            if ("repair".equals(type)) {
                boolean open = "accepted".equals(state) || "doing".equals(state);
                if (open) {
                    engineeringOpenCount++;
                    // 急件數與單列的 isUrgent 取同一個值（repair_tickets.is_urgent，沒有
                    // 工務單列時退回 tasks.urgent）。contracts 把 urgentCount 定義成
                    // 「手上單中 isUrgent=true 的筆數」，而 EngineeringTicket.isUrgent 的
                    // 口徑又是 RepairTicketSummary.isUrgent，兩邊若各讀各的欄位，同一份
                    // 回應裡的數字就可能和清單對不上。
                    if (Boolean.TRUE.equals(row.get("ticket_is_urgent"))) {
                        engineeringUrgentCount++;
                    }
                }
                boolean completedToday = "done".equals(state) && doneToday;
                if (completedToday) {
                    engineeringCompletedToday++;
                }
                if (open || completedToday) {
                    engineeringTicketRows.add(row);
                }
            }
        }
        // 排序口徑（contracts MyToday.todayTasks）：未結束的四個狀態排在已結束的
        // done／inspected 之前；未結束組內 doing 最前、其餘依房號遞增；已結束組
        // 依完成時間由新到舊。兩組的次級排序鍵不同，所以先比組別再各自比。
        todayTaskRows.sort(Comparator
                .comparingInt((Map<String, Object> r) -> isFinishedState(String.valueOf(r.get("state"))) ? 1 : 0)
                .thenComparingInt(r -> "doing".equals(String.valueOf(r.get("state"))) ? 0 : 1)
                .thenComparing(r -> isFinishedState(String.valueOf(r.get("state")))
                        ? "" : String.valueOf(r.get("room_no")))
                .thenComparing(
                        r -> isFinishedState(String.valueOf(r.get("state")))
                                ? SqlTimestamps.toOffsetDateTime(r.get("done_at")) : null,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(r -> String.valueOf(r.get("room_no"))));
        List<Map<String, Object>> todayTasks = new ArrayList<>();
        for (Map<String, Object> row : todayTaskRows) {
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("taskId", String.valueOf(row.get("id")));
            task.put("roomNo", row.get("room_no"));
            task.put("title", row.get("title"));
            task.put("type", row.get("type"));
            task.put("state", row.get("state"));
            task.put("redoCount", row.get("redo_count"));
            task.put("estMin", row.get("est_min"));
            task.put("workedMinutes", elapsedMinutes(row));
            task.put("holdReason", row.get("hold_reason"));
            task.put("inspection", row.get("inspection"));
            task.put("inspectionWaived", row.get("inspection_waived"));
            todayTasks.add(task);
        }

        nextTasks.sort(Comparator
                .comparingInt((Map<String, Object> n) -> ((Number) n.get("redoCount")).intValue()).reversed()
                .thenComparing(n -> "cleaning".equals(n.get("type")) ? 1 : 0)
                .thenComparing(n -> String.valueOf(n.get("roomNo"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cleaningDone", cleaningDone);
        result.put("cleaningTotal", cleaningTotal);
        result.put("inspectedCount", inspectedCount);
        result.put("completedCount", completedCount);
        result.put("adHocCompleted", adHocCompleted);
        result.put("remainingMinutes", (int) Math.max(0, remainingMinutes));
        result.put("todayTasks", todayTasks);
        result.put("nextTasks", nextTasks);
        result.put("zeroReturnStreakDays", 0);
        if ("engineering".equals(actor.dept())) {
            Map<String, Object> engineeringSummary = new LinkedHashMap<>();
            engineeringSummary.put("openCount", engineeringOpenCount);
            engineeringSummary.put("completedToday", engineeringCompletedToday);
            engineeringSummary.put("urgentCount", engineeringUrgentCount);
            engineeringSummary.put("tickets", engineeringTickets(engineeringTicketRows));
            result.put("engineeringSummary", engineeringSummary);
        } else {
            result.put("engineeringSummary", null);
        }
        return result;
    }

    /**
     * todayTasks 的收錄狀態（contracts TodayTask.state）：有負責人且今日有效的六個值。
     * cancelled 不收錄，還在池中（pool）、等審核（pending_review）、被退回（rejected）
     * 的單也不在首頁任務列。
     */
    private static final Set<String> TODAY_TASK_STATES =
            Set.of("accepted", "doing", "deferred", "interrupted", "done", "inspected");

    /**
     * 組 contracts 的 engineeringSummary.tickets：本人手上（accepted／doing）與今天完修
     * （done）的工務單。收錄判定在 {@link #myToday} 的統計迴圈內就做完，這裡只負責排序
     * 與逐欄組裝，所以陣列筆數與 openCount／completedToday 必然對得上。
     *
     * <p>排序（contracts）：急件在前，其次未結案（accepted、doing）在已完修（done）之前，
     * 同組內依開單時間由舊到新。
     *
     * <p>公共區域的判定沿用既有的公共區域佔位房口徑（房號等於
     * {@link AutomationRuleService#PUBLIC_AREA_ROOM_NO}，與
     * {@code RepairService.releaseToPool} 判斷「只有公共區域的單能放回池」同一把尺）：
     * 資料側 {@code tasks.room_id} 不可為空，公共區域的單一律掛在這間佔位房，所以契約要的
     * 「公共區域單沒有房號」在回應這層把 roomNo 換成 null。
     *
     * <p>{@code source} 與待認領池的修繕單同一套換算（{@link #deptPool}）：櫃檯交辦
     * （tasks.source=desk）→desk_forward，其餘→staff_report。
     */
    private List<Map<String, Object>> engineeringTickets(List<Map<String, Object>> ticketRows) {
        ticketRows.sort(Comparator
                .comparingInt((Map<String, Object> r) -> Boolean.TRUE.equals(r.get("ticket_is_urgent")) ? 0 : 1)
                .thenComparingInt(r -> "done".equals(String.valueOf(r.get("state"))) ? 1 : 0)
                .thenComparing(
                        r -> SqlTimestamps.toOffsetDateTime(r.get("ticket_created_at")),
                        Comparator.nullsLast(Comparator.naturalOrder())));
        List<Map<String, Object>> tickets = new ArrayList<>(ticketRows.size());
        for (Map<String, Object> row : ticketRows) {
            boolean publicArea =
                    AutomationRuleService.PUBLIC_AREA_ROOM_NO.equals(String.valueOf(row.get("room_no")));
            Map<String, Object> ticket = new LinkedHashMap<>();
            ticket.put("ticketNo", row.get("ticket_no"));
            ticket.put("taskId", String.valueOf(row.get("id")));
            ticket.put("roomNo", publicArea ? null : row.get("room_no"));
            ticket.put("isPublicArea", publicArea);
            ticket.put("title", row.get("title"));
            ticket.put("state", row.get("state"));
            ticket.put("isUrgent", Boolean.TRUE.equals(row.get("ticket_is_urgent")));
            ticket.put("source", "desk".equals(row.get("source")) ? "desk_forward" : "staff_report");
            tickets.add(ticket);
        }
        return tickets;
    }

    /** todayTasks 排序用的組別判定：done／inspected 算已結束，排在未結束的單之後。 */
    private static boolean isFinishedState(String state) {
        return "done".equals(state) || "inspected".equals(state);
    }

    /** 單一任務的剩餘分鐘：預估分鐘減已做分鐘，進行中的段落折算到現在，最低 0。 */
    private long remainingMinutesOf(Map<String, Object> row, OffsetDateTime now) {
        Number estMin = (Number) row.get("est_min");
        if (estMin == null) {
            return 0;
        }
        long workedSec = row.get("worked_sec") == null ? 0 : ((Number) row.get("worked_sec")).longValue();
        OffsetDateTime segmentStartedAt = SqlTimestamps.toOffsetDateTime(row.get("segment_started_at"));
        if (segmentStartedAt != null && segmentStartedAt.isBefore(now)) {
            workedSec += Duration.between(segmentStartedAt, now).getSeconds();
        }
        return Math.max(0, estMin.longValue() - workedSec / 60);
    }

    private static LocalDate toLocalDate(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return (LocalDate) v;
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
        detail.put("estMin", row.get("est_min"));
        detail.put("redoCount", row.get("redo_count"));
        detail.put("inspection", row.get("inspection"));
        detail.put("inspectionWaived", row.get("inspection_waived"));
        detail.put("roomNo", row.get("room_no"));
        detail.put("holdReason", row.get("hold_reason"));
        detail.put("deferredBySelf", row.get("deferred_by_self"));
        detail.put("elapsedMinutes", minutes);
        detail.put("accumulatedMinutes", minutes);
        detail.put("checkedSopItems", checkedSopItemIds(row));
        detail.put("skippedSopItems", skippedSopItemIds(row));
        detail.put("handovers", handoverService.handoversOf(row));
        detail.put("returnInfo", handoverService.returnInfoOf(row));
        detail.put("handoverRead", handoverService.isRead(
                ((Number) row.get("tenant_id")).longValue(),
                UUID.fromString(String.valueOf(row.get("id"))),
                CurrentActor.require().staffId()));
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
     * TaskDetail 的 skippedSopItems：被跳過的 SOP 項對應的 sop_items.id。形狀與
     * checkedSopItems 相同，差別只在讀的是勾記列的 skipped 旗標；同樣要 join 回
     * sop_items 把 item_key 快照還原成 id。
     */
    private List<UUID> skippedSopItemIds(Map<String, Object> row) {
        return jdbcTemplate.queryForList(
                """
                SELECT s.id
                  FROM task_sop_checks c
                  JOIN sop_items s ON s.tenant_id = c.tenant_id AND s.item_key = c.item_key
                 WHERE c.tenant_id = ? AND c.task_id = ? AND c.skipped
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
