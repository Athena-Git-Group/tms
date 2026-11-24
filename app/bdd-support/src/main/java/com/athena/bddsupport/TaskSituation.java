package com.athena.bddsupport;

public enum TaskSituation {

    TRIAGE("待派單"),

    POOL("待認領"),

    ACCEPTED_BY_PLAN("已接（班前排房）"),

    ACCEPTED_BY_CLAIM("已接（自己認領）"),

    ACCEPTED_BY_MANAGER("已接（主管指派）"),

    ACCEPTED_BY_REASSIGN("已接（被轉派）"),

    DOING("進行中"),

    DONE_AWAITING_INSPECTION("待查房"),

    DONE_AWAITING_VERIFICATION("已完修"),

    DONE("已完成"),

    INTERRUPTED_BY_LEAVE("中斷（請假）"),

    INTERRUPTED_BY_SELF("中斷（本人暫停）"),

    DEFERRED_BY_MANAGER("延後中（主管延後）"),

    DEFERRED_BY_SELF("延後中（本人延後）"),

    INSPECTION_PASSED("查房通過"),

    INSPECTION_WAIVED("免查房"),

    CLOSED_VERIFIED("已結案（驗收通過）"),

    CLOSED_NO_ACTION_NEEDED("已結案（不需處理）"),

    CANCELLED_BY_GUEST("已取消（住客收回）"),

    CANCELLED_BY_MANAGER("已取消（主管取消）"),

    CANCELLED_PLAN_SUPERSEDED("已取消（排房被取代）"),

    CANCELLED_BY_REPORTER("已取消（同仁撤回）"),

    ANSWERED("已回答");

    private final String term;

    TaskSituation(String term) {
        this.term = term;
    }

    public String term() {
        return term;
    }

    public static TaskSituation ofTerm(String term) {
        for (TaskSituation situation : values()) {
            if (situation.term.equals(term)) {
                return situation;
            }
        }
        throw new IllegalArgumentException("找不到「" + term + "」在單的狀況對照表中的權威值");
    }
}
