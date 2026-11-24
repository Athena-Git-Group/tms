package com.athena.bddsupport;

import com.athena.bddsupport.BackendChannel.CreatedStaff;

import java.time.OffsetDateTime;

public record GuestSeed(
        Situation situation, String title, String type, OffsetDateTime createdAt,
        CreatedStaff assignee, OffsetDateTime acceptedAt, OffsetDateTime startedAt, String answer) {

    public GuestSeed {
        if (situation == null || title == null || type == null || createdAt == null) {
            throw new IllegalArgumentException("狀況、標題、類型與送出時間都要給");
        }
        if (answer != null && situation != Situation.ANSWERED) {
            throw new IllegalArgumentException("只有已回答的需求有櫃檯回覆，「" + situation.term() + "」不能給");
        }
    }

    String dept() {
        if (situation.dept != null) {
            return situation.dept;
        }
        if (assignee != null) {
            return assignee.dept();
        }
        return switch (type) {
            case "other" -> "frontdesk";
            case "repair" -> "engineering";
            default -> "housekeeping";
        };
    }

    public enum Situation {

        TRIAGE("待派單", TaskSituation.TRIAGE, "frontdesk"),

        HOUSEKEEPING_POOL("房務待認領", TaskSituation.POOL, "housekeeping"),

        ENGINEERING_POOL("工務待認領", TaskSituation.POOL, "engineering"),

        HOUSEKEEPING_ACCEPTED("房務已接", TaskSituation.ACCEPTED_BY_CLAIM, "housekeeping"),

        ENGINEERING_ACCEPTED("工務已接", TaskSituation.ACCEPTED_BY_CLAIM, "engineering"),

        DEFERRED("延後中", TaskSituation.DEFERRED_BY_MANAGER, "housekeeping"),

        DOING("進行中", TaskSituation.DOING, null),

        INTERRUPTED("中斷", TaskSituation.INTERRUPTED_BY_LEAVE, null),

        DONE_AWAITING_INSPECTION("完成等查房", TaskSituation.DONE_AWAITING_INSPECTION, "housekeeping"),

        DONE_AWAITING_VERIFICATION("已完修等驗收", TaskSituation.DONE_AWAITING_VERIFICATION, "engineering"),

        DONE("已完成", TaskSituation.DONE, null),

        INSPECTION_PASSED("查房通過", TaskSituation.INSPECTION_PASSED, "housekeeping"),

        CLOSED("已結案", TaskSituation.CLOSED_VERIFIED, "engineering"),

        CANCELLED_BY_GUEST("已取消（住客收回）", TaskSituation.CANCELLED_BY_GUEST, null),

        CANCELLED_BY_DESK("已取消（櫃檯取消）", TaskSituation.CANCELLED_BY_MANAGER, null),

        ANSWERED("已回答", TaskSituation.ANSWERED, "frontdesk");

        private final String term;
        private final TaskSituation taskSituation;
        private final String dept;

        Situation(String term, TaskSituation taskSituation, String dept) {
            this.term = term;
            this.taskSituation = taskSituation;
            this.dept = dept;
        }

        public String term() {
            return term;
        }

        public TaskSituation taskSituation() {
            return taskSituation;
        }

        public boolean dispatchedToEngineering() {
            return "engineering".equals(dept);
        }

        public static Situation ofTerm(String term) {
            for (Situation situation : values()) {
                if (situation.term.equals(term)) {
                    return situation;
                }
            }
            throw new IllegalArgumentException("找不到「" + term + "」在住客需求的狀況對照表中的權威值");
        }
    }
}
