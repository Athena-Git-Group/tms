package com.athena.tms.bdd.steps.desk;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.DeskContext;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.dto.desk.DeskMyToday;
import com.athena.tms.dto.desk.DeskQuickReply;
import com.athena.tms.dto.desk.DeskTask;
import com.athena.tms.dto.desk.DeskTimelineEntry;
import com.athena.tms.service.JwtService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 本模組 {@code 櫃檯看板與派單/dsl.md} 的 Then 句型。
 *
 * <p>每一句照該列的「必查」做三件事：比對畫面拿到的回應、比對資料庫裡的權威狀態（{@code tasks}、
 * {@code task_events} 等），以及需要時以別人的身分再讀一次，確認別的畫面看到的也一樣。
 * 回應與資料庫只要有一邊對不上就算失敗，因為只看回應會放過「回應寫對、資料沒存」的錯。
 *
 * <p>這個類別裡的再讀都不會改動場景記著的回應。{@link ScenarioWorld#lastResponse()} 與 {@link DeskContext}
 * 只留最後一次呼叫，若再讀蓋掉它們，接在後面的 Then（例如「這次操作被拒絕」）會把再讀的結果當成 When 的結果而誤判。
 *
 * <p>「操作前」的單與事件取自 When 送出寫入前記下的 {@link LastOperationGuard}；
 * 這個模組會改單的 When 都會先記，句型只負責比對。
 */
public class DeskThenSteps {

    private static final String FRONTDESK = "frontdesk";
    private static final String HOUSEKEEPING = "housekeeping";
    private static final String ENGINEERING = "engineering";

    /** 本模組用語對照的「歷程事件」。 */
    private static final Map<String, String> TIMELINE_EVENT = Map.of(
            "建立", "created",
            "派單", "triaged",
            "復原派單", "triage_undone",
            "當場回答", "answered",
            "認領", "claimed",
            "開始", "started",
            "完成", "completed",
            "勾選清潔項目", "sop_checked");

    /** 本模組用語對照的「到手方式」。 */
    private static final Map<String, String> ACCEPTED_VIA = Map.of(
            "班前排房", "plan_assigned",
            "自己認領", "self_claimed",
            "主管指派", "manager_assigned",
            "被轉派", "reassigned");

    /** 本模組用語對照的「派單方式」。 */
    private static final Map<String, String> ASSIGN_VIA = Map.of("按鈕", "button", "拖曳", "drag");

    /** 本模組用語對照的「取消原因」。 */
    private static final Map<String, String> CANCEL_REASON = Map.of(
            "住客收回", "guest_withdrew",
            "主管取消", "manager_void",
            "排房被取代", "plan_superseded",
            "同仁撤回", "staff_withdrew");

    /** 本模組用語對照的「來源」。 */
    private static final Map<String, String> SOURCE = Map.of(
            "住客掃碼", "guest",
            "櫃檯開單", "desk",
            "系統排房", "system",
            "同仁報修", "staff");

    /** 歷程操作者身分與派單操作者角色共用的對照；住客與系統不在這裡，因為他們沒有角色。 */
    private static final Map<String, String> ACTOR_ROLE = Map.of(
            "櫃檯", "frontdesk",
            "房務", "housekeeping",
            "工務", "engineering",
            "主管", "manager",
            "值班主管", "duty_manager");

    private static final Set<String> TASK_STATES = Set.of(
            "triage", "pool", "accepted", "doing", "done", "inspected", "closed", "deferred", "interrupted",
            "cancelled");

    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M/d");

    private final ScenarioWorld world;
    private final DeskApi deskApi;
    private final DeskContext deskContext;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final LastOperationGuard lastOperationGuard;
    private final TenantFixtures tenantFixtures;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final MutableClock clock;

    public DeskThenSteps(
            ScenarioWorld world,
            DeskApi deskApi,
            DeskContext deskContext,
            BddApiClient apiClient,
            DbProbe dbProbe,
            LastOperationGuard lastOperationGuard,
            TenantFixtures tenantFixtures,
            JwtService jwtService,
            ObjectMapper objectMapper,
            MutableClock clock) {
        this.world = world;
        this.deskApi = deskApi;
        this.deskContext = deskContext;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.lastOperationGuard = lastOperationGuard;
        this.tenantFixtures = tenantFixtures;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- 看板

    @那麼("櫃檯看板剛好有這些房號的單：{}")
    public void boardHasExactlyTheseRooms(String roomList) {
        JsonNode board = boardJson();
        Set<String> expected = new TreeSet<>(splitList(roomList));
        Set<String> actual = new TreeSet<>();
        for (JsonNode card : board.path("tasks")) {
            actual.add(card.path("roomNo").asString());
        }
        if (!expected.equals(actual)) {
            throw new AssertionError("DeskBoard.tasks 的房號預期剛好是 " + expected + "，實際 " + actual);
        }

        LocalDate businessDate = businessDateOf(board);
        for (Map<String, Object> row : dbProbe.queryMaps(
                """
                SELECT t.id, t.ended_at, r.room_no
                  FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ?
                """,
                tenantId())) {
            String roomNo = String.valueOf(row.get("room_no"));
            Instant endedAt = instantOf(row.get("ended_at"));
            LocalDate endedDate = endedAt == null ? null : localDate(endedAt);
            boolean onBoard = endedDate == null || endedDate.equals(businessDate);
            boolean endedBeforeToday = endedDate != null && endedDate.isBefore(businessDate);
            if (expected.contains(roomNo) && !onBoard) {
                throw new AssertionError("「" + roomNo + "」在看板上，但 tasks.ended_at=" + endedAt
                        + " 不是空的、也不在 businessDate " + businessDate);
            }
            if (!expected.contains(roomNo) && !endedBeforeToday) {
                throw new AssertionError("「" + roomNo + "」不在看板上，但 tasks.ended_at=" + endedAt
                        + " 沒有早於 businessDate " + businessDate);
            }
        }
    }

    @那麼("櫃檯看板上 {string} 的資料如下：")
    public void boardCardFieldsAre(String taskKey, DataTable table) {
        UUID taskId = taskIdOf(taskKey);
        DeskTask card = boardCard(taskId);
        Map<String, Object> row = taskRow(taskId);
        for (Map<String, String> item : table.asMaps()) {
            String name = item.get("項目");
            String value = item.get("值");
            switch (name) {
                case "狀態" -> {
                    String expected = stateValue(value);
                    check("DeskTask.state", expected, card.state());
                    check("tasks.state", expected, row.get("state"));
                }
                case "已當場回答" -> {
                    boolean expected = yes(value);
                    check("DeskTask.answered", expected, card.answered());
                    check("tasks.answer 是否有值", expected, row.get("answer") != null);
                }
                case "部門" -> {
                    String expected = "未分部門".equals(value) ? FRONTDESK : Terms.dept(value);
                    check("DeskTask.dept", expected, card.dept());
                    check("tasks.dept", expected, row.get("dept"));
                }
                case "單號" -> {
                    check("DeskTask.displayNo", value, card.displayNo());
                    check("資料庫的單號（tasks.task_no 或 repair_tickets.ticket_no）", value, displayNoInDb(taskId, row));
                }
                default -> throw new IllegalArgumentException("櫃檯看板的資料表不支援項目「" + name + "」");
            }
        }
        if (!TASK_STATES.contains(card.state())) {
            throw new AssertionError("DeskTask.state=" + card.state() + " 不在既有的十個狀態值之內");
        }
    }

    @那麼("櫃檯看板上 {string} 的等待從 {string} 起算")
    public void boardWaitingStartsAt(String taskKey, String hhmm) {
        UUID taskId = taskIdOf(taskKey);
        DeskTask card = boardCard(taskId);
        Instant expected = todayAt(hhmm);
        checkInstant("DeskTask.waitingSince", expected, instantOf(card.waitingSince()));
        if ("triage".equals(card.state())) {
            checkInstant("待派單的 DeskTask.waitingSince 應等於 createdAt", instantOf(card.createdAt()),
                    instantOf(card.waitingSince()));
        }
        checkInstant("task_events 裡 created 與 returned_to_pool 最新一筆的時間", expected, latestWaitingEventAt(taskId));

        if ("pool".equals(card.state())) {
            JsonNode poolItem = poolItemSeenBy(card.dept(), taskId)
                    .orElseThrow(() -> new AssertionError(card.dept() + " 的同仁在待認領看不到「" + taskKey + "」"));
            long expectedMinutes = Duration.between(expected, clock.instant()).toMinutes();
            check("listDeptPool.waitedMinutes", expectedMinutes, poolItem.path("waitedMinutes").asLong());
        }
    }

    @那麼("櫃檯看板上各單的到手方式如下：")
    public void boardAcceptedViaAre(DataTable table) {
        for (Map<String, String> item : table.asMaps()) {
            String roomNo = item.get("房號");
            String expectedVia = lookup(ACCEPTED_VIA, item.get("到手方式"), "到手方式");
            UUID taskId = taskIdOf(roomNo);
            DeskTask card = boardCard(taskId);
            check(roomNo + " 的 DeskTask.acceptedVia", expectedVia, card.acceptedVia());
            if (card.acceptedAt() == null) {
                throw new AssertionError(roomNo + " 的 DeskTask.acceptedAt 應有值");
            }

            Map<String, Object> event = latestAcceptEvent(taskId)
                    .orElseThrow(() -> new AssertionError(roomNo + " 沒有任何 claimed、assigned 或 reassigned 事件"));
            check(roomNo + " 最近一筆到手事件換算的到手方式", expectedVia, acceptedViaOf(event, taskRow(taskId)));
            checkInstant(roomNo + " 的 DeskTask.acceptedAt 應等於最近一筆到手事件的時間",
                    instantOf(event.get("created_at")), instantOf(card.acceptedAt()));
        }
    }

    @那麼("櫃檯看板上 {string} 沒有任何急件標記")
    public void boardCardHasNoUrgentMark(String taskKey) {
        UUID taskId = taskIdOf(taskKey);
        JsonNode node = boardCardNode(taskId);
        for (String field : node.propertyNames()) {
            String lower = field.toLowerCase(Locale.ROOT);
            if (lower.contains("urgent") || lower.contains("emergency") || lower.contains("priority")) {
                throw new AssertionError("櫃檯看板的「" + taskKey + "」帶了表示急件的欄位 " + field + "：" + node);
            }
        }

        // 急件單的等待起算必須和一般單用同一把尺，不能因為急件另外算。
        DeskTask card = toRecord(node, DeskTask.class);
        if ("triage".equals(card.state()) || "pool".equals(card.state())) {
            checkInstant("「" + taskKey + "」的 DeskTask.waitingSince", latestWaitingEventAt(taskId),
                    instantOf(card.waitingSince()));
        } else {
            check("「" + taskKey + "」不在待派單或待認領時的 DeskTask.waitingSince", null, card.waitingSince());
        }
    }

    @那麼("櫃檯看板上住客打字的需求如下：")
    public void boardGuestTypedTextsAre(DataTable table) {
        for (Map<String, String> item : table.asMaps()) {
            String roomNo = item.get("房號");
            String original = item.get("原文");
            String translated = blankToNull(item.get("翻譯"));
            UUID taskId = taskIdOf(roomNo);
            DeskTask card = boardCard(taskId);

            check(roomNo + " 的 DeskTask.guestTyped", true, card.guestTyped());
            if (card.guestText() == null) {
                throw new AssertionError(roomNo + " 是住客打字的需求，DeskTask.guestText 不應是 null");
            }
            check(roomNo + " 的 guestText.original", original, card.guestText().original());
            check(roomNo + " 的 guestText.translated", translated, card.guestText().translated());
            if (item.containsKey("原文語言")) {
                String langTerm = blankToNull(item.get("原文語言"));
                String lang = langTerm == null ? null : Terms.lang(langTerm);
                check(roomNo + " 的 guestText.lang", lang, card.guestText().lang());
            }

            // 翻譯只能來自單上原有的 llm_translated；單上沒有時回應也必須是 null，代表後端沒有自己去翻。
            JsonNode note = noteOf(taskRow(taskId));
            check(roomNo + " 的 tasks.note 原文", original, textOrNull(note, "text"));
            check(roomNo + " 的 tasks.note llm_translated", translated, textOrNull(note, "llm_translated"));
        }
    }

    @那麼("櫃檯看板上各單的住客如下：")
    public void boardGuestsAre(DataTable table) {
        for (Map<String, String> item : table.asMaps()) {
            String roomNo = item.get("房號");
            String guest = blankToNull(item.get("住客"));
            boolean vip = yes(item.get("VIP"));
            DeskTask card = boardCard(taskIdOf(roomNo));
            check(roomNo + " 的 DeskTask.guestName", guest, card.guestName());
            check(roomNo + " 的 DeskTask.vip", vip, card.vip());

            Map<String, Object> stay = inHouseReservation(roomNo).orElse(null);
            check(roomNo + " 入住中訂房的住客姓名", guest, stay == null ? null : stay.get("guest_name"));
            check(roomNo + " 入住中訂房是不是 VIP", vip, stay != null && isVip(stay));
        }
    }

    @那麼("櫃檯看板帶的後端時間是今天 {string}，今天的日期與後端時鐘的日期相同")
    public void boardServerTimeIs(String hhmm) {
        JsonNode board = boardJson();
        Instant serverTime = parseInstant(board, "serverTime");
        checkInstant("DeskBoard.serverTime 應等於後端時鐘", clock.instant(), serverTime);
        checkInstant("DeskBoard.serverTime 應是今天 " + hhmm, todayAt(hhmm), serverTime);
        check("DeskBoard.businessDate 應是後端時鐘在伺服器時區的日期", localDate(clock.instant()), businessDateOf(board));
    }

    @那麼("櫃檯看板帶的樓層依序為 {}")
    public void boardFloorsAre(String floorList) {
        List<Integer> expected = splitList(floorList).stream().map(Integer::valueOf).toList();
        List<Integer> actual = new ArrayList<>();
        for (JsonNode floor : boardJson().path("floors")) {
            actual.add(floor.asInt());
        }
        check("DeskBoard.floors", expected, actual);
        List<Integer> sortedDistinct = new ArrayList<>(new TreeSet<>(actual));
        check("DeskBoard.floors 應由低到高、不重複", sortedDistinct, actual);

        List<Integer> roomFloors = dbProbe
                .queryMaps("SELECT DISTINCT floor FROM rooms WHERE tenant_id = ? ORDER BY floor", tenantId())
                .stream()
                .map(row -> ((Number) row.get("floor")).intValue())
                .toList();
        check("房間主檔的樓層", roomFloors, actual);
    }

    @那麼("櫃檯看板帶的本人今日張數是已派單 {int} 張、當場回答 {int} 張")
    public void boardMyTodayCounts(int assignedCount, int answeredCount) {
        JsonNode board = boardJson();
        DeskMyToday myToday = toRecord(board.path("myToday"), DeskMyToday.class);
        check("DeskBoard.myToday.assignedCount", assignedCount, myToday.assignedCount());
        check("DeskBoard.myToday.answeredCount", answeredCount, myToday.answeredCount());

        // 只數讀看板那一位自己的事件，別人派的、別人回答的都不能算進來。
        String reader = deskContext.lastBoardReader();
        LocalDate businessDate = businessDateOf(board);
        int dbAssigned = 0;
        int dbAnswered = 0;
        for (Map<String, Object> event : dbProbe.queryMaps(
                """
                SELECT * FROM task_events
                 WHERE tenant_id = ? AND actor_id = ? AND type IN ('triaged', 'answered')
                 ORDER BY task_id, seq
                """,
                tenantId(), staffId(reader))) {
            if (!localDate(instantOf(event.get("created_at"))).equals(businessDate)) {
                continue;
            }
            if ("answered".equals(event.get("type"))) {
                dbAnswered++;
            } else if (!undoneRightAfter(event)) {
                dbAssigned++;
            }
        }
        check(reader + " 今天的 triaged 事件扣掉緊接著被復原的", assignedCount, dbAssigned);
        check(reader + " 今天的 answered 事件", answeredCount, dbAnswered);
    }

    // ---------------------------------------------------------------- 派單

    @那麼("{string} 仍是待派單，歷程沒有任何派單紀錄")
    public void stillAwaitingTriage(String taskKey) {
        UUID taskId = taskIdOf(taskKey);
        Map<String, Object> row = taskRow(taskId);
        check("tasks.state", "triage", row.get("state"));
        check("tasks.dept", FRONTDESK, row.get("dept"));
        check("tasks.assignee_id", null, row.get("assignee_id"));
        check("tasks.answer", null, row.get("answer"));
        List<String> types = eventTypes(taskId);
        if (types.contains("triaged") || types.contains("answered")) {
            throw new AssertionError("「" + taskKey + "」的 task_events 不應有 triaged 或 answered，實際 " + types);
        }

        for (String dept : List.of(HOUSEKEEPING, ENGINEERING)) {
            if (poolItemSeenBy(dept, taskId).isPresent()) {
                throw new AssertionError(dept + " 的同仁在待認領看得到還在待派單的「" + taskKey + "」");
            }
        }
    }

    @那麼("{string} 在 {string} 的待認領，沒有負責人")
    public void inDeptPoolWithoutAssignee(String taskKey, String deptTerm) {
        UUID taskId = taskIdOf(taskKey);
        String dept = Terms.dept(deptTerm);
        Map<String, Object> row = taskRow(taskId);
        check("tasks.state", "pool", row.get("state"));
        check("tasks.dept", dept, row.get("dept"));
        check("tasks.assignee_id", null, row.get("assignee_id"));

        if (poolItemSeenBy(dept, taskId).isEmpty()) {
            throw new AssertionError(dept + " 的同仁在待認領看不到「" + taskKey + "」");
        }
        String otherDept = otherFieldDept(dept);
        if (poolItemSeenBy(otherDept, taskId).isPresent()) {
            throw new AssertionError(otherDept + " 的同仁在待認領看得到派給 " + dept + " 的「" + taskKey + "」");
        }

        DeskTask card = sideRead(() -> cardOnFreshBoard(deskReader(), taskId));
        check("再讀看板的 DeskTask.state", "pool", card.state());
        check("再讀看板的 DeskTask.dept", dept, card.dept());
    }

    @那麼("全館沒有因為這次派單多出任何一張單")
    public void noExtraTaskCreated() {
        long before = lastOperationGuard.tenantTaskCountBefore();
        long after = dbProbe.count("SELECT count(*) FROM tasks WHERE tenant_id = ?", tenantId());
        check("全館 tasks 的筆數（派單前後）", before, after);
    }

    @那麼("{string} 的歷程最新一筆是 {string} 以 {string} 派給 {string}")
    public void latestEventIsAssignment(String taskKey, String operator, String viaTerm, String deptTerm) {
        UUID taskId = taskIdOf(taskKey);
        String via = lookup(ASSIGN_VIA, viaTerm, "派單方式");
        String dept = Terms.dept(deptTerm);

        Map<String, Object> latest = latestEvent(taskId);
        check("最新一筆 task_events.type", "triaged", latest.get("type"));
        checkSameId("最新一筆 task_events.actor_id", staffId(operator), latest.get("actor_id"));
        JsonNode payload = payloadOf(latest);
        check("triaged 事件 payload.dept", dept, textOrNull(payload, "dept"));
        check("triaged 事件 payload.via", via, textOrNull(payload, "via"));

        DeskTimelineEntry last = lastEntry(timelineOf(detailReadBy(operator, taskId)));
        check("timeline 最後一筆的 type", "triaged", last.type());
        check("timeline 最後一筆的 actor.name", operator, last.actor() == null ? null : last.actor().name());
        check("timeline 最後一筆的 detail.dept", dept, last.detail() == null ? null : last.detail().dept());
        check("timeline 最後一筆的 detail.via", via, last.detail() == null ? null : last.detail().via());
    }

    @那麼("{string} 那筆派單記的操作者角色是 {string}")
    public void assignmentActorRoleIs(String taskKey, String roleTerm) {
        UUID taskId = taskIdOf(taskKey);
        String role = lookup(ACTOR_ROLE, roleTerm, "操作者角色");
        Map<String, Object> triaged = latestEventOfType(taskId, "triaged")
                .orElseThrow(() -> new AssertionError("「" + taskKey + "」沒有任何 triaged 事件"));
        check("triaged 事件的 actor_role", role, triaged.get("actor_role"));

        // 記的必須是他派單當下的角色，值班主管要記成 duty_manager，不能借用 manager。
        Map<String, Object> staff = staffRow(triaged.get("actor_id"));
        check("派單的人在 staff_accounts 的角色", role, staff.get("role"));

        String actorName = String.valueOf(staff.get("name"));
        int seq = seqOf(triaged);
        DeskTimelineEntry entry = timelineOf(detailReadBy(actorName, taskId)).stream()
                .filter(e -> e.seq() == seq)
                .findFirst()
                .orElseThrow(() -> new AssertionError("readDeskTask 的 timeline 找不到 seq=" + seq + " 的派單"));
        check("timeline 那筆派單的 actor.role", role, entry.actor() == null ? null : entry.actor().role());
    }

    @那麼("{string} 只被派出一次，派單的人是 {string}")
    public void assignedOnlyOnceBy(String taskKey, String operator) {
        UUID taskId = taskIdOf(taskKey);
        List<Map<String, Object>> triaged = eventsOfType(taskId, "triaged");
        check("「" + taskKey + "」的 triaged 事件筆數", 1, triaged.size());
        checkSameId("triaged 事件的 actor_id", staffId(operator), triaged.get(0).get("actor_id"));
        check("「" + taskKey + "」的 answered 事件筆數", 0, eventsOfType(taskId, "answered").size());
    }

    @那麼("重送拿到與第一次相同的成功回應")
    public void resendGotSameSuccess() {
        ScenarioWorld.ApiResponse first = deskContext.firstAssignResponseBeforeResend();
        ScenarioWorld.ApiResponse resent = deskContext.resentAssignResponse();
        check("第一次派單的狀態碼", 200, first.status());
        check("重送的狀態碼", 200, resent.status());
        toRecord(resent.json(), DeskTask.class);
        if (!Objects.equals(first.json(), resent.json())) {
            throw new AssertionError("重送的回應與第一次不同：第一次=" + first.body() + "，重送=" + resent.body());
        }

        String key = deskContext.lastAssignIdempotencyKey();
        long processed = dbProbe.count(
                "SELECT count(*) FROM processed_ops WHERE tenant_id = ? AND op_id = ?", tenantId(), key);
        check("processed_ops 裡這把鍵的筆數", 1L, processed);
    }

    @那麼("這次操作被拒絕，因為 {string} 已被 {string} 派給 {string}")
    public void rejectedBecauseAlreadyAssigned(String taskKey, String operator, String deptTerm) {
        UUID taskId = taskIdOf(taskKey);
        JsonNode context = rejectionContext("task_not_in_triage");
        check("context.resolution", "assigned", textOrNull(context, "resolution"));
        check("context.actorName", operator, textOrNull(context, "actorName"));
        check("context.dept", Terms.dept(deptTerm), textOrNull(context, "dept"));
        check("context.roomNo", roomNoOf(taskId), textOrNull(context, "roomNo"));
        check("context.state", "pool", textOrNull(context, "state"));
        assertUnchangedSinceWhen(taskId);
    }

    @那麼("這次操作被拒絕，因為 {string} 已被 {string} 當場回答")
    public void rejectedBecauseAlreadyAnswered(String taskKey, String operator) {
        UUID taskId = taskIdOf(taskKey);
        JsonNode context = rejectionContext("task_not_in_triage");
        check("context.resolution", "answered", textOrNull(context, "resolution"));
        check("context.actorName", operator, textOrNull(context, "actorName"));
        check("context.roomNo", roomNoOf(taskId), textOrNull(context, "roomNo"));
        check("context.state", "done", textOrNull(context, "state"));
        assertUnchangedSinceWhen(taskId);
        Object answerBefore = lastOperationGuard.current().taskRow().get("answer");
        if (answerBefore == null) {
            throw new AssertionError("先送到的當場回答應已在 tasks.answer 留下回覆，操作前卻是空的");
        }
        check("tasks.answer（應仍是先送到的那一段）", answerBefore, taskRow(taskId).get("answer"));
    }

    @那麼("這次操作被拒絕，因為 {string} 已被住客收回")
    public void rejectedBecauseGuestWithdrew(String taskKey) {
        UUID taskId = taskIdOf(taskKey);
        JsonNode context = rejectionContext("task_not_in_triage");
        check("context.resolution", "guest_withdrew", textOrNull(context, "resolution"));
        check("context.roomNo", roomNoOf(taskId), textOrNull(context, "roomNo"));
        check("context.state", "cancelled", textOrNull(context, "state"));
        assertUnchangedSinceWhen(taskId);
        check("tasks.state", "cancelled", taskRow(taskId).get("state"));
    }

    @那麼("這次操作被拒絕，因為 {string} 已經不是待派單")
    public void rejectedBecauseNoLongerAwaitingTriage(String taskKey) {
        UUID taskId = taskIdOf(taskKey);
        JsonNode context = rejectionContext("task_not_in_triage");
        check("context.resolution", "other", textOrNull(context, "resolution"));
        check("context.state", taskRow(taskId).get("state"), textOrNull(context, "state"));
        check("context.roomNo", roomNoOf(taskId), textOrNull(context, "roomNo"));
        assertUnchangedSinceWhen(taskId);
    }

    // ---------------------------------------------------------------- 復原

    @那麼("{string} 回到待派單，還沒分部門")
    public void backToAwaitingTriage(String taskKey) {
        UUID taskId = taskIdOf(taskKey);
        ScenarioWorld.ApiResponse response = world.lastResponse();
        requireOk("deskUndoAssign", response);
        DeskTask card = toRecord(response.json(), DeskTask.class);
        check("回應的 DeskTask.id", taskId, card.id());
        check("回應的 DeskTask.state", "triage", card.state());
        check("回應的 DeskTask.dept", FRONTDESK, card.dept());

        Map<String, Object> row = taskRow(taskId);
        check("tasks.state", "triage", row.get("state"));
        check("tasks.dept", FRONTDESK, row.get("dept"));
        check("tasks.reminder_stage", null, row.get("reminder_stage"));
        check("tasks.pool_source", null, row.get("pool_source"));
        check("tasks.assignee_id", null, row.get("assignee_id"));

        String formerDept = latestEventOfType(taskId, "triaged")
                .map(event -> textOrNull(payloadOf(event), "dept"))
                .orElseThrow(() -> new AssertionError("「" + taskKey + "」沒有被派出過，無從確認原本的部門"));
        if (poolItemSeenBy(formerDept, taskId).isPresent()) {
            throw new AssertionError("復原後原本被派去的 " + formerDept + " 仍在待認領看得到「" + taskKey + "」");
        }
    }

    @那麼("{string} 給人看的歷程沒有派單這一步")
    public void timelineHidesAssignment(String taskKey) {
        UUID taskId = taskIdOf(taskKey);
        List<DeskTimelineEntry> timeline = timelineOf(detailReadBy(deskReader(), taskId));
        for (DeskTimelineEntry entry : timeline) {
            if ("triaged".equals(entry.type()) || "triage_undone".equals(entry.type())) {
                throw new AssertionError("復原後的 timeline 不應有 " + entry.type() + "（seq=" + entry.seq() + "）");
            }
        }
        // 派單與復原兩筆以外的每一筆都照實留著，讀起來就是派單前的樣子。
        assertTimelineMatchesEvents("復原後的 timeline", timeline, visibleTimelineEvents(dbProbe.eventsOf(taskId)));
    }

    @那麼("{string} 的事件紀錄保留派給 {string} 與復原各一筆，兩筆相鄰")
    public void assignAndUndoEventsKeptAdjacent(String taskKey, String deptTerm) {
        UUID taskId = taskIdOf(taskKey);
        String dept = Terms.dept(deptTerm);
        List<Map<String, Object>> events = dbProbe.eventsOf(taskId);

        List<Map<String, Object>> triaged = events.stream()
                .filter(e -> "triaged".equals(e.get("type")) && dept.equals(textOrNull(payloadOf(e), "dept")))
                .toList();
        check("payload.dept 為 " + dept + " 的 triaged 事件筆數", 1, triaged.size());
        check("triage_undone 事件筆數", 1, eventsOfType(taskId, "triage_undone").size());

        int triagedSeq = seqOf(triaged.get(0));
        Map<String, Object> next = events.stream()
                .filter(e -> seqOf(e) == triagedSeq + 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("triaged（seq=" + triagedSeq + "）之後沒有緊接的事件"));
        check("緊接 triaged 的事件種類", "triage_undone", next.get("type"));
        check("triage_undone 事件 payload.dept（被收回的部門）", dept, textOrNull(payloadOf(next), "dept"));

        // seq 從 1 起連續不跳號，代表沒有任何一筆被刪掉。
        for (int i = 0; i < events.size(); i++) {
            check("task_events 第 " + (i + 1) + " 筆的 seq", i + 1, seqOf(events.get(i)));
        }
        Instant triagedAt = instantOf(triaged.get(0).get("created_at"));
        Instant undoneAt = instantOf(next.get("created_at"));
        if (undoneAt.isBefore(triagedAt)) {
            throw new AssertionError("triage_undone 的時間 " + undoneAt + " 早於 triaged 的時間 " + triagedAt);
        }
    }

    @那麼("這次復原被拒絕，因為已經超過可以復原的時間")
    public void undoRejectedExpired() {
        UUID taskId = lastOperationGuard.current().taskId();
        JsonNode context = rejectionContext("undo_expired");
        check("context.roomNo", roomNoOf(taskId), textOrNull(context, "roomNo"));
        check("context.dept", taskRow(taskId).get("dept"), textOrNull(context, "dept"));
        assertUnchangedSinceWhen(taskId);
    }

    @那麼("這次復原被拒絕，因為 {string} 已經認領，這張單留在 {string}")
    public void undoRejectedClaimed(String staffName, String deptTerm) {
        UUID taskId = lastOperationGuard.current().taskId();
        String dept = Terms.dept(deptTerm);
        JsonNode context = rejectionContext("undo_claimed");
        check("context.assigneeName", staffName, textOrNull(context, "assigneeName"));
        check("context.dept", dept, textOrNull(context, "dept"));
        check("context.roomNo", roomNoOf(taskId), textOrNull(context, "roomNo"));
        assertUnchangedSinceWhen(taskId);
        Map<String, Object> row = taskRow(taskId);
        checkSameId("tasks.assignee_id", staffId(staffName), row.get("assignee_id"));
        check("tasks.dept", dept, row.get("dept"));
    }

    @那麼("這次復原被拒絕，因為住客已經收回這張需求")
    public void undoRejectedGuestWithdrew() {
        UUID taskId = lastOperationGuard.current().taskId();
        JsonNode context = rejectionContext("undo_guest_withdrew");
        check("context.roomNo", roomNoOf(taskId), textOrNull(context, "roomNo"));
        assertUnchangedSinceWhen(taskId);
        check("tasks.state", "cancelled", taskRow(taskId).get("state"));
    }

    @那麼("這次復原被拒絕，因為這張單已經不是剛派出的樣子")
    public void undoRejectedUnavailable() {
        UUID taskId = lastOperationGuard.current().taskId();
        JsonNode context = rejectionContext("undo_unavailable");
        check("context.state", taskRow(taskId).get("state"), textOrNull(context, "state"));
        check("context.roomNo", roomNoOf(taskId), textOrNull(context, "roomNo"));
        assertUnchangedSinceWhen(taskId);
    }

    // ---------------------------------------------------------------- 當場回答

    @那麼("{string} 已由 {string} 當場回答結案，回覆為 {string}")
    public void answeredAndClosedBy(String taskKey, String deskStaff, String answer) {
        UUID taskId = taskIdOf(taskKey);
        Map<String, Object> row = taskRow(taskId);
        check("tasks.state", "done", row.get("state"));
        check("tasks.answer", answer, row.get("answer"));
        if (row.get("done_at") == null || row.get("ended_at") == null) {
            throw new AssertionError("當場回答結案後 tasks.done_at 與 ended_at 都應有值，實際 done_at="
                    + row.get("done_at") + "，ended_at=" + row.get("ended_at"));
        }
        List<Map<String, Object>> answered = eventsOfType(taskId, "answered");
        check("「" + taskKey + "」的 answered 事件筆數", 1, answered.size());
        checkSameId("answered 事件的 actor_id", staffId(deskStaff), answered.get(0).get("actor_id"));

        JsonNode detail = detailReadBy(deskStaff, taskId);
        check("readDeskTask 的 answered", true, detail.path("answered").asBoolean());
        check("readDeskTask 的 answer", answer, textOrNull(detail, "answer"));
        check("readDeskTask 的 answeredByName", deskStaff, textOrNull(detail, "answeredByName"));
    }

    @那麼("{string} 的回覆長度是 {int} 個字")
    public void answerLengthIs(String taskKey, int length) {
        requireOk("deskAnswerTask", world.lastResponse());
        Map<String, Object> row = taskRow(taskIdOf(taskKey));
        check("tasks.state", "done", row.get("state"));
        String stored = (String) row.get("answer");
        if (stored == null) {
            throw new AssertionError("tasks.answer 應有值");
        }
        check("tasks.answer 的字數", length, stored.codePointCount(0, stored.length()));
        check("tasks.answer（應等於送出的字串）", deskContext.lastSentAnswer(), stored);
    }

    @那麼("{string} 的住客原文仍是 {string}")
    public void guestOriginalTextKept(String taskKey, String original) {
        UUID taskId = taskIdOf(taskKey);
        LastOperationGuard.Snapshot before = snapshotOf(taskId);
        Map<String, Object> row = taskRow(taskId);
        check("tasks.note（應與當場回答前一字不差）", before.taskRow().get("note"), row.get("note"));
        check("tasks.note 的住客原文", original, textOrNull(noteOf(row), "text"));

        JsonNode detail = detailReadBy(deskReader(), taskId);
        check("readDeskTask 的 guestText.original", original, textOrNull(detail.path("guestText"), "original"));
    }

    @那麼("{string} 仍未分部門，沒有負責人，不算任何部門做掉的單")
    public void answeredTaskNotCountedForAnyDept(String taskKey) {
        UUID taskId = taskIdOf(taskKey);
        Map<String, Object> row = taskRow(taskId);
        check("tasks.dept", FRONTDESK, row.get("dept"));
        check("tasks.assignee_id", null, row.get("assignee_id"));
        check("「" + taskKey + "」的 completed 事件筆數", 0, eventsOfType(taskId, "completed").size());

        // 回答前後同仁的完成數相同，而且這張單不在任何人的 todayTasks 裡，才算沒有被算進任何人的今日完成。
        Map<String, Integer> before = deskContext.completedCountsBeforeAnswer();
        for (Map<String, Object> staff : fieldStaff()) {
            String name = String.valueOf(staff.get("name"));
            JsonNode today = myTodayOf(uuid(staff.get("id")));
            for (JsonNode todayTask : today.path("todayTasks")) {
                if (taskId.toString().equalsIgnoreCase(todayTask.path("taskId").asString())) {
                    throw new AssertionError(name + " 的 getMyToday.todayTasks 裡出現了當場回答的「" + taskKey + "」");
                }
            }
            Integer countBefore = before.get(String.valueOf(staff.get("id")));
            if (countBefore == null) {
                throw new AssertionError("當場回答前沒有記到 " + name + " 的今日完成數，無法比對前後");
            }
            check(name + " 的 getMyToday.completedCount（應與當場回答前相同）", countBefore,
                    today.path("completedCount").asInt());
        }
    }

    @那麼("{string} 的歷程最新一筆是 {string} 的當場回答，帶回覆 {string}")
    public void latestEventIsAnswer(String taskKey, String operator, String answer) {
        UUID taskId = taskIdOf(taskKey);
        Map<String, Object> latest = latestEvent(taskId);
        check("最新一筆 task_events.type", "answered", latest.get("type"));
        checkSameId("最新一筆 task_events.actor_id", staffId(operator), latest.get("actor_id"));
        check("answered 事件 payload.answer", answer, textOrNull(payloadOf(latest), "answer"));

        DeskTimelineEntry last = lastEntry(timelineOf(detailReadBy(operator, taskId)));
        check("timeline 最後一筆的 type", "answered", last.type());
        check("timeline 最後一筆的 detail.answer", answer, last.detail() == null ? null : last.detail().answer());
    }

    @那麼("{string} 的住客在進度頁讀到回覆 {string}")
    public void guestSeesAnswer(String roomNo, String answer) {
        UUID taskId = taskIdOf(roomNo);
        ScenarioWorld.ApiResponse response = sideRead(() -> deskApi.listGuestRequests(roomNo));
        requireOk("listGuestRequests", response);
        JsonNode request = null;
        for (JsonNode item : response.json()) {
            if (taskId.toString().equalsIgnoreCase(item.path("id").asString())) {
                request = item;
            }
        }
        if (request == null) {
            throw new AssertionError("「" + roomNo + "」的住客在進度頁找不到這張需求：" + response.body());
        }
        check("GuestRequest.answer", answer, textOrNull(request, "answer"));
    }

    // ---------------------------------------------------------------- 詳情與歷程

    @那麼("櫃檯詳情的資料如下：")
    public void detailFieldsAre(DataTable table) {
        JsonNode detail = detailJson();
        UUID taskId = UUID.fromString(detail.path("id").asString());
        Map<String, Object> row = taskRow(taskId);
        for (Map<String, String> item : table.asMaps()) {
            String name = item.get("項目");
            String value = blankToNull(item.get("值"));
            checkDetailItem(name, value, detail, taskId, row);
        }
        assertNoGuestHeadcount(detail);
    }

    @那麼("櫃檯詳情的歷程依序為：")
    public void detailTimelineIs(DataTable table) {
        JsonNode detail = detailJson();
        UUID taskId = UUID.fromString(detail.path("id").asString());
        List<DeskTimelineEntry> timeline = timelineOf(detail);
        List<Map<String, String>> rows = table.asMaps();
        check("timeline 筆數", rows.size(), timeline.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> expected = rows.get(i);
            DeskTimelineEntry entry = timeline.get(i);
            String where = "timeline 第 " + (i + 1) + " 筆";
            check(where + " 的 type", lookup(TIMELINE_EVENT, expected.get("事件"), "歷程事件"), entry.type());
            if (entry.at() == null) {
                throw new AssertionError(where + " 沒有帶 at");
            }
            if (entry.actor() == null) {
                throw new AssertionError(where + " 沒有帶 actor");
            }
            check(where + " 的 actor.name", expected.get("操作者"), entry.actor().name());
            String identity = expected.get("操作者身分");
            switch (identity) {
                case "住客" -> check(where + " 的 actor.kind", "guest", entry.actor().kind());
                case "系統" -> check(where + " 的 actor.kind", "system", entry.actor().kind());
                default -> {
                    check(where + " 的 actor.kind", "staff", entry.actor().kind());
                    check(where + " 的 actor.role", lookup(ACTOR_ROLE, identity, "操作者身分"), entry.actor().role());
                }
            }
        }
        assertTimelineMatchesEvents("readDeskTask 的 timeline", timeline,
                visibleTimelineEvents(dbProbe.eventsOf(taskId)));
    }

    @那麼("{string} 的歷程比這一步之前多一筆 {string}，原有各筆都沒有改動")
    public void timelineGrewByOne(String taskKey, String eventTerm) {
        UUID taskId = taskIdOf(taskKey);
        String type = lookup(TIMELINE_EVENT, eventTerm, "歷程事件");
        snapshotOf(taskId);
        List<Map<String, Object>> before = lastOperationGuard.eventsBefore();
        List<Map<String, Object>> after = dbProbe.eventsOf(taskId);

        check("task_events 筆數（應比這一步之前多一筆）", before.size() + 1, after.size());
        for (int i = 0; i < before.size(); i++) {
            assertSameEvent(before.get(i), after.get(i));
        }
        Map<String, Object> added = after.get(after.size() - 1);
        check("多出來那一筆的 type", type, added.get("type"));

        List<DeskTimelineEntry> timeline = timelineOf(detailReadBy(deskReader(), taskId));
        DeskTimelineEntry last = lastEntry(timeline);
        check("timeline 最後一筆的 type", type, last.type());
        check("timeline 最後一筆的 seq", seqOf(added), last.seq());
        assertTimelineMatchesEvents("timeline 除了最後一筆的前幾筆",
                timeline.subList(0, timeline.size() - 1), visibleTimelineEvents(before));
    }

    @那麼("櫃檯詳情的歷程沒有 {string} 這種事件")
    public void detailTimelineOmits(String eventTerm) {
        String type = lookup(TIMELINE_EVENT, eventTerm, "歷程事件");
        JsonNode detail = detailJson();
        UUID taskId = UUID.fromString(detail.path("id").asString());
        for (DeskTimelineEntry entry : timelineOf(detail)) {
            if (type.equals(entry.type())) {
                throw new AssertionError("timeline 不應有 " + type + "，卻出現在 seq=" + entry.seq());
            }
        }
        // 必須是讀取時略過，而不是根本沒記，否則這句驗不到任何東西。
        if (eventsOfType(taskId, type).isEmpty()) {
            throw new AssertionError("task_events 沒有任何 " + type + "，無法確認是讀取時略過");
        }
    }

    // ---------------------------------------------------------------- 常用回覆

    @那麼("常用回覆的標題依序為 {}")
    public void quickReplyTitlesAre(String titleList) {
        List<DeskQuickReply> replies = quickReplies();
        check("常用回覆的標題", splitList(titleList), replies.stream().map(DeskQuickReply::title).toList());
        for (DeskQuickReply reply : replies) {
            if (reply.key() == null || reply.body() == null) {
                throw new AssertionError("常用回覆「" + reply.title() + "」應帶 key 與 body，實際 " + reply);
            }
        }
    }

    @那麼("常用回覆 {string} 的全文是 {string}")
    public void quickReplyBodyIs(String title, String body) {
        DeskQuickReply reply = quickReplies().stream()
                .filter(r -> title.equals(r.title()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("常用回覆裡沒有標題「" + title + "」"));
        check("常用回覆「" + title + "」的 body", body, reply.body());

        JsonNode page = null;
        for (JsonNode candidate : guestInfoPages()) {
            if (title.equals(textOrNull(candidate, "title"))) {
                page = candidate;
            }
        }
        if (page == null) {
            throw new AssertionError("租戶設定 guest_info_pages 沒有標題「" + title + "」的頁");
        }
        check("guest_info_pages「" + title + "」的 body", body, textOrNull(page, "body"));
    }

    @那麼("常用回覆是空的")
    public void quickRepliesEmpty() {
        ScenarioWorld.ApiResponse response = world.lastResponse();
        requireOk("listDeskQuickReplies", response);
        if (!response.json().isArray() || !response.json().isEmpty()) {
            throw new AssertionError("常用回覆應是空陣列，實際 " + response.body());
        }
    }

    // ================================================================ 詳情各項的比對

    private void checkDetailItem(String name, String value, JsonNode detail, UUID taskId, Map<String, Object> row) {
        JsonNode guest = detail.path("guest");
        JsonNode accepted = detail.path("accepted");
        JsonNode cancellation = detail.path("cancellation");
        JsonNode repair = detail.path("repair");
        switch (name) {
            case "住客" -> {
                check("DeskTaskDetail.guest.name", value, textOrNull(guest, "name"));
                if (value == null && !isAbsent(guest)) {
                    throw new AssertionError("空房的 DeskTaskDetail.guest 應是 null，實際 " + guest);
                }
                Map<String, Object> stay = guestReservationOf(taskId, row).orElse(null);
                check("這張單的訂房住客", value, stay == null ? null : stay.get("guest_name"));
            }
            case "入住日" -> {
                check("DeskTaskDetail.guest.checkinDate", value, monthDayOf(textOrNull(guest, "checkinDate")));
                check("訂房的入住日", value, reservationMonthDay(taskId, row, "checkin_at"));
            }
            case "退房日" -> {
                check("DeskTaskDetail.guest.checkoutDate", value, monthDayOf(textOrNull(guest, "checkoutDate")));
                check("訂房的退房日", value, reservationMonthDay(taskId, row, "checkout_at"));
            }
            case "來源" -> {
                String expected = lookup(SOURCE, value, "來源");
                check("DeskTaskDetail.source", expected, textOrNull(detail, "source"));
                if ("staff".equals(expected)) {
                    if (repairTicketRow(taskId).isEmpty()) {
                        throw new AssertionError("來源是同仁報修，但 repair_tickets 沒有這張單");
                    }
                } else {
                    check("tasks.source", expected, row.get("source"));
                }
            }
            case "開單人" -> {
                check("DeskTaskDetail.createdByName", value, textOrNull(detail, "createdByName"));
                check("created 事件的操作者", value, actorNameOfLatest(taskId, "created"));
            }
            case "回覆" -> {
                check("DeskTaskDetail.answer", value, textOrNull(detail, "answer"));
                check("tasks.answer", value, row.get("answer"));
            }
            case "回答的人" -> {
                check("DeskTaskDetail.answeredByName", value, textOrNull(detail, "answeredByName"));
                check("answered 事件的操作者", value, actorNameOfLatest(taskId, "answered"));
            }
            case "到手方式" -> {
                String expected = value == null ? null : lookup(ACCEPTED_VIA, value, "到手方式");
                check("DeskTaskDetail.accepted.via", expected, textOrNull(accepted, "via"));
                check("最近一筆到手事件換算的到手方式", expected,
                        latestAcceptEvent(taskId).map(e -> acceptedViaOf(e, row)).orElse(null));
            }
            case "到手時間" -> {
                check("DeskTaskDetail.accepted.at", value, hourMinuteOf(parseInstant(accepted, "at")));
                check("最近一筆到手事件的時間", value, latestAcceptEvent(taskId)
                        .map(e -> hourMinuteOf(instantOf(e.get("created_at"))))
                        .orElse(null));
            }
            case "經手的人" -> {
                check("DeskTaskDetail.accepted.byName", value, textOrNull(accepted, "byName"));
                if (value != null) {
                    check("最近一筆到手事件的操作者", value, latestAcceptEvent(taskId)
                            .map(e -> staffNameOf(e.get("actor_id")))
                            .orElse(null));
                }
            }
            case "原負責人" ->
                check("DeskTaskDetail.accepted.previousAssigneeName", value,
                        textOrNull(accepted, "previousAssigneeName"));
            case "取消原因" -> {
                String expected = value == null ? null : lookup(CANCEL_REASON, value, "取消原因");
                check("DeskTaskDetail.cancellation.reason", expected, textOrNull(cancellation, "reason"));
                check("tasks.cancel_reason", expected, row.get("cancel_reason"));
            }
            case "取消的人" -> {
                check("DeskTaskDetail.cancellation.byName", value, textOrNull(cancellation, "byName"));
                check("cancelled 事件的操作者", value, actorNameOfLatest(taskId, "cancelled"));
            }
            case "結束時間" -> {
                check("DeskTaskDetail.endedAt", value, hourMinuteOf(parseInstant(detail, "endedAt")));
                check("tasks.ended_at", value, hourMinuteOf(instantOf(row.get("ended_at"))));
            }
            case "完修結果" -> {
                String expected = value == null ? null : Terms.resolutionType(value);
                check("DeskTaskDetail.repair.resolutionType", expected, textOrNull(repair, "resolutionType"));
                check("repair_tickets.resolution_type", expected,
                        repairTicketRow(taskId).map(t -> t.get("resolution_type")).orElse(null));
            }
            case "驗收主管" -> {
                check("DeskTaskDetail.repair.verifiedByName", value, textOrNull(repair, "verifiedByName"));
                check("repair_tickets.verified_by 的姓名", value,
                        repairTicketRow(taskId).map(t -> staffNameOf(t.get("verified_by"))).orElse(null));
            }
            case "SOP 進度" -> {
                JsonNode sop = detail.path("sop");
                String actual = isAbsent(sop) ? null : sop.path("done").asInt() + "/" + sop.path("total").asInt();
                check("DeskTaskDetail.sop", value, actual);
            }
            default -> throw new IllegalArgumentException("櫃檯詳情的資料表不支援項目「" + name + "」");
        }
    }

    /** 訂房資料沒有住客人數，回應也不該冒出人數，免得畫面顯示一個沒有來源的數字。 */
    private static void assertNoGuestHeadcount(JsonNode detail) {
        JsonNode guest = detail.path("guest");
        if (!isAbsent(guest)) {
            for (String field : guest.propertyNames()) {
                if (!Set.of("name", "checkinDate", "checkoutDate").contains(field)) {
                    throw new AssertionError("DeskTaskDetail.guest 多了契約沒有的欄位 " + field + "：" + guest);
                }
            }
        }
        for (String field : detail.propertyNames()) {
            String lower = field.toLowerCase(Locale.ROOT);
            if (lower.contains("headcount") || lower.contains("pax") || lower.contains("adult")
                    || (lower.contains("guest") && lower.contains("count"))) {
                throw new AssertionError("DeskTaskDetail 帶了住客人數欄位 " + field);
            }
        }
    }

    // ================================================================ 回應讀取

    private JsonNode boardJson() {
        ScenarioWorld.ApiResponse response = deskContext.lastBoardResponse();
        requireOk("getDeskBoard", response);
        return response.json();
    }

    private JsonNode boardCardNode(UUID taskId) {
        return cardIn(boardJson(), taskId)
                .orElseThrow(() -> new AssertionError("櫃檯看板上找不到 task id=" + taskId));
    }

    private DeskTask boardCard(UUID taskId) {
        return toRecord(boardCardNode(taskId), DeskTask.class);
    }

    private static Optional<JsonNode> cardIn(JsonNode board, UUID taskId) {
        for (JsonNode card : board.path("tasks")) {
            if (taskId.toString().equalsIgnoreCase(card.path("id").asString())) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }

    /** 以櫃檯介面的身分重新讀一次看板，取這張單；呼叫端負責包在 {@link #sideRead(Supplier)} 裡。 */
    private DeskTask cardOnFreshBoard(String reader, UUID taskId) {
        ScenarioWorld.ApiResponse response = deskApi.getBoard(reader);
        requireOk("getDeskBoard（再讀）", response);
        JsonNode card = cardIn(response.json(), taskId)
                .orElseThrow(() -> new AssertionError("再讀櫃檯看板找不到 task id=" + taskId));
        return toRecord(card, DeskTask.class);
    }

    private JsonNode detailJson() {
        ScenarioWorld.ApiResponse response = deskContext.lastDetailResponse();
        requireOk("readDeskTask", response);
        return response.json();
    }

    private JsonNode detailReadBy(String reader, UUID taskId) {
        ScenarioWorld.ApiResponse response = sideRead(() -> deskApi.readTask(reader, taskId));
        requireOk("readDeskTask（" + reader + " 再讀）", response);
        return response.json();
    }

    private List<DeskTimelineEntry> timelineOf(JsonNode detail) {
        List<DeskTimelineEntry> timeline = new ArrayList<>();
        for (JsonNode entry : detail.path("timeline")) {
            timeline.add(toRecord(entry, DeskTimelineEntry.class));
        }
        return timeline;
    }

    private static DeskTimelineEntry lastEntry(List<DeskTimelineEntry> timeline) {
        if (timeline.isEmpty()) {
            throw new AssertionError("readDeskTask 的 timeline 是空的");
        }
        return timeline.get(timeline.size() - 1);
    }

    private List<DeskQuickReply> quickReplies() {
        ScenarioWorld.ApiResponse response = world.lastResponse();
        requireOk("listDeskQuickReplies", response);
        if (!response.json().isArray()) {
            throw new AssertionError("常用回覆應是陣列，實際 " + response.body());
        }
        List<DeskQuickReply> replies = new ArrayList<>();
        for (JsonNode item : response.json()) {
            replies.add(toRecord(item, DeskQuickReply.class));
        }
        return replies;
    }

    /** 被拒絕的回應必須是 409 與指定的 {@code Error.code}，回傳 {@code context} 給句型逐欄比對。 */
    private JsonNode rejectionContext(String code) {
        ScenarioWorld.ApiResponse response = world.lastResponse();
        check("被拒絕的狀態碼", 409, response.status());
        if (response.json() == null) {
            throw new AssertionError("被拒絕的回應沒有 body");
        }
        check("Error.code", code, textOrNull(response.json(), "code"));
        JsonNode context = response.json().path("context");
        if (!context.isObject()) {
            throw new AssertionError("Error.context 應是物件，實際 " + response.body());
        }
        return context;
    }

    private Optional<JsonNode> poolItemSeenBy(String dept, UUID taskId) {
        for (JsonNode item : poolItemsSeenBy(dept)) {
            if (taskId.toString().equalsIgnoreCase(item.path("taskId").asString())) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /** 以該部門一位在職同仁的身分呼叫 listDeptPool，看這個部門的待認領。 */
    private List<JsonNode> poolItemsSeenBy(String dept) {
        Map<String, Object> staff = dbProbe
                .queryMaps(
                        "SELECT id FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND active = true ORDER BY name",
                        tenantId(), dept)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("本場景沒有在職的「" + dept + "」同仁，無法以他的身分看待認領"));
        String jwt = mintJwtFor(uuid(staff.get("id")));
        ScenarioWorld.ApiResponse response = sideRead(() -> apiClient.get("/pool", BddApiClient.Auth.staff(jwt)));
        requireOk("listDeptPool（" + dept + "）", response);
        List<JsonNode> items = new ArrayList<>();
        for (JsonNode item : response.json().path("items")) {
            items.add(item);
        }
        return items;
    }

    private JsonNode myTodayOf(UUID staffId) {
        String jwt = mintJwtFor(staffId);
        ScenarioWorld.ApiResponse response = sideRead(() -> apiClient.get("/me/today", BddApiClient.Auth.staff(jwt)));
        requireOk("getMyToday", response);
        return response.json();
    }

    private <T> T sideRead(Supplier<T> read) {
        ScenarioWorld.ApiResponse last = peek(world::lastResponse);
        ScenarioWorld.ApiResponse board = peek(deskContext::lastBoardResponse);
        String boardReader = peek(deskContext::lastBoardReader);
        ScenarioWorld.ApiResponse detail = peek(deskContext::lastDetailResponse);
        try {
            return read.get();
        } finally {
            world.setLastResponse(last);
            deskContext.rememberBoardResponse(board);
            deskContext.rememberBoardReader(boardReader);
            deskContext.rememberDetailResponse(detail);
        }
    }

    private static <T> T peek(Supplier<T> getter) {
        try {
            return getter.get();
        } catch (IllegalStateException notYetRecorded) {
            return null;
        }
    }

    // ================================================================ 資料庫讀取

    private long tenantId() {
        return tenantFixtures.testTenantId();
    }

    private Map<String, Object> taskRow(UUID taskId) {
        return dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此任務：" + taskId));
    }

    private String roomNoOf(UUID taskId) {
        return dbProbe
                .queryOne(
                        "SELECT r.room_no FROM tasks t JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id WHERE t.id = ?",
                        taskId)
                .map(row -> String.valueOf(row.get("room_no")))
                .orElseThrow(() -> new AssertionError("找不到 task id=" + taskId + " 的房號"));
    }

    private Optional<Map<String, Object>> repairTicketRow(UUID taskId) {
        return dbProbe.queryOne("SELECT * FROM repair_tickets WHERE task_id = ?", taskId);
    }

    private String displayNoInDb(UUID taskId, Map<String, Object> row) {
        if ("repair".equals(row.get("type"))) {
            return repairTicketRow(taskId).map(t -> (String) t.get("ticket_no")).orElse(null);
        }
        return (String) row.get("task_no");
    }

    private List<String> eventTypes(UUID taskId) {
        return dbProbe.eventsOf(taskId).stream().map(e -> String.valueOf(e.get("type"))).toList();
    }

    private List<Map<String, Object>> eventsOfType(UUID taskId, String type) {
        return dbProbe.eventsOf(taskId).stream().filter(e -> type.equals(e.get("type"))).toList();
    }

    private Map<String, Object> latestEvent(UUID taskId) {
        List<Map<String, Object>> events = dbProbe.eventsOf(taskId);
        if (events.isEmpty()) {
            throw new AssertionError("task id=" + taskId + " 沒有任何 task_events");
        }
        return events.get(events.size() - 1);
    }

    private Optional<Map<String, Object>> latestEventOfType(UUID taskId, String type) {
        List<Map<String, Object>> events = eventsOfType(taskId, type);
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(events.size() - 1));
    }

    private Optional<Map<String, Object>> latestAcceptEvent(UUID taskId) {
        return dbProbe.queryOne(
                """
                SELECT * FROM task_events
                 WHERE task_id = ? AND type IN ('claimed', 'assigned', 'reassigned')
                 ORDER BY seq DESC
                 LIMIT 1
                """,
                taskId);
    }

    private String actorNameOfLatest(UUID taskId, String type) {
        return latestEventOfType(taskId, type).map(e -> staffNameOf(e.get("actor_id"))).orElse(null);
    }

    /** 等待起算是 created 與 returned_to_pool 兩種事件中最新一筆的時間；triaged 不算在內。 */
    private Instant latestWaitingEventAt(UUID taskId) {
        return dbProbe
                .queryOne(
                        "SELECT max(created_at) AS at FROM task_events WHERE task_id = ? AND type IN ('created', 'returned_to_pool')",
                        taskId)
                .map(row -> instantOf(row.get("at")))
                .orElse(null);
    }

    private boolean undoneRightAfter(Map<String, Object> triaged) {
        return dbProbe.count(
                "SELECT count(*) FROM task_events WHERE task_id = ? AND seq = ? AND type = 'triage_undone'",
                triaged.get("task_id"), seqOf(triaged) + 1) > 0;
    }

    private Optional<Map<String, Object>> inHouseReservation(String roomNo) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), clock.getZone());
        return dbProbe.queryOne(
                """
                SELECT rc.*
                  FROM reservations_cache rc
                  JOIN rooms r ON r.tenant_id = rc.tenant_id AND r.id = rc.room_id
                 WHERE rc.tenant_id = ? AND r.room_no = ? AND rc.superseded_at IS NULL
                   AND rc.checkin_at <= ? AND rc.checkout_at > ?
                """,
                tenantId(), roomNo, now, now);
    }

    /** 契約 DeskTask.guestName 的認定：單有連結訂房時取那筆，沒有時取這間房現在住著的訂房。 */
    private Optional<Map<String, Object>> guestReservationOf(UUID taskId, Map<String, Object> row) {
        Object reservationId = row.get("reservation_id");
        if (reservationId != null) {
            return dbProbe.queryOne("SELECT * FROM reservations_cache WHERE id = ?", reservationId);
        }
        return inHouseReservation(roomNoOf(taskId));
    }

    private String reservationMonthDay(UUID taskId, Map<String, Object> row, String column) {
        return guestReservationOf(taskId, row)
                .map(stay -> localDate(instantOf(stay.get(column))).format(MONTH_DAY))
                .orElse(null);
    }

    private boolean isVip(Map<String, Object> stay) {
        Object attrs = stay.get("attrs");
        if (attrs == null) {
            return false;
        }
        for (Map.Entry<String, JsonNode> attr : objectMapper.readTree(String.valueOf(attrs)).properties()) {
            if ("vip".equalsIgnoreCase(attr.getKey()) && attr.getValue().asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private Iterable<JsonNode> guestInfoPages() {
        List<Map<String, Object>> rows = dbProbe.queryMaps(
                "SELECT value FROM tenant_config WHERE tenant_id = ? AND key = 'guest_info_pages' ORDER BY updated_at DESC",
                tenantId());
        if (rows.isEmpty()) {
            throw new AssertionError("租戶設定沒有 guest_info_pages");
        }
        return objectMapper.readTree(String.valueOf(rows.get(0).get("value")));
    }

    /** 房務與工務的在職同仁，當場回答的單不能算進他們任何一位的今日完成。 */
    private List<Map<String, Object>> fieldStaff() {
        return dbProbe.queryMaps(
                "SELECT id, name FROM staff_accounts WHERE tenant_id = ? AND active = true AND dept IN (?, ?) ORDER BY name",
                tenantId(), HOUSEKEEPING, ENGINEERING);
    }

    private Map<String, Object> staffRow(Object staffId) {
        if (staffId == null) {
            throw new AssertionError("事件沒有記操作者");
        }
        return dbProbe.queryOne("SELECT * FROM staff_accounts WHERE id = ?", staffId)
                .orElseThrow(() -> new AssertionError("staff_accounts 找不到 id=" + staffId));
    }

    private String staffNameOf(Object staffId) {
        return staffId == null ? null : String.valueOf(staffRow(staffId).get("name"));
    }

    /** 本場景裡任一位可以用櫃檯介面的人（櫃檯、主管、值班主管），用來再讀看板或詳情。 */
    private String deskReader() {
        for (Map<String, Object> staff : dbProbe.queryMaps(
                """
                SELECT name FROM staff_accounts
                 WHERE tenant_id = ? AND active = true AND role IN ('frontdesk', 'manager', 'duty_manager')
                 ORDER BY CASE role WHEN 'frontdesk' THEN 0 ELSE 1 END, name
                """,
                tenantId())) {
            String name = String.valueOf(staff.get("name"));
            if (peek(() -> world.jwt(name)) != null) {
                return name;
            }
        }
        throw new IllegalStateException("本場景沒有櫃檯、主管或值班主管，無法再讀一次櫃檯介面");
    }

    private String mintJwtFor(UUID staffId) {
        Map<String, Object> staff = staffRow(staffId);
        return jwtService
                .issue(tenantId(), staffId, (String) staff.get("role"), (String) staff.get("dept"))
                .token();
    }

    private UUID staffId(String name) {
        return UUID.fromString(world.staffId(name));
    }

    /** 任務用房號指；修繕單用工務單號（{@code W-} 開頭）指。 */
    private UUID taskIdOf(String key) {
        if (key.startsWith("W-")) {
            return UUID.fromString(world.repairTicketTaskId(key));
        }
        return UUID.fromString(world.taskId(key));
    }

    /** 拿 When 送出前記下的快照，並確認記的就是這張單；記的是別張單時比對沒有意義。 */
    private LastOperationGuard.Snapshot snapshotOf(UUID taskId) {
        LastOperationGuard.Snapshot snapshot = lastOperationGuard.current();
        if (!taskId.equals(snapshot.taskId())) {
            throw new IllegalStateException(
                    "操作前的快照記的是 task id=" + snapshot.taskId() + "，不是要檢查的 " + taskId);
        }
        return snapshot;
    }

    /** 被拒絕的操作不能動到這張單，也不能多寫任何事件。 */
    private void assertUnchangedSinceWhen(UUID taskId) {
        LastOperationGuard.Snapshot before = snapshotOf(taskId);
        Map<String, Object> after = taskRow(taskId);
        if (!Objects.equals(before.taskRow(), after)) {
            throw new AssertionError("操作被拒絕後 tasks 仍被改動：操作前=" + before.taskRow() + "，操作後=" + after);
        }
        check("操作被拒絕後 task_events 的筆數", before.eventCount(), dbProbe.eventsOf(taskId).size());
    }

    private void assertSameEvent(Map<String, Object> before, Map<String, Object> after) {
        String where = "seq=" + before.get("seq") + " 的事件";
        check(where + "的 seq", before.get("seq"), after.get("seq"));
        check(where + "的 type", before.get("type"), after.get("type"));
        check(where + "的 actor_id", before.get("actor_id"), after.get("actor_id"));
        check(where + "的 actor_role", before.get("actor_role"), after.get("actor_role"));
        check(where + "的 payload", payloadOf(before), payloadOf(after));
        checkInstant(where + "的時間", instantOf(before.get("created_at")), instantOf(after.get("created_at")));
    }

    /**
     * 契約規定給人看的歷程略過兩種事件：被復原的派單與緊接其後的那筆復原（成對略過），以及逐項勾選清潔項目。
     * 這裡照同一條規則從 {@code task_events} 挑出應該出現在歷程上的事件，用來和回應的 timeline 逐筆對。
     */
    private static List<Map<String, Object>> visibleTimelineEvents(List<Map<String, Object>> events) {
        Set<Integer> hidden = new HashSet<>();
        for (int i = 0; i < events.size(); i++) {
            Map<String, Object> event = events.get(i);
            if ("sop_checked".equals(event.get("type"))) {
                hidden.add(seqOf(event));
            }
            if ("triaged".equals(event.get("type")) && i + 1 < events.size()) {
                Map<String, Object> next = events.get(i + 1);
                if ("triage_undone".equals(next.get("type")) && seqOf(next) == seqOf(event) + 1) {
                    hidden.add(seqOf(event));
                    hidden.add(seqOf(next));
                }
            }
        }
        return events.stream().filter(e -> !hidden.contains(seqOf(e))).toList();
    }

    private static void assertTimelineMatchesEvents(
            String what, List<DeskTimelineEntry> timeline, List<Map<String, Object>> events) {
        check(what + " 的筆數", events.size(), timeline.size());
        for (int i = 0; i < events.size(); i++) {
            Map<String, Object> event = events.get(i);
            DeskTimelineEntry entry = timeline.get(i);
            String where = what + " 第 " + (i + 1) + " 筆";
            check(where + " 的 seq", seqOf(event), entry.seq());
            check(where + " 的 type", event.get("type"), entry.type());
            checkInstant(where + " 的 at", instantOf(event.get("created_at")), instantOf(entry.at()));
        }
    }

    /**
     * 依最近一筆到手事件換算到手方式：系統做的指派是班前排房，人做的指派是主管指派；
     * 認領必須是負責人自己認的才算自己認領，否則回傳一個不會相符的說明，讓比對失敗時看得出原因。
     */
    private static String acceptedViaOf(Map<String, Object> event, Map<String, Object> taskRow) {
        String type = String.valueOf(event.get("type"));
        return switch (type) {
            case "claimed" -> sameId(event.get("actor_id"), taskRow.get("assignee_id"))
                    ? "self_claimed"
                    : "claimed（認領的人不是負責人）";
            case "assigned" -> "system".equals(event.get("actor_role")) ? "plan_assigned" : "manager_assigned";
            case "reassigned" -> "reassigned";
            default -> type;
        };
    }

    private JsonNode payloadOf(Map<String, Object> event) {
        Object payload = event.get("payload");
        if (payload == null) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(String.valueOf(payload));
    }

    private JsonNode noteOf(Map<String, Object> taskRow) {
        Object note = taskRow.get("note");
        if (note == null) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(String.valueOf(note));
        } catch (RuntimeException notJson) {
            throw new AssertionError("tasks.note 不是 JSON，讀不出住客原文：" + note, notJson);
        }
    }

    private <T> T toRecord(JsonNode node, Class<T> type) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (RuntimeException e) {
            throw new AssertionError("回應不符合契約 " + type.getSimpleName() + "：" + node, e);
        }
    }

    // ================================================================ 小工具

    private static void requireOk(String operation, ScenarioWorld.ApiResponse response) {
        if (response == null || response.status() != 200 || response.json() == null) {
            throw new AssertionError(operation + " 預期回 200，實際 "
                    + (response == null ? "沒有回應" : "status=" + response.status() + "，body=" + response.body()));
        }
    }

    private Instant todayAt(String hhmm) {
        LocalDate today = localDate(clock.instant());
        return ZonedDateTime.of(today, LocalTime.parse(hhmm), clock.getZone()).toInstant();
    }

    private LocalDate localDate(Instant instant) {
        return LocalDate.ofInstant(instant, clock.getZone());
    }

    private String hourMinuteOf(Instant instant) {
        return instant == null ? null : LocalTime.ofInstant(instant, clock.getZone()).format(HOUR_MINUTE);
    }

    private static String monthDayOf(String isoDate) {
        return isoDate == null ? null : LocalDate.parse(isoDate).format(MONTH_DAY);
    }

    private static LocalDate businessDateOf(JsonNode board) {
        return LocalDate.parse(board.path("businessDate").asString());
    }

    private static Instant parseInstant(JsonNode node, String field) {
        String text = textOrNull(node, field);
        return text == null ? null : OffsetDateTime.parse(text).toInstant();
    }

    private static Instant instantOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        throw new IllegalStateException("無法換算成時間點的型別：" + value.getClass());
    }

    private static int seqOf(Map<String, Object> event) {
        return ((Number) event.get("seq")).intValue();
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private static boolean sameId(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        return String.valueOf(a).equalsIgnoreCase(String.valueOf(b));
    }

    private static void checkSameId(String what, UUID expected, Object actual) {
        if (!sameId(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull();
    }

    private static String textOrNull(JsonNode node, String field) {
        if (isAbsent(node) || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean yes(String value) {
        return switch (value) {
            case "是" -> true;
            case "否" -> false;
            default -> throw new IllegalArgumentException("只接受「是」或「否」，實際「" + value + "」");
        };
    }

    private static String stateValue(String term) {
        try {
            return Terms.taskState(term);
        } catch (IllegalArgumentException notTaskState) {
            return Terms.repairTicketState(term);
        }
    }

    private static String otherFieldDept(String dept) {
        return switch (dept) {
            case HOUSEKEEPING -> ENGINEERING;
            case ENGINEERING -> HOUSEKEEPING;
            default -> throw new IllegalArgumentException("待認領只分房務與工務，沒有「" + dept + "」");
        };
    }

    private static List<String> splitList(String list) {
        return List.of(list.split("、")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String lookup(Map<String, String> table, String term, String tableName) {
        String value = term == null ? null : table.get(term);
        if (value == null) {
            throw new IllegalArgumentException("找不到「" + term + "」在" + tableName + "對照表中的權威值");
        }
        return value;
    }

    private static void check(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }

    private static void checkInstant(String what, Instant expected, Instant actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }

    private static Instant instantOf(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
