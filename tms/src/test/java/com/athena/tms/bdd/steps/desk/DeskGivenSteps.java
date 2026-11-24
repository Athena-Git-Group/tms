package com.athena.tms.bdd.steps.desk;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.DeskBoardFixtures;
import com.athena.tms.bdd.support.DeskContext;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.ReservationFixtures;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.StaffFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.common.UuidV7;
import com.athena.tms.service.GuestTenantConfigReader;
import com.athena.tms.service.TaskEngine;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

public class DeskGivenSteps {

    private static final Set<String> SITUATIONS = Set.of(
            "待派單", "待認領", "待開始", "進行中", "待查房", "已送達", "已完修", "查房通過", "已當場回答",
            "已取消（主管取消）", "已取消（住客收回）");

    private static final Pattern RELATIVE_TIME = Pattern.compile("^(今天|昨天|前天|(\\d+) 天前) (\\d{1,2}:\\d{2})$");

    private static final Pattern MONTH_DAY = Pattern.compile("^(\\d{1,2})/(\\d{1,2})$");

    private static final int DEFAULT_CLEANING_SOP_ITEMS = 8;

    private final ScenarioWorld world;
    private final DeskApi deskApi;
    private final DeskContext deskContext;
    private final DeskBoardFixtures boardFixtures;
    private final BddApiClient apiClient;
    private final JdbcClient jdbcClient;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final ReservationFixtures reservationFixtures;
    private final StaffFixtures staffFixtures;
    private final MutableClock clock;
    private final ObjectMapper objectMapper;
    private final GuestTenantConfigReader guestConfigReader;

    public DeskGivenSteps(
            ScenarioWorld world,
            DeskApi deskApi,
            DeskContext deskContext,
            BddApiClient apiClient,
            JdbcClient jdbcClient,
            TaskEngine taskEngine,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            ReservationFixtures reservationFixtures,
            StaffFixtures staffFixtures,
            MutableClock clock,
            ObjectMapper objectMapper,
            GuestTenantConfigReader guestConfigReader) {
        this.world = world;
        this.deskApi = deskApi;
        this.deskContext = deskContext;
        this.apiClient = apiClient;
        this.jdbcClient = jdbcClient;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.reservationFixtures = reservationFixtures;
        this.staffFixtures = staffFixtures;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.guestConfigReader = guestConfigReader;
        this.boardFixtures = new DeskBoardFixtures(
                taskEngine, jdbcClient, tenantFixtures, roomFixtures, deskApi, objectMapper, clock);
    }

    @假設("看板範圍內外有以下的單：")
    public void boardHasTasks(DataTable table) {
        List<Map<String, String>> rows = new ArrayList<>(table.asMaps(String.class, String.class));
        rows.sort(Comparator.comparingInt(r -> "進行中".equals(r.get("現況")) ? 1 : 0));
        for (Map<String, String> row : rows) {
            String roomNo = requireCell(row, "房號");
            String situation = requireCell(row, "現況");
            if (!SITUATIONS.contains(situation)) {
                throw new IllegalArgumentException("「現況」不在句型支援的值裡：" + situation);
            }
            DeskBoardFixtures.Spec spec = new DeskBoardFixtures.Spec(
                    roomNo,
                    requireCell(row, "內容"),
                    Terms.poolCategory(requireCell(row, "類別")),
                    situation,
                    relativeTime(blankToNull(row.get("時間"))),
                    staffIdOrNull(row.get("負責人")),
                    blankToNull(row.get("單號")),
                    blankToNull(row.get("到手方式")),
                    staffIdOrNull(row.get("經手主管")),
                    staffIdOrNull(row.get("原負責人")));
            DeskBoardFixtures.Created created = boardFixtures.create(spec);
            world.rememberTask(roomNo, created.taskId().toString());
            if (created.repairTicketNo() != null) {
                world.rememberRepairTicket(created.repairTicketNo(), created.taskId().toString());
            }
        }
    }

    @假設("{string} 的單在 {string} 被同仁退回待認領")
    public void returnedToPoolByColleagueAt(String taskKey, String hhmm) {
        boardFixtures.returnToPoolByColleague(resolveTaskId(taskKey), todayAt(hhmm));
    }

    @假設("以下房間現在有住客：")
    public void roomsCurrentlyOccupied(DataTable table) {
        LocalDate today = today();
        List<Stay> stays = new ArrayList<>();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String roomNo = requireCell(row, "房號");
            String guestName = requireCell(row, "住客");
            boolean vip = yes(row.get("VIP"));
            LocalDate checkin = monthDay(blankToNull(row.get("入住日")), today.minusDays(1));
            LocalDate checkout = monthDay(blankToNull(row.get("退房日")), today.plusDays(1));
            if (!checkout.isAfter(checkin)) {
                throw new IllegalArgumentException("退房日要晚於入住日：" + row);
            }
            roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
            UUID reservationId = reservationFixtures.upsertReservation(
                    roomNo, vip ? List.of("vip") : List.of(), checkin, LocalTime.of(15, 0), checkout, guestName);
            stays.add(new Stay(roomNo, reservationId));
        }
        if (usesRelativeDay(table)) {
            moveClockLaterTodayIntoAllStays(stays);
        }
        stays.stream().filter(s -> !inHouseNow(s)).findFirst().ifPresent(this::moveClockIntoStay);
        List<String> notInHouse = stays.stream().filter(s -> !inHouseNow(s)).map(Stay::roomNo).toList();
        if (!notInHouse.isEmpty()) {
            throw new IllegalStateException("這些房的住期和其他房沒有共同的「現在」，無法同時算入住中：" + notInHouse);
        }
    }

    @假設("^飯店的客房樓層為 (\\d+(?:、\\d+)*)$")
    public void hotelFloorsAre(String floorList) {
        long tenantId = tenantFixtures.testTenantId();
        List<Integer> floors = Arrays.stream(floorList.split("、")).map(String::trim).map(Integer::valueOf).toList();
        for (int floor : floors) {
            roomFixtures.ensureRoom(floor + "01", floor);
        }
        int lowest = floors.stream().min(Integer::compare).orElseThrow();
        List<Map<String, Object>> strays = jdbcClient
                .sql("SELECT id, floor FROM rooms WHERE tenant_id = ?")
                .params(tenantId)
                .query()
                .listOfRows()
                .stream()
                .filter(room -> !floors.contains(((Number) room.get("floor")).intValue()))
                .toList();
        for (Map<String, Object> room : strays) {
            try {
                jdbcClient.sql("DELETE FROM rooms WHERE tenant_id = ? AND id = ?").params(tenantId, room.get("id")).update();
            } catch (DataIntegrityViolationException referenced) {
                jdbcClient
                        .sql("UPDATE rooms SET floor = ? WHERE tenant_id = ? AND id = ?")
                        .params(lowest, tenantId, room.get("id"))
                        .update();
            }
        }
        Set<Integer> actual = new TreeSet<>(jdbcClient
                .sql("SELECT DISTINCT floor FROM rooms WHERE tenant_id = ?")
                .params(tenantId)
                .query(Integer.class)
                .list());
        if (!actual.equals(new TreeSet<>(floors))) {
            throw new IllegalStateException("房間主檔的樓層是 " + actual + "，沒有排成 " + floors);
        }
    }

    @假設("飯店的分類規則把含 {string} 的文字分給 {string}")
    public void classificationRule(String keyword, String deptChinese) {
        String dept = Terms.dept(deptChinese);
        if (!"housekeeping".equals(dept) && !"engineering".equals(dept)) {
            throw new IllegalArgumentException("分類規則只能分給房務或工務，收到「" + deptChinese + "」");
        }
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("rule_id", "fx-rule-" + UUID.randomUUID());
        rule.put("keywords", List.of(keyword));
        rule.put("dept", dept);
        jdbcClient
                .sql(
                        """
                        INSERT INTO tenant_config (tenant_id, key, value, updated_at)
                        VALUES (?, 'classification_rules', ?::jsonb, ?)
                        ON CONFLICT (tenant_id, key)
                        DO UPDATE SET value = tenant_config.value || EXCLUDED.value, updated_at = EXCLUDED.updated_at
                        """)
                .params(tenantFixtures.testTenantId(), objectMapper.writeValueAsString(List.of(rule)), now())
                .update();
    }

    @假設("飯店的服務項目 {string} 設定交給 {string}")
    public void requestMenuItemGoesTo(String label, String deptChinese) {
        String dept = Terms.dept(deptChinese);
        if (!"housekeeping".equals(dept) && !"engineering".equals(dept)) {
            throw new IllegalArgumentException("服務項目只能交給房務或工務，收到「" + deptChinese + "」");
        }
        long tenantId = tenantFixtures.testTenantId();
        List<Map<String, Object>> menu = new ArrayList<>();
        for (GuestTenantConfigReader.MenuItem item : guestConfigReader.requestMenu(tenantId)) {
            if (!label.equals(item.label())) {
                menu.add(menuItem(item.key(), item.label(), item.dept(), item.type()));
            }
        }
        menu.add(menuItem(label, label, dept, "amenity"));
        jdbcClient
                .sql(
                        """
                        INSERT INTO tenant_config (tenant_id, key, value, updated_at)
                        VALUES (?, 'request_menu', ?::jsonb, ?)
                        ON CONFLICT (tenant_id, key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at
                        """)
                .params(tenantId, objectMapper.writeValueAsString(menu), now())
                .update();
        boolean listed = guestConfigReader.requestMenu(tenantId).stream()
                .anyMatch(item -> label.equals(item.label()) && dept.equals(item.dept()));
        if (!listed) {
            throw new IllegalStateException("住客頁的勾選清單沒有交給「" + deptChinese + "」的「" + label + "」");
        }
    }

    private static Map<String, Object> menuItem(String key, String label, String dept, String type) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("label", label);
        item.put("dept", dept);
        item.put("type", type);
        return item;
    }

    @假設("{string} 已復原 {string} 的派單")
    public void deskAlreadyUndid(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        requireSuccess(deskApi.undoAssign(staffName, taskId), staffName + " 復原 " + taskKey + " 的派單");
        Map<String, Object> task = taskRow(taskId);
        requireEquals("triage", task.get("state"), taskKey + " 復原後的 tasks.state");
        requireEquals("frontdesk", task.get("dept"), taskKey + " 復原後的 tasks.dept");
        requireEquals("triage_undone", latestEventType(taskId), taskKey + " 復原後最新一筆事件");
    }

    @假設("{string} 已當場回答 {string}，回覆 {string}")
    public void deskAlreadyAnswered(String staffName, String taskKey, String reply) {
        UUID taskId = resolveTaskId(taskKey);
        requireSuccess(deskApi.answer(staffName, taskId, reply), staffName + " 當場回答 " + taskKey);
        Map<String, Object> task = taskRow(taskId);
        requireEquals("done", task.get("state"), taskKey + " 當場回答後的 tasks.state");
        requireEquals(reply.strip(), task.get("answer"), taskKey + " 當場回答後的 tasks.answer");
        if (countEvents(taskId, "answered") < 1) {
            throw new IllegalStateException(taskKey + " 當場回答後沒有 answered 事件");
        }
    }

    @假設("{string} 的住客已自己收回這張需求")
    public void guestWithdrew(String roomNo) {
        UUID taskId = resolveTaskId(roomNo);
        requireSuccess(deskApi.cancelGuestRequest(roomNo, taskId), roomNo + " 的住客收回需求");
        Map<String, Object> task = taskRow(taskId);
        requireEquals("cancelled", task.get("state"), roomNo + " 收回後的 tasks.state");
        requireEquals("guest_withdrew", task.get("cancel_reason"), roomNo + " 收回後的 tasks.cancel_reason");
        if (task.get("ended_at") == null) {
            throw new IllegalStateException(roomNo + " 住客收回後 tasks.ended_at 沒有寫入");
        }
    }

    @假設("{string} 已認領 {string}")
    public void staffAlreadyClaimed(String staffName, String taskKey) {
        claim(staffName, resolveTaskId(taskKey), taskKey);
    }

    @假設("{string} 已開始處理 {string}")
    public void staffAlreadyStarted(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        requireSuccess(
                apiClient.post("/tasks/" + taskId + "/start", BddApiClient.Auth.staff(world.jwt(staffName)), null),
                staffName + " 開始處理 " + taskKey);
        requireEquals("doing", taskRow(taskId).get("state"), taskKey + " 開始後的 tasks.state");
        requireEquals("started", latestEventType(taskId), taskKey + " 開始後最新一筆事件");
    }

    @假設("{string} 被 {string} 認領後因請假退回待認領")
    public void claimedThenReturnedForLeave(String taskKey, String staffName) {
        UUID taskId = resolveTaskId(taskKey);
        claim(staffName, taskId, taskKey);

        StaffFixtures.CreatedStaff manager =
                staffFixtures.createManagerLikeStaff("登記請假的主管-" + UUID.randomUUID(), "manager", "active");
        LocalDate today = today();
        Map<String, Object> body = Map.of(
                "tenantId", tenantFixtures.testTenantId(),
                "staffId", world.staffId(staffName),
                "fromAt", today.atStartOfDay(clock.getZone()).toOffsetDateTime()
                        .withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "toAt", today.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime()
                        .withOffsetSameInstant(ZoneOffset.UTC).toString());
        requireSuccess(apiClient.post("/leaves", BddApiClient.Auth.staff(manager.jwt()), body), staffName + " 請假");

        Map<String, Object> task = taskRow(taskId);
        requireEquals("pool", task.get("state"), taskKey + " 請假退回後的 tasks.state");
        requireEquals(null, task.get("assignee_id"), taskKey + " 請假退回後的負責人");
        requireEquals("leave_return", task.get("pool_source"), taskKey + " 請假退回後的 tasks.pool_source");
        if ("triaged".equals(latestEventType(taskId))) {
            throw new IllegalStateException(taskKey + " 請假退回後最新一筆事件仍是 triaged");
        }
    }

    @假設("派出後過了 {int} 秒")
    public void secondsAfterDispatch(int seconds) {
        clock.setInstant(clock.instant().plusSeconds(seconds));
    }

    @假設("{string} 已在 {string} 勾選 {int} 項清潔項目")
    public void staffCheckedSopItems(String staffName, String taskKey, int count) {
        UUID taskId = resolveTaskId(taskKey);
        long tenantId = tenantFixtures.testTenantId();
        List<UUID> items = ensureCleaningSopItems(tenantId);
        if (count > items.size()) {
            throw new IllegalArgumentException("要勾 " + count + " 項，但飯店的清潔 SOP 只有 " + items.size() + " 項");
        }
        for (UUID itemId : items.subList(0, count)) {
            Map<String, Object> body = Map.of("sopItemId", itemId.toString(), "result", "checked");
            requireSuccess(
                    apiClient.post("/tasks/" + taskId + "/sop-check", BddApiClient.Auth.staff(world.jwt(staffName)), body),
                    staffName + " 勾選 " + taskKey + " 的清潔項目");
        }
        long checked = jdbcClient
                .sql("SELECT count(*) FROM task_sop_checks WHERE tenant_id = ? AND task_id = ? AND checked")
                .params(tenantId, taskId)
                .query(Long.class)
                .single();
        requireEquals((long) count, checked, taskKey + " 已勾選的清潔項目數");
        requireEquals((long) count, countEvents(taskId, "sop_checked"), taskKey + " 的 sop_checked 事件數");
    }

    @假設("飯店的住客資訊頁依序如下：")
    public void guestInfoPages(DataTable table) {
        List<Map<String, Object>> pages = new ArrayList<>();
        int index = 1;
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("key", "page-" + index++);
            page.put("title", requireCell(row, "標題"));
            page.put("body", requireCell(row, "全文"));
            pages.add(page);
        }
        writeGuestInfoPages(pages);
    }

    @假設("飯店沒有設定住客資訊頁")
    public void noGuestInfoPages() {
        writeGuestInfoPages(List.of());
    }

    private void writeGuestInfoPages(List<Map<String, Object>> pages) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO tenant_config (tenant_id, key, value, updated_at)
                        VALUES (?, 'guest_info_pages', ?::jsonb, ?)
                        ON CONFLICT (tenant_id, key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at
                        """)
                .params(tenantFixtures.testTenantId(), objectMapper.writeValueAsString(pages), now())
                .update();
    }

    private void claim(String staffName, UUID taskId, String taskKey) {
        requireSuccess(
                apiClient.post("/tasks/" + taskId + "/claim", BddApiClient.Auth.staff(world.jwt(staffName)), null),
                staffName + " 認領 " + taskKey);
        Map<String, Object> task = taskRow(taskId);
        requireEquals("accepted", task.get("state"), taskKey + " 認領後的 tasks.state");
        requireEquals(world.staffId(staffName), String.valueOf(task.get("assignee_id")), taskKey + " 認領後的負責人");
        requireEquals("claimed", latestEventType(taskId), taskKey + " 認領後最新一筆事件");
    }

    private List<UUID> ensureCleaningSopItems(long tenantId) {
        List<UUID> items = activeCleaningSopItems(tenantId);
        if (!items.isEmpty()) {
            return items;
        }
        for (int order = 1; order <= DEFAULT_CLEANING_SOP_ITEMS; order++) {
            jdbcClient
                    .sql(
                            """
                            INSERT INTO sop_items (id, tenant_id, applies_to, item_key, label, sort_order, active, created_at)
                            VALUES (?, ?, 'cleaning', ?, ?, ?, true, ?)
                            """)
                    .params(UuidV7.generate(), tenantId, "cleaning-" + order, "清潔項目 " + order, order, now())
                    .update();
        }
        return activeCleaningSopItems(tenantId);
    }

    private List<UUID> activeCleaningSopItems(long tenantId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id FROM sop_items
                         WHERE tenant_id = ? AND applies_to = 'cleaning' AND active
                         ORDER BY sort_order, item_key
                        """)
                .params(tenantId)
                .query(UUID.class)
                .list();
    }

    private record Stay(String roomNo, UUID reservationId) {
    }

    private boolean inHouseNow(Stay stay) {
        return jdbcClient
                .sql(
                        """
                        SELECT count(*) FROM reservations_cache
                         WHERE id = ? AND superseded_at IS NULL AND checkin_at <= ? AND checkout_at > ?
                        """)
                .params(stay.reservationId(), now(), now())
                .query(Long.class)
                .single() > 0;
    }

    private void moveClockIntoStay(Stay stay) {
        Instant[] stayRange = jdbcClient
                .sql("SELECT checkin_at, checkout_at FROM reservations_cache WHERE id = ?")
                .params(stay.reservationId())
                .query((rs, rowNum) -> new Instant[] {
                    rs.getObject("checkin_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("checkout_at", OffsetDateTime.class).toInstant()
                })
                .single();
        Instant checkinAt = stayRange[0];
        Instant checkoutAt = stayRange[1];
        LocalTime timeOfDay = clock.instant().atZone(clock.getZone()).toLocalTime();
        ZonedDateTime candidate = ZonedDateTime.of(
                checkinAt.atZone(clock.getZone()).toLocalDate(), timeOfDay, clock.getZone());
        if (candidate.toInstant().isBefore(checkinAt)) {
            candidate = candidate.plusDays(1);
        }
        if (!candidate.toInstant().isBefore(checkoutAt)) {
            throw new IllegalStateException(stay.roomNo() + " 的住期內找不到和現在同一時刻的時間點");
        }
        clock.setInstant(candidate.toInstant());
    }

    private Instant relativeTime(String cell) {
        if (cell == null) {
            return clock.instant();
        }
        Matcher m = RELATIVE_TIME.matcher(cell.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "「時間」格式須為 `今天 HH:mm`、`昨天 HH:mm`、`前天 HH:mm` 或 `N 天前 HH:mm`，收到「" + cell + "」");
        }
        int daysAgo = switch (m.group(1)) {
            case "今天" -> 0;
            case "昨天" -> 1;
            case "前天" -> 2;
            default -> Integer.parseInt(m.group(2));
        };
        return ZonedDateTime.of(today().minusDays(daysAgo), hhmm(m.group(3)), clock.getZone()).toInstant();
    }

    private Instant todayAt(String hhmm) {
        return ZonedDateTime.of(today(), hhmm(hhmm), clock.getZone()).toInstant();
    }

    private static LocalTime hhmm(String s) {
        String trimmed = s.trim();
        return LocalTime.parse(trimmed.length() == 4 ? "0" + trimmed : trimmed);
    }

    private static final Map<String, Integer> RELATIVE_DAY = Map.of("昨天", -1, "今天", 0, "明天", 1, "後天", 2);

    private static boolean usesRelativeDay(DataTable table) {
        return table.asMaps(String.class, String.class).stream()
                .anyMatch(row -> RELATIVE_DAY.containsKey(Objects.toString(row.get("入住日"), "").trim())
                        || RELATIVE_DAY.containsKey(Objects.toString(row.get("退房日"), "").trim()));
    }

    private void moveClockLaterTodayIntoAllStays(List<Stay> stays) {
        if (stays.stream().allMatch(this::inHouseNow)) {
            return;
        }
        LocalDate today = today();
        Instant latestCheckin = stays.stream()
                .map(stay -> jdbcClient
                        .sql("SELECT checkin_at FROM reservations_cache WHERE id = ?")
                        .params(stay.reservationId())
                        .query(OffsetDateTime.class)
                        .single()
                        .toInstant())
                .max(Comparator.naturalOrder())
                .orElseThrow();
        if (!latestCheckin.isAfter(clock.instant())
                || !latestCheckin.atZone(clock.getZone()).toLocalDate().equals(today)) {
            return;
        }
        Instant before = clock.instant();
        clock.setInstant(latestCheckin);
        if (!stays.stream().allMatch(this::inHouseNow)) {
            clock.setInstant(before);
        }
    }

    private LocalDate monthDay(String cell, LocalDate fallback) {
        if (cell == null) {
            return fallback;
        }
        Integer offset = RELATIVE_DAY.get(cell.trim());
        if (offset != null) {
            return today().plusDays(offset);
        }
        Matcher m = MONTH_DAY.matcher(cell.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("日期格式須為 `M/D`，或昨天、今天、明天、後天，收到「" + cell + "」");
        }
        return LocalDate.of(today().getYear(), Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    private LocalDate today() {
        return clock.instant().atZone(clock.getZone()).toLocalDate();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private UUID resolveTaskId(String key) {
        if (world.hasTask(key)) {
            return UUID.fromString(world.taskId(key));
        }
        if (world.hasRepairTicket(key)) {
            return UUID.fromString(world.repairTicketTaskId(key));
        }
        throw new IllegalStateException("本案例沒有建立過「" + key + "」這張單");
    }

    private Map<String, Object> taskRow(UUID taskId) {
        return jdbcClient
                .sql("SELECT * FROM tasks WHERE tenant_id = ? AND id = ?")
                .params(tenantFixtures.testTenantId(), taskId)
                .query()
                .singleRow();
    }

    private String latestEventType(UUID taskId) {
        return jdbcClient
                .sql("SELECT type FROM task_events WHERE tenant_id = ? AND task_id = ? ORDER BY seq DESC LIMIT 1")
                .params(tenantFixtures.testTenantId(), taskId)
                .query(String.class)
                .single();
    }

    private long countEvents(UUID taskId, String type) {
        return jdbcClient
                .sql("SELECT count(*) FROM task_events WHERE tenant_id = ? AND task_id = ? AND type = ?")
                .params(tenantFixtures.testTenantId(), taskId, type)
                .query(Long.class)
                .single();
    }

    private UUID staffIdOrNull(String name) {
        String trimmed = blankToNull(name);
        return trimmed == null ? null : UUID.fromString(world.staffId(trimmed.trim()));
    }

    private static void requireSuccess(ScenarioWorld.ApiResponse response, String what) {
        if (response.status() < 200 || response.status() > 299) {
            throw new IllegalStateException(
                    "前置步驟「" + what + "」沒有成功：狀態碼 " + response.status() + "，body=" + response.body());
        }
    }

    private static void requireEquals(Object expected, Object actual, String what) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException("前置步驟沒有做到：" + what + " 預期 " + expected + "，實際 " + actual);
        }
    }

    private static boolean yes(String cell) {
        String value = blankToNull(cell);
        if (value == null || "否".equals(value.trim())) {
            return false;
        }
        if ("是".equals(value.trim())) {
            return true;
        }
        throw new IllegalArgumentException("「VIP」只能寫是或否，收到「" + cell + "」");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v.trim();
    }
}
