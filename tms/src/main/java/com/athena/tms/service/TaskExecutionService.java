package com.athena.tms.service;

import com.athena.tms.common.codes.TaskState;
import com.athena.tms.common.codes.TaskType;
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
import com.athena.tms.repair.RepairResolution;
import com.athena.tms.repair.RepairService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TaskExecutionService(
            TaskEngine taskEngine,
            JdbcTemplate jdbcTemplate,
            PushOutboxWriter pushOutboxWriter,
            TaskRowLookup taskRowLookup,
            RepairService repairService,
            HandoverService handoverService,
            NotificationService notificationService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.taskEngine = taskEngine;
        this.jdbcTemplate = jdbcTemplate;
        this.pushOutboxWriter = pushOutboxWriter;
        this.taskRowLookup = taskRowLookup;
        this.repairService = repairService;
        this.handoverService = handoverService;
        this.notificationService = notificationService;
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
        try {
            return transition(tenantId, taskId, actor,
                    (actorRef) -> taskEngine.claim(tenantId, taskId, actor.dept(), actorRef));
        } catch (StateConflictException conflict) {
            throw claimConflictWithClaimerName(tenantId, taskId, conflict);
        }
    }

    /**
     * 被別人搶先認領時，回應要說得出是誰認領的，讓員工知道去找誰而不是以為系統出錯。
     *
     * <p>查不到認領人姓名時照原樣往上拋：狀態不符還有別的原因（不同部門、這張單已經
     * 結案），那些情形講不出姓名。
     */
    private StateConflictException claimConflictWithClaimerName(
            long tenantId, UUID taskId, StateConflictException conflict) {
        List<String> names = jdbcTemplate.queryForList(
                """
                SELECT s.name FROM tasks t
                  JOIN staff_accounts s ON s.tenant_id = t.tenant_id AND s.id = t.assignee_id
                 WHERE t.tenant_id = ? AND t.id = ? AND t.state <> 'pool'
                """,
                String.class, tenantId, taskId);
        if (names.isEmpty() || names.get(0) == null) {
            return conflict;
        }
        return new StateConflictException("這單已被" + names.get(0) + "認領");
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
     * 負責人完成自己進行中的任務（contracts tasksComplete）。完成與完成後的連動包在同一個
     * 交易裡，不會出現「單已結案但報修人沒收到通知」的中間態。修繕任務（工務單）的完成
     * 另外帶完修結果與完修收尾，見 {@link #completeRepair}。
     */
    @Transactional
    public Map<String, Object> complete(UUID taskId, CompleteTaskRequest request) {
        long tenantId = TenantContext.require();
        Map<String, Object> task = taskRowLookup.require(tenantId, taskId);
        if (TaskType.REPAIR.equals(String.valueOf(task.get("type")))) {
            CompleteTaskRequest repairRequest =
                    request == null ? new CompleteTaskRequest(null, null, null) : request;
            return completeRepair(tenantId, taskId, task, repairRequest);
        }
        Map<String, Object> detail = transition(tenantId, taskId, CurrentActor.require(),
                (actorRef) -> taskEngine.complete(tenantId, taskId, actorRef));
        return withCompletionExtras(tenantId, CurrentActor.require(), task, detail);
    }

    /**
     * 工務完成一張工務單。完修結果決定兩件事：要不要附這一次的完修照片，以及單是落在 done
     * 等主管驗收、還是直接結案（closed）。兩者都照 {@link RepairResolution} 的對照。
     * 狀態轉移成功後，完修收尾交給 {@link RepairService#resolveOnComplete}。
     */
    private Map<String, Object> completeRepair(
            long tenantId, UUID taskId, Map<String, Object> task, CompleteTaskRequest request) {
        RepairResolution resolution = RepairResolution.of(request.resolutionType());
        if (resolution.requiresPhotos()) {
            requireCompletionPhotos(tenantId, taskId, request);
        }
        Map<String, Object> completedPayload = Map.of("resolutionType", resolution.code());
        Map<String, Object> detail = transition(tenantId, taskId, CurrentActor.require(),
                (actorRef) -> taskEngine.complete(
                        tenantId, taskId, actorRef, completedPayload, resolution.closesOnComplete()));
        repairService.resolveOnComplete(taskId, request.photoIds(), request.resolutionNote(), resolution);
        return withCompletionExtras(tenantId, CurrentActor.require(), task, detail);
    }

    /**
     * 完成回應在 TaskDetail 之上多帶的兩個值（contracts TaskCompletion）：接下來該去哪一間
     * （nextStep），以及今天做到第幾張打掃單（cleaningDoneToday）。
     *
     * <p>兩個值都在送出完成的同一次往返裡給完。要員工為了知道下一間是哪一間再打一次端點，
     * 完成畫面就會在訊號差的樓層卡住，而那正是最需要立刻看到回饋的時候。
     *
     * <p>今日進度只有打掃類帶。那個計數數的是打掃單，備品單完成時回一個打掃數字，畫面會
     * 顯示一個與這次操作無關的進度。
     */
    private Map<String, Object> withCompletionExtras(
            long tenantId, CurrentActor actor, Map<String, Object> completedTask, Map<String, Object> detail) {
        Map<String, Object> response = new LinkedHashMap<>(detail);
        List<Map<String, Object>> rows = openAndDoneRowsOf(tenantId, actor);
        response.put("nextStep", nextStepOf(rows));
        if ("cleaning".equals(String.valueOf(completedTask.get("type")))) {
            response.put("cleaningDoneToday", cleaningDoneTodayOf(rows));
        }
        response.put("celebrationEnabled", celebrationEnabled(tenantId));
        return response;
    }

    /**
     * 租戶有沒有開著完成慶祝的氣氛呈現，給完成畫面決定要不要呈現飾帶、彩屑與今日進度。
     *
     * <p>租戶沒有設定過這個鍵時視為開著，完成畫面照常呈現氣氛。
     */
    private boolean celebrationEnabled(long tenantId) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT value #>> '{}' FROM tenant_config WHERE tenant_id = ? AND key = 'completion_celebration_enabled'",
                String.class, tenantId);
        return values.isEmpty() || !"false".equals(values.get(0));
    }

    /**
     * 算兩個衍生值要用的資料列：呼叫者名下還沒結束的單，加上今天已結案的打掃單。
     *
     * <p>一次查完兩個值共用的資料列，不為了 nextStep 和 cleaningDoneToday 各查一次——
     * 兩次查詢之間若有別的動作落地，同一份回應裡的兩個值會對應到不同時刻的世界。
     *
     * <p>兩個值都是查詢期算出來的，不存欄位也不存計數器：存起來就會和首頁的「已清完間數」
     * 各算各的，而那兩個數字必須永遠一致。
     */
    private List<Map<String, Object>> openAndDoneRowsOf(long tenantId, CurrentActor actor) {
        return jdbcTemplate.queryForList(
                """
                SELECT t.id, t.type, t.state, t.work_date, t.done_at, t.redo_count, r.room_no
                  FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND t.assignee_id = ?
                   AND t.state IN ('accepted', 'doing', 'interrupted', 'deferred', """ + TaskState.FINISHED_SQL_LIST + """
                       )
                """,
                tenantId, actor.staffId());
    }

    /**
     * 完成之後員工該去哪裡，三者擇一：還有待開始的單就指那一張（next_task）、沒有待開始
     * 但還有沒做完的就指那一張（unfinished_task）、兩者都沒有就是今天排定的房都清完了
     * （all_clear）。
     *
     * <p>種類寫成 kind 這個封閉三值，不讓 App 靠「taskId 是不是 null」推斷：「查不到下一間」
     * 與「今天都清完了」在畫面上會長得一樣，員工看到的結論卻相反。
     *
     * <p>待開始那一組的排序與首頁「下一間」（{@link #myToday} 的 nextTasks）同一套，否則
     * 同一時刻兩個畫面會指向不同房間。待開始收錄時不看日期，也與 nextTasks 一致。
     * 沒做完那一組依進行中、已中斷、已延後排，同一組內房號遞增；它們屬不屬於今天，判定
     * 沿用 {@link #belongsToToday}。
     */
    private Map<String, Object> nextStepOf(List<Map<String, Object>> rows) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
        OffsetDateTime dayStart = today.atStartOfDay(clock.getZone()).toOffsetDateTime();
        OffsetDateTime dayEnd = today.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime();

        List<Map<String, Object>> accepted = new ArrayList<>();
        List<Map<String, Object>> unfinished = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String state = String.valueOf(row.get("state"));
            if ("accepted".equals(state)) {
                accepted.add(row);
                continue;
            }
            if (!UNFINISHED_NEXT_STEP_STATES.contains(state)) {
                continue;
            }
            OffsetDateTime doneAt = SqlTimestamps.toOffsetDateTime(row.get("done_at"));
            boolean doneToday = doneAt != null && !doneAt.isBefore(dayStart) && doneAt.isBefore(dayEnd);
            if (belongsToToday(toLocalDate(row.get("work_date")), state, today, doneToday)) {
                unfinished.add(row);
            }
        }

        accepted.sort(Comparator
                .comparingInt((Map<String, Object> r) -> ((Number) r.get("redo_count")).intValue()).reversed()
                .thenComparing(r -> "cleaning".equals(String.valueOf(r.get("type"))) ? 1 : 0)
                .thenComparing(r -> String.valueOf(r.get("room_no"))));
        unfinished.sort(Comparator
                .comparingInt((Map<String, Object> r) -> UNFINISHED_NEXT_STEP_ORDER.indexOf(String.valueOf(r.get("state"))))
                .thenComparing(r -> String.valueOf(r.get("room_no"))));

        if (!accepted.isEmpty()) {
            return nextStep("next_task", accepted.getFirst());
        }
        if (!unfinished.isEmpty()) {
            return nextStep("unfinished_task", unfinished.getFirst());
        }
        Map<String, Object> allClear = new LinkedHashMap<>();
        allClear.put("kind", "all_clear");
        return allClear;
    }

    /** 挑選順序：進行中的排最前，再來已中斷，最後已延後。手上正在做的那一間最該先回去。 */
    private static final List<String> UNFINISHED_NEXT_STEP_ORDER = List.of("doing", "interrupted", "deferred");

    private static final Set<String> UNFINISHED_NEXT_STEP_STATES = Set.copyOf(UNFINISHED_NEXT_STEP_ORDER);

    private static Map<String, Object> nextStep(String kind, Map<String, Object> row) {
        Map<String, Object> nextStep = new LinkedHashMap<>();
        nextStep.put("kind", kind);
        nextStep.put("taskId", String.valueOf(row.get("id")));
        nextStep.put("roomNo", row.get("room_no"));
        return nextStep;
    }

    /**
     * 今天做到第幾張打掃單：本人今日（work_date＝今天）未取消、已結案的打掃任務數。
     *
     * <p>收錄口徑與首頁「X / Y 間房已清完」的分子（{@link #myToday} 的 cleaningDone）
     * 同一套，含鎖定免查房而直接結案的單——完成畫面與首頁給出不同數字，員工無從判斷
     * 哪一個才算數。
     */
    private int cleaningDoneTodayOf(List<Map<String, Object>> rows) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
        int done = 0;
        for (Map<String, Object> row : rows) {
            String state = String.valueOf(row.get("state"));
            boolean doneLike = TaskState.isFinished(state);
            if (doneLike && "cleaning".equals(String.valueOf(row.get("type")))
                    && today.equals(toLocalDate(row.get("work_date")))) {
                done++;
            }
        }
        return done;
    }

    /**
     * 完成前的完修照片驗證：完修結果要附照片時（哪幾種要附見 {@link RepairResolution}），
     * 必須附至少 1 張這一次的完修照片才能完成，缺照片時回 422 completion_photo_required，
     * 不進任何狀態轉移。放行的話，工務單會在沒有完修證據的情況下進待驗收，主管就無從確認
     * 真的修好了。
     *
     * <p>驗收不通過退回重修的單，上一輪已綁在這張單上的完修照片不能拿來抵用：這次送來的照片
     * 全都是不通過之前就綁好的舊照片時，同樣視為沒附照片。驗收不通過不解綁舊照片，
     * 區分輪次看照片上傳時間與 repair_tickets.last_verify_failed_at。
     */
    private void requireCompletionPhotos(long tenantId, UUID taskId, CompleteTaskRequest request) {
        if (request.photoIds() == null || request.photoIds().isEmpty()) {
            throw new UnprocessableEntityException("completion_photo_required", "需要完修照片");
        }
        String placeholders = String.join(", ", Collections.nCopies(request.photoIds().size(), "?"));
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(taskId);
        params.addAll(request.photoIds());
        Integer stale = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM repair_photos p
                  JOIN repair_tickets rt ON rt.tenant_id = p.tenant_id AND rt.task_id = p.ticket_task_id
                 WHERE p.tenant_id = ? AND p.ticket_task_id = ?
                   AND rt.last_verify_failed_at IS NOT NULL
                   AND COALESCE(p.uploaded_at, p.created_at) <= rt.last_verify_failed_at
                   AND p.id IN (""" + placeholders + ")",
                Integer.class, params.toArray());
        if (stale != null && stale == request.photoIds().size()) {
            throw new UnprocessableEntityException("completion_photo_required", "需要這一次的完修照片");
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
     *
     * <p>事件值用 pool_new_request：對收件人而言這就是「待接池多了一張可以接的
     * 單」，和住客新需求進池是同一件事，差別只在單的來路。退池本身的來龍去脈
     * 記在任務事件（returned_to_pool）裡，不是推播要告訴收件人的事。
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
                    objectMapper, recipient, "pool_new_request", roomNo + " 已附交代退回待接池");
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
            PushOutboxWriter.targetOwnedTask(payload, String.valueOf(before.get("type")), taskId);
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
        PushOutboxWriter.targetOwnedTask(payload, String.valueOf(before.get("type")), taskId);
        pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload,
                "reassigned_to_me:" + taskId + ":seq:" + seq + ":" + newAssigneeId);

        return toDetail(taskRowLookup.require(tenantId, taskId));
    }

    /**
     * 負責人恢復已中斷的任務。
     *
     * <p>恢復跟開工一樣要先確認這位員工沒有另一筆進行中的任務：兩者都把一張單推進 doing，
     * 而「一位員工同時最多一筆 doing」是 uq_tasks_one_doing_per_staff 的落地義務。少了這
     * 一句，恢復會直接撞唯一索引炸成 500，員工看到的不是「哪一間還在進行中」。
     */
    public Map<String, Object> resume(UUID taskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        rejectIfAnotherTaskDoing(tenantId, taskId, actor);
        return transition(tenantId, taskId, actor,
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
        List<Map<String, Object>> rows = poolRows(tenantId, targetDept, actor.staffId());
        OffsetDateTime now = OffsetDateTime.now(clock);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        // 工務池分流磚的三個數字（contracts DeptPool.counts 的 urgent／staffReport／
        // deskForward）。與 byCategory 一樣在過濾之前累計，counts 才會維持「統計全部、
        // 篩選只影響 items」這條既有規則；來源值也直接用下面算好的 poolItemSource，
        // 不另寫一套換算，分流磚的數字與單卡上的來源標籤才不會各說各話。
        int urgentCount = 0;
        int staffReportCount = 0;
        int deskForwardCount = 0;
        for (Map<String, Object> row : rows) {
            String category = String.valueOf(row.get("type"));
            boolean urgent = Boolean.TRUE.equals(row.get("urgent"));
            String poolItemSource = "repair".equals(category)
                    ? ("desk".equals(row.get("source")) ? "desk_forward" : "staff_report")
                    : (row.get("pool_source") != null ? String.valueOf(row.get("pool_source")) : "desk_forward");
            byCategory.merge(category, 1, Integer::sum);
            if (urgent) {
                urgentCount++;
            }
            if ("staff_report".equals(poolItemSource)) {
                staffReportCount++;
            } else if ("desk_forward".equals(poolItemSource)) {
                deskForwardCount++;
            }
            if (categoryFilter != null && !categoryFilter.equals(category)) {
                continue;
            }
            if (urgentFilter != null && urgentFilter != urgent) {
                continue;
            }
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
            item.put("waitedMinutes", waitedMinutes(row, now));
            // 未接提醒階段：原值回傳，還沒進提醒階段就是 null。這裡只是把
            // tasks.reminder_stage 露給 App 標示單卡，排序仍走上面的
            // unclaimed_overdue 旗標，兩者不互相影響。
            item.put("reminderStage", row.get("reminder_stage"));
            item.put("returnReason", row.get("return_reason"));
            item.put("handoverNote", row.get("requeue_note"));
            item.put("verificationReturnReason", verificationReturnReason(row));
            item.put("createdAt", row.get("created_at"));
            items.add(item);
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("all", rows.size());
        counts.put("cleaning", byCategory.getOrDefault("cleaning", 0));
        counts.put("amenity", byCategory.getOrDefault("amenity", 0));
        counts.put("repair", byCategory.getOrDefault("repair", 0));
        counts.put("other", byCategory.getOrDefault("other", 0));
        // 急件與來源三格只有工務池有意義（contracts DeptPool.counts）：房務的待認領
        // 分流磚依任務類別分格，工務依急件與來源分格。房務池不給這三個鍵，讓畫面一看
        // 就知道這個維度在這一池不存在，而不是拿到一排 0 誤以為「這一池沒有急件」。
        if ("engineering".equals(targetDept)) {
            counts.put("urgent", urgentCount);
            counts.put("staffReport", staffReportCount);
            counts.put("deskForward", deskForwardCount);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("counts", counts);
        result.put("activeStaffCount", activeStaffCount(tenantId, targetDept));
        return result;
    }

    /**
     * 撈出一個部門待認領池的全部任務，排序就是 {@link #deptPool} 回應裡 items 的順序。
     * 抽成獨立方法是因為 {@code MyToday.poolBadgeCount} 的口徑契約寫明「與 listDeptPool
     * 不帶篩選時回的 items 筆數一致」——兩邊共用這一支，徽章上的數字與使用者點進去
     * 看到的張數就不可能各算各的。
     *
     * <p>先在內層算好 unclaimed_overdue 與 same_floor 兩個旗標，外層 ORDER BY 直接引用。
     * SQL 不允許在 ORDER BY 的運算式裡引用同一層 SELECT 的別名，不包一層就得在排序鍵
     * 重抄整段未接超時判斷。
     *
     * <p>{@code pool_entered_at} 是這張單最近一次進池的時刻，供
     * {@code PoolItem.waitedMinutes} 算等待時間。取最近一次 created 或 returned_to_pool
     * 事件、查不到事件時退回 {@code tasks.created_at}——與未接提醒掃描
     * （{@link UnclaimedReminderScanJob}）判斷「等多久了」同一把尺。不直接用
     * {@code created_at} 是因為被退回待認領的單會再進池一次，開單時刻早就不是它開始等的
     * 時刻了。
     */
    private List<Map<String, Object>> poolRows(long tenantId, String dept, UUID staffId) {
        return jdbcTemplate.queryForList(
                """
                SELECT * FROM (
                    SELECT t.id, t.state, t.title, t.type, t.urgent, t.priority_notify, t.pool_source, t.source,
                           t.return_reason, t.requeue_note, t.created_at, t.reminder_stage,
                           r.room_no, r.floor,
                           rt.ticket_no, rt.last_verify_failed_reason,
                           (t.reminder_stage IS NOT NULL) AS unclaimed_overdue,
                           COALESCE((SELECT MAX(te.created_at) FROM task_events te
                                      WHERE te.tenant_id = t.tenant_id AND te.task_id = t.id
                                        AND te.type IN ('created', 'returned_to_pool')),
                                    t.created_at) AS pool_entered_at,
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
                staffId, tenantId, dept);
    }

    /**
     * 一張待認領單已經等了幾分鐘：回應當下的時間減去進池時刻（{@code pool_entered_at}，
     * 口徑見 {@link #poolRows}），不足一分鐘算 0。由後端算而不是 App 依 createdAt 自己算，
     * 等待時間才有單一權威口徑，畫面也不必跟著裝置時鐘跳動。
     */
    private static int waitedMinutes(Map<String, Object> row, OffsetDateTime now) {
        OffsetDateTime enteredAt = SqlTimestamps.toOffsetDateTime(row.get("pool_entered_at"));
        if (enteredAt == null) {
            return 0;
        }
        long minutes = Duration.between(enteredAt.toInstant(), now.toInstant()).toMinutes();
        return (int) Math.max(0, minutes);
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
     * 打掃動線。
     *
     * <p>首頁任務列（todayTasks）收錄本人今日未取消的全部任務（accepted、doing、
     * deferred、interrupted、done、inspected 六個狀態），已做分鐘沿用
     * {@link #elapsedMinutes} 的口徑。它和 nextTasks 是兩個不同用途的陣列：
     * nextTasks 只是其中 accepted 的推薦子集，排序規則也不同，兩邊各自組——
     * 「子集」是契約寫明的關係，所以兩邊判定「這張單算不算今天的」必須同一套，
     * 判定寫在 {@link #belongsToToday}。
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
                       rt.ticket_no, rt.last_verify_failed_reason,
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
        // 今天已完修（今天完修、已結束的工務單，等驗收的 done 與驗收過的 closed 都算）、
        // 急件數（手上單中 isUrgent 的數量）。與上面
        // 幾個既有統計共用同一次 rows 查詢，不為了這三個數字另開一次查表。
        int engineeringOpenCount = 0;
        int engineeringCompletedToday = 0;
        int engineeringUrgentCount = 0;
        // 工務首頁單列（engineeringSummary.tickets）：與上面三個計數欄位用同一組判定——
        // 手上單（accepted／doing）加上今天完修、已結束的工務單——所以陣列筆數與 openCount／
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
            boolean doneLike = TaskState.isFinished(state);
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
                if (!"cleaning".equals(type) && doneLike) {
                    adHocCompleted++;
                }
            }
            if ("accepted".equals(state) || "doing".equals(state)
                    || "deferred".equals(state) || "interrupted".equals(state)) {
                remainingMinutes += remainingMinutesOf(row, now);
            }
            if (TODAY_TASK_STATES.contains(state)
                    && belongsToToday(toLocalDate(row.get("work_date")), state, today, doneToday)) {
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
                boolean completedToday = doneLike && doneToday;
                if (completedToday) {
                    engineeringCompletedToday++;
                }
                if (open || completedToday) {
                    engineeringTicketRows.add(row);
                }
            }
        }
        // 排序口徑（contracts MyToday.todayTasks）：未結束的四個狀態排在已結束的
        // done／inspected／closed 之前；未結束組內 doing 最前、其餘依房號遞增；已結束組
        // 依完成時間由新到舊。兩組的次級排序鍵不同，所以先比組別再各自比。
        todayTaskRows.sort(Comparator
                .comparingInt((Map<String, Object> r) -> TaskState.isFinished(String.valueOf(r.get("state"))) ? 1 : 0)
                .thenComparingInt(r -> "doing".equals(String.valueOf(r.get("state"))) ? 0 : 1)
                .thenComparing(r -> TaskState.isFinished(String.valueOf(r.get("state")))
                        ? "" : String.valueOf(r.get("room_no")))
                .thenComparing(
                        r -> TaskState.isFinished(String.valueOf(r.get("state")))
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
        Map<String, Object> duty = todayDuty(tenantId, actor.staffId(), today);
        result.put("shift", duty.get("shift"));
        result.put("floors", duty.get("floors"));
        result.put("poolBadgeCount", poolBadgeCount(tenantId, actor));
        result.put("unreadNotificationBadgeCount",
                notificationService.unreadCount(tenantId, actor.staffId()));
        if ("engineering".equals(actor.dept())) {
            Map<String, Object> engineeringSummary = new LinkedHashMap<>();
            engineeringSummary.put("openCount", engineeringOpenCount);
            engineeringSummary.put("completedToday", engineeringCompletedToday);
            engineeringSummary.put("urgentCount", engineeringUrgentCount);
            // 今天預計要修幾張、今日保養做了幾張：兩者都沒有資料來源可算。排房計畫
            // （cleaning_plans）只排房務的打掃，工務沒有任何「今天預計處理哪些單」的
            // 排程；repair_tickets 也分不出報修與定期保養——category 分的是冷氣、水電、
            // 家具設備、門鎖、其他，沒有保養這一維。契約把三個欄位都訂成 nullable 就是
            // 為了這種情況：寧可回 null 讓畫面退回「完成數＋在手數」的分母、保養那一格
            // 整個不畫，也不要湊一個會讓人誤以為真有排程的數字。
            engineeringSummary.put("plannedToday", null);
            engineeringSummary.put("maintenanceDone", null);
            engineeringSummary.put("maintenanceTotal", null);
            engineeringSummary.put("tickets", engineeringTickets(engineeringTicketRows));
            result.put("engineeringSummary", engineeringSummary);
        } else {
            result.put("engineeringSummary", null);
        }
        return result;
    }

    /**
     * 首頁問候列要接在日期後面的兩段：今天的班別與今天負責的樓層
     * （contracts MyToday.shift、MyToday.floors）。唯一記載「誰今天上哪一班、顧哪些房」
     * 的地方是已發佈的排房計畫——{@code cleaning_plans} 一份計畫對應一天一班，
     * {@code plan_rooms} 記這份計畫把哪些房分給誰，樓層由房間本身的 floor 來。草稿不算數，
     * 只認 published 的那一份（一天一班同時只會有一份已發佈）。
     *
     * <p>查不到就兩個都回 null，契約把兩欄都訂成 nullable 正是為此：排房計畫目前只排
     * 房務的打掃，工務與櫃檯同仁沒有任何排班資料可讀；房務同仁在計畫發佈之前也一樣。
     * 這種時候畫面就只顯示日期。
     *
     * <p>班別回的是排房計畫裡的那個值（{@code cleaning_plans.shift}，值域來自租戶設定
     * 的 shift_times），與排房計畫 API 回的 shift 同一套值。系統裡沒有任何地方存班別的
     * 中文顯示名稱，所以這裡不編一個出來。
     */
    private Map<String, Object> todayDuty(long tenantId, UUID staffId, LocalDate today) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT p.shift, r.floor
                  FROM plan_rooms pr
                  JOIN cleaning_plans p ON p.tenant_id = pr.tenant_id AND p.id = pr.plan_id
                  JOIN rooms r ON r.tenant_id = pr.tenant_id AND r.id = pr.room_id
                 WHERE pr.tenant_id = ? AND pr.staff_id = ?
                   AND p.work_date = ? AND p.status = 'published'
                 ORDER BY r.floor
                """,
                tenantId, staffId, today);
        Map<String, Object> duty = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            duty.put("shift", null);
            duty.put("floors", null);
            return duty;
        }
        List<Integer> floors = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Number floor = (Number) row.get("floor");
            if (floor != null && !floors.contains(floor.intValue())) {
                floors.add(floor.intValue());
            }
        }
        duty.put("shift", rows.get(0).get("shift"));
        duty.put("floors", floors);
        return duty;
    }

    /**
     * 分頁列「待認領」徽章上的數字（contracts MyToday.poolBadgeCount）：呼叫者現在搶得到
     * 的待認領單數。口徑契約寫明與 listDeptPool 不帶篩選時回的 items 筆數一致，所以這裡
     * 直接數 {@link #poolRows} 的列數——不帶篩選時 {@link #deptPool} 的 items 就是這些列
     * 一列一筆，兩邊走同一支查詢就不會出現「徽章說 3 張、點進去只有 2 張」。
     *
     * <p>主管與老闆不屬於單一部門，沒有「自己搶得到」的那一池（查待認領得自己指定看哪一
     * 池，見 {@link #resolvePoolDept}），所以回 0，畫面不顯示徽章。
     */
    private int poolBadgeCount(long tenantId, CurrentActor actor) {
        if (!FIELD_DEPTS.contains(actor.role()) || actor.dept() == null) {
            return 0;
        }
        return poolRows(tenantId, actor.dept(), actor.staffId()).size();
    }

    /**
     * todayTasks 的收錄狀態（contracts TodayTask.state）：有負責人且今日有效的七個值。
     * cancelled 不收錄，還在池中（pool）的單也不在首頁任務列。
     */
    private static final Set<String> TODAY_TASK_STATES =
            Stream.concat(Stream.of("accepted", "doing", "deferred", "interrupted"), TaskState.FINISHED.stream())
                    .collect(Collectors.toUnmodifiableSet());

    /**
     * 這張單算不算「今天的」。只有打掃類會排工作日（{@code TaskEngine.create} 只對
     * cleaning 要求 work_date，產品自己開單的 {@code GuestRequestService} 也只在
     * cleaning 時給值），備品、其他、修繕三類的 work_date 永遠是 null。若照字面只比
     * work_date，這三類就永遠進不了首頁任務列，但 nextTasks 收 accepted 時根本不看
     * 日期，同一份回應裡「今天的單」會有兩套口徑，契約寫的「nextTasks 是 todayTasks
     * 中 accepted 的子集」也就不成立。
     *
     * <p>所以沒有工作日的單改看狀態：還沒結束（accepted、doing、deferred、
     * interrupted）就是手上今天要處理的——這與 nextTasks、remainingMinutes 的口徑
     * 一致；已結束的（{@link TaskState#FINISHED}）則看完成時間是否落在今天，與今日完成單數同一
     * 把尺。有工作日的單維持比對工作日，打掃類的行為不變。
     */
    private static boolean belongsToToday(LocalDate workDate, String state, LocalDate today, boolean doneToday) {
        if (workDate != null) {
            return today.equals(workDate);
        }
        return TaskState.isFinished(state) ? doneToday : true;
    }

    /**
     * 組 contracts 的 engineeringSummary.tickets：本人手上（accepted／doing）與今天完修、
     * 已結束（done 或 closed）的工務單。收錄判定在 {@link #myToday} 的統計迴圈內就做完，這裡只負責排序
     * 與逐欄組裝，所以陣列筆數與 openCount／completedToday 必然對得上。
     *
     * <p>排序（contracts）：急件在前，其次未結束（accepted、doing）在已結束（done、closed）之前，
     * 同組內依開單時間由舊到新。已結束與今天已完修用同一個 {@link TaskState#isFinished} 判斷，
     * 兩邊各寫一份狀態清單的話，驗收結案的單會被算進已完修卻排在手上單之間。
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
                .thenComparingInt(r -> TaskState.isFinished(String.valueOf(r.get("state"))) ? 1 : 0)
                .thenComparing(
                        r -> SqlTimestamps.toOffsetDateTime(r.get("ticket_created_at")),
                        Comparator.nullsLast(Comparator.naturalOrder())));
        List<Map<String, Object>> tickets = new ArrayList<>(ticketRows.size());
        for (Map<String, Object> row : ticketRows) {
            boolean publicArea = AutomationRuleService.isPublicAreaRoomNo(row.get("room_no"));
            Map<String, Object> ticket = new LinkedHashMap<>();
            ticket.put("ticketNo", row.get("ticket_no"));
            ticket.put("taskId", String.valueOf(row.get("id")));
            ticket.put("roomNo", publicArea ? null : row.get("room_no"));
            ticket.put("isPublicArea", publicArea);
            ticket.put("title", row.get("title"));
            ticket.put("state", row.get("state"));
            ticket.put("isUrgent", Boolean.TRUE.equals(row.get("ticket_is_urgent")));
            ticket.put("source", "desk".equals(row.get("source")) ? "desk_forward" : "staff_report");
            // 已做分鐘走 TaskDetail.elapsedMinutes 同一支算法（契約要求兩邊同口徑）：
            // 已結束段落的累計秒數加上還在進行中的這一段。還沒開工的單兩者都沒有，自然是 0。
            ticket.put("workedMinutes", elapsedMinutes(row));
            ticket.put("verificationReturnReason", verificationReturnReason(row));
            tickets.add(ticket);
        }
        return tickets;
    }

    /**
     * 工務單正處於驗收不通過、退回重修中時，最近一次不通過的原因（contracts
     * PoolItem／EngineeringTicket.verificationReturnReason）；其他單為 null。
     * 不通過只會把單從 done 送回 accepted 或 pool，而再完修就回到 done，所以單還在
     * pool、accepted 或 doing 而且有不通過紀錄，就表示退回之後還沒再完修。
     * 兩個欄位共用這一處，待認領單卡與首頁單卡的「驗收退回」標示才不會各算各的。
     */
    private static Object verificationReturnReason(Map<String, Object> row) {
        String state = String.valueOf(row.get("state"));
        boolean reworking = "pool".equals(state) || "accepted".equals(state) || "doing".equals(state);
        return reworking ? row.get("last_verify_failed_reason") : null;
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
        // 任務頁走秒公式的基準值（contracts TaskDetail.accumulatedSeconds）：App 以
        // 「這個值 + (現在 − segmentStartedAt)」逐秒重算已做時間。基準必須不含正在走
        // 的這一段，所以直接給 worked_sec（段落結束才結算進去的累計秒數）；若改給含
        // 本段的分鐘值換算，App 會把正在走的這一段加兩次，顯示約兩倍的已做時間。
        detail.put("accumulatedSeconds",
                row.get("worked_sec") == null ? 0 : ((Number) row.get("worked_sec")).intValue());
        // 目前這一段計時的起算時刻（contracts TaskDetail.segmentStartedAt）。任務頁拿它
        // 逐秒重算碼表，不必為了跳動的秒數反覆打端點。欄位本身只在 doing 時有值——
        // 開工寫入、中斷與完工清空——所以其餘狀態自然是 null，這裡不另外判狀態。
        detail.put("segmentStartedAt", SqlTimestamps.toOffsetDateTime(row.get("segment_started_at")));
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
