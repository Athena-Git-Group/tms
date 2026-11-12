package com.athena.app.bdd.steps.manager;

/**
 * 主管工作台模組的情境內共用狀態：目前打開的是哪一類清單（待查房、待驗收），
 * 以及查房畫面上各項目的判定。清單斷言的「依序為」不帶類別參數，只能由點格子的
 * When 把選擇留下來；Cucumber 以 constructor injection 讓同情境的 step 類別共用同一個實例。
 */
public class ManagerContext {

    /** 目前打開的清單（契約 ManagerQueues 的鍵：inspection、verify）；還沒點過為 null。 */
    private String openedQueue;

    public void openQueue(String queueKey) {
        this.openedQueue = queueKey;
    }

    public String openedQueue() {
        return openedQueue;
    }

    /** 查房畫面上已點過的判定：顯示名稱 → 通過／不通過；打開查房畫面時清空。 */
    private final java.util.Map<String, String> judgements = new java.util.LinkedHashMap<>();

    public void recordJudgement(String itemLabel, String verdict) {
        judgements.put(itemLabel, verdict);
    }

    public void clearJudgements() {
        judgements.clear();
    }

    public java.util.Map<String, String> judgements() {
        return java.util.Collections.unmodifiableMap(judgements);
    }

    /**
     * 主管動作之前各類清單的張數（契約鍵 → counts），給介面根的「畫面回到清單，某單不在清單上」
     * 比「對應格數比操作前少 1」用。那一句在動作之後才跑，操作前的數字只能由打開清單或點格子
     * 的句子留下來；沒有留的話那一句略過少 1 的比對，只驗其餘必查。
     */
    private final java.util.Map<String, Integer> queueCountsBefore = new java.util.LinkedHashMap<>();

    public void recordQueueCountBefore(String queueKey, int count) {
        queueCountsBefore.put(queueKey, count);
    }

    /** 某類清單在動作前的張數；沒有記過回 null。 */
    public Integer queueCountBefore(String queueKey) {
        return queueCountsBefore.get(queueKey);
    }

    /** 查房畫面目前開著哪一間房（房號）；還沒從待查房清單點進任何一間為 null。 */
    private String inspectingRoomNo;

    public void openInspection(String roomNo) {
        this.inspectingRoomNo = roomNo;
    }

    public String inspectingRoomNo() {
        return inspectingRoomNo;
    }
}
