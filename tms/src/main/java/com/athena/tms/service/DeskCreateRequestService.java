package com.athena.tms.service;

import com.athena.tms.common.codes.PoolSource;
import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.ApiError;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.dto.desk.DeskCreateRequestItem;
import com.athena.tms.dto.desk.DeskCreateRequestOther;
import com.athena.tms.dto.desk.DeskCreateRequestsRequest;
import com.athena.tms.dto.desk.DeskCreateRequestsResult;
import com.athena.tms.dto.desk.DeskMenuKey;
import com.athena.tms.dto.desk.DeskRoom;
import com.athena.tms.dto.desk.DeskRoomGuest;
import com.athena.tms.dto.desk.DeskTask;
import com.athena.tms.entity.Task;
import com.athena.tms.repair.RepairTicketDraft;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class DeskCreateRequestService {
    private static final String OTHER_REPAIR_CATEGORY = "other";
    private static final int OTHER_TEXT_MAX_LENGTH = 60;
    private static final Set<String> OTHER_DEPTS = Set.of("housekeeping", "engineering");

    private final JdbcTemplate jdbcTemplate;
    private final TaskEngine taskEngine;
    private final DeskBoardService deskBoardService;
    private final DeskCreateRequestIdempotency idempotency;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DeskCreateRequestService(
            JdbcTemplate jdbcTemplate,
            TaskEngine taskEngine,
            DeskBoardService deskBoardService,
            DeskCreateRequestIdempotency idempotency,
            ObjectMapper objectMapper,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskEngine = taskEngine;
        this.deskBoardService = deskBoardService;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public DeskCreateRequestIdempotency.Outcome createRequests(
            String idempotencyKey, DeskCreateRequestsRequest request) {
        DeskBoardService.requireIdempotencyKey(idempotencyKey);
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        return this.idempotency.runOnce(tenantId, idempotencyKey, () -> this.create(tenantId, actor, request));
    }

    private DeskCreateRequestsResult create(long tenantId, CurrentActor actor, DeskCreateRequestsRequest request) {
        validate(request);
        UUID roomId = this.requireGuestRoom(tenantId, request.roomNo());
        Actor creator = Actor.staff(actor.role(), actor.staffId());

        List<UUID> created = new ArrayList<>();
        List<DeskCreateRequestItem> items = request.items() == null ? List.of() : request.items();
        items.stream()
                .sorted(Comparator.comparing(item -> DeskMenuKey.fromKey(item.menuKey()).orElseThrow()))
                .forEach(item -> {
                    DeskMenuKey key = DeskMenuKey.fromKey(item.menuKey()).orElseThrow();
                    int quantity = key.recordedQuantity(item.quantity());
                    String note = TaskNote.menuItem(key.key(), quantity).toJson(this.objectMapper);
                    created.add(this.open(tenantId, roomId, creator, key.dept(), key.type(), key.title(quantity),
                            note, key.repairCategory()));
                });
        DeskCreateRequestOther other = request.other();
        if (other != null) {
            String text = other.text().strip();
            boolean engineering = "engineering".equals(other.dept());
            created.add(this.open(tenantId, roomId, creator, other.dept(), engineering ? "repair" : "other", text,
                    null, OTHER_REPAIR_CATEGORY));
        }
        List<DeskTask> cards = created.stream().map(id -> this.deskBoardService.card(tenantId, id)).toList();
        return new DeskCreateRequestsResult(OffsetDateTime.now(this.clock), cards);
    }

    private static void validate(DeskCreateRequestsRequest request) {
        List<ApiError.Detail> details = new ArrayList<>();
        if (request.roomNo() == null || request.roomNo().isBlank()) {
            details.add(new ApiError.Detail("roomNo", "房號空白"));
        }
        List<DeskCreateRequestItem> items = request.items() == null ? List.of() : request.items();
        Set<DeskMenuKey> seen = EnumSet.noneOf(DeskMenuKey.class);
        for (int i = 0; i < items.size(); i++) {
            DeskCreateRequestItem item = items.get(i);
            String field = "items[" + i + "]";
            if (item == null) {
                details.add(new ApiError.Detail(field, "品項是空的"));
                continue;
            }
            Optional<DeskMenuKey> key = DeskMenuKey.fromKey(item.menuKey());
            if (key.isEmpty()) {
                details.add(new ApiError.Detail(field + ".menuKey", "不在代開的 12 個品項裡：" + item.menuKey()));
                continue;
            }
            if (!seen.add(key.get())) {
                details.add(new ApiError.Detail(field + ".menuKey", "同一個品項出現兩次：" + item.menuKey()));
            }
            Integer quantity = item.quantity();
            if (quantity == null || quantity < 1 || quantity > key.get().maxQuantity()) {
                details.add(new ApiError.Detail(field + ".quantity",
                        "數量要在 1 到 " + key.get().maxQuantity() + " 之間，收到：" + quantity));
            }
        }
        DeskCreateRequestOther other = request.other();
        if (other != null) {
            String text = other.text() == null ? "" : other.text().strip();
            if (text.isEmpty()) {
                details.add(new ApiError.Detail("other.text", "其他需求去掉前後空白後是空的"));
            } else if (text.codePointCount(0, text.length()) > OTHER_TEXT_MAX_LENGTH) {
                details.add(new ApiError.Detail("other.text", "其他需求超過 " + OTHER_TEXT_MAX_LENGTH + " 字"));
            }
            if (other.dept() == null || !OTHER_DEPTS.contains(other.dept())) {
                details.add(new ApiError.Detail("other.dept", "其他需求只能給 housekeeping 或 engineering，收到：" + other.dept()));
            }
        }
        if (items.isEmpty() && other == null) {
            details.add(new ApiError.Detail("items", "沒有任何品項也沒有其他需求"));
        }
        if (!details.isEmpty()) {
            throw new UnprocessableEntityException("代開單的內容不合規定", details);
        }
    }

    public List<DeskRoom> listRooms() {
        long tenantId = TenantContext.require();
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        return this.jdbcTemplate.query(
                """
                SELECT r.room_no, guest.guest_name, guest.vip, guest.checkin_at, guest.checkout_at
                  FROM rooms r
                  LEFT JOIN LATERAL (
                        SELECT %s
                          FROM reservations_cache rc
                         WHERE rc.tenant_id = r.tenant_id AND rc.room_id = r.id AND %s
                         LIMIT 1) guest ON true
                 WHERE r.tenant_id = ? AND r.room_no <> ?
                 ORDER BY r.floor, r.room_no
                """.formatted(InHouseGuest.COLUMNS, InHouseGuest.STAYING_AT),
                (rs, rowNum) -> {
                    OffsetDateTime checkinAt = rs.getObject("checkin_at", OffsetDateTime.class);
                    DeskRoomGuest guest = checkinAt == null ? null : new DeskRoomGuest(
                            rs.getString("guest_name"),
                            rs.getBoolean("vip"),
                            this.localDate(checkinAt),
                            this.localDate(rs.getObject("checkout_at", OffsetDateTime.class)));
                    return new DeskRoom(rs.getString("room_no"), guest != null, guest);
                },
                now, now, tenantId, AutomationRuleService.PUBLIC_AREA_ROOM_NO);
    }

    private LocalDate localDate(OffsetDateTime at) {
        return at.atZoneSameInstant(this.clock.getZone()).toLocalDate();
    }

    private UUID open(
            long tenantId, UUID roomId, Actor creator, String dept, String type, String title, String note,
            String repairCategory) {
        boolean engineering = "engineering".equals(dept);
        RepairTicketDraft draft = engineering
                ? new RepairTicketDraft(repairCategory, title, creator.id(), null, null)
                : null;
        Task task = this.taskEngine.create(new TaskCreateCommand(
                tenantId, type, "desk", roomId, null, dept, "pool", null, title, note, null, null, null, creator,
                false, draft));
        this.jdbcTemplate.update(
                "UPDATE tasks SET pool_source = ? WHERE tenant_id = ? AND id = ?", PoolSource.DESK_FORWARD, tenantId, task.getId());
        return task.getId();
    }

    private UUID requireGuestRoom(long tenantId, String roomNo) {
        return this.jdbcTemplate.queryForList(
                        "SELECT id FROM rooms WHERE tenant_id = ? AND room_no = ? AND room_no <> ?",
                        UUID.class, tenantId, roomNo, AutomationRuleService.PUBLIC_AREA_ROOM_NO)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("查無房號: " + roomNo, Map.of("roomNo", roomNo)));
    }
}
