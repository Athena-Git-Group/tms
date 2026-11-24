package com.athena.tms.bdd.steps.desk;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.DeskContext;
import com.athena.tms.bdd.support.DeskCreateFaults;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.ReservationFixtures;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.common.UuidV7;
import com.athena.tms.service.GuestTenantConfigReader;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

public class DeskCreateRequestGivenSteps {

    private static final String PUBLIC_AREA_ROOM_NO = "公共區域";

    private final DeskApi deskApi;
    private final DeskContext deskContext;
    private final DeskCreateFaults deskCreateFaults;
    private final ScenarioWorld world;
    private final DbProbe dbProbe;
    private final JdbcClient jdbcClient;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final ReservationFixtures reservationFixtures;
    private final MutableClock clock;
    private final ObjectMapper objectMapper;
    private final GuestTenantConfigReader guestConfigReader;
    private final LastOperationGuard lastOperationGuard;

    public DeskCreateRequestGivenSteps(
            DeskApi deskApi,
            DeskContext deskContext,
            DeskCreateFaults deskCreateFaults,
            ScenarioWorld world,
            DbProbe dbProbe,
            JdbcClient jdbcClient,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            ReservationFixtures reservationFixtures,
            MutableClock clock,
            ObjectMapper objectMapper,
            GuestTenantConfigReader guestConfigReader,
            LastOperationGuard lastOperationGuard) {
        this.deskApi = deskApi;
        this.deskContext = deskContext;
        this.deskCreateFaults = deskCreateFaults;
        this.world = world;
        this.dbProbe = dbProbe;
        this.jdbcClient = jdbcClient;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.reservationFixtures = reservationFixtures;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.guestConfigReader = guestConfigReader;
        this.lastOperationGuard = lastOperationGuard;
    }

    @假設("{string} 剛才代開 {string} 的以下單時後端出錯，一張都沒開出：")
    public void createdRequestsButBackendFailed(String staffName, String roomNo, DataTable table) {
        long tenantId = tenantFixtures.testTenantId();
        DeskCreateRequestRows.Request request = DeskCreateRequestRows.parse(table);
        String idempotencyKey = UUID.randomUUID().toString();
        LastOperationGuard.CreateRequestSnapshot before = lastOperationGuard.createRequestCounts(tenantId);
        deskCreateFaults.failEveryTaskOf(tenantId);
        ScenarioWorld.ApiResponse response;
        try {
            response = deskApi.createRequests(staffName, roomNo, request.items(), request.other(), idempotencyKey);
        } finally {
            deskCreateFaults.removeAll();
        }
        if (response.status() < 500 || response.status() > 599) {
            throw new IllegalStateException("「" + staffName + "」代開「" + roomNo + "」預期後端出錯回 5xx，實際狀態碼="
                    + response.status() + "，body=" + response.body());
        }
        long processed = dbProbe.count(
                "SELECT count(*) FROM processed_ops WHERE tenant_id = ? AND op_id = ?", tenantId, idempotencyKey);
        if (processed != 0) {
            throw new IllegalStateException("後端出錯的那次代開不該在 processed_ops 留下這把鍵：" + idempotencyKey);
        }
        LastOperationGuard.CreateRequestSnapshot after = lastOperationGuard.createRequestCounts(tenantId);
        if (!before.equals(after)) {
            throw new IllegalStateException("後端出錯的那次代開不該留下任何新單、事件、工務單、outbox 或推進流水號："
                    + "之前 " + before + "，之後 " + after);
        }
    }

    @假設("開 {string} 這張單時後端會出錯")
    public void taskTitledWillFail(String title) {
        deskCreateFaults.failTaskTitled(title);
    }

    @假設("{string} 的住客已勾選服務項目 {string} 送出")
    public void guestAlreadySubmittedMenuItem(String roomNo, String label) {
        String itemId = guestConfigReader.requestMenu(tenantFixtures.testTenantId()).stream()
                .filter(item -> label.equals(item.label()))
                .map(GuestTenantConfigReader.MenuItem::key)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("住客頁的勾選清單沒有「" + label + "」"));
        ScenarioWorld.ApiResponse response = deskApi.createGuestMenuRequest(roomNo, itemId);
        rememberGuestTask(roomNo, response, "勾選「" + label + "」");
    }

    @假設("{string} 的住客已打字送出 {string}")
    public void guestAlreadySubmittedText(String roomNo, String text) {
        ScenarioWorld.ApiResponse response = deskApi.createGuestRequest(roomNo, text, null);
        rememberGuestTask(roomNo, response, "打字「" + text + "」");
    }

    @假設("飯店的分類規則把含 {string} 的文字分給 {string}，對應品項 {string}")
    public void classificationRuleWithMenuKey(String keyword, String deptChinese, String menuKey) {
        String dept = Terms.dept(deptChinese);
        if (!"housekeeping".equals(dept) && !"engineering".equals(dept)) {
            throw new IllegalArgumentException("分類規則只能分給房務或工務，收到「" + deptChinese + "」");
        }
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("rule_id", "fx-rule-" + UUID.randomUUID());
        rule.put("keywords", List.of(keyword));
        rule.put("dept", dept);
        rule.put("menu_key", menuKey);
        jdbcClient
                .sql(
                        """
                        INSERT INTO tenant_config (tenant_id, key, value, updated_at)
                        VALUES (?, 'classification_rules', ?::jsonb, ?)
                        ON CONFLICT (tenant_id, key)
                        DO UPDATE SET value = tenant_config.value || EXCLUDED.value, updated_at = EXCLUDED.updated_at
                        """)
                .params(tenantFixtures.testTenantId(), objectMapper.writeValueAsString(List.of(rule)), OffsetDateTime.now(clock))
                .update();
    }

    @假設("飯店有代表公共區域的佔位房")
    public void hotelHasPublicAreaPlaceholderRoom() {
        long tenantId = tenantFixtures.testTenantId();
        jdbcClient
                .sql(
                        """
                        INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state, occupancy, updated_at)
                        VALUES (?, ?, ?, 0, 'public_area', 'cleaned', 'vacant', ?)
                        ON CONFLICT DO NOTHING
                        """)
                .params(UuidV7.generate(), tenantId, PUBLIC_AREA_ROOM_NO, OffsetDateTime.now(clock))
                .update();
        long rows = dbProbe.count(
                "SELECT count(*) FROM rooms WHERE tenant_id = ? AND room_no = ?", tenantId, PUBLIC_AREA_ROOM_NO);
        if (rows != 1) {
            throw new IllegalStateException("房間主檔應有一列公共區域佔位房，實際 " + rows + " 列");
        }
    }

    @假設("以下房間的訂房現在不算在住：")
    public void reservationsNotInHouse(DataTable table) {
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String roomNo = requireCell(row, "房號");
            String guestName = requireCell(row, "住客");
            String situation = requireCell(row, "情形");
            roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
            switch (situation) {
                case "已退房" -> reservationFixtures.upsertReservation(
                        roomNo, List.of(), today.minusDays(3), LocalTime.of(15, 0), today.minusDays(1), guestName);
                case "還沒入住" -> reservationFixtures.upsertReservation(
                        roomNo, List.of(), today.plusDays(1), LocalTime.of(15, 0), today.plusDays(3), guestName);
                case "已被取代" -> {
                    UUID reservationId = reservationFixtures.upsertReservation(
                            roomNo, List.of(), today.minusDays(1), LocalTime.of(15, 0), today.plusDays(1), guestName);
                    jdbcClient
                            .sql("UPDATE reservations_cache SET superseded_at = ? WHERE id = ?")
                            .params(OffsetDateTime.now(clock), reservationId)
                            .update();
                }
                default -> throw new IllegalArgumentException("「情形」只能是已退房、還沒入住、已被取代，收到「" + situation + "」");
            }
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            long inHouse = dbProbe.count(
                    """
                    SELECT count(*) FROM reservations_cache rc
                      JOIN rooms r ON r.id = rc.room_id AND r.tenant_id = rc.tenant_id
                     WHERE rc.tenant_id = ? AND r.room_no = ? AND rc.superseded_at IS NULL
                       AND rc.checkin_at <= ? AND rc.checkout_at > ?
                    """,
                    tenantFixtures.testTenantId(), row.get("房號"), now, now);
            if (inHouse != 0) {
                throw new IllegalStateException("「" + row.get("房號") + "」現在仍有算在住的訂房");
            }
        }
    }

    private void rememberGuestTask(String roomNo, ScenarioWorld.ApiResponse response, String what) {
        if (response.status() < 200 || response.status() > 299 || response.json() == null
                || !response.json().hasNonNull("id")) {
            throw new IllegalStateException("「" + roomNo + "」的住客" + what + "送出失敗，狀態碼=" + response.status()
                    + "，body=" + response.body());
        }
        String taskId = response.json().get("id").asString();
        world.rememberTask(roomNo, taskId);
        dbProbe.taskRow(UUID.fromString(taskId))
                .map(row -> String.valueOf(row.get("title")))
                .ifPresent(title -> world.rememberTask(title, taskId));
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("表格欄位「" + column + "」必填：" + row);
        }
        return value.trim();
    }
}
