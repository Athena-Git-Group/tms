package com.athena.tms.repair;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.ForbiddenException;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.entity.Task;
import com.athena.tms.service.PushOutboxWriter;
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

/**
 * 工務單業務：兩段式照片、開單前檢查、開立、查看、補照片與撤回。工務單本身是一筆修繕任務
 * （type=repair、dept=engineering、source=staff），狀態與負責人一律看 tasks，
 * 所以狀態轉移委派 {@link TaskEngine}；這裡只負責工務單專屬的延伸資料——取號、
 * 照片綁定、原任務中斷與急件推播。
 */
@Service
public class RepairService {

    /**
     * 類別權威代碼 → 中文名稱。中斷模式的停住原因（「等工務：水電」）與工務單
     * 任務標題是給現場員工看的，必須用中文；對照表收錄 contracts RepairCategory
     * enum 的全部值，collection 查不到時退回原代碼而不是炸掉——類別值域由
     * request 驗證把關，這裡不重複承擔。
     */
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "air_conditioning", "冷氣",
            "plumbing_electrical", "水電",
            "furniture_equipment", "家具／設備",
            "door_lock", "門鎖",
            "other", "其他");

    private static final DateTimeFormatter CHECKIN_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final TaskEngine taskEngine;
    private final PushOutboxWriter pushOutboxWriter;
    private final TaskRowLookup taskRowLookup;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RepairService(
            JdbcTemplate jdbcTemplate,
            TaskEngine taskEngine,
            PushOutboxWriter pushOutboxWriter,
            TaskRowLookup taskRowLookup,
            ObjectMapper objectMapper,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskEngine = taskEngine;
        this.pushOutboxWriter = pushOutboxWriter;
        this.taskRowLookup = taskRowLookup;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 照片採兩段式（contracts createRepairPhoto）：先在這裡取得照片 id，內容
     * 另外用 {@link #markUploaded} 上傳。拆成兩段是為了讓 App 在上傳失敗時
     * 能拿同一個 id 重試失敗的那張，不必整批重來；還沒綁單的 pending 列由
     * 保留期限排程清理，不會永久殘留。
     */
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

    /**
     * 上傳一張照片的內容（contracts uploadRepairPhotoContent）。照片本體不入庫
     * （沿用 lost_items.photo_ref 的外存慣例），資料庫只記引用與 uploaded 狀態。
     * 契約規定這個操作冪等——App 斷線重送同一個 id 不該變成錯誤，所以用無條件
     * 覆寫而不是「只允許 pending→uploaded」的條件式轉移；uploaded_at 用
     * COALESCE 保留第一次成功的時間，重送不會改寫。
     */
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

    /**
     * 開單前檢查（contracts precheckRepairTicket）：報修人選定類別時呼叫，
     * 不建立工務單。
     *
     * <p>重複提示要保證同一個故障不會被兩位員工開成兩張單。做法是查同房、
     * 同類別、還沒結案（不是 done 也不是 cancelled）的工務單，有就回傳摘要，
     * 讓報修人改走「補照片到既有單」；同時把看到提示的員工記下來，之後他補
     * 照片時才認得出他和這張單的關係。少了這筆紀錄，他回頭補照片會被當成
     * 無關人員擋下，提示就變成一條死路。
     *
     * <p>急件建議看原任務房間今天的訂單——今日入住或 VIP 的房間，
     * 工務太慢會直接影響住客入住，所以提前建議設為急件，提示文字組成
     * 「這間『VIP · 15:00 入住』，影響入住的話建議設為急件」讓報修人自行決定。
     */
    @Transactional
    public Map<String, Object> precheck(UUID sourceTaskId, String category) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        Map<String, Object> sourceTask = taskRowLookup.require(tenantId, sourceTaskId);

        Map<String, Object> duplicate = null;
        List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(
                """
                SELECT rt.task_id, rt.ticket_no, rt.category, rt.created_at, rt.reporter_id,
                       t.state, t.urgent, r.room_no, s.name AS reporter_name
                  FROM repair_tickets rt
                  JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                  JOIN rooms r ON r.tenant_id = rt.tenant_id AND r.id = rt.room_id
                  JOIN staff_accounts s ON s.tenant_id = rt.tenant_id AND s.id = rt.reporter_id
                 WHERE rt.tenant_id = ? AND rt.room_id = ? AND rt.category = ?
                   AND t.state NOT IN ('done', 'cancelled')
                 ORDER BY rt.created_at DESC
                 LIMIT 1
                """,
                tenantId, sourceTask.get("room_id"), category);
        if (!duplicates.isEmpty()) {
            Map<String, Object> row = duplicates.get(0);
            UUID ticketTaskId = UUID.fromString(String.valueOf(row.get("task_id")));
            duplicate = summaryFromRow(ticketTaskId, row);

            // 報修人補照片的權限走 repair_tickets.reporter_id，這張表只收其他員工；
            // 主鍵含 staff_id，同一位員工反覆選同類別也只會留一列。
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

        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("suggested", false);
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
                tenantId, sourceTask.get("room_id"), clock.getZone().getId(), today);
        if (!reservations.isEmpty()) {
            Map<String, Object> reservation = reservations.get(0);
            boolean vip = Boolean.TRUE.equals(reservation.get("vip"));
            String checkinTime = toOffsetDateTime(reservation.get("checkin_at"))
                    .atZoneSameInstant(clock.getZone())
                    .format(CHECKIN_TIME);
            String label = (vip ? "VIP · " : "") + checkinTime + " 入住";
            suggestion.put("suggested", true);
            suggestion.put("message", "這間『" + label + "』，影響入住的話建議設為急件");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("duplicate", duplicate);
        body.put("urgentSuggestion", suggestion);
        return body;
    }

    /** 查看工務單詳情（contracts readRepairTicket）。 */
    public Map<String, Object> read(UUID ticketTaskId) {
        long tenantId = TenantContext.require();
        return ticketView(tenantId, ticketTaskId);
    }

    /**
     * 補照片到既有工務單（contracts appendRepairTicketPhotos）。可以補的人只有
     * 報修人與在開單前檢查時觸發過這張單重複提示的員工；放寬到任何人，照片就
     * 可能被與這個故障無關的人塞進來，工務到場會對不上現況。照片綁定沿用開單
     * 的規則：只認領還沒綁單的照片列，ticket_task_id 一旦綁定就不可改。
     */
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

    /**
     * 撤回工務單（contracts withdrawRepairTicket）：工務認領前報錯房、報錯類別
     * 的單可以收回，認領後工務已經安排工作，不能再憑空消失。狀態轉移交給
     * {@link TaskEngine#withdrawFromPool}（只允許 pool 轉 cancelled 的條件式更新）；
     * 轉移被拒而且這張單已有人認領時，把一般的狀態衝突換成契約規定的
     * already_claimed 與「已被工務認領」，App 才能直接告訴報修人原因。
     */
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

    /**
     * 工務完修結案時，保證報修那一側同步知道「修好了」：推播報修人，並把因這張單
     * 而中斷的原任務停住原因改成「工務已修好：<類別>」。工務單任務本身的 doing→done
     * 轉移已由 TaskEngine 處理，這裡只補工務單的延伸資料——完修照片綁定與處理說明
     * （工務判斷不需維修時，結案理由也記在處理說明裡）。少了這段連動，報修人只能
     * 自己反覆去查單，原任務上也仍顯示「等工務」，員工不知道可以回去把房間做完。
     *
     * <p>原任務不自動恢復，何時回去做由負責人自己決定，所以只改停住原因、不動狀態；
     * 用條件式更新限定 interrupted，原任務已被恢復或退池時一列也不改。推播以
     * 「單＋收件人」組冪等鍵，請求重試時同一位報修人不會收到兩次。
     */
    @Transactional
    public void resolveOnComplete(UUID ticketTaskId, List<UUID> photoIds, String resolutionNote) {
        long tenantId = TenantContext.require();
        Map<String, Object> ticket = requireTicket(tenantId, ticketTaskId);
        bindUnclaimedPhotos(tenantId, ticketTaskId, photoIds);
        if (resolutionNote != null) {
            jdbcTemplate.update(
                    "UPDATE repair_tickets SET resolution_note = ? WHERE tenant_id = ? AND task_id = ?",
                    resolutionNote, tenantId, ticketTaskId);
        }

        String categoryLabel = CATEGORY_LABELS.getOrDefault(
                String.valueOf(ticket.get("category")), String.valueOf(ticket.get("category")));

        Object sourceTaskId = ticket.get("source_task_id");
        if (sourceTaskId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE tasks SET hold_reason = ?, updated_at = now()
                     WHERE tenant_id = ? AND id = ? AND state = 'interrupted'
                    """,
                    "工務已修好：" + categoryLabel, tenantId, sourceTaskId);
        }

        String roomNo = queryFirstString(
                "SELECT room_no FROM rooms WHERE tenant_id = ? AND id = ?", tenantId, ticket.get("room_id"));
        UUID reporterId = UUID.fromString(String.valueOf(ticket.get("reporter_id")));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("recipientStaffId", reporterId.toString());
        payload.put("event", "repair_resolved");
        payload.put("title", roomNo + " " + categoryLabel + "已修好");
        ObjectNode target = payload.putObject("target");
        target.put("kind", "task");
        target.put("taskId", ticketTaskId.toString());
        pushOutboxWriter.enqueue(tenantId, "task:" + ticketTaskId, payload,
                "repair_resolved:" + ticketTaskId + ":" + reporterId);
    }

    /** 這個 task id 必須是一張工務單；一般任務沒有 repair_tickets 延伸列，直接回 404。 */
    private Map<String, Object> requireTicket(long tenantId, UUID ticketTaskId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM repair_tickets WHERE tenant_id = ? AND task_id = ?", tenantId, ticketTaskId);
        if (rows.isEmpty()) {
            throw new NotFoundException("查無工務單: " + ticketTaskId);
        }
        return rows.get(0);
    }

    /** 組 contracts RepairTicket 回應：工務單摘要加上報修照片張數與說明。 */
    private Map<String, Object> ticketView(long tenantId, UUID ticketTaskId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT rt.ticket_no, rt.category, rt.description, rt.created_at, rt.source_task_id,
                       rt.resolution_note, t.state, t.urgent, r.room_no, s.name AS reporter_name,
                       (SELECT count(*) FROM repair_photos p
                         WHERE p.tenant_id = rt.tenant_id AND p.ticket_task_id = rt.task_id
                           AND p.purpose = 'report') AS photo_count
                  FROM repair_tickets rt
                  JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                  JOIN rooms r ON r.tenant_id = rt.tenant_id AND r.id = rt.room_id
                  JOIN staff_accounts s ON s.tenant_id = rt.tenant_id AND s.id = rt.reporter_id
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
        if (row.get("source_task_id") != null) {
            body.put("sourceTaskId", String.valueOf(row.get("source_task_id")));
        }
        return body;
    }

    /**
     * 組 contracts RepairTicketSummary 的共同欄位。開單前檢查的重複提示與工務單
     * 詳情查的是不同 SQL，但欄位別名刻意取成一致（ticket_no、room_no 等），
     * 摘要組裝集中在這裡，兩邊的欄位命名與格式就不會各走各的。
     */
    private Map<String, Object> summaryFromRow(UUID ticketTaskId, Map<String, Object> row) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", ticketTaskId.toString());
        body.put("ticketNo", row.get("ticket_no"));
        body.put("roomNo", row.get("room_no"));
        body.put("category", row.get("category"));
        body.put("urgent", Boolean.TRUE.equals(row.get("urgent")));
        body.put("state", row.get("state"));
        body.put("reporterName", row.get("reporter_name"));
        body.put("createdAt", toOffsetDateTime(row.get("created_at")).toString());
        return body;
    }

    /**
     * 把照片綁到工務單：只認領還沒綁單的照片列，ticket_task_id 一旦綁定就不可改
     * （dbml 註記）。引用到別張單的照片時這裡一列也不會動，開單與補照片共用
     * 同一條規則。
     */
    private void bindUnclaimedPhotos(long tenantId, UUID ticketTaskId, List<UUID> photoIds) {
        for (UUID photoId : photoIds) {
            jdbcTemplate.update(
                    "UPDATE repair_photos SET ticket_task_id = ? WHERE tenant_id = ? AND id = ? AND ticket_task_id IS NULL",
                    ticketTaskId, tenantId, photoId);
        }
    }

    /**
     * 開立工務單（contracts createRepairTicket）：取號、建修繕任務（送出直接進
     * 工務待接池，不經主管審核）、落 repair_tickets、綁定照片；中斷模式時原任務
     * 轉 interrupted（停住原因「等工務：<類別>」，已做時間由 TaskEngine 結算保留）
     * 並連結新單；急件時推播全體工務與主管。整段在同一個交易裡——契約要求
     * 任一步失敗時工務單不落地、原任務也不變，不能出現「單建了但原任務沒中斷」
     * 的中間態。
     */
    @Transactional
    public Map<String, Object> create(
            UUID sourceTaskId,
            String category,
            String description,
            List<UUID> photoIds,
            boolean urgent,
            boolean interruptSourceTask) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        Actor staffActor = Actor.staff(actor.role(), actor.staffId());
        requireUploadedPhotos(tenantId, photoIds);
        Map<String, Object> sourceTask = taskRowLookup.require(tenantId, sourceTaskId);
        UUID roomId = UUID.fromString(String.valueOf(sourceTask.get("room_id")));
        String categoryLabel = CATEGORY_LABELS.getOrDefault(category, category);

        String ticketNo = nextTicketNo(tenantId);
        Task ticketTask = taskEngine.create(new TaskCreateCommand(
                tenantId, "repair", "staff", roomId, null, "engineering", "pool", null,
                categoryLabel + " 工務單", null, null, null, null, staffActor));
        UUID ticketTaskId = ticketTask.getId();
        // urgent 不在 TaskCreateCommand 的欄位裡（它是工務單這個 plan 才加的
        // tasks 欄位），建完再補一筆 UPDATE，仍在同一個交易內。
        if (urgent) {
            jdbcTemplate.update(
                    "UPDATE tasks SET urgent = true WHERE tenant_id = ? AND id = ?", tenantId, ticketTaskId);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbcTemplate.update(
                """
                INSERT INTO repair_tickets
                    (task_id, tenant_id, ticket_no, room_id, category, description,
                     reporter_id, source_task_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ticketTaskId, tenantId, ticketNo, roomId, category, description,
                actor.staffId(), sourceTaskId, now);

        bindUnclaimedPhotos(tenantId, ticketTaskId, photoIds);

        if (interruptSourceTask) {
            taskEngine.interrupt(tenantId, sourceTaskId, "等工務：" + categoryLabel, staffActor);
            jdbcTemplate.update(
                    "UPDATE tasks SET linked_repair_task_id = ? WHERE tenant_id = ? AND id = ?",
                    ticketTaskId, tenantId, sourceTaskId);
        }

        String roomNo = String.valueOf(sourceTask.get("room_no"));
        if (urgent) {
            notifyUrgent(tenantId, ticketTaskId, ticketNo, roomNo, categoryLabel);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", ticketTaskId.toString());
        body.put("ticketNo", ticketNo);
        body.put("roomNo", roomNo);
        body.put("category", category);
        body.put("urgent", urgent);
        body.put("state", "pool");
        body.put("reporterName", staffName(tenantId, actor.staffId()));
        body.put("createdAt", now.toString());
        body.put("photoCount", photoIds.size());
        body.put("sourceTaskId", sourceTaskId.toString());
        return body;
    }

    /**
     * 開單的照片規則（contracts createRepairTicket）：1 到 3 張，而且每張都已上傳。
     * 在取號與建任務之前先驗完，驗不過就整個請求拒絕，工務單不落地、原任務不變。
     * 張數之外還要查 repair_photos 的 status——App 端有照片上傳失敗時，照片列
     * 還停在 pending，若放行開單，工務到場會拿到缺圖的單，對不上現場故障。
     */
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

    /**
     * 取下一個單號。計數器用 INSERT … ON CONFLICT 一句完成「第一張單時建列、
     * 之後遞增」，和建單同一個交易——建單失敗回滾時號碼一併回滾，單號不會跳號。
     */
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

    /**
     * 急件推播該租戶所有在職工務員工與主管（contracts createRepairTicket：
     * urgent=true 推播該租戶所有工務與主管），去處標亮這張單。收件人以
     * 「單＋收件人」組冪等鍵，請求重試時 ON CONFLICT 會略過已寫入的列，
     * 同一個人不會收到兩次。
     */
    private void notifyUrgent(long tenantId, UUID ticketTaskId, String ticketNo, String roomNo, String categoryLabel) {
        List<UUID> recipients = jdbcTemplate.queryForList(
                """
                SELECT id FROM staff_accounts
                 WHERE tenant_id = ? AND active = true AND (dept = 'engineering' OR role = 'manager')
                """,
                UUID.class, tenantId);
        for (UUID recipient : recipients) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("recipientStaffId", recipient.toString());
            payload.put("event", "repair_urgent");
            payload.put("title", roomNo + " 急件報修（" + categoryLabel + " " + ticketNo + "）");
            ObjectNode target = payload.putObject("target");
            target.put("kind", "pool");
            target.put("dept", "engineering");
            target.put("highlightTaskId", ticketTaskId.toString());
            pushOutboxWriter.enqueue(tenantId, "task:" + ticketTaskId, payload,
                    "repair_urgent:" + ticketTaskId + ":" + recipient);
        }
    }

    private String staffName(long tenantId, UUID staffId) {
        return queryFirstString(
                "SELECT name FROM staff_accounts WHERE tenant_id = ? AND id = ?", tenantId, staffId);
    }

    /** 查單一字串欄位的第一列；查無資料回 null，不丟例外。 */
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
