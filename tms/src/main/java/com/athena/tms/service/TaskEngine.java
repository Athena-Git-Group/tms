package com.athena.tms.service;

import com.athena.tms.common.UuidV7;
import com.athena.tms.common.codes.TaskCancelReason;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.TaskEventRepository;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.entity.Task;
import com.athena.tms.entity.TaskEvent;
import com.athena.tms.service.OutboxWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class TaskEngine {
    /**
     * 任務可以直接建立起點的狀態。已在進行或已結束的狀態不收進這個集合：開單這個操作
     * 本身不該讓一張單一出生就是做到一半或已經做完，那些狀態只能由後續的轉移走到。
     */
    private static final Set<String> CREATABLE_STATES = Set.of("triage", "pool", "accepted");
    /**
     * 中斷與完成共用的 SET 片段：把目前段落（segment_started_at 到傳入時刻）的秒數
     * 併進 worked_sec，並清空段落起點。佔一個參數位（段落結束時刻）。
     */
    private static final String CLOSE_SEGMENT_SET = "worked_sec = worked_sec + COALESCE(CAST(EXTRACT(EPOCH FROM (? - segment_started_at)) AS int), 0),\n       segment_started_at = NULL";
    private final JdbcTemplate jdbcTemplate;
    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TaskEngine(JdbcTemplate jdbcTemplate, TaskRepository taskRepository, TaskEventRepository taskEventRepository, OutboxWriter outboxWriter, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public Task create(TaskCreateCommand command) {
        boolean assigned;
        if (!CREATABLE_STATES.contains(command.initialState())) {
            throw new UnprocessableEntityException("任務起點狀態只能是 triage/pool/accepted，收到: " + command.initialState());
        }
        if ("guest".equals(command.source()) && command.reservationId() == null) {
            throw new UnprocessableEntityException("guest 來源任務必須綁定訂單（reservation_id）");
        }
        if ("cleaning".equals(command.type()) && command.workDate() == null) {
            throw new UnprocessableEntityException("打掃任務必填 work_date（E-09 一房一天約束的依據）");
        }
        assigned = command.assigneeId() != null;
        if (assigned != "accepted".equals(command.initialState())) {
            throw new UnprocessableEntityException("accepted 起點（排房發佈）必須同時帶 assignee；triage/pool 起點不得帶");
        }
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        Task task = new Task();
        task.setId(UuidV7.generate());
        task.setTenantId(command.tenantId());
        task.setType(command.type());
        task.setSource(command.source());
        task.setRoomId(command.roomId());
        task.setReservationId(command.reservationId());
        task.setDept(command.dept());
        task.setState(command.initialState());
        task.setAssigneeId(command.assigneeId());
        task.setTitle(command.title());
        task.setNote(command.note());
        task.setEstMin(command.estMin());
        task.setPlanId(command.planId());
        task.setWorkDate(command.workDate());
        task.setUrgent(command.urgent());
        task.setEventSeq(assigned ? 2 : 1);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task = (Task)this.taskRepository.saveAndFlush(task);
        this.recordEvent(command.tenantId(), task.getId(), 1, "created", command.actor(), this.createdSnapshot(command));
        if (assigned) {
            this.recordEvent(command.tenantId(), task.getId(), 2, "assigned", command.actor(), TaskEngine.payload("assignee_id", command.assigneeId()));
        }
        return task;
    }

    @Transactional
    public int assignFromTriage(long tenantId, UUID taskId, String dept, Actor actor) {
        int seq = this.guard("指派部門", "UPDATE tasks\n   SET state = 'pool', dept = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'triage'\nRETURNING event_seq\n", dept, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "triaged", actor, TaskEngine.payload("dept", dept));
    }

    @Transactional
    public int answer(long tenantId, UUID taskId, String answer, Actor actor) {
        int seq = this.guard("回覆", "UPDATE tasks\n   SET state = 'done', note = ?, done_at = now(), event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'triage'\nRETURNING event_seq\n", answer, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "answered", actor, TaskEngine.payload("answer", answer));
    }

    @Transactional
    public int claim(long tenantId, UUID taskId, String claimerDept, Actor claimer) {
        if (claimer.id() == null) {
            throw new UnprocessableEntityException("認領者必須是具名員工");
        }
        int seq = this.guard("認領", "UPDATE tasks\n   SET state = 'accepted', assignee_id = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'pool' AND dept = ?\nRETURNING event_seq\n", claimer.id(), tenantId, taskId, claimerDept);
        return this.recordEvent(tenantId, taskId, seq, "claimed", claimer, TaskEngine.payload("assignee_id", claimer.id()));
    }

    /**
     * 開工：待開始（accepted）與本人延後（deferred 且 deferred_by_self）的任務都能進入
     * 進行中（doing）。started_at 只記第一次開工；每次進 doing 都重開一段
     * segment_started_at；離開 deferred 時清空 hold_reason。
     * 時刻用注入的 {@link Clock}（測試以可調時鐘覆蓋），不用資料庫 now()。
     */
    @Transactional
    public int start(long tenantId, UUID taskId, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("開始任務必須是具名員工");
        }
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        // 免查與否在「開工當下」依租戶開關鎖定到 tasks.inspection_waived：之後主管改設定
        // 不影響這張單（只有打掃任務有查房概念，其他種類一律 false）。
        boolean waiverEnabled = "true".equals(this.tenantConfigText(tenantId, "inspection_waived_enabled"));
        int seq = this.guard("開始", "UPDATE tasks\n   SET state = 'doing', started_at = COALESCE(started_at, ?), segment_started_at = ?,\n       inspection_waived = (type = 'cleaning' AND ?),\n       hold_reason = NULL, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ?\n   AND (state = 'accepted' OR (state = 'deferred' AND deferred_by_self)) AND assignee_id = ?\nRETURNING event_seq\n", now, now, waiverEnabled, tenantId, taskId, actor.id());
        return this.recordEvent(tenantId, taskId, seq, "started", actor, null);
    }

    /** 本人恢復：interrupted→doing，重開一段 segment_started_at 並清空停住原因。 */
    @Transactional
    public int resume(long tenantId, UUID taskId, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("恢復任務必須是具名員工");
        }
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        int seq = this.guard("恢復", "UPDATE tasks\n   SET state = 'doing', segment_started_at = ?, hold_reason = NULL,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'interrupted' AND assignee_id = ?\nRETURNING event_seq\n", now, tenantId, taskId, actor.id());
        return this.recordEvent(tenantId, taskId, seq, "resumed", actor, null);
    }

    /**
     * 本人完成：doing→done。把最後這段（segment_started_at 到現在）的秒數併進
     * worked_sec 並清空段落起點，實際工時就等於各段 doing 時間的總和、不含中斷期間。
     * 打掃任務還沒勾也沒跳過的 SOP 項一律記為跳過（不擋完成但留痕），
     * 並把跳過項的名稱寫進 completed 事件的 payload。
     */
    @Transactional
    public int complete(long tenantId, UUID taskId, Actor actor) {
        return this.complete(tenantId, taskId, actor, null, false);
    }

    /**
     * 修繕任務的完成，在 {@link #complete(long, UUID, Actor)} 之上多兩件事。
     * {@code extraPayload} 的欄位會併進 completed 事件的 payload，修繕任務用它帶完修結果
     * resolutionType。{@code closeRepair} 為 true 時任務一步落在 closed（已結案），不經 done
     * 等主管驗收。工務判斷不需處理的單用它：完成事件本身就是結案事件，不另記驗收。
     */
    @Transactional
    public int complete(long tenantId, UUID taskId, Actor actor, Map<String, Object> extraPayload, boolean closeRepair) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("完成任務必須是具名員工");
        }
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        // 開工時鎖定免查（inspection_waived）的打掃任務直接免查結案：不經「待查房」（done），
        // 一步落在 inspected、查房結果記 waived；不需處理的工務單一步落在 closed；
        // 其他任務維持 done 等查房、驗收或送達。
        int seq = this.guard("完成", "UPDATE tasks\n   SET state = CASE WHEN type = 'cleaning' AND inspection_waived THEN 'inspected'\n                    WHEN type = 'repair' AND ? THEN 'closed' ELSE 'done' END,\n       inspection = CASE WHEN type = 'cleaning' AND inspection_waived THEN 'waived' ELSE inspection END,\n       " + CLOSE_SEGMENT_SET + ", done_at = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'doing' AND assignee_id = ?\nRETURNING event_seq\n", closeRepair, now, now, tenantId, taskId, actor.id());
        List<String> skippedLabels = this.markUncheckedSopAsSkipped(tenantId, taskId, now);
        Map<String, Object> payload = skippedLabels == null ? null : TaskEngine.payload("skipped_items", skippedLabels);
        if (extraPayload != null && !extraPayload.isEmpty()) {
            payload = payload == null ? new LinkedHashMap<>() : payload;
            payload.putAll(extraPayload);
        }
        int completedSeq = this.recordEvent(tenantId, taskId, seq, "completed", actor, payload);
        if (this.isWaivedClosed(tenantId, taskId)) {
            this.writebackRoomStatusIfEnabled(tenantId, taskId, actor);
        }
        return completedSeq;
    }

    /** 這張單是否剛以免查結案收尾（state=inspected 且 inspection=waived）。 */
    private boolean isWaivedClosed(long tenantId, UUID taskId) {
        Map<String, Object> row = this.jdbcTemplate.queryForMap(
                "SELECT state, inspection FROM tasks WHERE tenant_id = ? AND id = ?", tenantId, taskId);
        return "inspected".equals(row.get("state")) && "waived".equals(row.get("inspection"));
    }

    /**
     * 完成打掃任務時，把還沒勾也沒跳過的 SOP 項補記為跳過（task_sop_checks 投影，
     * 不記 checked_by——跳過是完成動作的推定結果，不是誰勾的），並回傳完成當下
     * 所有跳過項的名稱供 completed 事件留痕。非打掃任務沒有 SOP 清單，回傳 null。
     */
    private List<String> markUncheckedSopAsSkipped(long tenantId, UUID taskId, OffsetDateTime now) {
        String type = (String)this.jdbcTemplate.queryForObject("SELECT type FROM tasks WHERE tenant_id = ? AND id = ?", String.class, tenantId, taskId);
        if (!"cleaning".equals(type)) {
            return null;
        }
        List<Map<String, Object>> missing = this.jdbcTemplate.queryForList("SELECT s.item_key, s.label\n  FROM sop_items s\n WHERE s.tenant_id = ? AND s.applies_to = 'cleaning' AND s.active\n   AND NOT EXISTS (SELECT 1 FROM task_sop_checks c\n                    WHERE c.tenant_id = s.tenant_id AND c.task_id = ? AND c.item_key = s.item_key)\n ORDER BY s.sort_order\n", tenantId, taskId);
        for (Map<String, Object> item : missing) {
            this.jdbcTemplate.update("INSERT INTO task_sop_checks (id, tenant_id, task_id, item_key, label, checked, skipped, checked_by, checked_at)\nVALUES (?, ?, ?, ?, ?, false, true, NULL, ?)\nON CONFLICT (tenant_id, task_id, item_key) DO NOTHING\n", UuidV7.generate(), tenantId, taskId, item.get("item_key"), item.get("label"), now);
        }
        return this.jdbcTemplate.queryForList("SELECT label FROM task_sop_checks WHERE tenant_id = ? AND task_id = ? AND skipped", String.class, tenantId, taskId);
    }

    @Transactional
    public int passInspection(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard("查房通過", "UPDATE tasks\n   SET state = 'inspected', inspection = 'passed', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'done'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "inspection_passed", actor, null);
    }

    /**
     * 合格結案（免查結案或查房通過）後把房況回寫 PMS：寫一筆 outbox kind=pms_status
     * （payload 形狀對應 contracts shared.yaml 的 PmsRoomStatusWriteback：roomNo＋status，
     * 免查結案與查房通過都回寫 inspected（已整理），不分住客房與可售），並在任務事件
     * 補一筆 pms_writeback 留痕。「回寫房態」開關（pms.writeback.room_status）關閉、或
     * PMS 為手動匯入模式（pms.provider=manual，沒有回寫對象）時照樣合格結案但不回寫。
     * 冪等鍵綁「任務＋事件序號」，同一次結案重放不會寫出第二筆。
     */
    @Transactional
    public void writebackRoomStatusIfEnabled(long tenantId, UUID taskId, Actor actor) {
        if ("false".equals(this.tenantConfigText(tenantId, "pms.writeback.room_status"))
                || "manual".equals(this.tenantConfigText(tenantId, "pms.provider"))) {
            return;
        }
        Map<String, Object> room = this.jdbcTemplate.queryForMap("SELECT r.id AS room_id, r.room_no\n  FROM tasks t\n  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id\n WHERE t.tenant_id = ? AND t.id = ?\n", tenantId, taskId);
        String roomNo = String.valueOf(room.get("room_no"));
        Integer seq = (Integer)this.jdbcTemplate.queryForObject("UPDATE tasks SET event_seq = event_seq + 1, updated_at = now() WHERE tenant_id = ? AND id = ? RETURNING event_seq", Integer.class, tenantId, taskId);
        String payload = this.toJson(TaskEngine.payload("roomNo", roomNo, "status", "inspected"));
        this.jdbcTemplate.update("INSERT INTO outbox (tenant_id, aggregate_key, kind, payload, idempotency_key, status, attempts, created_at)\nVALUES (?, ?, 'pms_status', ?::jsonb, ?, 'pending', 0, ?)\nON CONFLICT (tenant_id, kind, idempotency_key) DO NOTHING\n", tenantId, "room:" + tenantId + ":" + String.valueOf(room.get("room_id")), payload, "pms_status:task:" + String.valueOf(taskId) + ":seq:" + seq, OffsetDateTime.now(this.clock));
        this.recordEvent(tenantId, taskId, seq, "pms_writeback", actor, TaskEngine.payload("roomNo", roomNo, "status", "inspected"));
    }

    /** 讀租戶設定的文字值（jsonb 任意型別一律以文字呈現），沒有這個鍵時回 null。 */
    private String tenantConfigText(long tenantId, String key) {
        List<String> values = this.jdbcTemplate.queryForList(
                "SELECT value #>> '{}' FROM tenant_config WHERE tenant_id = ? AND key = ?", String.class, tenantId, key);
        return values.isEmpty() ? null : values.get(0);
    }

    @Transactional
    public int failInspection(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("查房退回", "UPDATE tasks\n   SET state = 'accepted', started_at = NULL, done_at = NULL,\n       redo_count = redo_count + 1, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'done'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "inspection_failed", actor, TaskEngine.payload("note", note));
    }

    /**
     * 抽查退回：主管抽查已結案的打掃任務、任一項不合格時，把任務退回原負責人重做。
     * 和 {@link #failInspection} 的差別在起點與重做內容：一般查房退回的起點是 done
     * （還沒結案），抽查退回的起點是 inspected（已結案），而且重做要從頭來——已做
     * 時間直接歸零（worked_sec=0、清空進行中段落的起點），不是只退狀態。前提條件
     * 直接寫在 UPDATE 的 WHERE 裡，任務不在 inspected 時一列也不改並回 409，
     * 同一張單重送抽查退回不會重複加 redo_count。負責人（assignee_id）不動，
     * 原清房者接手重做。
     */
    @Transactional
    public int failSpotCheck(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("抽查退回", """
                UPDATE tasks
                   SET state = 'accepted', started_at = NULL, done_at = NULL,
                       worked_sec = 0, segment_started_at = NULL,
                       inspection = 'failed', redo_count = redo_count + 1,
                       event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'inspected'
                RETURNING event_seq
                """, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "inspection_failed", actor, TaskEngine.payload("note", note));
    }

    /**
     * 查房退回後原清房者已下班或請假時，把單放回部門待認領。{@code pool_source} 記為
     * inspection_return，讓待認領清單與交接畫面看得出這張單是查房退回來的，而不是新單。
     */
    @Transactional
    public int returnToPool(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("退回待接池", "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, pool_source = 'inspection_return', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'accepted'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, TaskEngine.payload("note", note));
    }

    /**
     * 本人中斷：doing→interrupted。把目前這段（segment_started_at 到現在）的秒數
     * 併進 worked_sec 並清空段落起點，已做時間就停在中斷當下；停住原因記進
     * hold_reason。時刻用注入的 {@link Clock}，和已做時間的讀取共用同一個時間來源。
     */
    @Transactional
    public int interrupt(long tenantId, UUID taskId, String reason, Actor actor) {
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        int seq = this.guard("中斷", "UPDATE tasks\n   SET state = 'interrupted',\n       " + CLOSE_SEGMENT_SET + ", hold_reason = ?,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'doing'\nRETURNING event_seq\n", now, reason, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "interrupted", actor, TaskEngine.payload("reason", reason, "by_self", true));
    }

    /**
     * 附交代退池（contracts tasksRequeue）：interrupted 或 accepted → pool。
     * 中斷單由主管收攏退池；還沒開工的單（accepted）則是原負責人交接時
     * 自己附交代退池（例如換人接手前留下「浴室洗好了，床還沒鋪」），
     * 兩條路徑共用同一個轉移與 returned_to_pool 事件。
     */
    @Transactional
    public int requeueToPool(long tenantId, UUID taskId, String note, Actor actor) {
        int seq = this.guard("重新排入待接池", "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, pool_source = 'manager_requeue',\n       priority_notify = true, requeue_note = ?, hold_reason = NULL,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state IN ('interrupted', 'accepted')\nRETURNING event_seq\n", note, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, TaskEngine.payload("note", note));
    }

    /**
     * 把待接池的任務延後。具名員工當呼叫者時任務會掛到他名下，之後本人開工
     * 的負責人核對才過得了；系統當呼叫者時任務維持沒有負責人。
     */
    @Transactional
    public int defer(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard("暫緩", "UPDATE tasks\n   SET state = 'deferred', assignee_id = COALESCE(assignee_id, ?), event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'pool'\nRETURNING event_seq\n", actor.id(), tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "deferred", actor, null);
    }

    /**
     * 負責人延後自己名下還沒開工的任務。任務保留負責人並記下原因，deferred_by_self
     * 標成 true。少了這個標記，班末退池與主管延後的隔日回池會把兩種延後混在一起處理，
     * 本人延後的單就會被錯誤地送回待接池。
     */
    @Transactional
    public int deferBySelf(long tenantId, UUID taskId, String reason, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("本人延後必須是具名員工");
        }
        int seq = this.guard("延後", "UPDATE tasks\n   SET state = 'deferred', deferred_by_self = true, hold_reason = ?,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'accepted' AND assignee_id = ?\nRETURNING event_seq\n", reason, tenantId, taskId, actor.id());
        return this.recordEvent(tenantId, taskId, seq, "deferred", actor, TaskEngine.payload("reason", reason, "by_self", true));
    }

    @Transactional
    public int undefer(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard("解除暫緩", "UPDATE tasks\n   SET state = 'pool', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'deferred'\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, null);
    }

    /**
     * 主管改派：還沒開工（accepted）的任務換負責人。前提條件寫在 UPDATE 的 WHERE 裡，
     * 任務不在 accepted 時一列也不改並回 409。
     */
    @Transactional
    public int reassign(long tenantId, UUID taskId, UUID newAssigneeId, Actor actor) {
        if (newAssigneeId == null) {
            throw new UnprocessableEntityException("改派必須指定新負責人（assigneeId）");
        }
        int seq = this.guard("改派", "UPDATE tasks\n   SET assignee_id = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state = 'accepted'\nRETURNING event_seq\n", newAssigneeId, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "reassigned", actor, TaskEngine.payload("assignee_id", newAssigneeId));
    }

    /**
     * 工務自行把公共區域的工務單放回工務待認領（contracts releaseRepairTicketToPool）：
     * accepted／doing → pool，assignee 清空；是否只限公共區域由 {@link
     * com.athena.tms.repair.RepairService#releaseToPool} 事先擋下，這裡只管狀態轉移。
     * doing 時目前這段的秒數併進 worked_sec 並清空段落起點，和其他「進行中轉非進行中」
     * 的轉移共用同一套已做時間結算；pool_source 清空——這是負責人自己放回，不屬於
     * PoolSource enum 既有任何一種「被動退池」情境。
     */
    @Transactional
    public int releaseToPool(long tenantId, UUID taskId, Actor actor) {
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        int seq = this.guard(
                "放回工務待認領",
                "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, pool_source = NULL,\n       "
                        + CLOSE_SEGMENT_SET
                        + ",\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state IN ('accepted', 'doing')\nRETURNING event_seq\n",
                now, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, null);
    }

    /**
     * 請假連鎖的退池：請假員工名下未完成的任務（accepted/doing/interrupted/deferred）
     * 回到部門待接池，標請假退回並優先通知；進行中的單先把目前段落的秒數併進
     * worked_sec，接手的人看得到已做時間。
     */
    @Transactional
    public int returnToPoolForLeave(long tenantId, UUID taskId, Actor actor) {
        OffsetDateTime now = OffsetDateTime.now(this.clock);
        int seq = this.guard("請假退池", "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, pool_source = 'leave_return',\n       priority_notify = true, " + CLOSE_SEGMENT_SET + ",\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ?\n   AND state IN ('accepted', 'doing', 'interrupted', 'deferred')\nRETURNING event_seq\n", now, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "returned_to_pool", actor, TaskEngine.payload("reason", "leave_return"));
    }

    @Transactional
    public int cancelByGuest(long tenantId, UUID taskId) {
        int seq = this.guard("收回", "UPDATE tasks\n   SET state = 'cancelled', cancel_reason = 'guest_withdrew', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state IN ('triage', 'pool')\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "cancelled", Actor.guest(), TaskEngine.payload("reason", "guest_withdrew"));
    }

    @Transactional
    public int cancel(long tenantId, UUID taskId, String reason, Actor actor) {
        if (reason == null || !TaskCancelReason.ALL.contains(reason)) {
            throw new UnprocessableEntityException("取消必附原因 enum（" + String.join((CharSequence)"/", TaskCancelReason.ALL) + "），收到: " + reason);
        }
        int seq = this.guard("取消", "UPDATE tasks\n   SET state = 'cancelled', cancel_reason = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ?\n   AND state IN ('triage', 'pool', 'accepted', 'doing', 'deferred', 'interrupted')\nRETURNING event_seq\n", reason, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "cancelled", actor, TaskEngine.payload("reason", reason));
    }

    /**
     * 報修人在工務認領前撤回工務單：只有 pool（待認領）的單能轉 cancelled，取消原因
     * 固定記 staff_withdrew。前提條件直接寫在 UPDATE 的 WHERE 裡（state = 'pool'），
     * 條件不符就一列也不改並丟 409；已被認領的單若也能撤回，工務手上的工作會憑空
     * 消失，所以 accepted／doing 等認領後狀態不在允許範圍內。cancel_reason 與事件
     * payload 的 reason 同值，查任務列與查事件歷程得到同一個答案。
     */
    @Transactional
    public int withdrawFromPool(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard(
                "撤回",
                """
                UPDATE tasks
                   SET state = 'cancelled', cancel_reason = 'staff_withdrew', event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'pool'
                RETURNING event_seq
                """,
                tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "cancelled", actor, TaskEngine.payload("reason", "staff_withdrew"));
    }

    @Transactional
    public int rate(long tenantId, UUID taskId, int rating) {
        if (rating < 1 || rating > 5) {
            throw new UnprocessableEntityException("評分必須是 1–5 的整數，收到: " + rating);
        }
        int seq = this.guard("評分", "UPDATE tasks\n   SET rating = ?, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND state IN ('done', 'inspected') AND rating IS NULL\nRETURNING event_seq\n", rating, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "rated", Actor.guest(), TaskEngine.payload("rating", rating));
    }

    @Transactional
    public int sopCheck(long tenantId, UUID taskId, String itemKey, String label, boolean checked, boolean skipped, Actor actor) {
        if (actor.id() == null) {
            throw new UnprocessableEntityException("SOP 勾記必須是具名員工");
        }
        int seq = this.guard("SOP 勾記", "UPDATE tasks\n   SET event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND assignee_id = ?\nRETURNING event_seq\n", tenantId, taskId, actor.id());
        // 投影列與事件同一交易落地：同一項重勾以 (tenant_id, task_id, item_key) 冪等覆寫最新勾記。
        this.jdbcTemplate.update("INSERT INTO task_sop_checks (id, tenant_id, task_id, item_key, label, checked, skipped, checked_by, checked_at)\nVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)\nON CONFLICT (tenant_id, task_id, item_key)\nDO UPDATE SET label = EXCLUDED.label, checked = EXCLUDED.checked, skipped = EXCLUDED.skipped,\n              checked_by = EXCLUDED.checked_by, checked_at = EXCLUDED.checked_at\n", UuidV7.generate(), tenantId, taskId, itemKey, label, checked, skipped, actor.id(), OffsetDateTime.now(this.clock));
        return this.recordEvent(tenantId, taskId, seq, "sop_checked", actor, TaskEngine.payload("item_key", itemKey, "label", label, "checked", checked, "skipped", skipped));
    }

    /**
     * 主管將急件降級為一般件（contracts downgradeRepairTicketUrgency）。急件判定
     * 不是任務狀態轉移，is_urgent／urgent_downgrade_reason 的實際改寫在
     * {@code RepairService}（repair_tickets 延伸資料），這裡只負責和
     * {@link #sopCheck} 一樣「不靠 state 轉移」的事件落地：遞增 event_seq、
     * 寫 task_events（type=urgent_downgraded，payload.downgradeReason 帶原因）。
     */
    @Transactional
    public int recordUrgentDowngraded(long tenantId, UUID taskId, String downgradeReason, Actor actor) {
        int seq = this.guard("急件降級", "UPDATE tasks\n   SET event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ?\nRETURNING event_seq\n", tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "urgent_downgraded", actor, TaskEngine.payload("downgradeReason", downgradeReason));
    }

    /**
     * 主管驗收通過已完修的工務單（contracts verifyRepairTicket）：done→closed，留一筆
     * repair_verified 事件。單不是 done 時一列也不改，回 409；兩位主管同時送出時，
     * 這句 UPDATE 只會讓先到的那一次成立。是否限主管呼叫、驗收人與驗收時間的落地由
     * {@link com.athena.tms.repair.RepairService#verify} 負責。
     */
    @Transactional
    public int verifyRepairTicket(long tenantId, UUID taskId, Actor actor) {
        int seq = this.guard(
                "驗收",
                "UPDATE tasks\n   SET state = 'closed', event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND type = 'repair' AND state = 'done'\nRETURNING event_seq\n",
                tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "repair_verified", actor, null);
    }

    /**
     * 主管判定已完修的工務單驗收不通過（contracts verifyRepairTicket 的 failed）。與通過共用同一道
     * state='done' 條件，先到的那一次成立，後到的回 409。兩條去處都清空完修時間，開工時間、累計工時
     * 與重做次數不動：
     * <ul>
     *   <li>原處理人接得了：done→accepted，回到他名下待開始，負責人不變。</li>
     *   <li>原處理人接不了（請假或已下班）：done→pool，清空負責人，進池來源記 verification_return，
     *       退池原因記「驗收不通過 · 原處理人已下班或請假」，由工務待認領重新認領。</li>
     * </ul>
     * 事件 payload 帶原因、去處（assignee 或 pool）與原處理人，重放時才還原得出這次退回給了誰。
     */
    @Transactional
    public int failRepairVerification(long tenantId, UUID taskId, Actor actor, String reason, UUID assigneeId, boolean toPool) {
        String sql = toPool
                ? "UPDATE tasks\n   SET state = 'pool', assignee_id = NULL, pool_source = 'verification_return',\n       return_reason = '驗收不通過 · 原處理人已下班或請假', done_at = NULL,\n       event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND type = 'repair' AND state = 'done'\nRETURNING event_seq\n"
                : "UPDATE tasks\n   SET state = 'accepted', done_at = NULL, event_seq = event_seq + 1, updated_at = now()\n WHERE tenant_id = ? AND id = ? AND type = 'repair' AND state = 'done'\nRETURNING event_seq\n";
        int seq = this.guard("驗收不通過", sql, tenantId, taskId);
        return this.recordEvent(tenantId, taskId, seq, "repair_verification_failed", actor,
                TaskEngine.payload("reason", reason, "destination", toPool ? "pool" : "assignee", "assigneeId", assigneeId));
    }

    private int guard(String operation, String sql, Object ... params) {
        try {
            Integer seq = (Integer)this.jdbcTemplate.queryForObject(sql, Integer.class, params);
            if (seq == null) {
                throw new IllegalStateException("RETURNING event_seq 不應為 null");
            }
            return seq;
        }
        catch (EmptyResultDataAccessException zeroRows) {
            throw new StateConflictException("任務目前狀態不允許「" + operation + "」");
        }
    }

    private int recordEvent(long tenantId, UUID taskId, int seq, String type, Actor actor, Map<String, Object> payload) {
        TaskEvent event = new TaskEvent();
        event.setTenantId(tenantId);
        event.setTaskId(taskId);
        event.setSeq(seq);
        event.setType(type);
        event.setActorRole(actor.role());
        event.setActorId(actor.id());
        event.setPayload(this.toJson(payload));
        // 事件時間用注入的 Clock：工時斷言要拿 started/interrupted/resumed/completed
        // 的事件間隔重算各段 doing 時間，事件時間和 tasks 欄位必須同一個時間來源。
        event.setCreatedAt(OffsetDateTime.now(this.clock));
        event = (TaskEvent)this.taskEventRepository.save(event);
        this.outboxWriter.enqueueTaskEvent(event);
        return seq;
    }

    private Map<String, Object> createdSnapshot(TaskCreateCommand command) {
        return TaskEngine.payload("type", command.type(), "source", command.source(), "room_id", command.roomId(), "reservation_id", command.reservationId(), "dept", command.dept(), "state", command.initialState(), "title", command.title(), "note", command.note(), "est_min", command.estMin(), "plan_id", command.planId(), "work_date", command.workDate());
    }

    private static Map<String, Object> payload(Object ... keyValues) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] == null) continue;
            map.put((String)keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return this.objectMapper.writeValueAsString(payload);
        }
        catch (JacksonException e) {
            throw new IllegalStateException("事件 payload 序列化失敗", e);
        }
    }
}
