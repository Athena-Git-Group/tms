package com.athena.tms.bdd.support;

import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.repair.RepairTicketDraft;
import com.athena.tms.service.TaskEngine;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

public class DeskBoardFixtures {

    private static final Set<String> ENDED_SITUATIONS = Set.of(
            "已送達", "查房通過", "已當場回答", "已取消（主管取消）", "已取消（住客收回）");

    private static final Pattern TASK_NO = Pattern.compile("^T-(\\d{4,})$");

    private static final String DEFAULT_ANSWER = "櫃檯已當場回覆";

    private final TaskEngine engine;
    private final JdbcClient jdbcClient;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final DeskApi deskApi;
    private final ObjectMapper objectMapper;
    private final MutableClock clock;

    public DeskBoardFixtures(
            TaskEngine engine,
            JdbcClient jdbcClient,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            DeskApi deskApi,
            ObjectMapper objectMapper,
            MutableClock clock) {
        this.engine = engine;
        this.jdbcClient = jdbcClient;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.deskApi = deskApi;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public record Spec(
            String roomNo,
            String content,
            String type,
            String situation,
            Instant at,
            UUID assigneeId,
            String taskNo,
            String acceptedVia,
            UUID managerId,
            UUID previousAssigneeId) {
    }

    public Created create(Spec spec) {
        validate(spec);
        long tenantId = tenantFixtures.testTenantId();
        UUID roomId = roomFixtures.ensureRoom(spec.roomNo(), RoomFixtures.floorFromRoomNo(spec.roomNo()));
        LocalDate workDate = "cleaning".equals(spec.type())
                ? spec.at().atZone(clock.getZone()).toLocalDate()
                : null;

        UUID taskId = withTenant(tenantId, () -> build(tenantId, roomId, workDate, spec));

        String ticketNo = null;
        if ("repair".equals(spec.type())) {
            ticketNo = settleRepairTicket(tenantId, taskId, spec);
        } else {
            assignTaskNo(tenantId, taskId, spec.taskNo());
        }
        backdate(tenantId, taskId, spec.at(), ENDED_SITUATIONS.contains(spec.situation()));
        return new Created(taskId, ticketNo);
    }

    public record Created(UUID taskId, String repairTicketNo) {
    }

    public void returnToPoolByColleague(UUID taskId, Instant at) {
        long tenantId = tenantFixtures.testTenantId();
        Map<String, Object> task = taskRow(tenantId, taskId);
        if (!"pool".equals(task.get("state"))) {
            throw new IllegalStateException("只有待認領的單能被同仁退回待認領，這張單目前是 " + task.get("state"));
        }
        String dept = String.valueOf(task.get("dept"));
        Actor colleague = Actor.staff(dept, firstFieldStaff(tenantId, dept));
        withTenant(tenantId, () -> {
            engine.claim(tenantId, taskId, dept, colleague);
            engine.releaseToPool(tenantId, taskId, colleague);
            return null;
        });

        List<Map<String, Object>> lastTwo = jdbcClient
                .sql("SELECT id FROM task_events WHERE tenant_id = ? AND task_id = ? ORDER BY seq DESC LIMIT 2")
                .params(tenantId, taskId)
                .query()
                .listOfRows();
        setEventTime(lastTwo.get(0).get("id"), at);
        setEventTime(lastTwo.get(1).get("id"), at.minusSeconds(60));
        jdbcClient
                .sql("UPDATE tasks SET updated_at = ? WHERE tenant_id = ? AND id = ?")
                .params(offset(at), tenantId, taskId)
                .update();
    }

    private void validate(Spec spec) {
        String situation = spec.situation();
        if (spec.acceptedVia() != null && !"待開始".equals(situation)) {
            throw new IllegalArgumentException("「到手方式」只用於待開始的單，收到現況「" + situation + "」");
        }
        if (spec.previousAssigneeId() != null && !"被轉派".equals(spec.acceptedVia())) {
            throw new IllegalArgumentException("「原負責人」只用於到手方式是被轉派的單");
        }
        if ("被轉派".equals(spec.acceptedVia()) && spec.previousAssigneeId() == null) {
            throw new IllegalArgumentException("到手方式是被轉派時「原負責人」必填");
        }
        if (spec.taskNo() != null && "repair".equals(spec.type())) {
            throw new IllegalArgumentException("「單號」只用於非修繕單；修繕單的單號是工務單號");
        }
        if (spec.taskNo() != null && !TASK_NO.matcher(spec.taskNo()).matches()) {
            throw new IllegalArgumentException("「單號」要是 T- 加至少 4 位數字，收到「" + spec.taskNo() + "」");
        }
        Map<String, Set<String>> typesBySituation = Map.of(
                "待查房", Set.of("cleaning"),
                "查房通過", Set.of("cleaning"),
                "已送達", Set.of("amenity", "other"),
                "已完修", Set.of("repair"));
        Set<String> allowed = typesBySituation.get(situation);
        if (allowed != null && !allowed.contains(spec.type())) {
            throw new IllegalArgumentException("現況「" + situation + "」不會出現在類型 " + spec.type() + " 的單上");
        }
        if (("待派單".equals(situation) || "已當場回答".equals(situation)) && "repair".equals(spec.type())) {
            throw new IllegalArgumentException("住客打字的待派單不會是修繕單");
        }
    }

    private UUID build(long tenantId, UUID roomId, LocalDate workDate, Spec spec) {
        return switch (spec.situation()) {
            case "待派單" -> createGuestTyped(tenantId, roomId, workDate, spec, "triage");
            case "已當場回答" -> {
                UUID id = createGuestTyped(tenantId, roomId, workDate, spec, "triage");
                answerOnTheSpot(tenantId, id);
                yield id;
            }
            case "待認領" -> createInPool(tenantId, roomId, workDate, spec, deptOf(tenantId, spec));
            case "待開始" -> accept(tenantId, roomId, workDate, spec);
            case "進行中", "待查房", "已送達", "已完修", "查房通過" -> work(tenantId, roomId, workDate, spec);
            case "已取消（主管取消）" -> {
                UUID id = createInPool(tenantId, roomId, workDate, spec, deptOf(tenantId, spec));
                engine.cancel(tenantId, id, "manager_void", Actor.staff("manager", spec.managerId()));
                yield id;
            }
            case "已取消（住客收回）" -> {
                UUID id = createGuestTyped(tenantId, roomId, workDate, spec, "pool");
                jdbcClient
                        .sql("UPDATE tasks SET pool_source = 'guest_scan' WHERE tenant_id = ? AND id = ?")
                        .params(tenantId, id)
                        .update();
                engine.cancelByGuest(tenantId, id);
                yield id;
            }
            default -> throw new IllegalArgumentException("不支援的現況「" + spec.situation() + "」");
        };
    }

    private UUID createGuestTyped(long tenantId, UUID roomId, LocalDate workDate, Spec spec, String initialState) {
        String sessionToken = deskApi.guestSessionToken(spec.roomNo());
        UUID reservationId = jdbcClient
                .sql("SELECT reservation_id FROM guest_sessions WHERE session_token = ?")
                .params(sessionToken)
                .query(UUID.class)
                .single();
        String dept = "triage".equals(initialState) ? "frontdesk" : deptOf(tenantId, spec);
        String note = objectMapper.writeValueAsString(Map.of("text", spec.content()));
        return engine.create(new TaskCreateCommand(
                        tenantId, spec.type(), "guest", roomId, reservationId, dept, initialState, null,
                        spec.content(), note, null, null, workDate, Actor.guest(), false,
                        repairTicketDraft(tenantId, dept)))
                .getId();
    }

    private UUID createInPool(long tenantId, UUID roomId, LocalDate workDate, Spec spec, String dept) {
        return engine.create(new TaskCreateCommand(
                        tenantId, spec.type(), "repair".equals(spec.type()) ? "staff" : "desk", roomId, null, dept,
                        "pool", null, spec.content(), null, null, null, workDate, Actor.system(), false,
                        repairTicketDraft(tenantId, dept)))
                .getId();
    }

    private UUID accept(long tenantId, UUID roomId, LocalDate workDate, Spec spec) {
        String via = spec.acceptedVia() == null ? "自己認領" : spec.acceptedVia();
        UUID assigneeId = assigneeOrFirstOfDept(tenantId, spec);
        String dept = staffDept(tenantId, assigneeId);
        switch (via) {
            case "班前排房" -> {
                return engine.create(new TaskCreateCommand(
                                tenantId, spec.type(), "system", roomId, null, dept, "accepted", assigneeId,
                                spec.content(), null, null, null, workDate, Actor.system(), false,
                                repairTicketDraft(tenantId, dept)))
                        .getId();
            }
            case "自己認領" -> {
                UUID id = createInPool(tenantId, roomId, workDate, spec, dept);
                engine.claim(tenantId, id, dept, Actor.staff(dept, assigneeId));
                return id;
            }
            case "主管指派" -> {
                UUID id = createInPool(tenantId, roomId, workDate, spec, dept);
                managerAssignsFromPool(tenantId, id, assigneeId, spec.managerId());
                return id;
            }
            case "被轉派" -> {
                UUID id = createInPool(tenantId, roomId, workDate, spec, dept);
                UUID previous = spec.previousAssigneeId();
                engine.claim(tenantId, id, staffDept(tenantId, previous), Actor.staff(dept, previous));
                engine.reassign(tenantId, id, assigneeId, Actor.staff("manager", spec.managerId()));
                return id;
            }
            default -> throw new IllegalArgumentException(
                    "到手方式只支援 班前排房、自己認領、主管指派、被轉派，收到「" + via + "」");
        }
    }

    private void managerAssignsFromPool(long tenantId, UUID taskId, UUID assigneeId, UUID managerId) {
        int seq = jdbcClient
                .sql(
                        """
                        UPDATE tasks SET state = 'accepted', assignee_id = ?, event_seq = event_seq + 1, updated_at = now()
                         WHERE tenant_id = ? AND id = ? AND state = 'pool'
                        RETURNING event_seq
                        """)
                .params(assigneeId, tenantId, taskId)
                .query(Integer.class)
                .single();
        insertEvent(tenantId, taskId, seq, "assigned", "manager", managerId,
                Map.of("assignee_id", assigneeId.toString()));
    }

    private UUID work(long tenantId, UUID roomId, LocalDate workDate, Spec spec) {
        UUID assigneeId = assigneeOrFirstOfDept(tenantId, spec);
        String dept = staffDept(tenantId, assigneeId);
        Actor worker = Actor.staff(dept, assigneeId);
        UUID id = createInPool(tenantId, roomId, workDate, spec, dept);
        engine.claim(tenantId, id, dept, worker);
        engine.start(tenantId, id, worker);
        switch (spec.situation()) {
            case "進行中" -> {
            }
            case "已完修" -> engine.complete(tenantId, id, worker, Map.of("resolutionType", "fixed"), false);
            case "查房通過" -> {
                engine.complete(tenantId, id, worker);
                engine.passInspection(tenantId, id, Actor.staff("manager", spec.managerId()));
            }
            default -> engine.complete(tenantId, id, worker);
        }
        return id;
    }

    private void answerOnTheSpot(long tenantId, UUID taskId) {
        UUID frontdeskId = jdbcClient
                .sql(
                        """
                        SELECT id FROM staff_accounts
                         WHERE tenant_id = ? AND role = 'frontdesk' AND active = true
                         ORDER BY created_at, id LIMIT 1
                        """)
                .params(tenantId)
                .query(UUID.class)
                .optional()
                .orElse(null);
        int seq = jdbcClient
                .sql(
                        """
                        UPDATE tasks
                           SET state = 'done', answer = ?, done_at = now(), ended_at = now(),
                               event_seq = event_seq + 1, updated_at = now()
                         WHERE tenant_id = ? AND id = ? AND state = 'triage'
                        RETURNING event_seq
                        """)
                .params(DEFAULT_ANSWER, tenantId, taskId)
                .query(Integer.class)
                .single();
        insertEvent(tenantId, taskId, seq, "answered", "frontdesk", frontdeskId, Map.of("answer", DEFAULT_ANSWER));
    }

    private RepairTicketDraft repairTicketDraft(long tenantId, String dept) {
        return "engineering".equals(dept)
                ? new RepairTicketDraft("other", null, firstFieldStaff(tenantId, "engineering"), null, null)
                : null;
    }

    private String settleRepairTicket(long tenantId, UUID taskId, Spec spec) {
        Map<String, Object> task = taskRow(tenantId, taskId);
        Object assignee = task.get("assignee_id");
        UUID reporterId = assignee != null ? (UUID) assignee : firstFieldStaff(tenantId, "engineering");
        String state = String.valueOf(task.get("state"));
        String resolutionType = "done".equals(state) || "closed".equals(state) ? "fixed" : null;
        jdbcClient.sql("UPDATE tasks SET urgent = false WHERE tenant_id = ? AND id = ?").params(tenantId, taskId).update();
        return jdbcClient
                .sql(
                        """
                        UPDATE repair_tickets
                           SET reporter_id = ?, created_at = ?, is_urgent = false, urgent_rule = NULL,
                               resolution_type = ?
                         WHERE tenant_id = ? AND task_id = ?
                        RETURNING ticket_no
                        """)
                .params(reporterId, offset(spec.at()), resolutionType, tenantId, taskId)
                .query(String.class)
                .single();
    }

    private void assignTaskNo(long tenantId, UUID taskId, String requested) {
        String taskNo;
        if (requested != null) {
            Matcher m = TASK_NO.matcher(requested);
            m.matches();
            jdbcClient
                    .sql(
                            """
                            INSERT INTO task_counters (tenant_id, last_no) VALUES (?, ?)
                            ON CONFLICT (tenant_id) DO UPDATE SET last_no = GREATEST(task_counters.last_no, EXCLUDED.last_no)
                            """)
                    .params(tenantId, Integer.parseInt(m.group(1)))
                    .update();
            taskNo = requested;
        } else {
            Object existing = taskRow(tenantId, taskId).get("task_no");
            if (existing != null) {
                taskNo = String.valueOf(existing);
            } else {
                int n = jdbcClient
                        .sql(
                                """
                                INSERT INTO task_counters (tenant_id, last_no) VALUES (?, 1)
                                ON CONFLICT (tenant_id) DO UPDATE SET last_no = task_counters.last_no + 1
                                RETURNING last_no
                                """)
                        .param(tenantId)
                        .query(Integer.class)
                        .single();
                taskNo = String.format("T-%04d", n);
            }
        }
        jdbcClient
                .sql("UPDATE tasks SET task_no = ? WHERE tenant_id = ? AND id = ?")
                .params(taskNo, tenantId, taskId)
                .update();
        jdbcClient
                .sql(
                        """
                        UPDATE task_events
                           SET payload = COALESCE(payload, '{}'::jsonb) || jsonb_build_object('task_no', ?::text)
                         WHERE tenant_id = ? AND task_id = ? AND type = 'created'
                        """)
                .params(taskNo, tenantId, taskId)
                .update();
    }

    private void backdate(long tenantId, UUID taskId, Instant at, boolean ended) {
        List<Map<String, Object>> events = jdbcClient
                .sql("SELECT id, type FROM task_events WHERE tenant_id = ? AND task_id = ? ORDER BY seq")
                .params(tenantId, taskId)
                .query()
                .listOfRows();
        int n = events.size();
        Map<String, Instant> timeOfType = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            Instant t = at.minusSeconds(60L * (n - 1 - i));
            setEventTime(events.get(i).get("id"), t);
            timeOfType.put(String.valueOf(events.get(i).get("type")), t);
        }
        Instant first = at.minusSeconds(60L * (n - 1));
        Instant started = timeOfType.get("started");
        Instant done = timeOfType.containsKey("completed") ? timeOfType.get("completed") : timeOfType.get("answered");
        jdbcClient
                .sql(
                        """
                        UPDATE tasks
                           SET created_at = ?,
                               updated_at = ?,
                               started_at = ?,
                               segment_started_at = CASE WHEN state = 'doing' THEN ? ELSE segment_started_at END,
                               done_at = ?,
                               ended_at = ?
                         WHERE tenant_id = ? AND id = ?
                        """)
                .params(
                        offset(first),
                        offset(at),
                        started == null ? null : offset(started),
                        started == null ? null : offset(started),
                        done == null ? null : offset(done),
                        ended ? offset(at) : null,
                        tenantId,
                        taskId)
                .update();
    }

    private void insertEvent(
            long tenantId, UUID taskId, int seq, String type, String actorRole, UUID actorId, Map<String, ?> payload) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, now())
                        """)
                .params(tenantId, taskId, seq, type, actorRole, actorId, objectMapper.writeValueAsString(payload))
                .update();
    }

    private void setEventTime(Object eventId, Instant at) {
        jdbcClient.sql("UPDATE task_events SET created_at = ? WHERE id = ?").params(offset(at), eventId).update();
    }

    private String deptOf(long tenantId, Spec spec) {
        if (spec.assigneeId() != null) {
            return staffDept(tenantId, spec.assigneeId());
        }
        return "repair".equals(spec.type()) ? "engineering" : "housekeeping";
    }

    private UUID assigneeOrFirstOfDept(long tenantId, Spec spec) {
        if (spec.assigneeId() != null) {
            return spec.assigneeId();
        }
        return firstFieldStaff(tenantId, deptOf(tenantId, spec));
    }

    private String staffDept(long tenantId, UUID staffId) {
        return jdbcClient
                .sql("SELECT dept FROM staff_accounts WHERE tenant_id = ? AND id = ?")
                .params(tenantId, staffId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException("找不到員工 " + staffId + " 的部門"));
    }

    private UUID firstFieldStaff(long tenantId, String dept) {
        return jdbcClient
                .sql(
                        """
                        SELECT id FROM staff_accounts
                         WHERE tenant_id = ? AND dept = ? AND role = ? AND active = true
                         ORDER BY created_at, id LIMIT 1
                        """)
                .params(tenantId, dept, dept)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "飯店沒有部門 " + dept + " 的在職同仁可以當負責人；請在表格寫「負責人」或先建立員工"));
    }

    private Map<String, Object> taskRow(long tenantId, UUID taskId) {
        return jdbcClient
                .sql("SELECT * FROM tasks WHERE tenant_id = ? AND id = ?")
                .params(tenantId, taskId)
                .query()
                .singleRow();
    }

    private OffsetDateTime offset(Instant at) {
        return OffsetDateTime.ofInstant(at, clock.getZone());
    }

    private static <T> T withTenant(long tenantId, Supplier<T> action) {
        TenantContext.set(tenantId);
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }
}
