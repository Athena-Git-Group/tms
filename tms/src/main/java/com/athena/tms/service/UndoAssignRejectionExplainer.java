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
