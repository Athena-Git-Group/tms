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

@Service
public class TaskExecutionService {

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

    public Map<String, Object> detail(UUID taskId) {
        long tenantId = TenantContext.require();
        return toDetail(taskRowLookup.require(tenantId, taskId));
    }

    public Map<String, Object> deferBySelf(UUID taskId, HoldReasonRequest request) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        String reason = requireValidReason(request);
        return transition(tenantId, taskId, actor,
                (actorRef) -> taskEngine.deferBySelf(tenantId, taskId, reason, actorRef));
    }

    public Map<String, Object> interrupt(UUID taskId, HoldReasonRequest request) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        String reason = requireValidReason(request);
        return transition(tenantId, taskId, actor,
                (actorRef) -> taskEngine.interrupt(tenantId, taskId, reason, actorRef));
    }

    private static String requireValidReason(HoldReasonRequest request) {
        String reason = request == null || request.reason() == null ? "" : request.reason().strip();
        if (reason.isEmpty() || reason.length() > 60) {
            throw new UnprocessableEntityException("reason_invalid", "原因去掉頭尾空白後必須是 1–60 字");
        }
        return reason;
    }

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

    private boolean celebrationEnabled(long tenantId) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT value #>> '{}' FROM tenant_config WHERE tenant_id = ? AND key = 'completion_celebration_enabled'",
                String.class, tenantId);
        return values.isEmpty() || !"false".equals(values.get(0));
    }

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

    private static final List<String> UNFINISHED_NEXT_STEP_ORDER = List.of("doing", "interrupted", "deferred");

    private static final Set<String> UNFINISHED_NEXT_STEP_STATES = Set.copyOf(UNFINISHED_NEXT_STEP_ORDER);

    private static Map<String, Object> nextStep(String kind, Map<String, Object> row) {
        Map<String, Object> nextStep = new LinkedHashMap<>();
        nextStep.put("kind", kind);
        nextStep.put("taskId", String.valueOf(row.get("id")));
        nextStep.put("roomNo", row.get("room_no"));
        return nextStep;
    }

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

    public Map<String, Object> resume(UUID taskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        rejectIfAnotherTaskDoing(tenantId, taskId, actor);
        return transition(tenantId, taskId, actor,
                (actorRef) -> taskEngine.resume(tenantId, taskId, actorRef));
    }

    public Map<String, Object> deptPool(
            String deptFilter, String categoryFilter, Boolean urgentFilter, String sourceFilter) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        String targetDept = resolvePoolDept(actor, deptFilter);
        List<Map<String, Object>> rows = poolRows(tenantId, targetDept, actor.staffId());
        OffsetDateTime now = OffsetDateTime.now(clock);

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Integer> byCategory = new LinkedHashMap<>();
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

    /** SQL 不允許在 ORDER BY 的運算式裡引用同一層 SELECT 的別名，所以排序用的旗標先在內層查詢算好。 */
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

    private static int waitedMinutes(Map<String, Object> row, OffsetDateTime now) {
        OffsetDateTime enteredAt = SqlTimestamps.toOffsetDateTime(row.get("pool_entered_at"));
        if (enteredAt == null) {
            return 0;
        }
        long minutes = Duration.between(enteredAt.toInstant(), now.toInstant()).toMinutes();
        return (int) Math.max(0, minutes);
    }

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

    private int activeStaffCount(long tenantId, String dept) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND status = 'active'",
                Integer.class, tenantId, dept);
        return count == null ? 0 : count;
    }

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
        int engineeringOpenCount = 0;
        int engineeringCompletedToday = 0;
        int engineeringUrgentCount = 0;
        List<Map<String, Object>> engineeringTicketRows = new ArrayList<>();
        List<Map<String, Object>> nextTasks = new ArrayList<>();
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

    private int poolBadgeCount(long tenantId, CurrentActor actor) {
        if (!FIELD_DEPTS.contains(actor.role()) || actor.dept() == null) {
            return 0;
        }
        return poolRows(tenantId, actor.dept(), actor.staffId()).size();
    }

    private static final Set<String> TODAY_TASK_STATES =
            Stream.concat(Stream.of("accepted", "doing", "deferred", "interrupted"), TaskState.FINISHED.stream())
                    .collect(Collectors.toUnmodifiableSet());

    private static boolean belongsToToday(LocalDate workDate, String state, LocalDate today, boolean doneToday) {
        if (workDate != null) {
            return today.equals(workDate);
        }
        return TaskState.isFinished(state) ? doneToday : true;
    }

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
            ticket.put("workedMinutes", elapsedMinutes(row));
            ticket.put("verificationReturnReason", verificationReturnReason(row));
            tickets.add(ticket);
        }
        return tickets;
    }

    private static Object verificationReturnReason(Map<String, Object> row) {
        String state = String.valueOf(row.get("state"));
        boolean reworking = "pool".equals(state) || "accepted".equals(state) || "doing".equals(state);
        return reworking ? row.get("last_verify_failed_reason") : null;
    }

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
        detail.put("accumulatedSeconds",
                row.get("worked_sec") == null ? 0 : ((Number) row.get("worked_sec")).intValue());
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
