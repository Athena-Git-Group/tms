package com.athena.tms.bdd.steps.kb;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.ReservationFixtures;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.common.UuidV7;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;

public class KbGivenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final RoomFixtures roomFixtures;
    private final ReservationFixtures reservationFixtures;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;
    private final JdbcClient jdbcClient;

    public KbGivenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            RoomFixtures roomFixtures,
            ReservationFixtures reservationFixtures,
            TenantFixtures tenantFixtures,
            MutableClock clock,
            JdbcClient jdbcClient) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.roomFixtures = roomFixtures;
        this.reservationFixtures = reservationFixtures;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
        this.jdbcClient = jdbcClient;
    }

    @假設("{string} 的房間記錄有以下項目：")
    public void roomMemoryHasItems(String roomNo, DataTable table) {
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String kind = roomMemoryKind(requireCell(row, "類型"));
            String title = requireCell(row, "標題");
            String content = requireCell(row, "內容");
            LocalDate occurredOn = parseMonthDay(requireCell(row, "日期"));
            roomFixtures.addRoomMemory(roomNo, kind, title, content, occurredOn);
        }
    }

    @假設("{string} 沒有任何房間記錄")
    public void roomHasNoMemory(String roomNo) {
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        long tenantId = tenantFixtures.testTenantId();
        long count = dbProbe.count(
                """
                SELECT count(*) FROM room_memory rm
                  JOIN rooms r ON r.id = rm.room_id AND r.tenant_id = rm.tenant_id
                 WHERE r.tenant_id = ? AND r.room_no = ?
                """,
                tenantId, roomNo);
        if (count > 0) {
            throw new IllegalStateException("「" + roomNo + "」預期沒有任何房間記錄，實際已有 " + count + " 筆");
        }
    }

    @假設("{string} 在 {string} 剛跳過 SOP 項 {string}")
    public void staffJustSkippedSopItem(String staffName, String taskKey, String itemLabel) {
        UUID taskId = UUID.fromString(world.taskId(taskKey));
        UUID sopItemId = createSopItem(itemLabel);

        apiClient.post(
                "/tasks/" + taskId + "/sop-check",
                BddApiClient.Auth.staff(world.jwt(staffName)),
                Map.of("sopItemId", sopItemId, "result", "skipped"));
    }

    @假設("{string} 是今天的 VIP 房")
    public void isVipToday(String roomNo) {
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        reservationFixtures.upsertReservation(roomNo, List.of("vip"), today, null);
    }

    @假設("SOP {string} 的內容為 {string}")
    public void sopHasContent(String title, String content) {
        String label = title + "：" + content;
        createSopItem(label);
    }

    @假設("{string} 目前的住客姓名為 {string}")
    public void currentGuestNameIs(String roomNo, String guestName) {
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        reservationFixtures.upsertReservation(roomNo, List.of(), today, guestName);
    }

    @假設("{string} 已在 {string} 問過 {string}")
    public void staffAlreadyAsked(String staffName, String taskKey, String question) {
        UUID taskId = UUID.fromString(world.taskId(taskKey));
        askAndRememberAnswerId(staffName, taskId, question);
    }

    @假設("{string} 已把 {string} 轉問 {string}")
    public void staffAlreadyEscalated(String staffName, String question, String managerName) {
        UUID taskId = resolveTaskIdFromQuestion(question);
        askAndRememberAnswerId(staffName, taskId, question);

        String answerId = world.lastAnswerId();
        if (answerId == null) {
            throw new IllegalStateException("roomMemoryAsk 未回傳 answerId，無法接著轉問主管：" + question);
        }
        ScenarioWorld.ApiResponse resp = apiClient.post(
                "/room-memories/answers/" + answerId + "/escalate",
                BddApiClient.Auth.staff(world.jwt(staffName)),
                null);
        rememberEscalationId(resp);
    }

    private void askAndRememberAnswerId(String staffName, UUID taskId, String question) {
        Map<String, Object> body = Map.of("taskId", taskId.toString(), "question", question);
        ScenarioWorld.ApiResponse resp =
                apiClient.post("/room-memories/ask", BddApiClient.Auth.staff(world.jwt(staffName)), body);
        rememberAnswerId(resp);
    }

    private void rememberAnswerId(ScenarioWorld.ApiResponse resp) {
        if (resp.json() != null && resp.json().has("answerId") && !resp.json().get("answerId").isNull()) {
            world.setLastAnswerId(resp.json().get("answerId").asText());
        }
    }

    private void rememberEscalationId(ScenarioWorld.ApiResponse resp) {
        if (resp.json() != null && resp.json().has("id") && !resp.json().get("id").isNull()) {
            world.setLastEscalationId(resp.json().get("id").asText());
        }
    }

    private UUID resolveTaskIdFromQuestion(String question) {
        Matcher m = Pattern.compile("^(\\S+?)(?=\\s)").matcher(question);
        if (m.find() && world.hasTask(m.group(1))) {
            return UUID.fromString(world.taskId(m.group(1)));
        }
        throw new IllegalArgumentException(
                "問題「" + question + "」找不到對應已建立的任務（這句 dsl.md 沒有顯式「任務」參數，"
                        + "以問題開頭的房號推定任務，但沒有命中）");
    }

    private UUID createSopItem(String label) {
        long tenantId = tenantFixtures.testTenantId();
        UUID id = UuidV7.generate();
        jdbcClient
                .sql(
                        """
                        INSERT INTO sop_items (id, tenant_id, applies_to, item_key, label, sort_order, active, created_at)
                        VALUES (?, ?, 'cleaning', ?, ?, 1, true, ?)
                        """)
                .params(id, tenantId, "kb-sop-" + UuidV7.generate(), label, OffsetDateTime.now())
                .update();
        return id;
    }

    private LocalDate parseMonthDay(String mmdd) {
        String[] parts = mmdd.split("/");
        int month = Integer.parseInt(parts[0]);
        int day = Integer.parseInt(parts[1]);
        int year = clock.instant().atZone(clock.getZone()).getYear();
        return LocalDate.of(year, month, day);
    }

    private static String roomMemoryKind(String chinese) {
        return switch (chinese) {
            case "工務單" -> "repair";
            case "常客需求" -> "preference";
            default -> throw new IllegalArgumentException(
                    "查一下「房間記錄」類型只支援 工務單｜常客需求，收到: " + chinese);
        };
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }
}
