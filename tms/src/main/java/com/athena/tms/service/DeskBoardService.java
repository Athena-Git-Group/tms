package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.DeskAnswerText;
import com.athena.tms.common.SqlTimestamps;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.codes.ErrorCode;
import com.athena.tms.common.error.ApiException;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.desk.DeskAnswerRequest;
import com.athena.tms.dto.desk.DeskAssignRequest;
import com.athena.tms.dto.desk.DeskBoard;
import com.athena.tms.dto.desk.DeskMyToday;
import com.athena.tms.dto.desk.DeskTask;
import com.athena.tms.dto.desk.DeskTaskDetail;
import com.athena.tms.dto.desk.DeskTimelineEntry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 櫃檯 Web 的看板、單筆詳情與派單。
 *
 * <p>兩台櫃檯可能同時處理同一張待派單，先送到的生效。後到的那一次會收到 409 {@code task_not_in_triage}，
 * 說明由 {@link TaskNotInTriageExplainer} 組。
 */
@Service
public class DeskBoardService {
    private static final Set<String> ASSIGNABLE_DEPTS = Set.of("housekeeping", "engineering");
    private static final Set<String> ASSIGN_VIAS = Set.of("button", "drag");
    private static final String DEFAULT_VIA = "button";
    /** 開單人只對櫃檯代開與同仁報修有意義；住客與系統建的單不帶。 */
    private static final Set<String> CREATOR_NAMED_SOURCES = Set.of("desk", "staff");
    private static final Set<String> ACCEPT_EVENTS = Set.of("assigned", "claimed", "reassigned");
    /** 畫面的復原按鈕只出現 5 秒，後端多留 5 秒給網路延遲與逾時後重送。 */
    private static final Duration UNDO_WINDOW = Duration.ofSeconds(10);

    /**
     * 組一張卡片要的每個欄位，一次查完。看板一次讀很多張單，姓名、住客與事件都在這一句裡接上，不逐張回頭查。
     * 開頭兩個參數都是現在的時間，用來找房間現在住著的訂房；呼叫的一方再接自己的 WHERE 參數。
     *
     * <p>各段的口徑：住客先看單上連結的訂房，沒有才看房間現在住著的訂房（入住已到、退房未到、沒有被取代）；
     * 等待起算是 created 與 returned_to_pool 兩種事件中最新一筆的時間，和待認領清單同一把尺；
     * 到手方式看最近一筆 claimed、assigned 或 reassigned 事件；
     * 清潔 SOP 只在打掃單已接、進行中或中斷時算，總數是飯店啟用中的清潔 SOP 項目，已勾選不含跳過的。
     */
    private static final String TASK_SELECT = """
            SELECT t.*, r.room_no, r.floor,
                   rt.ticket_no,
                   assignee.name AS assignee_name,
                   guest.guest_name, COALESCE(guest.vip, false) AS vip,
                   guest.checkin_at AS guest_checkin_at, guest.checkout_at AS guest_checkout_at,
                   waiting.since AS waiting_event_at,
                   accepted.created_at AS accepted_at, accepted.type AS accepted_type,
                   accepted.actor_role AS accepted_actor_role,
                   interrupted.created_at AS interrupted_at, interrupted.by_self AS interrupted_by_self,
                   deferred.by_name AS deferred_by_name,
                   sop.done AS sop_done, sop.total AS sop_total
              FROM tasks t
              JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
              LEFT JOIN repair_tickets rt ON rt.tenant_id = t.tenant_id AND rt.task_id = t.id
              LEFT JOIN staff_accounts assignee ON assignee.tenant_id = t.tenant_id AND assignee.id = t.assignee_id
              LEFT JOIN LATERAL (
                    SELECT rc.guest_name, (rc.attrs ->> 'vip') = 'true' AS vip, rc.checkin_at, rc.checkout_at
                      FROM reservations_cache rc
                     WHERE rc.tenant_id = t.tenant_id
                       AND CASE WHEN t.reservation_id IS NOT NULL THEN rc.id = t.reservation_id
                                ELSE rc.room_id = t.room_id AND rc.superseded_at IS NULL
                                     AND rc.checkin_at <= ? AND rc.checkout_at > ?
                           END
                     LIMIT 1) guest ON true
              LEFT JOIN LATERAL (
                    SELECT max(e.created_at) AS since
                      FROM task_events e
                     WHERE t.state = 'pool' AND e.tenant_id = t.tenant_id AND e.task_id = t.id
                       AND e.type IN ('created', 'returned_to_pool')) waiting ON true
              LEFT JOIN LATERAL (
                    SELECT e.created_at, e.type, e.actor_role
                      FROM task_events e
                     WHERE t.assignee_id IS NOT NULL AND e.tenant_id = t.tenant_id AND e.task_id = t.id
                       AND e.type IN ('claimed', 'assigned', 'reassigned')
                     ORDER BY e.seq DESC
                     LIMIT 1) accepted ON true
              LEFT JOIN LATERAL (
                    SELECT e.created_at, COALESCE((e.payload ->> 'by_self')::boolean, false) AS by_self
                      FROM task_events e
                     WHERE t.state = 'interrupted' AND e.tenant_id = t.tenant_id AND e.task_id = t.id
                       AND e.type = 'interrupted'
                     ORDER BY e.seq DESC
                     LIMIT 1) interrupted ON true
              LEFT JOIN LATERAL (
                    SELECT s.name AS by_name
                      FROM task_events e
                      LEFT JOIN staff_accounts s ON s.tenant_id = e.tenant_id AND s.id = e.actor_id
                     WHERE t.state = 'deferred' AND e.tenant_id = t.tenant_id AND e.task_id = t.id
                       AND e.type = 'deferred'
                     ORDER BY e.seq DESC
                     LIMIT 1) deferred ON true
              LEFT JOIN LATERAL (
                    SELECT (SELECT count(*) FROM task_sop_checks c
                             WHERE c.tenant_id = t.tenant_id AND c.task_id = t.id AND c.checked AND NOT c.skipped) AS done,
                           (SELECT count(*) FROM sop_items s
                             WHERE s.tenant_id = t.tenant_id AND s.applies_to = 'cleaning' AND s.active) AS total
                     WHERE t.type = 'cleaning' AND t.state IN ('accepted', 'doing', 'interrupted')) sop ON true
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TaskEngine taskEngine;
    private final TaskNotInTriageExplainer notInTriageExplainer;
    private final UndoAssignRejectionExplainer undoRejectionExplainer;
    private final DeskTaskMapper deskTaskMapper;
    private final Clock clock;

    public DeskBoardService(
            JdbcTemplate jdbcTemplate,
            TaskEngine taskEngine,
            TaskNotInTriageExplainer notInTriageExplainer,
            UndoAssignRejectionExplainer undoRejectionExplainer,
            DeskTaskMapper deskTaskMapper,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskEngine = taskEngine;
        this.notInTriageExplainer = notInTriageExplainer;
        this.undoRejectionExplainer = undoRejectionExplainer;
        this.deskTaskMapper = deskTaskMapper;
        this.clock = clock;
    }

    /**
     * 看板一次讀取：還沒結束的單（不限日期）加上今天結束的單，再加飯店的樓層與登入者本人今天的兩個張數。
     * 「今天」是注入的時鐘在伺服器時區的日期。一共三句查詢：單、樓層、張數。
     */
    public DeskBoard board() {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        LocalDate today = now.atZoneSameInstant(this.clock.getZone()).toLocalDate();
        OffsetDateTime todayStart = today.atStartOfDay(this.clock.getZone()).toOffsetDateTime();
        OffsetDateTime tomorrowStart = today.plusDays(1).atStartOfDay(this.clock.getZone()).toOffsetDateTime();
        List<DeskTask> tasks = this.taskRows(
                        now,
                        """
                         WHERE t.tenant_id = ?
                           AND (t.ended_at IS NULL OR (t.ended_at >= ? AND t.ended_at < ?))
                         ORDER BY t.created_at DESC, t.id DESC
                        """,
                        tenantId, todayStart, tomorrowStart)
                .stream()
                .map(this.deskTaskMapper::toDeskTask)
                .toList();
        List<Integer> floors = this.jdbcTemplate.queryForList(
                "SELECT DISTINCT floor FROM rooms WHERE tenant_id = ? ORDER BY floor", Integer.class, tenantId);
        return new DeskBoard(now, today, floors, tasks, this.myToday(tenantId, actor.staffId(), todayStart, tomorrowStart));
    }

    /**
     * 本人今天的已派單與當場回答張數，從事件現算。被復原的派單（下一筆緊接著是 triage_undone）不算，
     * 所以同一張單派出、復原、再派出只算最後那一次。
     */
    private DeskMyToday myToday(long tenantId, UUID staffId, OffsetDateTime from, OffsetDateTime until) {
        return this.jdbcTemplate.queryForObject(
                """
                SELECT count(*) FILTER (
                           WHERE e.type = 'triaged'
                             AND NOT EXISTS (SELECT 1 FROM task_events u
                                              WHERE u.tenant_id = e.tenant_id AND u.task_id = e.task_id
                                                AND u.seq = e.seq + 1 AND u.type = 'triage_undone')) AS assigned,
                       count(*) FILTER (WHERE e.type = 'answered') AS answered
                  FROM task_events e
                 WHERE e.tenant_id = ? AND e.actor_id = ? AND e.created_at >= ? AND e.created_at < ?
                   AND e.type IN ('triaged', 'answered')
                """,
                (rs, rowNum) -> new DeskMyToday(rs.getInt("assigned"), rs.getInt("answered")),
                tenantId, staffId, from, until);
    }

    private List<Map<String, Object>> taskRows(OffsetDateTime now, String whereAndOrder, Object... params) {
        Object[] all = new Object[params.length + 2];
        all[0] = now;
        all[1] = now;
        System.arraycopy(params, 0, all, 2, params.length);
        return this.jdbcTemplate.queryForList(TASK_SELECT + whereAndOrder, all);
    }

    /**
     * 單筆詳情：卡片欄位，加上住客、開單人、回覆與回答的人、到手與取消的說明、工務單的完修結果，以及歷程。
     *
     * <p>查詢次數固定，不隨事件筆數增加：單一句、這張單的全部事件一句，工務單再多一句完修結果。
     * 回答的人、開單人、經手的人與原負責人都從同一份事件裡取。
     */
    public DeskTaskDetail detail(UUID taskId) {
        long tenantId = TenantContext.require();
        Map<String, Object> row = this.requireTaskRow(tenantId, taskId);
        DeskTask task = this.deskTaskMapper.toDeskTask(row);
        List<Map<String, Object>> events = this.eventRows(tenantId, taskId);
        String answer = (String) row.get("answer");
        return new DeskTaskDetail(
                task,
                OffsetDateTime.now(this.clock),
                answer,
                answer == null ? null : latestActorName(events, "answered"),
                this.guestOf(row),
                CREATOR_NAMED_SOURCES.contains(task.source()) ? latestActorName(events, "created") : null,
                acceptedOf(task, events),
                "cancelled".equals(task.state())
                        ? new DeskTaskDetail.Cancellation((String) row.get("cancel_reason"), latestActorName(events, "cancelled"))
                        : null,
                "repair".equals(task.type()) ? this.repairOf(tenantId, taskId) : null,
                timeline(events, task));
    }

    /**
     * 把待派單派給房務或工務。
     *
     * <p>單是不是還在待派單，由派單那一句更新自己判斷：條件寫在 UPDATE 的 WHERE 裡，單已經不在待派單時一列也不改，
     * 也不寫事件。這樣兩台櫃檯同時送出時只有一台會成功，不會出現兩筆派單。
     */
    public DeskTask assign(UUID taskId, String idempotencyKey, DeskAssignRequest request) {
        requireIdempotencyKey(idempotencyKey);
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        String dept = request.dept();
        if (dept == null || !ASSIGNABLE_DEPTS.contains(dept)) {
            throw new UnprocessableEntityException("只能派給房務（housekeeping）或工務（engineering），收到: " + dept);
        }
        String via = request.via() == null ? DEFAULT_VIA : request.via();
        if (!ASSIGN_VIAS.contains(via)) {
            throw new UnprocessableEntityException("派單方式只能是 button 或 drag，收到: " + via);
        }
        this.requireTaskRow(tenantId, taskId);
        try {
            this.taskEngine.assignFromTriage(tenantId, taskId, dept, via, Actor.staff(actor.role(), actor.staffId()));
        } catch (StateConflictException notInTriage) {
            throw this.notInTriageExplainer.explain(tenantId, taskId);
        }
        return this.deskTaskMapper.toDeskTask(this.requireTaskRow(tenantId, taskId));
    }

    /**
     * 派出後復原，把剛派出的單收回待派單。
     *
     * <p>收不收回由復原那一句更新自己判斷，和派單一樣，兩台櫃檯同時復原、或派出後馬上被認領時只會有一邊生效。
     * 沒收回時才回頭說明原因，判斷順序見 {@link UndoAssignRejectionExplainer}。
     */
    public DeskTask undoAssign(UUID taskId, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        this.requireTaskRow(tenantId, taskId);
        OffsetDateTime triagedAfter = OffsetDateTime.now(this.clock).minus(UNDO_WINDOW);
        try {
            this.taskEngine.undoTriage(tenantId, taskId, triagedAfter, Actor.staff(actor.role(), actor.staffId()));
        } catch (StateConflictException notUndone) {
            throw this.undoRejectionExplainer.explain(tenantId, taskId);
        }
        return this.deskTaskMapper.toDeskTask(this.requireTaskRow(tenantId, taskId));
    }

    /**
     * 當場回答並結案：櫃檯直接回住客一句話，這張待派單就結束。
     *
     * <p>存下來與住客看到的都是去掉頭尾空白後的字，必須是 1 到 120 字。
     * 單是不是還在待派單由回答那一句更新自己判斷，和派單一樣，兩台櫃檯同時處理時只有先到的生效。
     */
    public DeskTask answer(UUID taskId, String idempotencyKey, DeskAnswerRequest request) {
        requireIdempotencyKey(idempotencyKey);
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        String answer = DeskAnswerText.require(request.answer());
        this.requireTaskRow(tenantId, taskId);
        try {
            this.taskEngine.answer(tenantId, taskId, answer, Actor.staff(actor.role(), actor.staffId()));
        } catch (StateConflictException notInTriage) {
            throw this.notInTriageExplainer.explain(tenantId, taskId);
        }
        return this.deskTaskMapper.toDeskTask(this.requireTaskRow(tenantId, taskId));
    }

    /**
     * 櫃檯的寫入一定要帶防重複鍵。畫面在網路逾時後不知道上一次有沒有成功，只能拿同一把鍵重送；
     * 沒帶鍵的請求重送時會被當成新的一次，同一張單就可能被處理兩次。
     */
    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequired();
        }
    }

    private Map<String, Object> requireTaskRow(long tenantId, UUID taskId) {
        return this.taskRows(OffsetDateTime.now(this.clock), " WHERE t.tenant_id = ? AND t.id = ?", tenantId, taskId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("查無任務: " + taskId));
    }

    /**
     * 這張單的全部事件，由舊到新。操作者與接手的人的姓名在這一句裡接上；
     * 接手的人記在指派、認領、轉派事件內容的 {@code assignee_id}。
     */
    private List<Map<String, Object>> eventRows(long tenantId, UUID taskId) {
        return this.jdbcTemplate.queryForList(
                """
                SELECT e.seq, e.created_at, e.type, e.actor_role, actor.name AS actor_name,
                       e.payload ->> 'dept' AS dept, e.payload ->> 'via' AS via, e.payload ->> 'answer' AS answer,
                       e.payload ->> 'state' AS initial_state, e.payload ->> 'reason' AS reason,
                       (e.payload ->> 'by_self')::boolean AS by_self, e.payload ->> 'note' AS note,
                       e.payload ->> 'resolutionType' AS resolution_type, (e.payload ->> 'rating')::int AS rating,
                       assignee.name AS assignee_name
                  FROM task_events e
                  LEFT JOIN staff_accounts actor ON actor.tenant_id = e.tenant_id AND actor.id = e.actor_id
                  LEFT JOIN staff_accounts assignee ON assignee.tenant_id = e.tenant_id
                       AND e.type IN ('assigned', 'claimed', 'reassigned')
                       AND assignee.id = (e.payload ->> 'assignee_id')::uuid
                 WHERE e.tenant_id = ? AND e.task_id = ?
                 ORDER BY e.seq
                """,
                tenantId, taskId);
    }

    /** 最近一筆這種事件的操作者姓名；住客與系統沒有姓名，回 null。 */
    private static String latestActorName(List<Map<String, Object>> events, String type) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (type.equals(events.get(i).get("type"))) {
                return (String) events.get(i).get("actor_name");
            }
        }
        return null;
    }

    /** 住客的認定同卡片的住客姓名；空房，或訂房上沒有住客姓名時為 null。日期取飯店時區（伺服器時區）。 */
    private DeskTaskDetail.Guest guestOf(Map<String, Object> row) {
        String name = (String) row.get("guest_name");
        if (name == null) {
            return null;
        }
        return new DeskTaskDetail.Guest(name, this.localDate(row.get("guest_checkin_at")), this.localDate(row.get("guest_checkout_at")));
    }

    private LocalDate localDate(Object timestamp) {
        OffsetDateTime at = SqlTimestamps.toOffsetDateTime(timestamp);
        return at == null ? null : at.atZoneSameInstant(this.clock.getZone()).toLocalDate();
    }

    /**
     * 到手的說明取卡片算到手方式的同一筆事件（最近一筆指派、認領或轉派）。
     * 轉派事件只記新的負責人，原負責人是再往前一筆到手事件接手的人。
     */
    private static DeskTaskDetail.Accepted acceptedOf(DeskTask task, List<Map<String, Object>> events) {
        if (task.acceptedVia() == null) {
            return null;
        }
        int latest = -1;
        int previous = -1;
        for (int i = 0; i < events.size(); i++) {
            if (ACCEPT_EVENTS.contains(events.get(i).get("type"))) {
                previous = latest;
                latest = i;
            }
        }
        boolean byManager = "manager_assigned".equals(task.acceptedVia()) || "reassigned".equals(task.acceptedVia());
        String byName = byManager && latest >= 0 ? (String) events.get(latest).get("actor_name") : null;
        String previousAssigneeName = "reassigned".equals(task.acceptedVia()) && previous >= 0
                ? (String) events.get(previous).get("assignee_name")
                : null;
        return new DeskTaskDetail.Accepted(task.acceptedVia(), task.acceptedAt(), byName, previousAssigneeName);
    }

    /** 完修結果與驗收通過的主管都只記在工務單上。 */
    private DeskTaskDetail.Repair repairOf(long tenantId, UUID taskId) {
        return this.jdbcTemplate.queryForList(
                """
                SELECT rt.resolution_type, s.name AS verified_by_name
                  FROM repair_tickets rt
                  LEFT JOIN staff_accounts s ON s.tenant_id = rt.tenant_id AND s.id = rt.verified_by
                 WHERE rt.tenant_id = ? AND rt.task_id = ?
                """,
                tenantId, taskId).stream()
                .findFirst()
                .map(r -> new DeskTaskDetail.Repair((String) r.get("resolution_type"), (String) r.get("verified_by_name")))
                .orElse(null);
    }

    /** 給櫃檯看的歷程；哪些事件略過不列由 {@link DeskTimelineVisibility} 決定。 */
    private static List<DeskTimelineEntry> timeline(List<Map<String, Object>> events, DeskTask task) {
        List<String> types = events.stream().map(event -> (String) event.get("type")).toList();
        List<DeskTimelineEntry> visible = new ArrayList<>();
        for (int i : DeskTimelineVisibility.visibleIndexes(types)) {
            visible.add(timelineEntry(events.get(i), task, previousAssigneeName(events, i)));
        }
        return visible;
    }

    /** 轉派事件之前最近一筆到手事件接手的人；不是轉派或找不到時為 null。 */
    private static String previousAssigneeName(List<Map<String, Object>> events, int index) {
        if (!"reassigned".equals(events.get(index).get("type"))) {
            return null;
        }
        for (int i = index - 1; i >= 0; i--) {
            if (ACCEPT_EVENTS.contains(events.get(i).get("type"))) {
                return (String) events.get(i).get("assignee_name");
            }
        }
        return null;
    }

    private static DeskTimelineEntry timelineEntry(Map<String, Object> event, DeskTask task, String previousAssigneeName) {
        String type = (String) event.get("type");
        return new DeskTimelineEntry(
                ((Number) event.get("seq")).intValue(),
                SqlTimestamps.toOffsetDateTime(event.get("created_at")),
                type,
                actorOf((String) event.get("actor_role"), (String) event.get("actor_name"), task.guestName()),
                timelineDetail(type, event, task, previousAssigneeName));
    }

    /** 每種事件只帶契約列給它的那幾項，其餘留 null 不輸出；畫面沒有句子的事件種類不帶內容。 */
    private static DeskTimelineEntry.Detail timelineDetail(
            String type, Map<String, Object> event, DeskTask task, String previousAssigneeName) {
        String reason = (String) event.get("reason");
        String note = (String) event.get("note");
        return switch (type) {
            case "created" -> new DeskTimelineEntry.Detail((String) event.get("dept"),
                    initialState((String) event.get("initial_state")), task.guestTyped(), null,
                    null, null, null, null, null, null, null, null, null, null);
            case "triaged" -> new DeskTimelineEntry.Detail((String) event.get("dept"), null, null, null,
                    event.get("via") == null ? DEFAULT_VIA : (String) event.get("via"),
                    null, null, null, null, null, null, null, null, null);
            case "answered" -> new DeskTimelineEntry.Detail(null, null, null, null, null,
                    (String) event.get("answer"), null, null, null, null, null, null, null, null);
            case "assigned", "claimed", "reassigned" -> new DeskTimelineEntry.Detail(null, null, null, null, null, null,
                    (String) event.get("assignee_name"), previousAssigneeName, null, null, null, null, null, null);
            case "interrupted", "deferred" -> new DeskTimelineEntry.Detail(null, null, null, null, null, null, null, null,
                    reason, Boolean.TRUE.equals(event.get("by_self")), null, null, null, null);
            case "returned_to_pool" -> new DeskTimelineEntry.Detail(null, null, null, null, null, null, null, null,
                    reason, null, note, null, null, null);
            case "repair_verification_failed" -> new DeskTimelineEntry.Detail(null, null, null, null, null, null, null,
                    null, reason, null, null, null, null, null);
            case "inspection_failed" -> new DeskTimelineEntry.Detail(null, null, null, null, null, null, null, null,
                    null, null, note, null, null, null);
            case "cancelled" -> new DeskTimelineEntry.Detail(null, null, null, null, null, null, null, null, null, null,
                    null, reason, null, null);
            case "completed" -> event.get("resolution_type") == null ? null : new DeskTimelineEntry.Detail(null, null,
                    null, null, null, null, null, null, null, null, null, null, (String) event.get("resolution_type"), null);
            case "rated" -> new DeskTimelineEntry.Detail(null, null, null, null, null, null, null, null, null, null,
                    null, null, null, (Integer) event.get("rating"));
            default -> null;
        };
    }

    /** 建立快照的起點。工務單已沒有主管審核，舊單記的 pending_review 當成直接進待認領。 */
    private static String initialState(String state) {
        return "pending_review".equals(state) ? "pool" : state;
    }

    /** 住客的姓名取這張單的住客；空房為 null。系統沒有姓名。 */
    private static DeskTimelineEntry.Actor actorOf(String role, String name, String guestName) {
        if ("guest".equals(role)) {
            return new DeskTimelineEntry.Actor(role, null, guestName);
        }
        if ("system".equals(role)) {
            return new DeskTimelineEntry.Actor(role, null, null);
        }
        return new DeskTimelineEntry.Actor("staff", role, name);
    }

    private static final class IdempotencyKeyRequired extends ApiException {
        IdempotencyKeyRequired() {
            super(400, ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "這個操作必須帶 Idempotency-Key");
        }
    }
}
