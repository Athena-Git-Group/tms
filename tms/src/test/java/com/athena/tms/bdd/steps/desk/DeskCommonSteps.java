package com.athena.tms.bdd.steps.desk;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.DeskApi;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.ReservationFixtures;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import io.cucumber.java.zh_tw.當;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

public class DeskCommonSteps {

    private static final String VIA_BUTTON = "button";

    private static final String ENGINEERING = "engineering";

    private final ScenarioWorld world;
    private final DeskApi deskApi;
    private final DbProbe dbProbe;
    private final JdbcClient jdbcClient;
    private final MutableClock clock;
    private final RoomFixtures roomFixtures;
    private final ReservationFixtures reservationFixtures;
    private final LastOperationGuard lastOperationGuard;
    private final TenantFixtures tenantFixtures;

    public DeskCommonSteps(
            ScenarioWorld world,
            DeskApi deskApi,
            DbProbe dbProbe,
            JdbcClient jdbcClient,
            MutableClock clock,
            RoomFixtures roomFixtures,
            ReservationFixtures reservationFixtures,
            LastOperationGuard lastOperationGuard,
            TenantFixtures tenantFixtures) {
        this.world = world;
        this.deskApi = deskApi;
        this.dbProbe = dbProbe;
        this.jdbcClient = jdbcClient;
        this.clock = clock;
        this.roomFixtures = roomFixtures;
        this.reservationFixtures = reservationFixtures;
        this.lastOperationGuard = lastOperationGuard;
        this.tenantFixtures = tenantFixtures;
    }

    @假設("待派單有以下住客打字的需求：")
    public void triageHasGuestTypedRequests(DataTable table) {
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String roomNo = requireCell(row, "房號");
            String text = requireCell(row, "住客原話");
            String guestName = optionalCell(row, "住客");
            String arrivedAt = optionalCell(row, "進來的時間");
            String lang = optionalCell(row, "原文語言");
            String translated = optionalCell(row, "翻譯");

            if (guestName != null) {
                createStayingReservation(roomNo, guestName);
            }

            ScenarioWorld.ApiResponse response = createAt(arrivedAt, roomNo, text, langCode(lang));
            UUID taskId = createdTaskId(roomNo, response);
            assertLandedInTriage(roomNo, taskId);
            if (translated != null) {
                writeTranslation(taskId, translated);
            }
            world.rememberTask(roomNo, taskId.toString());
        }
    }

    @假設("{string} 已把 {string} 派給 {string}")
    public void deskHasAssigned(String staffName, String roomNo, String deptTerm) {
        UUID taskId = taskIdOf(roomNo);
        String dept = Terms.dept(deptTerm);
        String ticketNoBefore = repairTicketNoOf(taskId);
        int lastNoBefore = repairCounterLastNo();
        ScenarioWorld.ApiResponse response = deskApi.assign(staffName, taskId, dept, VIA_BUTTON);
        if (response.status() < 200 || response.status() > 299) {
            throw new AssertionError("「" + staffName + "」把「" + roomNo + "」派給「" + deptTerm
                    + "」沒有成功，狀態碼=" + response.status() + "，body=" + response.body());
        }
        Map<String, Object> row = dbProbe.taskRow(taskId)
                .orElseThrow(() -> new AssertionError("派單後 tasks 查無「" + roomNo + "」"));
        if (!"pool".equals(row.get("state")) || !dept.equals(row.get("dept"))) {
            throw new AssertionError("「" + roomNo + "」派給「" + deptTerm + "」後應在該部門待認領，實際 state="
                    + row.get("state") + "、dept=" + row.get("dept"));
        }
        assertLatestTriagedNow(roomNo, taskId);
        if (ENGINEERING.equals(dept)) {
            String expectedTicketNo = ticketNoBefore != null ? ticketNoBefore : ticketNo(lastNoBefore + 1);
            assertBecameRepairTicket(roomNo, taskId, row, expectedTicketNo);
        }
    }

    @當("{string} 讀取櫃檯看板")
    public void staffReadsDeskBoard(String staffName) {
        lastOperationGuard.captureWithoutTask();
        deskApi.getBoard(staffName);
    }

    @當("{string} 把 {string} 派給 {string}")
    public void deskAssigns(String staffName, String roomNo, String deptTerm) {
        UUID taskId = taskIdOf(roomNo);
        lastOperationGuard.captureFor(taskId);
        deskApi.assign(staffName, taskId, Terms.dept(deptTerm), VIA_BUTTON);
    }

    @當("{string} 復原 {string} 的派單")
    public void deskUndoesAssign(String staffName, String roomNo) {
        UUID taskId = taskIdOf(roomNo);
        lastOperationGuard.captureFor(taskId);
        deskApi.undoAssign(staffName, taskId);
    }

    private void assertLatestTriagedNow(String roomNo, UUID taskId) {
        List<Map<String, Object>> events = dbProbe.eventsOf(taskId);
        Map<String, Object> latest = events.isEmpty() ? Map.of() : events.get(events.size() - 1);
        Instant at = instantOf(latest.get("created_at"));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!"triaged".equals(latest.get("type")) || !now.equals(at)) {
            throw new AssertionError("「" + roomNo + "」派單後最新一筆事件應是時間為 " + now + " 的 triaged，實際 type="
                    + latest.get("type") + "、created_at=" + at);
        }
    }

    private void assertBecameRepairTicket(String roomNo, UUID taskId, Map<String, Object> row, String expectedTicketNo) {
        if (!"repair".equals(row.get("type"))) {
            throw new AssertionError("「" + roomNo + "」派給工務後 tasks.type 應是 repair，實際 " + row.get("type"));
        }
        Map<String, Object> triaged = dbProbe
                .queryOne(
                        """
                        SELECT jsonb_exists(payload, 'former_type') AS has_former_type,
                               jsonb_exists(payload, 'former_urgent') AS has_former_urgent
                          FROM task_events
                         WHERE task_id = ? AND type = 'triaged'
                         ORDER BY seq DESC
                         LIMIT 1
                        """,
                        taskId)
                .orElseThrow(() -> new AssertionError("「" + roomNo + "」派給工務後沒有 triaged 事件"));
        if (!Boolean.TRUE.equals(triaged.get("has_former_type")) || !Boolean.TRUE.equals(triaged.get("has_former_urgent"))) {
            throw new AssertionError("「" + roomNo + "」派給工務的 triaged payload 應帶 former_type 與 former_urgent");
        }
        List<Map<String, Object>> tickets =
                dbProbe.queryMaps("SELECT ticket_no, is_urgent FROM repair_tickets WHERE task_id = ?", taskId);
        if (tickets.size() != 1) {
            throw new AssertionError("「" + roomNo + "」派給工務後 repair_tickets 應恰好一列，實際 " + tickets.size() + " 列");
        }
        Map<String, Object> ticket = tickets.get(0);
        if (!expectedTicketNo.equals(ticket.get("ticket_no"))) {
            throw new AssertionError("「" + roomNo + "」的工務單號應是本飯店下一號 " + expectedTicketNo + "，實際 "
                    + ticket.get("ticket_no"));
        }
        if (!Objects.equals(row.get("urgent"), ticket.get("is_urgent"))) {
            throw new AssertionError("「" + roomNo + "」的 tasks.urgent=" + row.get("urgent")
                    + " 與 repair_tickets.is_urgent=" + ticket.get("is_urgent") + " 不同");
        }
        world.rememberRepairTicket(expectedTicketNo, taskId.toString());
    }

    private String repairTicketNoOf(UUID taskId) {
        return dbProbe
                .queryOne("SELECT ticket_no FROM repair_tickets WHERE task_id = ?", taskId)
                .map(ticket -> String.valueOf(ticket.get("ticket_no")))
                .orElse(null);
    }

    private int repairCounterLastNo() {
        return (int) dbProbe.count(
                "SELECT COALESCE((SELECT last_no FROM repair_ticket_counters WHERE tenant_id = ?), 0)",
                tenantFixtures.testTenantId());
    }

    private static Instant instantOf(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return null;
    }

    private static String ticketNo(int number) {
        return String.format("W-%04d", number);
    }

    private ScenarioWorld.ApiResponse createAt(String arrivedAt, String roomNo, String text, String lang) {
        if (arrivedAt == null) {
            return deskApi.createGuestRequest(roomNo, text, lang);
        }
        Instant now = clock.instant();
        clock.setTodayAt(arrivedAt);
        try {
            return deskApi.createGuestRequest(roomNo, text, lang);
        } finally {
            clock.setInstant(now);
        }
    }

    private void createStayingReservation(String roomNo, String guestName) {
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        LocalDate today = ZonedDateTime.ofInstant(clock.instant(), clock.getZone()).toLocalDate();
        reservationFixtures.upsertReservation(roomNo, List.of(), today, LocalTime.MIDNIGHT, guestName);
    }

    private static UUID createdTaskId(String roomNo, ScenarioWorld.ApiResponse response) {
        JsonNode json = response.json();
        if (response.status() < 200 || response.status() > 299 || json == null || !json.hasNonNull("id")) {
            throw new IllegalStateException("「" + roomNo + "」的住客送單失敗，狀態碼=" + response.status()
                    + "，body=" + response.body());
        }
        return UUID.fromString(json.get("id").asString());
    }

    private void assertLandedInTriage(String roomNo, UUID taskId) {
        Map<String, Object> row = dbProbe.taskRow(taskId)
                .orElseThrow(() -> new IllegalStateException("住客送單後 tasks 查無「" + roomNo + "」"));
        if (!"triage".equals(row.get("state")) || !"frontdesk".equals(row.get("dept"))) {
            throw new IllegalStateException("「" + roomNo + "」的住客原話被分類規則接走，沒有落在待派單：state="
                    + row.get("state") + "、dept=" + row.get("dept") + "；請換一段不會命中規則的住客原話");
        }
    }

    private void writeTranslation(UUID taskId, String translated) {
        jdbcClient
                .sql("""
                        UPDATE tasks
                           SET note = jsonb_set(COALESCE(note, '{}')::jsonb, '{llm_translated}', to_jsonb(?::text))::text
                         WHERE id = ?
                        """)
                .params(translated, taskId)
                .update();
    }

    private UUID taskIdOf(String roomNo) {
        return UUID.fromString(world.taskId(roomNo));
    }

    private static String langCode(String langTerm) {
        if (langTerm == null) {
            return null;
        }
        return Terms.lang(langTerm);
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = optionalCell(row, column);
        if (value == null) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return value;
    }

    private static String optionalCell(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
