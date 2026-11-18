package com.athena.tms.service;

import com.athena.tms.common.codes.ErrorCode;
import com.athena.tms.common.error.StateConflictException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 櫃檯要處理一張待派單（派單、當場回答），送到時單已經不是待派單，就用這裡組出 409 {@code task_not_in_triage}。
 *
 * <p>回的是例外而不是直接丟出，呼叫的一方寫 {@code throw explainer.explain(...)}，讀程式的人一眼看得出這裡就結束了。
 * 例外的 context 說明這張單是怎麼離開待派單的，讓畫面說得出「剛剛被誰派走」這類提示，而不是只說失敗。
 */
@Component
public class TaskNotInTriageExplainer {
    private final JdbcTemplate jdbcTemplate;
    private final TaskRowLookup taskRowLookup;

    public TaskNotInTriageExplainer(JdbcTemplate jdbcTemplate, TaskRowLookup taskRowLookup) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRowLookup = taskRowLookup;
    }

    /**
     * 看的是最後一筆讓單離開待派單的事件：派單、當場回答或取消。
     * 取消只有住客自己收回另外說明，其餘的取消與找不到這類事件的單都歸為其他。
     */
    public StateConflictException explain(long tenantId, UUID taskId) {
        Map<String, Object> row = this.taskRowLookup.require(tenantId, taskId);
        Map<String, Object> exit = this.lastExitEvent(tenantId, taskId);
        String exitType = (String) exit.get("type");
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("resolution", resolution(exitType, (String) exit.get("reason")));
        context.put("state", row.get("state"));
        context.put("roomNo", row.get("room_no"));
        if ("triaged".equals(exitType) || "answered".equals(exitType)) {
            context.put("actorName", exit.get("actor_name"));
        }
        if ("triaged".equals(exitType)) {
            context.put("dept", exit.get("dept"));
        }
        return new StateConflictException(ErrorCode.TASK_NOT_IN_TRIAGE, "這張單已經不是待派單", context);
    }

    private static String resolution(String exitType, String reason) {
        if ("triaged".equals(exitType)) {
            return "assigned";
        }
        if ("answered".equals(exitType)) {
            return "answered";
        }
        if ("cancelled".equals(exitType) && "guest_withdrew".equals(reason)) {
            return "guest_withdrew";
        }
        return "other";
    }

    /** 找不到這類事件時回空的，讓結果歸為其他。 */
    private Map<String, Object> lastExitEvent(long tenantId, UUID taskId) {
        return this.jdbcTemplate.queryForList(
                """
                SELECT e.type, e.payload ->> 'dept' AS dept, e.payload ->> 'reason' AS reason, s.name AS actor_name
                  FROM task_events e
                  LEFT JOIN staff_accounts s ON s.tenant_id = e.tenant_id AND s.id = e.actor_id
                 WHERE e.tenant_id = ? AND e.task_id = ? AND e.type IN ('triaged', 'answered', 'cancelled')
                 ORDER BY e.seq DESC
                 LIMIT 1
                """,
                tenantId, taskId).stream().findFirst().orElse(Map.of());
    }
}
