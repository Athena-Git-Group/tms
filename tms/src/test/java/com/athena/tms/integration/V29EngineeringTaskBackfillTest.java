package com.athena.tms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
class V29EngineeringTaskBackfillTest {

    private static final String VERSION_BEFORE_BACKFILL = "28";
    private static final AtomicInteger DATABASE_SEQ = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private String jdbcUrl;
    private JdbcTemplate jdbc;

    // 每個案例各開一個資料庫，停在 V28 上鋪資料，再看 V29 把它變成什麼樣子。
    @BeforeEach
    void freshDatabaseAtV28() {
        String database = "v29_case_" + DATABASE_SEQ.incrementAndGet();
        new JdbcTemplate(dataSource(POSTGRES.getJdbcUrl())).execute("CREATE DATABASE " + database);
        jdbcUrl = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
        jdbc = new JdbcTemplate(dataSource(jdbcUrl));
        flyway(VERSION_BEFORE_BACKFILL).migrate();
    }

    @Test
    void migratesFromV28ToLatestWithSeededRows() {
        long hotel = insertHotel("v29-flow");
        insertStaff(hotel, "fd-flow", "frontdesk", "frontdesk");
        UUID room = insertRoom(hotel, "305");
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-11-20T09:00:00+08:00");
        UUID task = insertTask(hotel, new TaskSeed("other", "guest", "frontdesk", "triage", room, "房間有怪聲", "T-0001", createdAt));
        insertEvent(hotel, task, 1, "created", "guest", null,
                "{\"task_no\":\"T-0001\",\"type\":\"other\",\"source\":\"guest\",\"dept\":\"frontdesk\",\"state\":\"triage\"}",
                createdAt);
        setTaskCounter(hotel, 1);
        setRepairTicketCounter(hotel, 0);

        migrateToLatest();

        assertThat(Integer.parseInt(currentVersion())).isGreaterThanOrEqualTo(Integer.parseInt(VERSION_BEFORE_BACKFILL));
        assertThat(projection(hotel, task)).isEqualTo(replay(hotel, task));
    }

    @Test
    void deskForwardedTasksGetARepairTicketContinuingTheHotelNumbers() {
        Hotel h = hotel("v29-desk");
        OffsetDateTime poolTriagedAt = at("10:05");
        UUID pooled = deskForwarded(h, "305", "T-0001", at("10:00"), poolTriagedAt);
        UUID accepted = deskForwarded(h, "306", "T-0002", at("10:01"), at("10:07"));
        insertEvent(h.id, accepted, 3, "claimed", "engineering", h.engineer, null, at("10:09"));
        jdbc.update("UPDATE tasks SET state = 'accepted', assignee_id = ? WHERE id = ?", h.engineer, accepted);
        setTaskCounter(h.id, 2);
        setRepairTicketCounter(h.id, 7);

        migrateToLatest();

        assertThat(ticket(pooled)).containsEntry("ticket_no", "W-0008").containsEntry("category", "other")
                .containsEntry("reporter_id", h.frontdesk).containsEntry("is_urgent", false)
                .containsEntry("created_at", Timestamp.from(poolTriagedAt.toInstant()));
        assertThat(ticket(accepted)).containsEntry("ticket_no", "W-0009").containsEntry("reporter_id", h.frontdesk);
        assertThat(repairTicketCounter(h.id)).isEqualTo(9);
        assertThat(eventCount(pooled)).isEqualTo(2);
        assertThat(eventCount(accepted)).isEqualTo(3);
        for (UUID task : List.of(pooled, accepted)) {
            assertThat(projection(h.id, task).type()).isEqualTo("repair");
            assertThat(projection(h.id, task)).isEqualTo(replay(h.id, task));
        }
        assertThat(engineeringTasksWithoutTicket()).isZero();
    }

    @Test
    void guestTasksThatWentStraightToEngineeringGoBackToTheDeskWithATaskNumber() {
        Hotel h = hotel("v29-guest");
        insertTask(h.id, new TaskSeed("other", "desk", "housekeeping", "done", h.room("301"), "毛巾", "T-0003", at("08:00")));
        UUID aircon = guestDirect(h, "305", null, at("10:00"));
        UUID earlierWithNo = guestDirect(h, "306", "T-0002", at("09:00"));
        setTaskCounter(h.id, 3);

        migrateToLatest();

        for (UUID task : List.of(aircon, earlierWithNo)) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT state, dept, type, pool_source, reminder_stage, priority_notify, event_seq FROM tasks WHERE id = ?", task);
            assertThat(row).containsEntry("state", "triage").containsEntry("dept", "frontdesk").containsEntry("type", "other")
                    .containsEntry("pool_source", null).containsEntry("reminder_stage", null)
                    .containsEntry("priority_notify", false).containsEntry("event_seq", 2);
            assertThat(projection(h.id, task)).isEqualTo(replay(h.id, task));
            assertThat(ticketCount(task)).isZero();
        }
        assertThat(projection(h.id, aircon).taskNo()).isEqualTo("T-0004");
        assertThat(projection(h.id, earlierWithNo).taskNo()).isEqualTo("T-0002");
        assertThat(taskCounter(h.id)).isEqualTo(4);

        Map<String, Object> undone = jdbc.queryForMap(
                "SELECT seq, actor_role, actor_id, payload::text AS payload FROM task_events WHERE task_id = ? AND type = 'triage_undone'",
                aircon);
        assertThat(undone).containsEntry("seq", 2).containsEntry("actor_role", "system").containsEntry("actor_id", null);
        JsonNode payload = JSON.readTree((String) undone.get("payload"));
        assertThat(payload.get("dept").asText()).isEqualTo("engineering");
        assertThat(payload.get("type").asText()).isEqualTo("other");
        assertThat(payload.get("task_no").asText()).isEqualTo("T-0004");
        assertThat(payload.get("reason").asText()).isEqualTo("guest_not_direct_backfill");

        Map<String, Object> outbox = jdbc.queryForMap(
                "SELECT aggregate_key, idempotency_key, status, payload::text AS payload FROM outbox WHERE kind = 'task_event' AND aggregate_key = ?",
                "task:" + aircon);
        assertThat(outbox).containsEntry("idempotency_key", "task:" + aircon + ":seq:2").containsEntry("status", "pending");
        JsonNode envelope = JSON.readTree((String) outbox.get("payload"));
        assertThat(envelope.get("tenant_id").asLong()).isEqualTo(h.id);
        assertThat(envelope.get("task_id").asText()).isEqualTo(aircon.toString());
        assertThat(envelope.get("seq").asInt()).isEqualTo(2);
        assertThat(envelope.get("type").asText()).isEqualTo("triage_undone");
        assertThat(envelope.get("actor_role").asText()).isEqualTo("system");
        assertThat(envelope.has("actor_id")).isFalse();
        assertThat(envelope.get("payload").get("task_no").asText()).isEqualTo("T-0004");
        assertThat(OffsetDateTime.parse(envelope.get("occurred_at").asText())).isNotNull();
        assertThat(engineeringTasksWithoutTicket()).isZero();
    }

    @Test
    void hotelsWithoutCountersGetBothCountersPushedToTheLastNumberUsed() {
        Hotel h = hotel("v29-nocounter");
        UUID desk = deskForwarded(h, "305", "T-0001", at("10:00"), at("10:05"));
        UUID guest = guestDirect(h, "306", null, at("10:10"));
        jdbc.update("DELETE FROM task_counters WHERE tenant_id = ?", h.id);

        migrateToLatest();

        assertThat(ticket(desk)).containsEntry("ticket_no", "W-0001");
        assertThat(projection(h.id, guest).taskNo()).isEqualTo("T-0002");
        assertThat(repairTicketCounter(h.id)).isEqualTo(1);
        assertThat(taskCounter(h.id)).isEqualTo(2);
    }

    @Test
    void eachHotelContinuesItsOwnNumbers() {
        Hotel first = hotel("v29-first");
        Hotel second = hotel("v29-second");
        UUID a = deskForwarded(first, "305", "T-0001", at("10:00"), at("10:05"));
        UUID b = deskForwarded(second, "305", "T-0001", at("10:00"), at("10:05"));
        UUID c = guestDirect(first, "306", null, at("10:10"));
        UUID d = guestDirect(second, "306", null, at("10:10"));
        setTaskCounter(first.id, 1);
        setTaskCounter(second.id, 40);
        setRepairTicketCounter(first.id, 3);
        setRepairTicketCounter(second.id, 120);

        migrateToLatest();

        assertThat(ticket(a)).containsEntry("ticket_no", "W-0004");
        assertThat(ticket(b)).containsEntry("ticket_no", "W-0121");
        assertThat(projection(first.id, c).taskNo()).isEqualTo("T-0002");
        assertThat(projection(second.id, d).taskNo()).isEqualTo("T-0041");
    }

    @Test
    void endedTasksAreLeftAlone() {
        Hotel h = hotel("v29-ended");
        UUID done = deskForwarded(h, "305", "T-0001", at("10:00"), at("10:05"));
        jdbc.update("UPDATE tasks SET state = 'done' WHERE id = ?", done);
        UUID cancelled = guestDirect(h, "306", null, at("10:10"));
        jdbc.update("UPDATE tasks SET state = 'cancelled' WHERE id = ?", cancelled);
        Map<String, Object> doneBefore = taskRow(done);
        Map<String, Object> cancelledBefore = taskRow(cancelled);

        migrateToLatest();

        assertThat(taskRow(done)).isEqualTo(doneBefore);
        assertThat(taskRow(cancelled)).isEqualTo(cancelledBefore);
        assertThat(ticketCount(done)).isZero();
        assertThat(eventCount(cancelled)).isEqualTo(1);
    }

    @Test
    void staffTaskInEngineeringWithoutDispatchStopsTheMigration() {
        Hotel h = hotel("v29-staff");
        UUID handmade = insertTask(h.id, new TaskSeed("repair", "staff", "engineering", "pool", h.room("305"), "手寫", null, at("10:00")));
        insertEvent(h.id, handmade, 1, "created", "engineering", h.engineer, "{\"dept\":\"engineering\"}", at("10:00"));
        assertMigrationStopsWithNothingChanged(h, handmade);
    }

    @Test
    void dispatchWithoutAnOperatorStopsTheMigration() {
        Hotel h = hotel("v29-noactor");
        UUID task = deskForwarded(h, "305", "T-0001", at("10:00"), at("10:05"));
        jdbc.update("UPDATE task_events SET actor_id = NULL WHERE task_id = ? AND type = 'triaged'", task);
        assertMigrationStopsWithNothingChanged(h, task);
    }

    @Test
    void guestTaskAlreadyClaimedFromEngineeringStopsTheMigration() {
        Hotel h = hotel("v29-guestclaimed");
        UUID task = guestDirect(h, "305", null, at("10:00"));
        jdbc.update("UPDATE tasks SET state = 'accepted', assignee_id = ? WHERE id = ?", h.engineer, task);
        assertMigrationStopsWithNothingChanged(h, task);
    }

    private void assertMigrationStopsWithNothingChanged(Hotel h, UUID unexpected) {
        UUID recognised = deskForwarded(h, "401", "T-0009", at("09:00"), at("09:05"));
        Map<String, Object> unexpectedBefore = taskRow(unexpected);
        Map<String, Object> recognisedBefore = taskRow(recognised);

        String message = migrateToLatestExpectingFailure();

        assertThat(message).contains("V29").contains("1 張").contains(unexpected.toString());
        assertThat(currentVersion()).isEqualTo(VERSION_BEFORE_BACKFILL);
        assertThat(taskRow(unexpected)).isEqualTo(unexpectedBefore);
        assertThat(taskRow(recognised)).isEqualTo(recognisedBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM repair_tickets", Integer.class)).isZero();
    }

    private UUID deskForwarded(Hotel h, String roomNo, String taskNo, OffsetDateTime createdAt, OffsetDateTime triagedAt) {
        UUID task = insertTask(h.id, new TaskSeed("other", "guest", "engineering", "pool", h.room(roomNo), "房間有怪聲", taskNo, createdAt));
        insertEvent(h.id, task, 1, "created", "guest", null,
                "{\"task_no\":\"" + taskNo + "\",\"type\":\"other\",\"source\":\"guest\",\"dept\":\"frontdesk\",\"state\":\"triage\"}",
                createdAt);
        insertEvent(h.id, task, 2, "triaged", "frontdesk", h.frontdesk, "{\"dept\":\"engineering\"}", triagedAt);
        return task;
    }

    private UUID guestDirect(Hotel h, String roomNo, String taskNo, OffsetDateTime createdAt) {
        UUID task = insertTask(h.id, new TaskSeed("repair", "guest", "engineering", "pool", h.room(roomNo), "冷氣不冷", taskNo, createdAt));
        String taskNoJson = taskNo == null ? "null" : "\"" + taskNo + "\"";
        insertEvent(h.id, task, 1, "created", "guest", null,
                "{\"task_no\":" + taskNoJson + ",\"type\":\"repair\",\"source\":\"guest\",\"dept\":\"engineering\",\"state\":\"pool\"}",
                createdAt);
        jdbc.update("UPDATE tasks SET pool_source = 'guest_scan', priority_notify = true, reminder_stage = 'staff_reminded' WHERE id = ?", task);
        return task;
    }

    private Hotel hotel(String code) {
        long id = insertHotel(code);
        return new Hotel(id, insertStaff(id, code + "-fd", "frontdesk", "frontdesk"),
                insertStaff(id, code + "-mt", "engineering", "engineering"));
    }

    private final class Hotel {
        final long id;
        final UUID frontdesk;
        final UUID engineer;
        private final Map<String, UUID> rooms = new java.util.HashMap<>();

        Hotel(long id, UUID frontdesk, UUID engineer) {
            this.id = id;
            this.frontdesk = frontdesk;
            this.engineer = engineer;
        }

        UUID room(String roomNo) {
            return rooms.computeIfAbsent(roomNo, no -> insertRoom(id, no));
        }
    }

    private static OffsetDateTime at(String hhmm) {
        return OffsetDateTime.parse("2026-11-20T" + hhmm + ":00+08:00");
    }

    private Map<String, Object> ticket(UUID taskId) {
        return jdbc.queryForMap(
                "SELECT ticket_no, category, reporter_id, is_urgent, created_at FROM repair_tickets WHERE task_id = ?", taskId);
    }

    private int ticketCount(UUID taskId) {
        return jdbc.queryForObject("SELECT count(*) FROM repair_tickets WHERE task_id = ?", Integer.class, taskId);
    }

    private int eventCount(UUID taskId) {
        return jdbc.queryForObject("SELECT count(*) FROM task_events WHERE task_id = ?", Integer.class, taskId);
    }

    private Map<String, Object> taskRow(UUID taskId) {
        return jdbc.queryForMap("SELECT * FROM tasks WHERE id = ?", taskId);
    }

    private int taskCounter(long tenantId) {
        return jdbc.queryForObject("SELECT last_no FROM task_counters WHERE tenant_id = ?", Integer.class, tenantId);
    }

    private int repairTicketCounter(long tenantId) {
        return jdbc.queryForObject("SELECT last_no FROM repair_ticket_counters WHERE tenant_id = ?", Integer.class, tenantId);
    }

    private int engineeringTasksWithoutTicket() {
        return jdbc.queryForObject(
                """
                SELECT count(*) FROM tasks t
                 WHERE t.dept = 'engineering' AND t.state NOT IN ('done', 'inspected', 'closed', 'cancelled')
                   AND NOT EXISTS (SELECT 1 FROM repair_tickets rt WHERE rt.tenant_id = t.tenant_id AND rt.task_id = t.id)
                """,
                Integer.class);
    }

    record TaskSeed(String type, String source, String dept, String state, UUID roomId, String title, String taskNo,
            OffsetDateTime createdAt) {
    }

    // dbml tasks Note 重放規則點名的欄位，加上 urgent。
    record TaskProjection(String state, String dept, String type, String reminderStage, String poolSource,
            String taskNo, boolean urgent) {
    }

    long insertHotel(String code) {
        return jdbc.queryForObject(
                "INSERT INTO tenants (name, code, created_at) VALUES (?, ?, now()) RETURNING id",
                Long.class, code, code);
    }

    UUID insertStaff(long tenantId, String loginId, String role, String dept) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO staff_accounts (id, tenant_id, name, login_id, password_hash, role, dept, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'not-a-real-hash', ?, ?, now(), now())
                """,
                id, tenantId, loginId, loginId, role, dept);
        return id;
    }

    UUID insertRoom(long tenantId, String roomNo) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state, occupancy, updated_at)
                VALUES (?, ?, ?, ?, 'standard_double', 'cleaned', 'occupied', now())
                """,
                id, tenantId, roomNo, Integer.parseInt(roomNo.substring(0, 1)));
        return id;
    }

    UUID insertTask(long tenantId, TaskSeed seed) {
        UUID id = UUID.randomUUID();
        Timestamp createdAt = Timestamp.from(seed.createdAt().toInstant());
        jdbc.update(
                """
                INSERT INTO tasks (id, tenant_id, type, source, room_id, dept, state, title, task_no,
                                   event_seq, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """,
                id, tenantId, seed.type(), seed.source(), seed.roomId(), seed.dept(), seed.state(), seed.title(),
                seed.taskNo(), createdAt, createdAt);
        return id;
    }

    void insertEvent(long tenantId, UUID taskId, int seq, String type, String actorRole, UUID actorId,
            String payloadJson, OffsetDateTime at) {
        jdbc.update(
                """
                INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                tenantId, taskId, seq, type, actorRole, actorId, payloadJson, Timestamp.from(at.toInstant()));
        jdbc.update("UPDATE tasks SET event_seq = GREATEST(event_seq, ?) WHERE tenant_id = ? AND id = ?",
                seq, tenantId, taskId);
    }

    void setTaskCounter(long tenantId, int lastNo) {
        jdbc.update(
                """
                INSERT INTO task_counters (tenant_id, last_no) VALUES (?, ?)
                ON CONFLICT (tenant_id) DO UPDATE SET last_no = EXCLUDED.last_no
                """,
                tenantId, lastNo);
    }

    void setRepairTicketCounter(long tenantId, int lastNo) {
        jdbc.update(
                """
                INSERT INTO repair_ticket_counters (tenant_id, last_no) VALUES (?, ?)
                ON CONFLICT (tenant_id) DO UPDATE SET last_no = EXCLUDED.last_no
                """,
                tenantId, lastNo);
    }

    void migrateToLatest() {
        flyway(null).migrate();
    }

    // 「讓遷移停下」的案例拿這段訊息斷言：V29 的 RAISE EXCEPTION 文字包在 Flyway 例外的 cause 裡。
    String migrateToLatestExpectingFailure() {
        FlywayException failure = catchThrowableOfType(FlywayException.class, this::migrateToLatest);
        assertThat(failure).as("遷移應該以例外停下").isNotNull();
        StringBuilder message = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            message.append(t.getMessage()).append('\n');
        }
        return message.toString();
    }

    String currentVersion() {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class);
    }

    TaskProjection projection(long tenantId, UUID taskId) {
        return jdbc.queryForObject(
                """
                SELECT state, dept, type, reminder_stage, pool_source, task_no, urgent
                  FROM tasks WHERE tenant_id = ? AND id = ?
                """,
                (rs, i) -> new TaskProjection(rs.getString("state"), rs.getString("dept"), rs.getString("type"),
                        rs.getString("reminder_stage"), rs.getString("pool_source"), rs.getString("task_no"),
                        rs.getBoolean("urgent")),
                tenantId, taskId);
    }

    // 只認 created、triaged、claimed、triage_undone 四種事件，照 dbml tasks Note 的重放規則把事件疊成任務列。
    // created 快照不帶 urgent，所以 urgent 從 false 起算，只由 triaged 的 former_urgent 在復原時還原。
    TaskProjection replay(long tenantId, UUID taskId) {
        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT type, payload::text AS payload FROM task_events WHERE tenant_id = ? AND task_id = ? ORDER BY seq",
                tenantId, taskId);
        String state = null;
        String dept = null;
        String type = null;
        String reminderStage = null;
        String poolSource = null;
        String taskNo = null;
        boolean urgent = false;
        List<JsonNode> triagedStack = new ArrayList<>();
        for (Map<String, Object> event : events) {
            JsonNode payload = event.get("payload") == null ? JSON.createObjectNode() : JSON.readTree((String) event.get("payload"));
            switch ((String) event.get("type")) {
                case "created" -> {
                    state = text(payload, "state", state);
                    dept = text(payload, "dept", dept);
                    type = text(payload, "type", type);
                    taskNo = text(payload, "task_no", taskNo);
                }
                case "triaged" -> {
                    state = "pool";
                    dept = text(payload, "dept", dept);
                    if ("engineering".equals(dept)) {
                        type = "repair";
                    }
                    triagedStack.add(payload);
                }
                case "claimed" -> state = "accepted";
                case "triage_undone" -> {
                    state = "triage";
                    dept = "frontdesk";
                    reminderStage = null;
                    poolSource = null;
                    if (!triagedStack.isEmpty()) {
                        JsonNode undone = triagedStack.remove(triagedStack.size() - 1);
                        type = text(undone, "former_type", type);
                        if (undone.has("former_urgent")) {
                            urgent = undone.get("former_urgent").asBoolean();
                        }
                    }
                    type = text(payload, "type", type);
                    taskNo = text(payload, "task_no", taskNo);
                }
                default -> { }
            }
        }
        return new TaskProjection(state, dept, type, reminderStage, poolSource, taskNo, urgent);
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static DriverManagerDataSource dataSource(String url) {
        return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
