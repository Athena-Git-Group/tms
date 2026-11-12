package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.ManagerKind;
import com.athena.tms.common.SqlTimestamps;
import com.athena.tms.common.error.ForbiddenException;
import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.ManagerQueues;
import com.athena.tms.dto.ManagerQueues.InspectionQueueItem;
import com.athena.tms.dto.ManagerQueues.VerifyQueueItem;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 主管待辦查詢（contracts getManagerQueues）：待查房、待驗收兩類清單與格子數字的唯一來源。
 * 兩條判定公式、排序、排除本人清的房都由這裡承接。
 *
 * <p>回哪幾類依主管種類（{@link ManagerKind}）：房務主管兩類都有，工務主管不查房，只有待驗收。
 * 兩類都是查詢期由 tasks 與 repair_tickets 算出來的，不存欄位也不存計數器，格子數字就是
 * 清單的長度；存起來就會和清單各算各的，格子寫 2、點進去卻 3 張。
 *
 * <p>兩類清單共用同一套排序與標記：依開始等主管的時間（waitingSince）由舊到新，不帶任何
 * 日期條件，昨天的單排在今天的前面；同一時刻的單依房號遞增，再依單號遞增。「是不是今天」
 * 由注入的 {@link Clock} 時區判定，與首頁 todayTasks 的「今天」同一把尺；系統沒有每家
 * 飯店各自的時區設定，App 拿不到那把尺，所以不交給 App 比對。
 */
@Service
public class ManagerQueueService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ManagerQueueService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /**
     * 查看呼叫者的待辦。只開放主管，其他角色回 403。工務主管不查房，不查待查房那一類；
     * 回應要省略哪些鍵由 {@link ManagerQueues#of} 決定。
     */
    @Transactional(readOnly = true)
    public ManagerQueues queues() {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        ManagerKind kind = ManagerKind.of(actor)
                .orElseThrow(() -> new ForbiddenException("只有主管可以查看主管待辦"));
        LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());

        List<InspectionQueueItem> inspection =
                kind.inspects() ? inspectionQueue(tenantId, actor.staffId(), today) : null;
        return ManagerQueues.of(inspection, verifyQueue(tenantId, today));
    }

    /**
     * 待查房＝打掃單停在 done 且負責人不是呼叫者本人。主管不能查自己清的房，所以自己清的
     * 房不出現在他的清單與數字裡，另一位主管看得到。免查房的單完成時直接落在 inspected、
     * 不會停在 done，所以不必另外排除。開始等主管的時間是最近一次完成的時間（done_at）。
     */
    private List<InspectionQueueItem> inspectionQueue(long tenantId, UUID callerStaffId, LocalDate today) {
        return jdbcTemplate.queryForList(
                """
                SELECT t.id, t.title, t.done_at AS waiting_since, t.redo_count, r.room_no,
                       s.name AS cleaner_name
                  FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                  JOIN staff_accounts s ON s.tenant_id = t.tenant_id AND s.id = t.assignee_id
                 WHERE t.tenant_id = ? AND t.type = 'cleaning' AND t.state = 'done'
                   AND t.assignee_id IS DISTINCT FROM ?
                 ORDER BY t.done_at, r.room_no, t.id
                """,
                tenantId, callerStaffId).stream()
                .map(row -> {
                    Base base = base(row, today);
                    return new InspectionQueueItem(
                            base.taskId(), base.roomNo(), base.publicArea(), base.title(),
                            base.waitingSince(), base.today(),
                            (String) row.get("cleaner_name"),
                            ((Number) row.get("redo_count")).intValue());
                })
                .toList();
    }

    /**
     * 待驗收＝修繕任務停在 done 且完修結果不是不需處理，含暫時處理、無法修復與定期保養的單，
     * 也含主管本人完修的單；房務主管與工務主管拿到同一份。驗收通過的單已離開 done，
     * 所以不必再看驗收時間。完修結果用 IS DISTINCT FROM 比，完修結果欄位出現之前就完修的
     * 舊單是 NULL，寫成不等於會把它們漏掉。不需處理的單完成當下就結案，正常不會停在 done，
     * 這個條件是讓它即使停在 done 也不進待驗收。
     *
     * <p>開始等主管的時間是最近一次完修的時間（done_at）；驗收不通過後重修再完修會重寫它，
     * 所以重修的單排在後面。同一時刻時公共區域的單排在有房號的單後面；它們在資料側掛在
     * 佔位房上，排序時先把佔位房號換成 NULL，才不會依佔位房號的字元順序插進房號之間。
     * wasVerificationFailed 看這張單有沒有被判過驗收不通過，清單列用它標「重修後再驗」。
     *
     * <p>來源（origin）由 tasks.source 推導：系統排程建的單（source=system）是定期保養
     * （scheduled），其餘都是有人報修（reported）。資料側目前沒有 origin 欄位，推導規則
     * 與工務單詳情同一套，兩邊才不會對同一張單說出兩種來源。
     */
    private List<VerifyQueueItem> verifyQueue(long tenantId, LocalDate today) {
        return jdbcTemplate.queryForList(
                """
                SELECT t.id, t.title, t.source, t.done_at AS waiting_since, r.room_no,
                       rt.ticket_no, rt.category, rt.description, rt.resolution_type,
                       rt.last_verify_failed_at IS NOT NULL AS was_verification_failed,
                       rep.name AS reporter_name, asg.name AS assignee_name
                  FROM tasks t
                  JOIN repair_tickets rt ON rt.tenant_id = t.tenant_id AND rt.task_id = t.id
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                  LEFT JOIN staff_accounts rep ON rep.tenant_id = rt.tenant_id AND rep.id = rt.reporter_id
                  LEFT JOIN staff_accounts asg ON asg.tenant_id = t.tenant_id AND asg.id = t.assignee_id
                 WHERE t.tenant_id = ? AND t.type = 'repair' AND t.state = 'done'
                   AND rt.resolution_type IS DISTINCT FROM 'no_action_needed'
                 ORDER BY t.done_at, NULLIF(r.room_no, ?) NULLS LAST, rt.ticket_no
                """,
                tenantId, AutomationRuleService.PUBLIC_AREA_ROOM_NO).stream()
                .map(row -> {
                    Base base = base(row, today);
                    return new VerifyQueueItem(
                            base.taskId(), base.roomNo(), base.publicArea(), base.title(),
                            base.waitingSince(), base.today(),
                            (String) row.get("ticket_no"),
                            (String) row.get("category"),
                            (String) row.get("description"),
                            (String) row.get("reporter_name"),
                            (String) row.get("assignee_name"),
                            "system".equals(row.get("source")) ? "scheduled" : "reported",
                            (String) row.get("resolution_type"),
                            (Boolean) row.get("was_verification_failed"));
                })
                .toList();
    }

    /** 清單每一列共用的欄位（contracts QueueItemBase），由同一支算法組出來。 */
    private record Base(
            UUID taskId,
            String roomNo,
            boolean publicArea,
            String title,
            OffsetDateTime waitingSince,
            boolean today) {}

    /**
     * 從查詢列組出共用欄位。公共區域的判定沿用既有的佔位房口徑：資料側 tasks.room_id 不可
     * 為空，公共區域的單一律掛在房號等於 {@link AutomationRuleService#PUBLIC_AREA_ROOM_NO}
     * 的佔位房，所以契約要的「公共區域單沒有房號」在這層把 roomNo 換成 null，那個佔位
     * 房號不會漏到 App。waitingSince 帶後端 Clock 的時區位移，App 顯示時直接用這個位移
     * 取時與分，不轉成手機時區。
     */
    private Base base(Map<String, Object> row, LocalDate today) {
        boolean publicArea = AutomationRuleService.isPublicAreaRoomNo(row.get("room_no"));
        OffsetDateTime raw = SqlTimestamps.toOffsetDateTime(row.get("waiting_since"));
        OffsetDateTime waitingSince = raw == null ? null : raw.atZoneSameInstant(clock.getZone()).toOffsetDateTime();
        return new Base(
                (UUID) row.get("id"),
                publicArea ? null : (String) row.get("room_no"),
                publicArea,
                (String) row.get("title"),
                waitingSince,
                waitingSince != null && waitingSince.toLocalDate().equals(today));
    }
}
