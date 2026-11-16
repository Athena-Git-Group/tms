package com.athena.bddsupport;

/**
 * 櫃檯看板上一張單可能處在的狀況，一個值對應驗收規格「單的狀況對照」的一列。
 *
 * <p>狀況不是新的狀態值：每一種都是後端既有的狀態，再加上分辨它所需要的那一點歷程。
 * 例如四種「已接」的狀態都是 {@code accepted}，差別只在最近一筆到手的事件是誰做的。
 * {@link BackendChannel#createTaskInSituation} 依這個值決定要寫出哪一串事件。
 */
public enum TaskSituation {

    /** 住客打字的需求沒有命中分類規則，等櫃檯派單。 */
    TRIAGE("待派單"),

    /** 在部門的待認領裡，還沒有負責人。 */
    POOL("待認領"),

    /** 班前排房時由系統指派給負責人。 */
    ACCEPTED_BY_PLAN("已接（班前排房）"),

    /** 負責人自己從待認領認領。 */
    ACCEPTED_BY_CLAIM("已接（自己認領）"),

    /** 主管從待認領指派給負責人。 */
    ACCEPTED_BY_MANAGER("已接（主管指派）"),

    /** 另一位同仁先認領，之後主管轉派給現在的負責人。 */
    ACCEPTED_BY_REASSIGN("已接（被轉派）"),

    DOING("進行中"),

    /** 打掃單做完了，等主管查房；還不算結束。 */
    DONE_AWAITING_INSPECTION("待查房"),

    /** 工務單修完了，等主管驗收；還不算結束。 */
    DONE_AWAITING_VERIFICATION("已完修"),

    /** 備品或其他類的單做完了，完成當下就結束。 */
    DONE("已完成"),

    /** 做到一半停住，不是負責人自己按的，例如他請假了。 */
    INTERRUPTED_BY_LEAVE("中斷（請假）"),

    /** 負責人自己按了暫停。 */
    INTERRUPTED_BY_SELF("中斷（本人暫停）"),

    DEFERRED_BY_MANAGER("延後中（主管延後）"),

    DEFERRED_BY_SELF("延後中（本人延後）"),

    INSPECTION_PASSED("查房通過"),

    /** 開工時就鎖定免查房的打掃單，完成當下直接結束。 */
    INSPECTION_WAIVED("免查房"),

    CLOSED_VERIFIED("已結案（驗收通過）"),

    /** 工務判斷不需處理，完成當下直接結案，不經主管驗收。 */
    CLOSED_NO_ACTION_NEEDED("已結案（不需處理）"),

    CANCELLED_BY_GUEST("已取消（住客收回）"),

    CANCELLED_BY_MANAGER("已取消（主管取消）"),

    CANCELLED_PLAN_SUPERSEDED("已取消（排房被取代）"),

    /** 報修的同仁在工務接手之前自己撤回。 */
    CANCELLED_BY_REPORTER("已取消（同仁撤回）"),

    /** 櫃檯當場回答住客，回答當下就結束，不進任何部門。 */
    ANSWERED("已回答");

    private final String term;

    TaskSituation(String term) {
        this.term = term;
    }

    /** 驗收規格裡這個狀況的說法。 */
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
