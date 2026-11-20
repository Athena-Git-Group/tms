package com.athena.bddsupport;

import com.athena.bddsupport.BackendChannel.CreatedStaff;

import java.time.OffsetDateTime;

/**
 * 住客這次住宿裡一張事先備好的需求，交給 {@link BackendChannel#seedGuestRequest} 建立。
 *
 * <p>和 {@link TaskSituationSpec} 一樣只收後端認得的值與已經算好的時刻，例如 {@code type} 給 {@code amenity}；
 * 沒寫的欄位要補成什麼是句型自己的規則，這裡不替呼叫端補。
 * {@code assignee}、{@code acceptedAt}、{@code startedAt} 只有已經有人接手的狀況要給，
 * {@code answer} 只有已回答要給。
 */
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

    /**
     * 這張需求所在的部門。
     *
     * <p>狀況本身決定部門的照狀況；否則跟著負責人；沒有負責人的已取消需求照類型，
     * 打字送出的其他類需求在待派單時就被取消，所以還在櫃檯。
     */
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

    /**
     * 住客頁驗收規格「需求的狀況對照」的一列。每一種都對到後端既有的狀況 {@link TaskSituation}，
     * 再加上那一列寫死的部門；沒有寫死部門的，部門由 {@link #dept()} 決定。
     */
    public enum Situation {

        TRIAGE("待派單", TaskSituation.TRIAGE, "frontdesk"),

        HOUSEKEEPING_POOL("房務待認領", TaskSituation.POOL, "housekeeping"),

        ENGINEERING_POOL("工務待認領", TaskSituation.POOL, "engineering"),

        HOUSEKEEPING_ACCEPTED("房務已接", TaskSituation.ACCEPTED_BY_CLAIM, "housekeeping"),

        ENGINEERING_ACCEPTED("工務已接", TaskSituation.ACCEPTED_BY_CLAIM, "engineering"),

        DEFERRED("延後中", TaskSituation.DEFERRED_BY_MANAGER, "housekeeping"),

        DOING("進行中", TaskSituation.DOING, null),

        INTERRUPTED("中斷", TaskSituation.INTERRUPTED_BY_LEAVE, null),

        DONE("已完成", TaskSituation.DONE, null),

        INSPECTION_PASSED("查房通過", TaskSituation.INSPECTION_PASSED, "housekeeping"),

        CLOSED("已結案", TaskSituation.CLOSED_VERIFIED, "engineering"),

        CANCELLED_BY_GUEST("已取消（住客收回）", TaskSituation.CANCELLED_BY_GUEST, null),

        CANCELLED_BY_DESK("已取消（櫃檯取消）", TaskSituation.CANCELLED_BY_MANAGER, null),

        /** 櫃檯當場回答，回答當下就結束；後端的樣子是 {@code done} 帶 {@code answer}、部門是櫃檯。 */
        ANSWERED("已回答", TaskSituation.ANSWERED, "frontdesk");

        private final String term;
        private final TaskSituation taskSituation;
        private final String dept;

        Situation(String term, TaskSituation taskSituation, String dept) {
            this.term = term;
            this.taskSituation = taskSituation;
            this.dept = dept;
        }

        /** 驗收規格裡這個狀況的說法。 */
        public String term() {
            return term;
        }

        public TaskSituation taskSituation() {
            return taskSituation;
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
