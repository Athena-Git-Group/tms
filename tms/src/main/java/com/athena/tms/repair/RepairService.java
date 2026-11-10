package com.athena.tms.repair;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.ForbiddenException;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.common.codes.StaffRole;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.entity.Task;
import com.athena.tms.service.AutomationRuleService;
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
     *
     * <p>公開供 {@link com.athena.tms.service.UnclaimedReminderScanJob} 共用——
     * 未接提醒推播的工務單標籤要用同一套中文對照，不另開第二份。
     */
    public static final Map<String, String> CATEGORY_LABELS = Map.of(
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
     * <p>急件判定（裁決 C）看原任務房間今天的訂單——今日入住或 VIP 的房間，
     * 工務太慢會直接影響住客入住，所以系統直接判定為急件，不是提示房務自行
     * 決定；判定結果與理由在這裡先算一次給報修人看，開單時 {@link #create} 會
     * 再算一次並真正寫入 repair_tickets／tasks。兩處都呼叫 {@link #judgeUrgent}，
     * 是為了讓同一間房在開單前與開單時判到同一個結果，不會因為各自維護一套
     * 規則而兩邊兜不起來。
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
                       t.state, rt.is_urgent, r.room_no, s.name AS reporter_name
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

        UUID roomId = UUID.fromString(String.valueOf(sourceTask.get("room_id")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("duplicate", duplicate);
        body.put("urgentJudgement", judgeUrgent(tenantId, roomId));
        return body;
    }

    /**
     * 急件判定（裁決 C）：系統依飯店設定規則判定，不是提示——房務不能更改
     * 判定結果，只能由主管事後呼叫 {@link #downgradeUrgency} 降級。目前唯一的
     * 判定維度是原任務房間今天是否有入住訂單、是否 VIP（{@code reservations_cache}），
     * 沒有命中規則時 {@code suggested=false}、{@code rule=null}，判定為一般件。
     */
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

    /** 查看工務單詳情（contracts readRepairTicket）。 */
    public Map<String, Object> read(UUID ticketTaskId) {
        long tenantId = TenantContext.require();
        return ticketView(tenantId, ticketTaskId);
    }

    /**
     * 列出同房的工務單（contracts listRepairTickets）：給原任務頁的工務單區使用。
     * 收錄該房間所有未撤回的單——撤回是 tasks.state=cancelled，報修人撤回後那張
     * 單就不該再出現在任務頁，所以這裡是唯一被排除的狀態；其餘六個狀態
     * （pending_review／rejected／pool／accepted／doing／done）全收，房務才看得到
     * 自己報的單現在走到哪一關。
     *
     * <p>欄位與 {@link #ticketView} 共用 {@link #summaryFromRow}，查詢的欄位別名
     * 刻意跟詳情那段一致，同一張單在列表與詳情不會出現兩套命名。契約沒有規定
     * 排序，取開單時間新到舊——任務頁要先看到最近報的那張。
     */
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

    /**
     * 主管將急件降級為一般件（contracts downgradeRepairTicketUrgency）：急件判定
     * 本身是系統算出的，房務開單時不能更改，只有主管可以事後用這個操作降級；
     * 不能反向把一般件升為急件，所以目前不是急件時回 409。只改 is_urgent／
     * urgent_downgrade_reason，urgent_rule 保留開單當下的原始判定理由供追溯；
     * 同一交易內把 tasks.urgent 同步改回 false（待認領排序看這欄）並寫
     * task_events（type=urgent_downgraded），事件本身不是狀態轉移，委派
     * {@link TaskEngine#recordUrgentDowngraded} 沿用 sopCheck 同一套「不靠 state
     * 轉移」的事件落地方式。
     */
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
     * 的單可以收回，等主管審核中（pending_review）還沒進池的單也可以撤回；
     * 認領後工務已經安排工作，不能再憑空消失。狀態轉移交給
     * {@link TaskEngine#withdrawFromPool}（只允許 pool／pending_review 轉
     * cancelled 的條件式更新）；轉移被拒而且這張單已有人認領時，把一般的狀態
     * 衝突換成契約規定的 already_claimed 與「已被工務認領」，App 才能直接告訴
     * 報修人原因。
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
     *
     * <p>完修三選一（contracts CompleteTaskRequest.resolutionType，省略視為 fixed）：
     * fixed 不另外動作（既有行為）；temporary 另建一張房號／類別都相同的追蹤單進工務
     * 待認領（{@link #createFollowUpTicket}）；cannot_fix 把該房 {@code rooms.hk_state}
     * 轉為 out_of_order（停用待確認）。
     */
    @Transactional
    public void resolveOnComplete(UUID ticketTaskId, List<UUID> photoIds, String resolutionNote, String resolutionType) {
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
        ObjectNode payload = PushOutboxWriter.payload(
                objectMapper, reporterId, "repair_completed", roomNo + " " + categoryLabel + "已修好");
        PushOutboxWriter.targetTask(payload, ticketTaskId);
        pushOutboxWriter.enqueue(tenantId, "task:" + ticketTaskId, payload,
                "repair_resolved:" + ticketTaskId + ":" + reporterId);

        String effectiveResolution = resolutionType == null ? "fixed" : resolutionType;
        if ("temporary".equals(effectiveResolution)) {
            createFollowUpTicket(tenantId, ticketTaskId, ticket);
        } else if ("cannot_fix".equals(effectiveResolution)) {
            jdbcTemplate.update(
                    "UPDATE rooms SET hk_state = 'out_of_order' WHERE tenant_id = ? AND id = ?",
                    tenantId, ticket.get("room_id"));
        }
    }

    /**
     * 暫時處理（resolutionType=temporary）自動建立的追蹤單：房號、類別都與原單相同，
     * 狀態固定 pool（進工務待認領，不經審核——追蹤單本身就是原單已審核過的延伸）、
     * {@code origin_ticket_task_id} 指回原單供追溯；報修人沿用原單報修人，維持同一個
     * 故障的追蹤脈絡。{@code tasks.source} 標 system——這張單不是任何人報修或交辦的，
     * 是系統因應「先暫時處理」自動補開的。
     */
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
                       rt.resolution_note, rt.is_urgent, rt.urgent_rule, rt.urgent_downgrade_reason,
                       rt.reject_reason, rt.verified_at, rt.origin_ticket_task_id,
                       t.state, r.room_no, s.name AS reporter_name,
                       v.name AS verifier_name, ot.ticket_no AS origin_ticket_no,
                       (SELECT count(*) FROM repair_photos p
                         WHERE p.tenant_id = rt.tenant_id AND p.ticket_task_id = rt.task_id
                           AND p.purpose = 'report') AS photo_count
                  FROM repair_tickets rt
                  JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                  JOIN rooms r ON r.tenant_id = rt.tenant_id AND r.id = rt.room_id
                  JOIN staff_accounts s ON s.tenant_id = rt.tenant_id AND s.id = rt.reporter_id
                  LEFT JOIN staff_accounts v ON v.tenant_id = rt.tenant_id AND v.id = rt.verified_by
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
        if (row.get("source_task_id") != null) {
            body.put("sourceTaskId", String.valueOf(row.get("source_task_id")));
        }
        String urgentRule = (String) row.get("urgent_rule");
        Map<String, Object> urgentJudgement = new LinkedHashMap<>();
        urgentJudgement.put("suggested", urgentRule != null);
        urgentJudgement.put("rule", urgentRule);
        body.put("urgentJudgement", urgentJudgement);
        body.put("urgentDowngradeReason", row.get("urgent_downgrade_reason"));
        body.put("rejectReason", row.get("reject_reason"));
        body.put("verifiedBy", row.get("verifier_name"));
        body.put("verifiedAt", row.get("verified_at") == null ? null : toOffsetDateTime(row.get("verified_at")).toString());
        body.put("originTicketNo", row.get("origin_ticket_no"));
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
        body.put("isUrgent", Boolean.TRUE.equals(row.get("is_urgent")));
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
     * 開立工務單（contracts createRepairTicket）：取號、建修繕任務、落 repair_tickets、
     * 綁定照片；中斷模式時原任務轉 interrupted（停住原因「等工務：<類別>」，已做時間由
     * TaskEngine 結算保留）並連結新單；急件時推播全體工務與主管。整段在同一個交易裡——
     * 契約要求任一步失敗時工務單不落地、原任務也不變，不能出現「單建了但原任務沒中斷」
     * 的中間態。
     *
     * <p>三種開單路徑擇一（contracts CreateRepairTicketRequest）：①帶 {@code sourceTaskId}
     * ——房號由原任務帶入，{@code tasks.source} 固定 staff（既有路徑，房務報修）；②③不帶
     * {@code sourceTaskId}，改帶 {@code roomId} 或 {@code isPublicArea}——房號由 {@code roomId}
     * 或公共區域佔位房決定，{@code tasks.source} 依呼叫者部門決定（工務自開＝staff、櫃檯交辦＝
     * desk，見 {@link #directOpenSource}）；指定 {@code assigneeId} 時直接 accepted 給該人
     * （櫃檯交辦進 pool 時額外標 {@code pool_source}=desk_forward，供 {@code listDeptPool}
     * 的來源篩選使用——工務自開沒有對應的 PoolSource 值，留白）。
     *
     * <p>急件（裁決 C）不接受請求參數：開單時系統依 {@link #judgeUrgent} 的規則自行判定，
     * 房務不能更改；判定結果同一交易內寫入 repair_tickets.is_urgent／urgent_rule，並同步
     * tasks.urgent（待認領池排序看這欄）。
     *
     * <p>開單去向（主管審核／退回／重送翻案）：{@code assigneeId} 有值時 accepted，略過
     * 工務待認領與審核；急件、或路徑②③（工務自開／櫃檯交辦）一律直接進 pool，不經審核
     * ——急件要工務馬上處理，自開／交辦的單也不是房務一線報修，不必多一層審核；只有路徑①
     * （房務報修）且非急件時先進 {@code pending_review}（等主管審核），主管用 {@link #approve}
     * 核准後才轉 pool，{@link #reject} 退回則轉 {@code rejected} 等報修人用 {@link #resubmit}
     * 補資料重送。
     */
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

        // 急件判定要在決定開單去向之前先算出來：路徑①（房務報修，sourceTaskId 有值）
        // 的一般件要先進 pending_review 等主管審核，急件則不論來源一律直接進 pool，
        // 兩者的差別就在這個判定結果，不能等建完任務才知道。
        Map<String, Object> urgentJudgement = judgeUrgent(tenantId, effectiveRoomId);
        boolean isUrgent = Boolean.TRUE.equals(urgentJudgement.get("suggested"));
        String urgentRule = (String) urgentJudgement.get("rule");

        String initialState;
        if (assigneeId != null) {
            initialState = "accepted";
        } else if (sourceTaskId != null && !isUrgent) {
            initialState = "pending_review";
        } else {
            initialState = "pool";
        }

        String ticketNo = nextTicketNo(tenantId);
        Task ticketTask = taskEngine.create(new TaskCreateCommand(
                tenantId, "repair", taskSource, effectiveRoomId, null, "engineering", initialState, assigneeId,
                categoryLabel + " 工務單", null, null, null, null, staffActor));
        UUID ticketTaskId = ticketTask.getId();

        if (isUrgent) {
            jdbcTemplate.update(
                    "UPDATE tasks SET urgent = true WHERE tenant_id = ? AND id = ?", tenantId, ticketTaskId);
        }
        if (poolSource != null && "pool".equals(initialState)) {
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
        return body;
    }

    /**
     * 不帶 {@code sourceTaskId} 的開單路徑（工務自開或櫃檯交辦）依呼叫者部門決定
     * {@code tasks.source}：工務＝staff（與房務報修同值，皆屬「員工自報」）、
     * 櫃檯＝desk。其他部門無從呼叫這個路徑，契約沒有定義對應語意，直接拒絕。
     */
    private static String directOpenSource(String dept) {
        return switch (dept) {
            case "engineering" -> "staff";
            case "frontdesk" -> "desk";
            default -> throw new ForbiddenException("只有工務或櫃檯可以不經過房務任務直接開立工務單");
        };
    }

    /**
     * 公共區域佔位房：與 {@link AutomationRuleService#PUBLIC_AREA_ROOM_NO}
     * 用同一個房號、同一套落地欄位（floor=0、room_type=public_area），兩邊各自建表是因為
     * 排程規則與工務直開是不同交易路徑；都用 {@code ON CONFLICT DO NOTHING} 搶著建，
     * 不會因為先後順序造成重複房號撞唯一索引。
     */
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

    /**
     * 工務自行把公共區域的工務單放回工務待認領（contracts releaseRepairTicketToPool）。
     * 只允許公共區域（房號＝公共區域佔位房）的單；客房工務單維持既有「只能暫停」，
     * 回 409 room_ticket_not_requeueable，避免與中斷／恢復機制混用。
     */
    @Transactional
    public Map<String, Object> releaseToPool(UUID ticketTaskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        Map<String, Object> ticket = requireTicket(tenantId, ticketTaskId);
        String roomNo = queryFirstString(
                "SELECT room_no FROM rooms WHERE tenant_id = ? AND id = ?", tenantId, ticket.get("room_id"));
        if (!AutomationRuleService.PUBLIC_AREA_ROOM_NO.equals(roomNo)) {
            throw new StateConflictException("room_ticket_not_requeueable", "客房工務單只能暫停，不能放回工務待認領");
        }
        taskEngine.releaseToPool(tenantId, ticketTaskId, Actor.staff(actor.role(), actor.staffId()));
        return ticketView(tenantId, ticketTaskId);
    }

    /**
     * 主管核准等審核中的一般件工務單（contracts approveRepairTicket）：pending_review→pool。
     * 只有主管能核准，否則房務報修的審核等於沒有把關；狀態轉移本身委派
     * {@link TaskEngine#approveRepairTicket}（條件式更新，單不在 pending_review 時回 409）。
     */
    @Transactional
    public Map<String, Object> approve(UUID ticketTaskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        requireManager(actor);
        requireTicket(tenantId, ticketTaskId);
        taskEngine.approveRepairTicket(tenantId, ticketTaskId, Actor.staff(actor.role(), actor.staffId()));
        return ticketView(tenantId, ticketTaskId);
    }

    /**
     * 主管退回等審核中的一般件工務單，須附原因（contracts rejectRepairTicket）：
     * pending_review→rejected。原因空白時整個請求拒絕，不讓報修人收到一張不知道哪裡
     * 該補的退回單。reject_reason 與狀態轉移在同一交易內落地，查單與查事件歷程會對得上。
     */
    @Transactional
    public Map<String, Object> reject(UUID ticketTaskId, String reason) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        requireManager(actor);
        requireTicket(tenantId, ticketTaskId);
        if (reason == null || reason.isBlank()) {
            throw new UnprocessableEntityException("reject_reason_required", "退回必須附原因");
        }
        taskEngine.rejectRepairTicket(tenantId, ticketTaskId, reason, Actor.staff(actor.role(), actor.staffId()));
        jdbcTemplate.update(
                "UPDATE repair_tickets SET reject_reason = ? WHERE tenant_id = ? AND task_id = ?",
                reason, tenantId, ticketTaskId);
        return ticketView(tenantId, ticketTaskId);
    }

    /**
     * 報修人補資料後重新送出被退回的工務單（contracts resubmitRepairTicket）：
     * rejected→pending_review。只有原報修人能送出——退回原因是主管對他說的，別人不能代他
     * 補；重送後清空 reject_reason，避免讀者以為上一輪的退回理由還適用於這次送出的內容。
     */
    @Transactional
    public Map<String, Object> resubmit(UUID ticketTaskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        Map<String, Object> ticket = requireTicket(tenantId, ticketTaskId);
        boolean isReporter =
                actor.staffId().toString().equalsIgnoreCase(String.valueOf(ticket.get("reporter_id")));
        if (!isReporter) {
            throw new ForbiddenException("只有報修人可以補資料再送出");
        }
        taskEngine.resubmitRepairTicket(tenantId, ticketTaskId, Actor.staff(actor.role(), actor.staffId()));
        jdbcTemplate.update(
                "UPDATE repair_tickets SET reject_reason = NULL WHERE tenant_id = ? AND task_id = ?",
                tenantId, ticketTaskId);
        return ticketView(tenantId, ticketTaskId);
    }

    /** 核准／退回限主管呼叫；不是主管時這張單的審核等於房務自己說了算。 */
    private void requireManager(CurrentActor actor) {
        if (!StaffRole.MANAGER.equals(actor.role())) {
            throw new ForbiddenException("只有主管可以核准或退回工務單");
        }
    }

    /**
     * 主管驗收通過已完修的工務單（contracts verifyRepairTicket）：state 仍是 done，
     * 不開新狀態值，只補 verified_by／verified_at。只有已完修（done）且尚未驗收
     * （verified_by 為 null）的單能被驗收；非 done 或已驗收過回 409 state_conflict。
     * state 轉移（實際上只是 event_seq／updated_at 推進，state 不變）委派
     * {@link TaskEngine#verifyRepairTicket}，verified_by／verified_at 的落地在同一交易內
     * 以 {@code verified_by IS NULL} 當二次保護，避免併發重複驗收。原型未描繪「驗收退回」，
     * 本輪不支援這個分支（上游缺口，見 truth-delta.md 行 304）。
     */
    @Transactional
    public Map<String, Object> verify(UUID ticketTaskId) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        requireManager(actor);
        Map<String, Object> ticket = requireTicket(tenantId, ticketTaskId);
        if (ticket.get("verified_by") != null) {
            throw new StateConflictException("已驗收，不能重複驗收");
        }
        taskEngine.verifyRepairTicket(tenantId, ticketTaskId, Actor.staff(actor.role(), actor.staffId()));
        int updated = jdbcTemplate.update(
                "UPDATE repair_tickets SET verified_by = ?, verified_at = ? "
                        + "WHERE tenant_id = ? AND task_id = ? AND verified_by IS NULL",
                actor.staffId(), OffsetDateTime.now(clock), tenantId, ticketTaskId);
        if (updated == 0) {
            throw new StateConflictException("已驗收，不能重複驗收");
        }
        return ticketView(tenantId, ticketTaskId);
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
            ObjectNode payload = PushOutboxWriter.payload(
                    objectMapper, recipient, "repair_urgent",
                    roomNo + " 急件報修（" + categoryLabel + " " + ticketNo + "）");
            PushOutboxWriter.targetPool(payload, "engineering", ticketTaskId);
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
