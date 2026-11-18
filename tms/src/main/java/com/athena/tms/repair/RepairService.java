package com.athena.tms.repair;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.ManagerKind;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.ForbiddenException;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.common.codes.ErrorCode;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.RepairVerificationOutcome;
import com.athena.tms.dto.VerifyRepairTicketRequest;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.entity.Task;
import com.athena.tms.service.AutomationRuleService;
import com.athena.tms.service.PushOutboxWriter;
import com.athena.tms.service.StaffAvailability;
import com.athena.tms.service.TaskEngine;
import com.athena.tms.service.TaskRowLookup;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class RepairService {

    public static final Map<String, String> CATEGORY_LABELS = Map.of(
            "air_conditioning", "冷氣",
            "plumbing_electrical", "水電",
            "furniture_equipment", "家具／設備",
            "door_lock", "門鎖",
            "other", "其他");

    private static final String VERIFY_PASSED = "passed";
    private static final String VERIFY_FAILED = "failed";
    private static final DateTimeFormatter CHECKIN_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final TaskEngine taskEngine;
    private final PushOutboxWriter pushOutboxWriter;
    private final TaskRowLookup taskRowLookup;
    private final ObjectMapper objectMapper;
    private final StaffAvailability staffAvailability;
    private final Clock clock;

    public RepairService(
            JdbcTemplate jdbcTemplate,
            TaskEngine taskEngine,
            PushOutboxWriter pushOutboxWriter,
            TaskRowLookup taskRowLookup,
            ObjectMapper objectMapper,
            StaffAvailability staffAvailability,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskEngine = taskEngine;
        this.pushOutboxWriter = pushOutboxWriter;
        this.taskRowLookup = taskRowLookup;
        this.objectMapper = objectMapper;
        this.staffAvailability = staffAvailability;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> createPhoto(String purpose, String takenAt) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        UUID photoId = UuidV7.generate();
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime taken = takenAt == null ? now : OffsetDateTime.parse(takenAt);
        jdbcTemplate.update(
                """
                INSERT INTO repair_photos (id, tenant_id, purpose, status, taken_at, uploaded_by, created_at)
                VALUES (?, ?, ?, 'pending', ?, ?, ?)
                """,
                photoId, tenantId, purpose, taken, actor.staffId(), now);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", photoId.toString());
        body.put("status", "pending");
        body.put("takenAt", taken.toString());
        return body;
    }

    @Transactional
    public Map<String, Object> markUploaded(UUID photoId) {
        long tenantId = TenantContext.require();
        OffsetDateTime now = OffsetDateTime.now(clock);
        int updated = jdbcTemplate.update(
                """
                UPDATE repair_photos
                   SET status = 'uploaded', storage_ref = ?, uploaded_at = COALESCE(uploaded_at, ?)
                 WHERE tenant_id = ? AND id = ?
                """,
                "upload:" + photoId, now, tenantId, photoId);
        if (updated == 0) {
            throw new NotFoundException("查無照片: " + photoId);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", photoId.toString());
        body.put("status", "uploaded");
        return body;
    }

    @Transactional
    public Map<String, Object> precheck(UUID sourceTaskId, String category) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        Map<String, Object> sourceTask = taskRowLookup.require(tenantId, sourceTaskId);

        Map<String, Object> duplicate = null;
        List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(
                """
                SELECT rt.task_id, rt.ticket_no, rt.category, rt.created_at, rt.reporter_id,
                       t.state, rt.is_urgent, r.room_no, s.name AS reporter_name
                  FROM repair_tickets rt
                  JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                  JOIN rooms r ON r.tenant_id = rt.tenant_id AND r.id = rt.room_id
                  JOIN staff_accounts s ON s.tenant_id = rt.tenant_id AND s.id = rt.reporter_id
                 WHERE rt.tenant_id = ? AND rt.room_id = ? AND rt.category = ?
                   AND t.state NOT IN ('cancelled', """ + TaskState.FINISHED_SQL_LIST + """
                       )
                 ORDER BY rt.created_at DESC
                 LIMIT 1
                """,
                tenantId, sourceTask.get("room_id"), category);
        if (!duplicates.isEmpty()) {
            Map<String, Object> row = duplicates.get(0);
            UUID ticketTaskId = UUID.fromString(String.valueOf(row.get("task_id")));
            duplicate = summaryFromRow(ticketTaskId, row);

            if (!actor.staffId().toString().equalsIgnoreCase(String.valueOf(row.get("reporter_id")))) {
                jdbcTemplate.update(
                        """
                        INSERT INTO repair_ticket_contributors (tenant_id, ticket_task_id, staff_id, granted_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (tenant_id, ticket_task_id, staff_id) DO NOTHING
                        """,
                        tenantId, ticketTaskId, actor.staffId(), OffsetDateTime.now(clock));
            }
        }

        UUID roomId = UUID.fromString(String.valueOf(sourceTask.get("room_id")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("duplicate", duplicate);
        body.put("urgentJudgement", judgeUrgent(tenantId, roomId));
        return body;
    }

    private Map<String, Object> judgeUrgent(long tenantId, UUID roomId) {
        Map<String, Object> judgement = new LinkedHashMap<>();
        judgement.put("suggested", false);
        judgement.put("rule", null);

        LocalDate today = OffsetDateTime.now(clock).toLocalDate();
        List<Map<String, Object>> reservations = jdbcTemplate.queryForList(
                """
                SELECT checkin_at, (attrs ->> 'vip') = 'true' AS vip
                  FROM reservations_cache
                 WHERE tenant_id = ? AND room_id = ? AND superseded_at IS NULL
                   AND (checkin_at AT TIME ZONE ?)::date = ?
                 ORDER BY checkin_at
                 LIMIT 1
                """,
                tenantId, roomId, clock.getZone().getId(), today);
        if (!reservations.isEmpty()) {
            Map<String, Object> reservation = reservations.get(0);
            boolean vip = Boolean.TRUE.equals(reservation.get("vip"));
            String checkinTime = toOffsetDateTime(reservation.get("checkin_at"))
                    .atZoneSameInstant(clock.getZone())
                    .format(CHECKIN_TIME);
            String rule = (vip ? "VIP · " : "") + checkinTime + " 入住";
            judgement.put("suggested", true);
            judgement.put("rule", rule);
        }
        return judgement;
    }

    public Map<String, Object> read(UUID ticketTaskId) {
        long tenantId = TenantContext.require();
        return ticketView(tenantId, ticketTaskId);
    }

    public List<Map<String, Object>> listByRoom(UUID roomId) {
        long tenantId = TenantContext.require();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT rt.task_id, rt.ticket_no, rt.category, rt.created_at, rt.is_urgent,
                       t.state, r.room_no, s.name AS reporter_name
                  FROM repair_tickets rt
                  JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                  JOIN rooms r ON r.tenant_id = rt.tenant_id AND r.id = rt.room_id
                  JOIN staff_accounts s ON s.tenant_id = rt.tenant_id AND s.id = rt.reporter_id
                 WHERE rt.tenant_id = ? AND rt.room_id = ? AND t.state <> 'cancelled'
                 ORDER BY rt.created_at DESC
                """,
                tenantId, roomId);
        List<Map<String, Object>> body = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            body.add(summaryFromRow(UUID.fromString(String.valueOf(row.get("task_id"))), row));
        }
        return body;
    }

    @Transactional
    public Map<String, Object> downgradeUrgency(UUID ticketTaskId, String downgradeReason) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        Map<String, Object> ticket = requireTicket(tenantId, ticketTaskId);
        if (!Boolean.TRUE.equals(ticket.get("is_urgent"))) {
            throw new StateConflictException("not_urgent", "這張單目前不是急件");
        }

        jdbcTemplate.update(
                "UPDATE repair_tickets SET is_urgent = false, urgent_downgrade_reason = ? WHERE tenant_id = ? AND task_id = ?",
                downgradeReason, tenantId, ticketTaskId);
        jdbcTemplate.update(
                "UPDATE tasks SET urgent = false WHERE tenant_id = ? AND id = ?", tenantId, ticketTaskId);

        taskEngine.recordUrgentDowngraded(
                tenantId, ticketTaskId, downgradeReason, Actor.staff(actor.role(), actor.staffId()));

        return ticketView(tenantId, ticketTaskId);
    }

    @Transactional
    public Map<String, Object> appendPhotos(UUID ticketTaskId, List<UUID> photoIds) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        Map<String, Object> ticket = requireTicket(tenantId, ticketTaskId);

        boolean isReporter =
                actor.staffId().toString().equalsIgnoreCase(String.valueOf(ticket.get("reporter_id")));
        if (!isReporter) {
            Integer contributor = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM repair_ticket_contributors
                     WHERE tenant_id = ? AND ticket_task_id = ? AND staff_id = ?
                    """,
                    Integer.class, tenantId, ticketTaskId, actor.staffId());
            if (contributor == null || contributor == 0) {
                throw new ForbiddenException("只有報修人或看過這張單重複提示的員工可以補照片");
            }
        }

        bindUnclaimedPhotos(tenantId, ticketTaskId, photoIds);
        return ticketView(tenantId, ticketTaskId);
    }

    @Transactional
    public Map<String, Object> withdraw(UUID ticketTaskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        requireTicket(tenantId, ticketTaskId);
        try {
            taskEngine.withdrawFromPool(tenantId, ticketTaskId, Actor.staff(actor.role(), actor.staffId()));
        } catch (StateConflictException conflict) {
            String state = String.valueOf(taskRowLookup.require(tenantId, ticketTaskId).get("state"));
            if ("accepted".equals(state) || "doing".equals(state)) {
                throw new StateConflictException("already_claimed", "已被工務認領");
            }
            throw conflict;
        }
        return ticketView(tenantId, ticketTaskId);
    }

    @Transactional
    public void resolveOnComplete(
            UUID ticketTaskId, List<UUID> photoIds, String resolutionNote, RepairResolution resolution) {
        long tenantId = TenantContext.require();
        Map<String, Object> ticket = requireTicket(tenantId, ticketTaskId);
        recordCompletionEvidence(tenantId, ticketTaskId, photoIds, resolutionNote);

        String categoryLabel = CATEGORY_LABELS.getOrDefault(
                String.valueOf(ticket.get("category")), String.valueOf(ticket.get("category")));
        markSourceTaskRepaired(tenantId, ticket, categoryLabel);
        notifyReporterRepaired(tenantId, ticketTaskId, ticket, categoryLabel);
        applyResolution(tenantId, ticketTaskId, ticket, resolution);
    }

    private void recordCompletionEvidence(
            long tenantId, UUID ticketTaskId, List<UUID> photoIds, String resolutionNote) {
        if (photoIds != null) {
            bindUnclaimedPhotos(tenantId, ticketTaskId, photoIds);
        }
        if (resolutionNote != null) {
            jdbcTemplate.update(
                    "UPDATE repair_tickets SET resolution_note = ? WHERE tenant_id = ? AND task_id = ?",
                    resolutionNote, tenantId, ticketTaskId);
        }
    }

    private void markSourceTaskRepaired(long tenantId, Map<String, Object> ticket, String categoryLabel) {
        Object sourceTaskId = ticket.get("source_task_id");
        if (sourceTaskId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE tasks SET hold_reason = ?, updated_at = now()
                     WHERE tenant_id = ? AND id = ? AND state = 'interrupted'
                    """,
                    "工務已修好：" + categoryLabel, tenantId, sourceTaskId);
        }
    }

    private void notifyReporterRepaired(
            long tenantId, UUID ticketTaskId, Map<String, Object> ticket, String categoryLabel) {
        Object sourceTaskId = ticket.get("source_task_id");
        String targetType = "repair";
        UUID targetTaskId = ticketTaskId;
        if (sourceTaskId != null) {
            targetTaskId = UUID.fromString(String.valueOf(sourceTaskId));
            targetType = String.valueOf(taskRowLookup.require(tenantId, targetTaskId).get("type"));
        }
        Integer completedSeq = jdbcTemplate.queryForObject(
                "SELECT event_seq FROM tasks WHERE tenant_id = ? AND id = ?", Integer.class, tenantId, ticketTaskId);
        pushToReporter(tenantId, ticketTaskId, ticket, "repair_completed",
                roomNoOf(tenantId, ticket) + " " + categoryLabel + "已修好", null,
                targetType, targetTaskId, "repair_resolved:" + ticketTaskId + ":" + completedSeq);
    }

    private void applyResolution(
            long tenantId, UUID ticketTaskId, Map<String, Object> ticket, RepairResolution resolution) {
        jdbcTemplate.update(
                "UPDATE repair_tickets SET resolution_type = ? WHERE tenant_id = ? AND task_id = ?",
                resolution.code(), tenantId, ticketTaskId);
        switch (resolution.afterComplete()) {
            case FOLLOW_UP_TICKET -> createFollowUpTicket(tenantId, ticketTaskId, ticket);
            case ROOM_OUT_OF_ORDER -> jdbcTemplate.update(
                    "UPDATE rooms SET hk_state = 'out_of_order' WHERE tenant_id = ? AND id = ?",
                    tenantId, ticket.get("room_id"));
            case NOTHING -> { }
        }
    }

    private void createFollowUpTicket(long tenantId, UUID originTicketTaskId, Map<String, Object> originTicket) {
        Actor systemActor = Actor.staff(CurrentActor.require().role(), CurrentActor.require().staffId());
        UUID roomId = UUID.fromString(String.valueOf(originTicket.get("room_id")));
        String category = String.valueOf(originTicket.get("category"));
        String categoryLabel = CATEGORY_LABELS.getOrDefault(category, category);

        String ticketNo = nextTicketNo(tenantId);
        Task followUpTask = taskEngine.create(new TaskCreateCommand(
                tenantId, "repair", "system", roomId, null, "engineering", "pool", null,
                categoryLabel + " 工務單（追蹤）", null, null, null, null, systemActor));
        UUID followUpTaskId = followUpTask.getId();

        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbcTemplate.update(
                """
                INSERT INTO repair_tickets
                    (task_id, tenant_id, ticket_no, room_id, category, reporter_id,
                     created_at, is_urgent, origin_ticket_task_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, false, ?)
                """,
                followUpTaskId, tenantId, ticketNo, roomId, category, originTicket.get("reporter_id"),
                now, originTicketTaskId);
    }

    private Map<String, Object> requireTicket(long tenantId, UUID ticketTaskId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM repair_tickets WHERE tenant_id = ? AND task_id = ?", tenantId, ticketTaskId);
        if (rows.isEmpty()) {
            throw new NotFoundException("查無工務單: " + ticketTaskId);
        }
        return rows.get(0);
    }

    private Map<String, Object> ticketView(long tenantId, UUID ticketTaskId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT rt.ticket_no, rt.category, rt.description, rt.created_at, rt.source_task_id,
                       rt.resolution_note, rt.is_urgent, rt.urgent_rule, rt.urgent_downgrade_reason,
                       rt.verified_at, rt.origin_ticket_task_id, rt.resolution_type,
                       t.state, t.source, t.done_at, r.room_no, s.name AS reporter_name,
                       v.name AS verifier_name, ot.ticket_no AS origin_ticket_no,
                       (SELECT count(*) FROM repair_photos p
                         WHERE p.tenant_id = rt.tenant_id AND p.ticket_task_id = rt.task_id
                           AND p.purpose = 'report') AS photo_count,
                       t.assignee_id, rt.last_verify_failed_reason, rt.last_verify_failed_at,
                       f.name AS last_verify_failed_by_name, a.name AS assignee_name
                  FROM repair_tickets rt
                  JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                  JOIN rooms r ON r.tenant_id = rt.tenant_id AND r.id = rt.room_id
                  JOIN staff_accounts s ON s.tenant_id = rt.tenant_id AND s.id = rt.reporter_id
                  LEFT JOIN staff_accounts v ON v.tenant_id = rt.tenant_id AND v.id = rt.verified_by
                  LEFT JOIN staff_accounts f ON f.tenant_id = rt.tenant_id AND f.id = rt.last_verify_failed_by
                  LEFT JOIN staff_accounts a ON a.tenant_id = t.tenant_id AND a.id = t.assignee_id
                  LEFT JOIN repair_tickets ot ON ot.tenant_id = rt.tenant_id AND ot.task_id = rt.origin_ticket_task_id
                 WHERE rt.tenant_id = ? AND rt.task_id = ?
                """,
                tenantId, ticketTaskId);
        if (rows.isEmpty()) {
            throw new NotFoundException("查無工務單: " + ticketTaskId);
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> body = summaryFromRow(ticketTaskId, row);
        body.put("photoCount", ((Number) row.get("photo_count")).intValue());
        body.put("description", row.get("description"));
        body.put("resolutionNote", row.get("resolution_note"));
        body.put("completionPhotos", completionPhotos(tenantId, ticketTaskId));
        if (row.get("source_task_id") != null) {
            body.put("sourceTaskId", String.valueOf(row.get("source_task_id")));
        }
        String urgentRule = (String) row.get("urgent_rule");
        Map<String, Object> urgentJudgement = new LinkedHashMap<>();
        urgentJudgement.put("suggested", urgentRule != null);
        urgentJudgement.put("rule", urgentRule);
        body.put("urgentJudgement", urgentJudgement);
        body.put("urgentDowngradeReason", row.get("urgent_downgrade_reason"));
        body.put("verifiedBy", row.get("verifier_name"));
        body.put("verifiedAt", row.get("verified_at") == null ? null : toOffsetDateTime(row.get("verified_at")).toString());
        body.put("originTicketNo", row.get("origin_ticket_no"));
        body.put("resolutionType", row.get("resolution_type"));
        body.put("doneAt", row.get("done_at") == null ? null : toOffsetDateTime(row.get("done_at")).toString());
        body.put("lastVerifyFailedReason", row.get("last_verify_failed_reason"));
        body.put("lastVerifyFailedBy", row.get("last_verify_failed_by_name"));
        body.put("lastVerifyFailedAt", row.get("last_verify_failed_at") == null
                ? null : toOffsetDateTime(row.get("last_verify_failed_at")).toString());
        body.put("assigneeName", row.get("assignee_name"));
        CurrentActor viewer = CurrentActor.get();
        body.put("viewerIsAssignee", viewer != null && row.get("assignee_id") != null
                && viewer.staffId().toString().equalsIgnoreCase(String.valueOf(row.get("assignee_id"))));
        body.put("origin", "system".equals(row.get("source")) ? "scheduled" : "reported");
        return body;
    }

    private List<Map<String, Object>> completionPhotos(long tenantId, UUID ticketTaskId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT p.id, p.status, p.taken_at
                  FROM repair_photos p
                  JOIN repair_tickets rt ON rt.tenant_id = p.tenant_id AND rt.task_id = p.ticket_task_id
                 WHERE p.tenant_id = ? AND p.ticket_task_id = ? AND p.purpose = 'completion'
                   AND (rt.last_verify_failed_at IS NULL
                        OR COALESCE(p.uploaded_at, p.created_at) > rt.last_verify_failed_at)
                 ORDER BY p.taken_at, p.id
                """,
                tenantId, ticketTaskId);
        List<Map<String, Object>> photos = new ArrayList<>(rows.size());
        for (Map<String, Object> photoRow : rows) {
            Map<String, Object> photo = new LinkedHashMap<>();
            photo.put("id", String.valueOf(photoRow.get("id")));
            photo.put("status", photoRow.get("status"));
            photo.put("takenAt", toOffsetDateTime(photoRow.get("taken_at")).toString());
            photos.add(photo);
        }
        return photos;
    }

    private Map<String, Object> summaryFromRow(UUID ticketTaskId, Map<String, Object> row) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", ticketTaskId.toString());
        body.put("ticketNo", row.get("ticket_no"));
        boolean publicArea = AutomationRuleService.isPublicAreaRoomNo(row.get("room_no"));
        body.put("roomNo", publicArea ? null : row.get("room_no"));
        body.put("isPublicArea", publicArea);
        body.put("category", row.get("category"));
        body.put("isUrgent", Boolean.TRUE.equals(row.get("is_urgent")));
        body.put("state", row.get("state"));
        body.put("reporterName", row.get("reporter_name"));
        body.put("createdAt", toOffsetDateTime(row.get("created_at")).toString());
        return body;
    }

    private void bindUnclaimedPhotos(long tenantId, UUID ticketTaskId, List<UUID> photoIds) {
        for (UUID photoId : photoIds) {
            jdbcTemplate.update(
                    "UPDATE repair_photos SET ticket_task_id = ? WHERE tenant_id = ? AND id = ? AND ticket_task_id IS NULL",
                    ticketTaskId, tenantId, photoId);
        }
    }

    @Transactional
    public Map<String, Object> create(
            UUID sourceTaskId,
            UUID roomId,
            Boolean isPublicArea,
            UUID assigneeId,
            String category,
            String description,
            List<UUID> photoIds,
            boolean interruptSourceTask) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        Actor staffActor = Actor.staff(actor.role(), actor.staffId());
        requireUploadedPhotos(tenantId, photoIds);

        UUID effectiveRoomId;
        String roomNo;
        String taskSource;
        String poolSource = null;
        if (sourceTaskId != null) {
            Map<String, Object> sourceTask = taskRowLookup.require(tenantId, sourceTaskId);
            effectiveRoomId = UUID.fromString(String.valueOf(sourceTask.get("room_id")));
            roomNo = String.valueOf(sourceTask.get("room_no"));
            taskSource = "staff";
        } else {
            effectiveRoomId = Boolean.TRUE.equals(isPublicArea) ? ensurePublicAreaRoom(tenantId) : roomId;
            if (effectiveRoomId == null) {
                throw new UnprocessableEntityException(
                        "repair_source_missing", "sourceTaskId、roomId、isPublicArea 必須擇一提供");
            }
            roomNo = queryFirstString(
                    "SELECT room_no FROM rooms WHERE tenant_id = ? AND id = ?", tenantId, effectiveRoomId);
            taskSource = directOpenSource(actor.dept());
            if ("desk".equals(taskSource)) {
                poolSource = "desk_forward";
            }
        }

        String categoryLabel = CATEGORY_LABELS.getOrDefault(category, category);

        Map<String, Object> urgentJudgement = judgeUrgent(tenantId, effectiveRoomId);
        boolean isUrgent = Boolean.TRUE.equals(urgentJudgement.get("suggested"));
        String urgentRule = (String) urgentJudgement.get("rule");

        String initialState = assigneeId != null ? TaskState.ACCEPTED : TaskState.POOL;

        String ticketNo = nextTicketNo(tenantId);
        Task ticketTask = taskEngine.create(new TaskCreateCommand(
                tenantId, "repair", taskSource, effectiveRoomId, null, "engineering", initialState, assigneeId,
                categoryLabel + " 工務單", null, null, null, null, staffActor, isUrgent));
        UUID ticketTaskId = ticketTask.getId();

        if (poolSource != null && TaskState.POOL.equals(initialState)) {
            jdbcTemplate.update(
                    "UPDATE tasks SET pool_source = ? WHERE tenant_id = ? AND id = ?",
                    poolSource, tenantId, ticketTaskId);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbcTemplate.update(
                """
                INSERT INTO repair_tickets
                    (task_id, tenant_id, ticket_no, room_id, category, description,
                     reporter_id, source_task_id, created_at, is_urgent, urgent_rule)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ticketTaskId, tenantId, ticketNo, effectiveRoomId, category, description,
                actor.staffId(), sourceTaskId, now, isUrgent, urgentRule);

        bindUnclaimedPhotos(tenantId, ticketTaskId, photoIds);

        if (interruptSourceTask) {
            taskEngine.interrupt(tenantId, sourceTaskId, "等工務：" + categoryLabel, staffActor);
            jdbcTemplate.update(
                    "UPDATE tasks SET linked_repair_task_id = ? WHERE tenant_id = ? AND id = ?",
                    ticketTaskId, tenantId, sourceTaskId);
        }

        if (isUrgent) {
            notifyUrgent(tenantId, ticketTaskId, ticketNo, roomNo, categoryLabel);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", ticketTaskId.toString());
        body.put("ticketNo", ticketNo);
        body.put("roomNo", roomNo);
        body.put("category", category);
        body.put("isUrgent", isUrgent);
        body.put("state", initialState);
        body.put("reporterName", staffName(tenantId, actor.staffId()));
        body.put("createdAt", now.toString());
        body.put("photoCount", photoIds.size());
        body.put("sourceTaskId", sourceTaskId == null ? null : sourceTaskId.toString());
        body.put("urgentJudgement", urgentJudgement);
        body.put("urgentDowngradeReason", null);
        body.put("assigneeName", assigneeId == null ? null : staffName(tenantId, assigneeId));
        return body;
    }

    private static String directOpenSource(String dept) {
        return switch (dept) {
            case "engineering" -> "staff";
            case "frontdesk" -> "desk";
            default -> throw new ForbiddenException("只有工務或櫃檯可以不經過房務任務直接開立工務單");
        };
    }

    private UUID ensurePublicAreaRoom(long tenantId) {
        String roomNo = AutomationRuleService.PUBLIC_AREA_ROOM_NO;
        String existing = queryFirstString(
                "SELECT id FROM rooms WHERE tenant_id = ? AND room_no = ?", tenantId, roomNo);
        if (existing != null) {
            return UUID.fromString(existing);
        }
        UUID id = UuidV7.generate();
        jdbcTemplate.update(
                """
                INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state, occupancy, updated_at)
                VALUES (?, ?, ?, 0, 'public_area', 'cleaned', 'vacant', ?)
                ON CONFLICT (tenant_id, room_no) DO NOTHING
                """,
                id, tenantId, roomNo, OffsetDateTime.now(clock));
        String after = queryFirstString(
                "SELECT id FROM rooms WHERE tenant_id = ? AND room_no = ?", tenantId, roomNo);
        return after != null ? UUID.fromString(after) : id;
    }

    @Transactional
    public Map<String, Object> releaseToPool(UUID ticketTaskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        Map<String, Object> ticket = requireTicket(tenantId, ticketTaskId);
        String roomNo = queryFirstString(
                "SELECT room_no FROM rooms WHERE tenant_id = ? AND id = ?", tenantId, ticket.get("room_id"));
        if (!AutomationRuleService.isPublicAreaRoomNo(roomNo)) {
            throw new StateConflictException("room_ticket_not_requeueable", "客房工務單只能暫停，不能放回工務待認領");
        }
        taskEngine.releaseToPool(tenantId, ticketTaskId, Actor.staff(actor.role(), actor.staffId()));
        return ticketView(tenantId, ticketTaskId);
    }

    @Transactional
    public RepairVerificationOutcome verify(UUID ticketTaskId, VerifyRepairTicketRequest request) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        if (ManagerKind.of(actor).isEmpty()) {
            throw new ForbiddenException("只有主管可以驗收工務單");
        }
        String result = request == null ? null : request.result();
        if (!VERIFY_PASSED.equals(result) && !VERIFY_FAILED.equals(result)) {
            throw new UnprocessableEntityException("驗收結果只能是 passed 或 failed");
        }
        String reason = request.reason() == null ? "" : request.reason().strip();
        if (VERIFY_FAILED.equals(result) && reason.isEmpty()) {
            throw new UnprocessableEntityException(ErrorCode.VERIFICATION_REASON_REQUIRED, "驗收不通過必須寫原因");
        }
        Map<String, Object> ticket = requireTicket(tenantId, ticketTaskId);
        Actor manager = Actor.staff(actor.role(), actor.staffId());
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID assigneeId = jdbcTemplate.queryForObject(
                "SELECT assignee_id FROM tasks WHERE tenant_id = ? AND id = ?", UUID.class, tenantId, ticketTaskId);

        String destination;
        if (VERIFY_PASSED.equals(result)) {
            taskEngine.verifyRepairTicket(tenantId, ticketTaskId, manager);
            jdbcTemplate.update(
                    "UPDATE repair_tickets SET verified_by = ?, verified_at = ? WHERE tenant_id = ? AND task_id = ?",
                    actor.staffId(), now, tenantId, ticketTaskId);
            destination = "closed";
        } else {
            boolean toPool = assigneeId == null || staffAvailability.unavailable(tenantId, assigneeId);
            int seq = taskEngine.failRepairVerification(tenantId, ticketTaskId, manager, reason, assigneeId, toPool);
            jdbcTemplate.update(
                    """
                    UPDATE repair_tickets
                       SET last_verify_failed_reason = ?, last_verify_failed_by = ?, last_verify_failed_at = ?,
                           resolution_type = NULL, resolution_note = NULL
                     WHERE tenant_id = ? AND task_id = ?
                    """,
                    reason, actor.staffId(), now, tenantId, ticketTaskId);
            destination = toPool ? "pool" : "assignee";
            if (!toPool) {
                pushVerificationFailed(tenantId, ticketTaskId, ticket, assigneeId, reason, seq);
            }
        }

        String roomNo = roomNoOf(tenantId, ticket);
        boolean publicArea = AutomationRuleService.isPublicAreaRoomNo(roomNo);
        return new RepairVerificationOutcome(
                ticketTaskId,
                (String) ticket.get("ticket_no"),
                publicArea ? null : roomNo,
                publicArea,
                result,
                destination,
                assigneeId == null ? null : staffName(tenantId, assigneeId),
                VERIFY_FAILED.equals(result) ? reason : null,
                staffName(tenantId, actor.staffId()),
                now);
    }

    private void requireUploadedPhotos(long tenantId, List<UUID> photoIds) {
        if (photoIds.isEmpty()) {
            throw new UnprocessableEntityException("photo_required", "至少需要 1 張照片");
        }
        if (photoIds.size() > 3) {
            throw new UnprocessableEntityException("photo_limit", "最多 3 張");
        }
        String placeholders = String.join(", ", Collections.nCopies(photoIds.size(), "?"));
        List<Object> params = new ArrayList<>(photoIds.size() + 1);
        params.add(tenantId);
        params.addAll(photoIds);
        Integer uploaded = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM repair_photos WHERE tenant_id = ? AND status = 'uploaded' AND id IN ("
                        + placeholders + ")",
                Integer.class, params.toArray());
        if (uploaded == null || uploaded != photoIds.size()) {
            throw new UnprocessableEntityException("photo_not_uploaded", "有照片還沒上傳成功");
        }
    }

    private String nextTicketNo(long tenantId) {
        Integer lastNo = jdbcTemplate.queryForObject(
                """
                INSERT INTO repair_ticket_counters (tenant_id, last_no) VALUES (?, 1)
                ON CONFLICT (tenant_id) DO UPDATE SET last_no = repair_ticket_counters.last_no + 1
                RETURNING last_no
                """,
                Integer.class, tenantId);
        return String.format("W-%04d", lastNo);
    }

    private void notifyUrgent(long tenantId, UUID ticketTaskId, String ticketNo, String roomNo, String categoryLabel) {
        List<UUID> recipients = jdbcTemplate.queryForList(
                """
                SELECT id FROM staff_accounts
                 WHERE tenant_id = ? AND active = true AND (dept = 'engineering' OR role = 'manager')
                """,
                UUID.class, tenantId);
        for (UUID recipient : recipients) {
            ObjectNode payload = PushOutboxWriter.payload(
                    objectMapper, recipient, "repair_urgent",
                    roomNo + " 急件報修（" + categoryLabel + " " + ticketNo + "）");
            PushOutboxWriter.targetPool(payload, "engineering", ticketTaskId);
            pushOutboxWriter.enqueue(tenantId, "task:" + ticketTaskId, payload,
                    "repair_urgent:" + ticketTaskId + ":" + recipient);
        }
    }

    private void pushVerificationFailed(long tenantId, UUID ticketTaskId, Map<String, Object> ticket, UUID assigneeId,
            String reason, int seq) {
        pushAboutTicket(tenantId, ticketTaskId, assigneeId, "repair_verification_failed",
                roomNoOf(tenantId, ticket) + " " + ticket.get("ticket_no") + " 驗收不通過", reason,
                "repair", ticketTaskId, "repair_verification_failed:" + ticketTaskId + ":" + seq);
    }

    private void pushToReporter(long tenantId, UUID ticketTaskId, Map<String, Object> ticket, String event,
            String title, String body, String targetTaskType, UUID targetTaskId, String idempotencyPrefix) {
        UUID reporterId = UUID.fromString(String.valueOf(ticket.get("reporter_id")));
        pushAboutTicket(tenantId, ticketTaskId, reporterId, event, title, body, targetTaskType, targetTaskId,
                idempotencyPrefix);
    }

    private void pushAboutTicket(long tenantId, UUID ticketTaskId, UUID recipientId, String event,
            String title, String body, String targetTaskType, UUID targetTaskId, String idempotencyPrefix) {
        ObjectNode payload = PushOutboxWriter.payload(objectMapper, recipientId, event, title);
        if (body != null) {
            payload.put("body", body);
        }
        PushOutboxWriter.targetOwnedTask(payload, targetTaskType, targetTaskId);
        pushOutboxWriter.enqueue(tenantId, "task:" + ticketTaskId, payload, idempotencyPrefix + ":" + recipientId);
    }

    private String roomNoOf(long tenantId, Map<String, Object> ticket) {
        return queryFirstString(
                "SELECT room_no FROM rooms WHERE tenant_id = ? AND id = ?", tenantId, ticket.get("room_id"));
    }

    private String staffName(long tenantId, UUID staffId) {
        return queryFirstString(
                "SELECT name FROM staff_accounts WHERE tenant_id = ? AND id = ?", tenantId, staffId);
    }

    private String queryFirstString(String sql, Object... args) {
        List<String> values = jdbcTemplate.queryForList(sql, String.class, args);
        return values.isEmpty() ? null : values.get(0);
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        throw new IllegalStateException("預期時間型別，實際 " + (value == null ? "null" : value.getClass()));
    }
}
