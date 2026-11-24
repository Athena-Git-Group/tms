package com.athena.tms.bdd.steps.desk;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.DeskContext;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.StaffFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.service.JwtService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class DeskCreateRequestThenSteps {

    private static final String REPLAYED_HEADER = "Idempotency-Replayed";

    private static final Map<String, String> TYPE = Map.of(
            "備品", "amenity",
            "其他", "other",
            "修繕", "repair");

    private static final Map<String, Integer> RELATIVE_DAY = Map.of("昨天", -1, "今天", 0, "明天", 1, "後天", 2);

    private static final Pattern MONTH_DAY = Pattern.compile("^(\\d{1,2})/(\\d{1,2})$");

    private final DeskContext deskContext;
    private final LastOperationGuard lastOperationGuard;
    private final DbProbe dbProbe;
    private final ScenarioWorld world;
    private final DeskApi deskApi;
    private final BddApiClient apiClient;
    private final JwtService jwtService;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;
    private final ObjectMapper objectMapper;

    public DeskCreateRequestThenSteps(
            DeskContext deskContext,
            LastOperationGuard lastOperationGuard,
            DbProbe dbProbe,
            ScenarioWorld world,
            DeskApi deskApi,
            BddApiClient apiClient,
            JwtService jwtService,
            TenantFixtures tenantFixtures,
            MutableClock clock,
            ObjectMapper objectMapper) {
        this.deskContext = deskContext;
        this.lastOperationGuard = lastOperationGuard;
        this.dbProbe = dbProbe;
        this.world = world;
        this.deskApi = deskApi;
        this.apiClient = apiClient;
        this.jwtService = jwtService;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @那麼("這次代開依序開出以下單：")
    public void createdTasksInOrder(DataTable table) {
        ScenarioWorld.ApiResponse response = deskContext.lastCreateResponse();
        requireStatus(response, 201, "這次代開");
        List<Map<String, String>> expected = table.asMaps(String.class, String.class);
        List<JsonNode> cards = cardsOf(response);
        if (cards.size() != expected.size()) {
            throw new AssertionError("這次代開預期開出 " + expected.size() + " 張單，實際 " + cards.size()
                    + " 張：" + response.body());
        }
        String roomNo = String.valueOf(deskContext.lastCreateRequestBody().get("roomNo"));
        for (int i = 0; i < expected.size(); i++) {
            Map<String, String> row = expected.get(i);
            JsonNode card = cards.get(i);
            String where = "第 " + (i + 1) + " 張卡片";
            check(where + "的 title", row.get("標題"), text(card, "title"));
            check(where + "的 dept", Terms.dept(row.get("部門")), text(card, "dept"));
            check(where + "的 type", lookup(TYPE, row.get("類型"), "類型"), text(card, "type"));
            String displayNo = text(card, "displayNo");
            if (displayNo == null || !displayNo.startsWith(row.get("單號開頭"))) {
                throw new AssertionError(where + "的 displayNo 預期以 " + row.get("單號開頭") + " 開頭，實際 " + displayNo);
            }
            check(where + "的 state", "pool", text(card, "state"));
            check(where + "的 source", "desk", text(card, "source"));
            check(where + "的 assigneeId", null, text(card, "assigneeId"));
            check(where + "的 roomNo", roomNo, text(card, "roomNo"));
        }

        LastOperationGuard.CreateRequestSnapshot before = lastOperationGuard.currentCreateRequest();
        long tasksNow = lastOperationGuard.createRequestCounts(before.tenantId()).tasks();
        check("這次代開後 tasks 多出的張數", (long) expected.size(), tasksNow - before.tasks());
        for (int i = 0; i < expected.size(); i++) {
            UUID taskId = UUID.fromString(text(cards.get(i), "id"));
            Map<String, Object> taskRow = requireTaskRow(taskId);
            String where = "「" + expected.get(i).get("標題") + "」";
            check(where + "的 tasks.pool_source", "desk_forward", taskRow.get("pool_source"));
            check(where + "的 tasks.reservation_id", null, taskRow.get("reservation_id"));
            long tickets = dbProbe.count("SELECT count(*) FROM repair_tickets WHERE task_id = ?", taskId);
            if ("W-".equals(expected.get(i).get("單號開頭"))) {
                check(where + "的 tasks.task_no", null, taskRow.get("task_no"));
                check(where + "的 repair_tickets 列數", 1L, tickets);
            } else {
                if (taskRow.get("task_no") == null) {
                    throw new AssertionError(where + "的 tasks.task_no 應有值");
                }
                check(where + "的 repair_tickets 列數", 0L, tickets);
            }
        }

        Map<String, Set<String>> idsByDept = new HashMap<>();
        for (JsonNode card : cards) {
            idsByDept.computeIfAbsent(text(card, "dept"), k -> new HashSet<>()).add(text(card, "id"));
        }
        for (String dept : List.of("housekeeping", "engineering")) {
            Set<String> mine = idsByDept.getOrDefault(dept, Set.of());
            Set<String> others = new HashSet<>();
            idsByDept.forEach((d, ids) -> {
                if (!d.equals(dept)) {
                    others.addAll(ids);
                }
            });
            Optional<Set<String>> pool = poolTaskIds(dept);
            if (pool.isEmpty()) {
                if (!mine.isEmpty()) {
                    throw new IllegalStateException("本案例沒有在職的「" + dept + "」員工，無法確認他們的待認領看得到這幾張單");
                }
                continue;
            }
            if (!pool.get().containsAll(mine)) {
                throw new AssertionError("「" + dept + "」的待認領應看得到 " + mine + "，實際 " + pool.get());
            }
            for (String id : others) {
                if (pool.get().contains(id)) {
                    throw new AssertionError("「" + dept + "」的待認領不該看到別的部門的單 " + id);
                }
            }
        }

        JsonNode board = boardOf(deskContext.lastCreateStaff());
        for (JsonNode card : cards) {
            JsonNode onBoard = findById(board.path("tasks"), text(card, "id"))
                    .orElseThrow(() -> new AssertionError("櫃檯看板上找不到「" + text(card, "title") + "」"));
            check("櫃檯看板上「" + text(card, "title") + "」的 state", "pool", text(onBoard, "state"));
        }
    }

    @那麼("這次代開開出一張單，標題就是送出的那 {int} 個字")
    public void createdOneTaskWithFullText(int length) {
        ScenarioWorld.ApiResponse response = deskContext.lastCreateResponse();
        requireStatus(response, 201, "這次代開");
        String sent = String.valueOf(otherOf(deskContext.lastCreateRequestBody()).get("text"));
        check("送出的字數", length, sent.codePointCount(0, sent.length()));
        List<JsonNode> cards = cardsOf(response);
        check("這次代開開出的張數", 1, cards.size());
        JsonNode card = cards.get(0);
        check("卡片的 title", sent, text(card, "title"));
        check("卡片的 state", "pool", text(card, "state"));
        check("卡片的 source", "desk", text(card, "source"));
        check("tasks.title", sent, requireTaskRow(UUID.fromString(text(card, "id"))).get("title"));
    }

    @那麼("這次代開回應的卡片和櫃檯看板上的那幾張一模一樣，帶著後端現在的時間")
    public void responseCardsMatchBoard() {
        ScenarioWorld.ApiResponse response = deskContext.lastCreateResponse();
        requireStatus(response, 201, "這次代開");
        String serverTime = text(response.json(), "serverTime");
        if (serverTime == null) {
            throw new AssertionError("這次代開的回應沒有 serverTime：" + response.body());
        }
        check("serverTime",
                clock.instant().truncatedTo(ChronoUnit.MILLIS),
                OffsetDateTime.parse(serverTime).toInstant().truncatedTo(ChronoUnit.MILLIS));
        List<JsonNode> cards = cardsOf(response);
        if (cards.isEmpty()) {
            throw new AssertionError("這次代開的回應沒有任何卡片");
        }
        JsonNode board = boardOf(deskContext.lastCreateStaff());
        for (JsonNode card : cards) {
            String title = text(card, "title");
            for (String field : List.of("id", "seq", "displayNo", "state", "dept", "type", "source", "roomNo",
                    "title", "createdAt", "waitingSince")) {
                if (!card.has(field)) {
                    throw new AssertionError("「" + title + "」的卡片不是完整的 DeskTask，缺 " + field);
                }
            }
            if (!card.has("menuKey")) {
                throw new AssertionError("「" + title + "」的卡片不是完整的 DeskTask，缺 menuKey");
            }
            JsonNode onBoard = findById(board.path("tasks"), text(card, "id"))
                    .orElseThrow(() -> new AssertionError("櫃檯看板上找不到「" + title + "」"));
            if (!card.equals(onBoard)) {
                throw new AssertionError("「" + title + "」代開回應的卡片和看板上的不同：\n回應=" + card + "\n看板=" + onBoard);
            }
            check("「" + title + "」的 waitingSince 等於 createdAt",
                    instantOf(text(card, "createdAt")), instantOf(text(card, "waitingSince")));
        }
    }

    @那麼("這次代開的每一張單，歷程都只有一筆 {string} 的建立，沒有派單")
    public void eachTaskHasOnlyCreatedEvent(String staffName) {
        ScenarioWorld.ApiResponse response = deskContext.lastCreateResponse();
        requireStatus(response, 201, "這次代開");
        UUID staffId = UUID.fromString(world.staffId(staffName));
        String role = dbProbe.queryOne("SELECT role FROM staff_accounts WHERE id = ?", staffId)
                .map(row -> String.valueOf(row.get("role")))
                .orElseThrow(() -> new IllegalStateException("staff_accounts 找不到「" + staffName + "」"));
        for (JsonNode card : cardsOf(response)) {
            UUID taskId = UUID.fromString(text(card, "id"));
            String where = "「" + text(card, "title") + "」";
            List<Map<String, Object>> events = dbProbe.eventsOf(taskId);
            if (events.size() != 1) {
                throw new AssertionError(where + "的 task_events 預期剛好一筆 created，實際 " + events);
            }
            Map<String, Object> created = events.get(0);
            check(where + "事件的 type", "created", created.get("type"));
            check(where + "事件的 actor_id", staffId.toString(), String.valueOf(created.get("actor_id")));
            check(where + "事件的 actor_role", role, created.get("actor_role"));
            check(where + "created 的 payload.source", "desk", text(jsonOf(created.get("payload")), "source"));

            JsonNode detail = sideRead(() -> deskApi.readTask(staffName, taskId)).json();
            JsonNode timeline = detail == null ? null : detail.path("timeline");
            if (timeline == null || timeline.size() != 1) {
                throw new AssertionError(where + "的 readDeskTask timeline 預期剛好一筆，實際 " + timeline);
            }
            check(where + "timeline 的 type", "created", text(timeline.get(0), "type"));
            check(where + "timeline 的 actor.name", staffName, text(timeline.get(0).path("actor"), "name"));
            check(where + "的 createdByName", staffName, text(detail, "createdByName"));
        }
    }

    @那麼("這次代開失敗，一張單都沒有留下，流水號也沒有被用掉")
    public void createFailedLeavingNothing() {
        ScenarioWorld.ApiResponse response = deskContext.lastCreateResponse();
        if (response.status() < 500 || response.status() > 599) {
            throw new AssertionError("這次代開預期回 5xx，實際狀態碼=" + response.status() + "，body=" + response.body());
        }
        LastOperationGuard.CreateRequestSnapshot before = lastOperationGuard.currentCreateRequest();
        LastOperationGuard.CreateRequestSnapshot after = lastOperationGuard.createRequestCounts(before.tenantId());
        check("tasks 張數", before.tasks(), after.tasks());
        check("task_events 筆數", before.taskEvents(), after.taskEvents());
        check("repair_tickets 張數", before.repairTickets(), after.repairTickets());
        check("outbox 筆數", before.outbox(), after.outbox());
        check("outbox 最大 id", before.outboxMaxId(), after.outboxMaxId());
        check("task_counters.last_no", before.lastTaskNo(), after.lastTaskNo());
        check("repair_ticket_counters.last_no", before.lastTicketNo(), after.lastTicketNo());
        check("processed_ops 這把鍵的筆數", 0L, processedOps(deskContext.lastCreateIdempotencyKey()));

        Set<String> existing = idsOf(lastOperationGuard.existingRowsBeforeCreate().tasks());
        for (String dept : List.of("housekeeping", "engineering")) {
            Set<String> fresh = new HashSet<>(poolTaskIds(dept).orElseThrow(() -> new IllegalStateException(
                    "本案例沒有在職的「" + dept + "」員工，無法確認他們的待認領沒有新的單")));
            fresh.removeAll(existing);
            if (!fresh.isEmpty()) {
                throw new AssertionError("「" + dept + "」的待認領出現了新的單：" + fresh);
            }
        }
    }

    @那麼("這次拿到的是第一次的結果，標示為回放，{string} 仍只有第一次開出的 {int} 張單")
    public void replayedFirstResult(String roomNo, int count) {
        List<ScenarioWorld.ApiResponse> responses = deskContext.createResponses();
        if (responses.size() < 2) {
            throw new IllegalStateException("本案例只代開過 " + responses.size() + " 次，沒有可比對的第一次結果");
        }
        ScenarioWorld.ApiResponse first = responses.get(0);
        ScenarioWorld.ApiResponse resent = responses.get(responses.size() - 1);
        requireStatus(resent, 201, "重送的代開");
        check("重送的 " + REPLAYED_HEADER, "true", resent.header(REPLAYED_HEADER));
        if (!Objects.equals(first.json(), resent.json())) {
            throw new AssertionError("重送拿到的 body 應與第一次相同：\n第一次=" + first.body() + "\n重送=" + resent.body());
        }
        Set<String> firstIds = idsOfCards(first);
        check("第一次開出的張數", count, firstIds.size());
        check("「" + roomNo + "」的代開任務", firstIds, deskTaskIdsOfRoom(roomNo));
        check("processed_ops 這把鍵的筆數", 1L, processedOps(deskContext.lastCreateIdempotencyKey()));
    }

    @那麼("兩次送出拿到同一批單，{string} 只開出 {int} 張單")
    public void bothSendsGotSameTasks(String roomNo, int count) {
        List<ScenarioWorld.ApiResponse> responses = deskContext.createResponses();
        if (responses.size() < 2) {
            throw new IllegalStateException("本案例只代開過 " + responses.size() + " 次");
        }
        ScenarioWorld.ApiResponse a = responses.get(responses.size() - 2);
        ScenarioWorld.ApiResponse b = responses.get(responses.size() - 1);
        requireStatus(a, 201, "第一次送出");
        requireStatus(b, 201, "第二次送出");
        check("兩次送出的卡片 id", idsOfCards(a), idsOfCards(b));
        long replayed = List.of(a, b).stream().filter(r -> "true".equals(r.header(REPLAYED_HEADER))).count();
        check("帶 " + REPLAYED_HEADER + ": true 的次數", 1L, replayed);
        Set<String> rooms = deskTaskIdsOfRoom(roomNo);
        check("「" + roomNo + "」代開的任務張數", count, rooms.size());
        check("「" + roomNo + "」代開的任務", idsOfCards(a), rooms);
        check("processed_ops 這把鍵的筆數", 1L, processedOps(deskContext.lastCreateIdempotencyKey()));
    }

    @那麼("住客這次送出的需求開單成功")
    public void guestRequestCreated() {
        ScenarioWorld.ApiResponse response = world.lastResponse();
        if (response.status() != 201) {
            throw new AssertionError("住客這次的 createGuestRequest 預期回 201，實際狀態碼=" + response.status()
                    + "，body=" + response.body());
        }
        String id = text(response.json(), "id");
        if (id == null) {
            throw new AssertionError("住客這次送出的回應沒有 id：" + response.body());
        }
        Map<String, Object> row = requireTaskRow(UUID.fromString(id));
        check("住客這張單的 tasks.source", "guest", row.get("source"));
        Set<String> added = idsOf(dbProbe.queryMaps(
                "SELECT id FROM tasks WHERE tenant_id = ?", tenantFixtures.testTenantId()));
        added.removeAll(world.taskIdsBeforeGuestRequest());
        check("住客這次送出後多出的任務", Set.of(id), added);
        Object body = world.lastRequest().body();
        if (body instanceof Map<?, ?> map && map.get("itemId") != null) {
            String item = String.valueOf(map.get("itemId"));
            if (!String.valueOf(row.get("title")).contains(item)) {
                throw new AssertionError("住客這張單的標題應是住客要的「" + item + "」，實際 " + row.get("title"));
            }
        }
    }

    @那麼("本案例原有的單都沒有任何改動")
    public void existingTasksUntouched() {
        LastOperationGuard.ExistingRows before = lastOperationGuard.existingRowsBeforeCreate();
        LastOperationGuard.ExistingRows after = lastOperationGuard.existingRows(tenantFixtures.testTenantId());
        Set<String> existingIds = idsOf(before.tasks());
        compareRows("tasks", before.tasks(), keep(after.tasks(), "id", existingIds), "id");
        compareRows("task_events", before.taskEvents(), keep(after.taskEvents(), "task_id", existingIds), "id");
        compareRows("repair_tickets", before.repairTickets(),
                keep(after.repairTickets(), "task_id", existingIds), "task_id");

        Set<String> added = idsOf(after.tasks());
        added.removeAll(existingIds);
        ScenarioWorld.ApiResponse response = deskContext.lastCreateResponse();
        check("這次新增的任務", idsOfCards(response), added);
    }

    @那麼("代開單的房間清單剛好如下：")
    public void roomListIs(DataTable table) {
        ScenarioWorld.ApiResponse response = deskContext.lastRoomsResponse();
        requireStatus(response, 200, "讀取代開單的房間清單");
        List<Map<String, String>> expected = table.asMaps(String.class, String.class);
        Map<String, JsonNode> actual = new HashMap<>();
        List<String> duplicated = new ArrayList<>();
        for (JsonNode room : response.json()) {
            if (text(room, "roomNo").startsWith(StaffFixtures.PLAN_ROOM_PREFIX)) {
                continue;
            }
            if (actual.put(text(room, "roomNo"), room) != null) {
                duplicated.add(text(room, "roomNo"));
            }
        }
        if (!duplicated.isEmpty()) {
            throw new AssertionError("房間清單有重複的房號 " + duplicated + "：" + response.body());
        }
        Set<String> expectedRooms = new LinkedHashSet<>();
        expected.forEach(row -> expectedRooms.add(row.get("房號")));
        check("房間清單的房號", expectedRooms, actual.keySet());

        long tenantId = tenantFixtures.testTenantId();
        Set<String> guestRooms = new HashSet<>();
        for (Map<String, Object> row : dbProbe.queryMaps(
                "SELECT room_no FROM rooms WHERE tenant_id = ? AND room_type <> 'public_area' AND room_no NOT LIKE ?",
                tenantId, StaffFixtures.PLAN_ROOM_PREFIX + "%")) {
            guestRooms.add(String.valueOf(row.get("room_no")));
        }
        check("rooms 的客房（不含公共區域佔位房）", expectedRooms, guestRooms);

        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Map<String, String> row : expected) {
            String roomNo = row.get("房號");
            JsonNode guest = actual.get(roomNo).path("guest");
            String guestName = blankToNull(row.get("住客"));
            Optional<Map<String, Object>> stay = dbProbe.queryOne(
                    """
                    SELECT rc.guest_name, rc.checkin_at, rc.checkout_at FROM reservations_cache rc
                      JOIN rooms r ON r.id = rc.room_id AND r.tenant_id = rc.tenant_id
                     WHERE rc.tenant_id = ? AND r.room_no = ? AND rc.superseded_at IS NULL
                       AND rc.checkin_at <= ? AND rc.checkout_at > ?
                    """,
                    tenantId, roomNo, now, now);
            if (guestName == null) {
                if (!guest.isMissingNode() && !guest.isNull()) {
                    throw new AssertionError("「" + roomNo + "」沒有在住的住客，guest 應為 null，實際 " + guest);
                }
                if (stay.isPresent()) {
                    throw new AssertionError("「" + roomNo + "」在 reservations_cache 仍有在住的訂房：" + stay.get());
                }
                continue;
            }
            if (guest.isMissingNode() || guest.isNull()) {
                throw new AssertionError("「" + roomNo + "」應有在住的住客「" + guestName + "」，實際 guest 為 null");
            }
            check("「" + roomNo + "」的 guest.name", guestName, text(guest, "name"));
            check("「" + roomNo + "」的 guest.vip", "是".equals(blankToNull(row.get("VIP"))), guest.path("vip").asBoolean());
            String checkin = blankToNull(row.get("入住日"));
            if (checkin != null) {
                check("「" + roomNo + "」的 guest.checkinDate", date(checkin).toString(), text(guest, "checkinDate"));
            }
            String checkout = blankToNull(row.get("退房日"));
            if (checkout != null) {
                check("「" + roomNo + "」的 guest.checkoutDate", date(checkout).toString(), text(guest, "checkoutDate"));
            }
            Map<String, Object> stayRow = stay.orElseThrow(
                    () -> new AssertionError("「" + roomNo + "」在 reservations_cache 沒有在住的訂房"));
            check("「" + roomNo + "」在住訂房的 guest_name", guestName, stayRow.get("guest_name"));
        }
    }

    @那麼("櫃檯看板上各單的對應品項如下：")
    public void boardMenuKeysAre(DataTable table) {
        ScenarioWorld.ApiResponse board = deskContext.lastBoardResponse();
        requireStatus(board, 200, "讀取櫃檯看板");
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String title = row.get("標題");
            String expected = blankToNull(row.get("對應品項"));
            JsonNode card = null;
            for (JsonNode task : board.json().path("tasks")) {
                if (title.equals(text(task, "title"))) {
                    if (card != null) {
                        throw new IllegalStateException("櫃檯看板上有兩張標題都是「" + title + "」的單");
                    }
                    card = task;
                }
            }
            if (card == null) {
                throw new AssertionError("櫃檯看板上找不到標題「" + title + "」的單：" + board.body());
            }
            check("「" + title + "」的 menuKey", expected, text(card, "menuKey"));
            Object noteMenuKey = dbProbe.queryOne(
                            "SELECT note::jsonb ->> 'menu_key' AS menu_key FROM tasks WHERE id = ?",
                            UUID.fromString(text(card, "id")))
                    .map(r -> r.get("menu_key"))
                    .orElse(null);
            check("「" + title + "」的 tasks.note.menu_key", expected, noteMenuKey);
        }
    }

    private JsonNode boardOf(String staffName) {
        ScenarioWorld.ApiResponse board = sideRead(() -> deskApi.getBoard(staffName));
        requireStatus(board, 200, "getDeskBoard（" + staffName + " 再讀）");
        return board.json();
    }

    private Optional<Set<String>> poolTaskIds(String dept) {
        long tenantId = tenantFixtures.testTenantId();
        Optional<Map<String, Object>> staff = dbProbe.queryOne(
                """
                SELECT id, role, dept FROM staff_accounts
                 WHERE tenant_id = ? AND dept = ? AND role = ? AND active = true
                 ORDER BY id LIMIT 1
                """,
                tenantId, dept, dept);
        if (staff.isEmpty()) {
            return Optional.empty();
        }
        UUID staffId = UUID.fromString(String.valueOf(staff.get().get("id")));
        String jwt = jwtService.issue(tenantId, staffId, dept, dept).token();
        ScenarioWorld.ApiResponse pool = sideRead(() -> apiClient.get("/pool", BddApiClient.Auth.staff(jwt)));
        requireStatus(pool, 200, "listDeptPool（" + dept + "）");
        Set<String> ids = new HashSet<>();
        for (JsonNode item : pool.json().path("items")) {
            ids.add(text(item, "taskId"));
        }
        return Optional.of(ids);
    }

    private Set<String> deskTaskIdsOfRoom(String roomNo) {
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> row : dbProbe.queryMaps(
                """
                SELECT t.id FROM tasks t
                  JOIN rooms r ON r.id = t.room_id AND r.tenant_id = t.tenant_id
                 WHERE t.tenant_id = ? AND r.room_no = ? AND t.source = 'desk'
                """,
                tenantFixtures.testTenantId(), roomNo)) {
            ids.add(String.valueOf(row.get("id")));
        }
        return ids;
    }

    private long processedOps(String idempotencyKey) {
        return dbProbe.count("SELECT count(*) FROM processed_ops WHERE tenant_id = ? AND op_id = ?",
                tenantFixtures.testTenantId(), idempotencyKey);
    }

    private LocalDate date(String cell) {
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        Integer offset = RELATIVE_DAY.get(cell.trim());
        if (offset != null) {
            return today.plusDays(offset);
        }
        Matcher m = MONTH_DAY.matcher(cell.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("日期格式須為 `M/D`，或昨天、今天、明天、後天，收到「" + cell + "」");
        }
        return LocalDate.of(today.getYear(), Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
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
        } catch (IllegalStateException none) {
            return null;
        }
    }

    private JsonNode jsonOf(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.readTree(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> otherOf(Map<String, Object> body) {
        Object other = body.get("other");
        if (!(other instanceof Map<?, ?>)) {
            throw new IllegalStateException("剛才那次代開沒有其他需求");
        }
        return (Map<String, Object>) other;
    }

    private static List<JsonNode> cardsOf(ScenarioWorld.ApiResponse response) {
        List<JsonNode> cards = new ArrayList<>();
        if (response.json() != null) {
            response.json().path("tasks").forEach(cards::add);
        }
        return cards;
    }

    private static Set<String> idsOfCards(ScenarioWorld.ApiResponse response) {
        Set<String> ids = new HashSet<>();
        cardsOf(response).forEach(card -> ids.add(text(card, "id")));
        return ids;
    }

    private static Set<String> idsOf(List<Map<String, Object>> rows) {
        Set<String> ids = new HashSet<>();
        rows.forEach(row -> ids.add(String.valueOf(row.get("id"))));
        return ids;
    }

    private static List<Map<String, Object>> keep(List<Map<String, Object>> rows, String column, Set<String> ids) {
        return rows.stream().filter(row -> ids.contains(String.valueOf(row.get(column)))).toList();
    }

    private static void compareRows(
            String table, List<Map<String, Object>> before, List<Map<String, Object>> after, String key) {
        if (before.size() != after.size()) {
            throw new AssertionError("原有的單的 " + table + " 列數變了：代開前 " + before.size() + "，代開後 " + after.size());
        }
        Map<String, Map<String, Object>> afterByKey = new HashMap<>();
        after.forEach(row -> afterByKey.put(String.valueOf(row.get(key)), row));
        for (Map<String, Object> row : before) {
            Map<String, Object> now = afterByKey.get(String.valueOf(row.get(key)));
            if (!Objects.equals(row, now)) {
                throw new AssertionError("原有的 " + table + " 被改動：代開前=" + row + "，代開後=" + now);
            }
        }
    }

    private static Optional<JsonNode> findById(JsonNode tasks, String id) {
        for (JsonNode task : tasks) {
            if (id.equals(text(task, "id"))) {
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }

    private Map<String, Object> requireTaskRow(UUID taskId) {
        return dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此任務：" + taskId));
    }

    private static java.time.Instant instantOf(String text) {
        return text == null ? null : OffsetDateTime.parse(text).toInstant();
    }

    private static void requireStatus(ScenarioWorld.ApiResponse response, int expected, String what) {
        if (response.status() != expected) {
            throw new AssertionError(what + " 預期回 " + expected + "，實際狀態碼=" + response.status()
                    + "，body=" + response.body());
        }
    }

    private static String lookup(Map<String, String> table, String key, String what) {
        String value = table.get(key);
        if (value == null) {
            throw new IllegalArgumentException("找不到「" + key + "」在" + what + "用語對照中的權威值");
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static void check(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }
}
