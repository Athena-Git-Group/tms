package com.athena.tms.service;

import com.athena.tms.common.codes.ErrorCode;
import com.athena.tms.common.error.StateConflictException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 派出後復原沒有收回時，用這裡說明原因，組出 409 的例外。
 *
 * <p>回的是例外而不是直接丟出，呼叫的一方寫 {@code throw explainer.explain(...)}，讀程式的人一眼看得出這裡就結束了。
 *
 * <p>原因由下面的判斷表由上而下比對，第一列符合的就是答案。順序是先看單現在的樣子、再看時間：
 * 有負責人、住客收回這兩種是單已經變了樣子，排在前面；單還是剛派出的樣子（仍待認領、最新一筆事件就是那筆派單）
 * 卻沒收回，才是超過時間；都不是就歸為其他。
 */
@Component
public class UndoAssignRejectionExplainer {
    private static final List<Rejection> REJECTIONS = List.of(
            new Rejection(
                    UndoFacts::hasAssignee,
                    ErrorCode.UNDO_CLAIMED,
                    "這張單已經有人認領，不能復原",
                    facts -> context(
                            "assigneeName", facts.assigneeName(),
                            "dept", facts.dept(),
                            "roomNo", facts.roomNo())),
            new Rejection(
                    UndoFacts::guestWithdrew,
                    ErrorCode.UNDO_GUEST_WITHDREW,
                    "住客已經收回這張需求，不能復原",
                    facts -> context("roomNo", facts.roomNo())),
            new Rejection(
                    UndoFacts::stillJustAssigned,
                    ErrorCode.UNDO_EXPIRED,
                    "已經超過可以復原的時間",
                    facts -> context("roomNo", facts.roomNo(), "dept", facts.dept())),
            new Rejection(
                    facts -> true,
                    ErrorCode.UNDO_UNAVAILABLE,
                    "這張單已經不是剛派出的樣子，不能復原",
                    facts -> context("state", facts.state(), "roomNo", facts.roomNo())));

    private final JdbcTemplate jdbcTemplate;
    private final TaskRowLookup taskRowLookup;

    public UndoAssignRejectionExplainer(JdbcTemplate jdbcTemplate, TaskRowLookup taskRowLookup) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskRowLookup = taskRowLookup;
    }

    public StateConflictException explain(long tenantId, UUID taskId) {
        UndoFacts facts = new UndoFacts(tenantId, taskId, this.taskRowLookup.require(tenantId, taskId));
        Rejection rejection = REJECTIONS.stream()
                .filter(candidate -> candidate.when().test(facts))
                .findFirst()
                .orElseThrow();
        return new StateConflictException(rejection.code(), rejection.message(), rejection.context().apply(facts));
    }

    /** context 的鍵照傳進來的順序排，回給畫面的 JSON 也是這個順序。 */
    private static Map<String, Object> context(Object... keysAndValues) {
        Map<String, Object> context = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            context.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return context;
    }

    private record Rejection(
            Predicate<UndoFacts> when, String code, String message, Function<UndoFacts, Map<String, Object>> context) {
    }

    /**
     * 這張單現在的樣子。要另外查資料庫的兩件事（負責人姓名、最新一筆事件）等判斷表真的問到才查，
     * 前面的列已經符合時就不會多查。
     */
    private final class UndoFacts {
        private final long tenantId;
        private final UUID taskId;
        private final Map<String, Object> row;

        private UndoFacts(long tenantId, UUID taskId, Map<String, Object> row) {
            this.tenantId = tenantId;
            this.taskId = taskId;
            this.row = row;
        }

        boolean hasAssignee() {
            return this.row.get("assignee_id") != null;
        }

        boolean guestWithdrew() {
            return "cancelled".equals(this.state()) && "guest_withdrew".equals(this.row.get("cancel_reason"));
        }

        /** 仍待認領，而且派單之後沒有再發生任何事：最新一筆事件就是那筆派單。 */
        boolean stillJustAssigned() {
            if (!"pool".equals(this.state())) {
                return false;
            }
            List<String> types = UndoAssignRejectionExplainer.this.jdbcTemplate.queryForList(
                    "SELECT type FROM task_events WHERE tenant_id = ? AND task_id = ? AND seq = ?",
                    String.class, this.tenantId, this.taskId, this.row.get("event_seq"));
            return types.equals(List.of("triaged"));
        }

        String assigneeName() {
            return UndoAssignRejectionExplainer.this.jdbcTemplate.queryForList(
                    "SELECT name FROM staff_accounts WHERE tenant_id = ? AND id = ?",
                    String.class, this.tenantId, this.row.get("assignee_id"))
                    .stream()
                    .findFirst()
                    .orElse(null);
        }

        String state() {
            return (String) this.row.get("state");
        }

        Object dept() {
            return this.row.get("dept");
        }

        Object roomNo() {
            return this.row.get("room_no");
        }
    }
}
